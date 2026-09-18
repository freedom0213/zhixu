package com.zhixu.course.service;

import com.zhixu.course.domain.dto.TeacherCourseDraftDTO;
import com.zhixu.course.domain.vo.TeacherCourseDraftVO;
import com.zhixu.course.domain.vo.TeacherMyCourseVO;

import java.util.List;

/**
 * 教师端建课 · 契约适配服务（契约 §13）
 * -----------------------------------------------------------------------------
 * 这一层不做业务实现，只做「契约形状 ↔ 老模型」的转换，内部全部转调既有服务：
 *   ICourseDraftService / ICourseCatalogueDraftService / ICourseTeacherDraftService
 * 好处：老站的课程数据模型（草稿 → 上架 copyToShelf）完整复用，
 * 教师端契约不因老模型实现细节而漂移。
 */
public interface ITeacherCourseDraftService {

    /** 读取草稿（courseId 为空 = 新建底稿） */
    TeacherCourseDraftVO getDraft(Long courseId);

    /** 步骤① 保存基本信息；返回课程id（新建时是新建出来的 id） */
    Long saveBasic(TeacherCourseDraftDTO.BasicSaveDTO dto);

    /** 步骤② 保存目录；返回保存后的目录（**含服务端分配的真实 id**，前端需据此更新本地树） */
    TeacherCourseDraftVO saveCatalog(TeacherCourseDraftDTO.CatalogSaveDTO dto);

    /** 步骤③ 登记小节视频；返回时长文本（服务端登记的值） */
    String saveSectionVideo(Long sectionId, TeacherCourseDraftDTO.SectionVideoDTO dto);

    /** 步骤③ 试看开关 */
    Boolean toggleSectionPreview(Long sectionId, Long courseId);

    /** 步骤④ 小节配题（引用题库题目） */
    void saveSectionQuiz(Long sectionId, TeacherCourseDraftDTO.SectionQuizDTO dto);

    /** 步骤⑤ 添加讲师 */
    TeacherCourseDraftVO.TeacherItem addTeacher(TeacherCourseDraftDTO.TeacherSaveDTO dto);

    /** 步骤⑤ 移除讲师 */
    void removeTeacher(Long courseId, Long teacherId);

    /** 提交上架：基本信息 / 目录不完整 → 422；视频 / 配题只警告 */
    TeacherCourseDraftVO.Checks publish(Long courseId);

    /**
     * 「我的课程」：当前登录老师讲的课（正式表）+ 建的课（草稿表），合并去重。
     * 只返回真实存在的字段；跨服务的聚合（学生数 / 考试场次）不在这里编造。
     */
    List<TeacherMyCourseVO> listMyCourses();
}
