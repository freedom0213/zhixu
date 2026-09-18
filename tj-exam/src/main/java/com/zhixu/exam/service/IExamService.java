package com.zhixu.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.exam.domain.dto.ExamFormDTO;
import com.zhixu.exam.domain.po.Exam;
import com.zhixu.exam.domain.po.ExamItem;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import com.zhixu.exam.domain.query.ExamPageQuery;
import com.zhixu.exam.domain.vo.ExamDetailVO;
import com.zhixu.exam.domain.vo.ExamPageVO;

import java.util.List;
import java.util.Map;

public interface IExamService extends IService<Exam> {

    /**
     * 新建草稿 —— 草稿只存「引用」，不生成快照（契约 §9.2）
     */
    Long saveExamDraft(ExamFormDTO dto);

    /**
     * 更新草稿（仅草稿可编辑，且只能本人编辑）
     */
    void updateExamDraft(Long id, ExamFormDTO dto);

    PageDTO<ExamPageVO> queryExamPage(ExamPageQuery query);

    /**
     * 状态计数（列表页筛选 chips；published 含批改中，与前端 mock 口径一致）
     */
    /** @param mine 只看我创建的（讲师端传 true） */
    Map<String, Integer> countExams(Boolean mine);

    /**
     * 详情：已发布读快照，草稿实时展开引用（回看类页面禁止直接查题库）
     */
    ExamDetailVO queryExamDetail(Long id);

    /**
     * 删除（仅本人草稿；已发布的考试有作答记录，只能结束不能删）
     */
    void deleteExam(Long id);

    /**
     * 发布 —— 服务端在这一步生成快照（paperVersion / snapshotItems），返回状态与冻结时间
     */
    Map<String, Object> publishExam(Long id, ExamFormDTO settings);

    /**
     * 按 items 冻结一份快照（题干/选项/答案/解析/分值全抄一份）
     * -----------------------------------------------------------------------------
     * 抽成公开方法是为了**只留一处实现**：正式考试的发布（{@link #publishExam}）与
     * 随堂练习的生成（p15 方案 A，由课程侧配题驱动）都必须走这里。
     * 各写一份迟早会分叉——而"判分只读快照"的纪律依赖这份快照的字段完整。
     */
    List<ExamSnapshotItem> buildSnapshot(List<ExamItem> items);
}
