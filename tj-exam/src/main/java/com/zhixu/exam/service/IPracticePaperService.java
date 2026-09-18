package com.zhixu.exam.service;

import com.zhixu.api.dto.exam.PracticeUpsertDTO;

/**
 * 随堂练习卷（p15 方案 A「配题即出卷」）
 * -----------------------------------------------------------------------------
 * 为什么需要它：平台上有两套「小节上的题」——
 *   ① 建课向导第④步配题 → `course_cata_subject_draft`（课程库）
 *   ② 学生取卷 `POST /es/exams/section/{id}/start` → `exam.section_id`（考试库）
 * 在 p15 之前这两者**从未接上**：老师配的题永远不会变成卷子，学生一道也看不到。
 * 这个服务就是把 ① 同步成 ② —— 幂等键是小节id，一节一张练习卷。
 */
public interface IPracticePaperService {

    /**
     * 幂等 upsert 一张随堂练习卷。
     * - 已有该小节的卷 → 更新题目并**重新冻结快照**（历史作答记录各自留着自己那份快照，不受影响）
     * - 没有 → 新建（`exam_type=2`、立即发布、含快照）
     * - `questionIds` 为空 → **停用**（status=3，不硬删：历史作答/成绩还挂在它上面）
     *
     * @return 练习卷 id；停用且原本没有卷时返回 null
     */
    Long upsert(PracticeUpsertDTO dto);
}
