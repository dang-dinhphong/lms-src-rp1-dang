package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * Task25：過去未入力の場合の表示
	 * 過去未入力箇所を判定する
	 * 
	 * @author ダンディンフォン
	 * @param lmsUserId 受験生番号
	 * @return 取得した未入力カウント数が0より大きい場合、trueを返し、過去日未入力確認ダイアログを表示
	 * それ以外はfalseを返す
	 * 
	 * @throws ParseException 日付のフォーマット・変換に必要
	 */
	public boolean emptiedAttendanceCheck(Integer lmsUserId) throws ParseException {

		//現在日付取得
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		String today = sdf.format(date);
		Date trainingDate = sdf.parse(today);

		//過去日の未入力数をカウント
		Integer countEmpty = tStudentAttendanceMapper
				.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, trainingDate);

		return countEmpty > 0;

	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 * @throws ParseException 
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) throws ParseException {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());

		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());

		//Task26
		/*勤怠FORM．中抜け時間（選択肢）	＝	勤怠Utilを使用して選択肢用の中抜け時間マップを取得
		 * 既存
		 * */
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		/** 勤怠FORM．時間マップ（選択肢）   ＝	勤怠Utilを使用して選択肢用の時間マップを取得 
		 * 勤怠FORM．分マップ（選択肢）	    ＝	勤怠Utilを使用して選択肢用の分マップを取得
		 * */
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));

			dailyAttendanceForm.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());

			dailyAttendanceForm.setTrainingStartTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm.setTrainingStartTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));

			dailyAttendanceForm.setTrainingEndTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm.setTrainingEndTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}

			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
			System.out.println(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());

			//出勤退勤時間手入力
			// 出勤時刻整形
			//			TrainingTime trainingStartTime = null;
			//			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			//			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			//退勤時刻整形
			//			TrainingTime trainingEndTime = null;
			//			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			//			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());

			//Task26：出勤・退勤時間の入力方法変更
			TrainingTime trainingStartTime = null;
			TrainingTime trainingEndTime = null;

			if (dailyAttendanceForm.getTrainingStartTimeHour() == null
					|| dailyAttendanceForm.getTrainingStartTimeMinute() == null) {
				tStudentAttendance.setTrainingStartTime("");
			} else {
				String trainingStartTimeStr = String.format("%1$02d:%2$02d",
						dailyAttendanceForm.getTrainingStartTimeHour(),
						dailyAttendanceForm.getTrainingStartTimeMinute());
				trainingStartTime = new TrainingTime(trainingStartTimeStr);
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			}

			if (dailyAttendanceForm.getTrainingEndTimeHour() == null
					|| dailyAttendanceForm.getTrainingEndTimeMinute() == null) {
				tStudentAttendance.setTrainingEndTime("");
			} else {
				String trainingEndTimeStr = String.format("%1$02d:%2$02d",
						dailyAttendanceForm.getTrainingEndTimeHour(),
						dailyAttendanceForm.getTrainingEndTimeMinute());
				trainingEndTime = new TrainingTime(trainingEndTimeStr);
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			}

			// 中抜け時間
//			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			if (dailyAttendanceForm.getBlankTime() == null) {
			    tStudentAttendance.setBlankTime(0);
			} else {
			    tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			}
			
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());

			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);

			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * Task 27：入力チェック
	 * @author ダンディンフォン
	 * @param attendanceForm 勤怠フォーム
	 * @param result 発生したエラーに対するエラーメッセージをBindingResultに追加
	 */
	public void inputCheck(AttendanceForm attendanceForm, BindingResult result) {
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm daily = attendanceForm.getAttendanceList().get(i);

			//すべて入力がない行はスキップ
			if (daily.getTrainingStartTimeHour() == null
					&& daily.getTrainingStartTimeMinute() == null
					&& daily.getTrainingEndTimeHour() == null
					&& daily.getTrainingEndTimeMinute() == null
					&& (daily.getNote() == null || daily.getNote().isEmpty())) {
				continue;
			}

			//ステータス文字数上限チェック
			if (daily.getNote() != null && daily.getNote().length() > 100) {
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].note",
						messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH)));
			}

			//出勤時間の「時」と「分」が両方とも入力ありチェック
			if ((daily.getTrainingStartTimeHour() == null) != (daily.getTrainingStartTimeMinute() == null)) {
				String startMsg = messageUtil.getMessage(Constants.INPUT_INVALID, new String[] { "出勤時間" });
				
				if (daily.getTrainingStartTimeHour() == null) {
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour", startMsg));
			    }
			    if (daily.getTrainingStartTimeMinute() == null) {
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeMinute", startMsg));
			    }
			}

			//退勤時間の「時」と「分」が両方とも入力ありチェック
			if ((daily.getTrainingEndTimeHour() == null) != (daily.getTrainingEndTimeMinute() == null)) {
				String endMsg = messageUtil.getMessage(Constants.INPUT_INVALID, new String[] { "退勤時間" });
				if (daily.getTrainingEndTimeHour() == null) {
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeHour", endMsg));
			    }
			    if (daily.getTrainingEndTimeMinute() == null) {
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeMinute", endMsg));
			    }
			}
			
			boolean isPunchIn = daily.getTrainingStartTimeHour() != null
					&& daily.getTrainingStartTimeMinute() != null;
			boolean isPunchOut = daily.getTrainingEndTimeHour() != null
					&& daily.getTrainingEndTimeMinute() != null;
			
			//出勤時間に入力なし＆退勤時間に入力ありチェック
			if (!isPunchIn && isPunchOut) {
				String noPunchInMsg = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY,new String[] { "退勤時間" });
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour",noPunchInMsg));
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeMinute", noPunchInMsg));
			}
			
			//出勤時間・退勤時間両方入力有り
			if (isPunchIn && isPunchOut) {
			    //出勤・退勤時間を計算する
			    int startTotal = (daily.getTrainingStartTimeHour() * 60) + daily.getTrainingStartTimeMinute();
			    int endTotal = (daily.getTrainingEndTimeHour() * 60) + daily.getTrainingEndTimeMinute();

			    //退勤時間は出勤時間より後かどうかチェック
			    if (endTotal <= startTotal) {
			        String msg = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeHour", msg));
			        result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeMinute", msg));
			    }

			    //中抜け時間が終業時間を超えないかチェック
			    Integer rawBlankTime = daily.getBlankTime();
			    int blankTimeVal = (rawBlankTime != null) ? rawBlankTime : 0;
			    if (blankTimeVal > (endTotal - startTotal)) {
			        String blankMsg = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR);
			        result.addError(new FieldError(result.getObjectName(), 
			            "attendanceList[" + i + "].blankTime", blankMsg));
			    }
			}
		}
		//マップで再度ロード
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

	}
	
//	/**
//	 * Task 28：定時ボタン追加
//	 * @author ダンディンフォン
//	 * @param rowDate 押下した「定時」ボタンの日付を取得値
//	 * @return 定時で出勤・退勤登録し、勤怠画面に遷移し、メッセージを表示
//	 */
//	public String setFixedTime(Date rowDate) {
//	    Date now = new Date();
//	    
//	    TrainingTime startTime = new TrainingTime("09:00");
//	    TrainingTime endTime = new TrainingTime("18:00");
//	    AttendanceStatusEnum status = attendanceUtil.getStatus(startTime, endTime);
//
//	    //定時ボタンを押下したレコード（行）を取得
//	    TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
//	            .findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), rowDate, Constants.DB_FLG_FALSE);
//	    
//	    //定時ボタンを押下した行が未入力であれば登録処理
//	    if (tStudentAttendance == null) {
//	        tStudentAttendance = new TStudentAttendance();
//	        tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
//	        tStudentAttendance.setTrainingDate(rowDate);
//	        tStudentAttendance.setTrainingStartTime(startTime.toString());
//	        tStudentAttendance.setTrainingEndTime(endTime.toString());
//	        tStudentAttendance.setStatus(status.code);
//	        tStudentAttendance.setBlankTime(0);
//	        tStudentAttendanceMapper.insert(tStudentAttendance);
//	    } else {//入力有りの行は変更処理
//	        tStudentAttendance.setTrainingStartTime(startTime.toString());
//	        tStudentAttendance.setTrainingEndTime(endTime.toString());
//	        tStudentAttendance.setStatus(status.code);
//	        tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
//	        tStudentAttendance.setLastModifiedDate(now);
//	        tStudentAttendanceMapper.update(tStudentAttendance);
//	    }
//	    return "出勤時間と退勤時間は定時で登録しました。";
//
//	}

}
