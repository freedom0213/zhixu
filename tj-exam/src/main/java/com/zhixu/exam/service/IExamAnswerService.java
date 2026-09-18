package com.zhixu.exam.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.ExamSubmitDTO;
import com.zhixu.exam.domain.vo.ExamRecordPageVO;
import com.zhixu.exam.domain.vo.ExamResultVO;
import com.zhixu.exam.domain.vo.ExamStartVO;
import com.zhixu.exam.domain.vo.ExamStatisticsVO;
import com.zhixu.exam.domain.vo.PendingReviewSummaryVO;
import com.zhixu.exam.domain.vo.StudentCourseExamVO;

import java.util.List;

/**
 * 作答与判分（p14：把"学生答题 → 判分 → 记录 → 题库正确率"这条链路建起来）
 * -----------------------------------------------------------------------------
 * 两条纪律：
 *   1) **判分只读快照**（作答时冻结的那份），绝不查题库当前答案 ——
 *      这样讲师改题不会改坏历史成绩，兑现「发布才生成快照」的既定规则。
 *   2) **幂等**：重复交卷返回既有成绩，不重新判分、不重复累加题库计数。
 */
public interface IExamAnswerService {

    /** 按小节开始作答（随堂练习入口：学生从课程学习页的「考试」小节进入） */
    ExamStartVO startBySection(Long sectionId);

    /** 按试卷id开始作答 */
    ExamStartVO start(Long examId);

    /** 交卷判分（幂等） */
    ExamResultVO submit(Long examId, ExamSubmitDTO dto);

    /** 我的这场成绩（答卷回看，读作答时冻结的快照） */
    ExamResultVO myResult(Long examId);

    /**
     * 某门课程下**已发布**的试卷（学生视角，P17）+ 我的作答状态。
     * 学生端「课程学习页 · 考试页签」与「课程详情页 · 课程试卷」共用这一个查询。
     */
    List<StudentCourseExamVO> publishedOfCourse(Long courseId);

    /** 我的作答记录（学生端「在线考试」列表） */
    PageDTO<ExamRecordPageVO> myRecords(Integer pageNo, Integer pageSize);

    /** 某场考试的作答列表（讲师批改用） */
    /**
     * 待批改总览（讲师视角，P18）：我建的、已发布的卷里，各场「已交卷 / 待复核 / 已复核」的份数。
     * 只含**有人交过卷**的场次，且待复核多的排前面 —— 页面要回答的是"我现在该干什么"。
     */
    PendingReviewSummaryVO pendingReview();

    List<ExamRecordPageVO> recordsOfExam(Long examId);

    /** 某场考试的成绩统计（只计已交卷） */
    ExamStatisticsVO statistics(Long examId);

    /** 看某一条作答记录的答卷（讲师批改用；不做"只能看自己"的限制） */
    ExamResultVO resultOfRecord(Long recordId);

    /** 复核成绩（讲师动作；选择题全自动判分，讲师只是确认） */
    void review(Long recordId);
}
