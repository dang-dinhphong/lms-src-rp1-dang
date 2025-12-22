package jp.co.sss.lms.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.entity.TStudentAttendance;

/**
 * 勤怠情報（受講生入力）テーブルマッパー
 * 
 * @author 東京ITスクール
 */
@Mapper
public interface TStudentAttendanceMapper {

	/**
	 * 勤怠情報（受講生入力）取得（LMSユーザーID）
	 * 
	 * @param lmsUserId
	 * @param deleteFlg
	 * @return 勤怠情報（受講生入力）エンティティ
	 */
	List<TStudentAttendance> findByLmsUserId(@Param("lmsUserId") Integer lmsUserId,
			@Param("deleteFlg") Short deleteFlg);

	/**
	 * 勤怠情報（受講生入力）取得（LMSユーザーID＆日付）
	 * 
	 * @param lmsUserId
	 * @param trainingDate
	 * @param deleteFlg
	 * @return 勤怠情報（受講生入力）エンティティ
	 */
	TStudentAttendance findByLmsUserIdAndTrainingDate(@Param("lmsUserId") Integer lmsUserId,
			@Param("trainingDate") Date trainingDate, @Param("deleteFlg") Short deleteFlg);

	/**
	 * 勤怠管理画面用DTOリスト取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @param deleteFlg
	 * @return 勤怠管理画面用DTOリスト
	 */
	List<AttendanceManagementDto> getAttendanceManagement(@Param("courseId") Integer courseId,
			@Param("lmsUserId") Integer lmsUserId, @Param("deleteFlg") Short deleteFlg);

	/**
	 * 勤怠情報（受講生入力）登録
	 * 
	 * @param tStudentAttendance
	 * @return 登録結果
	 */
	Boolean insert(TStudentAttendance tStudentAttendance);

	/**
	 * 勤怠情報（受講生入力）更新
	 * 
	 * @param tStudentAttendance
	 * @return 更新結果
	 */
	Boolean update(TStudentAttendance tStudentAttendance);
	
	
	/**
	 * Task25：過去未入力の場合の表示
	 * 過去日未入力件数をカウントする
	 * @author ダンディンフォン
	 * @param lmsUserId
	 * @param deleteFlg
	 * @param dateUtil
	 * @return カウント数
	 */
	Integer notEnterCount(@Param("lmsUserId") Integer lmsUserId,
			@Param("deleteFlg") Short deleteFlg, @Param("trainingDate") Date trainingDate);

}
