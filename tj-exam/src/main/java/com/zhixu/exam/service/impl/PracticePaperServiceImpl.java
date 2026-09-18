package com.zhixu.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhixu.api.dto.exam.PracticeUpsertDTO;
import com.zhixu.api.dto.exam.QuestionBizDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.StringUtils;
import com.zhixu.exam.domain.po.Exam;
import com.zhixu.exam.domain.po.ExamItem;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import com.zhixu.exam.domain.po.Question;
import com.zhixu.exam.domain.po.QuestionBiz;
import com.zhixu.exam.mapper.ExamMapper;
import com.zhixu.exam.service.IExamService;
import com.zhixu.exam.service.IPracticePaperService;
import com.zhixu.exam.service.IQuestionBizService;
import com.zhixu.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 随堂练习卷实现（p15 方案 A）
 * -----------------------------------------------------------------------------
 * 三条纪律：
 *   1. **幂等**：幂等键是 `section_id`，老师反复改配题只会更新同一张卷（不会越配越多）。
 *   2. **快照只走 {@link IExamService#buildSnapshot}**：与正式考试的发布共用一处实现，
 *      判分/回看/批改/统计读的就是它。
 *   3. **停用不硬删**：题被清空时置 status=3，历史作答与成绩仍可查（学生的答卷里还有他那份快照）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticePaperServiceImpl implements IPracticePaperService {

    /** 类型：随堂练习（1 = 正式考试） */
    private static final int TYPE_PRACTICE = 2;
    private static final int STATUS_PUBLISHED = 1;
    /** 已结束 —— 这里用来表示「这一节的练习被停用了」 */
    private static final int STATUS_FINISHED = 3;
    /** 题库没给分值时，每题按 5 分（与建课向导的默认口径一致） */
    private static final int DEFAULT_SCORE = 5;
    /**
     * 随堂练习**不限时**。列定义是 `duration int NOT NULL DEFAULT 90`，
     * 不显式写就会漏出默认的 90 —— 学生会看到「限时 90 分钟」而实际上没有限时（实测踩过）。
     * 0 = 不限时，前端 `v-if="paperInfo.duration"` 自然不显示。
     */
    private static final int NO_LIMIT = 0;
    private static final String NOTICE = "随堂练习：只能提交一次，提交后自动判分。";

    private final ExamMapper examMapper;
    private final IQuestionService questionService;
    private final IExamService examService;
    private final IQuestionBizService questionBizService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upsert(PracticeUpsertDTO dto) {
        if (dto == null || dto.getSectionId() == null || dto.getCourseId() == null) {
            throw new BadRequestException("同步随堂练习失败：缺少课程或小节信息");
        }
        if (dto.getOperatorId() == null) {
            // exam.creater 是 NOT NULL：宁可在这里报一句人话，也不要抛 SQL 约束错误
            throw new BadRequestException("同步随堂练习失败：缺少操作人信息");
        }
        Exam exist = examMapper.selectBySection(dto.getSectionId());
        List<Long> qIds = CollUtils.emptyIfNull(dto.getQuestionIds()).stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();

        // ------------------------------------------------------------------
        // 同步 question_biz：这是**学生端「练习」入口的唯一依据**。
        // 课程目录接口 `/cs/courses/{id}/catalogs` 的 subjectNum 走
        // `ExamClient.queryQuestionIdsByBizIds` → 读的就是这张表；前端 `subjectNum > 0`
        // 才在小节上显示「练习」按钮。**只建卷不写它 = 学生看不到入口**（本次实测踩到）。
        // ------------------------------------------------------------------
        syncQuestionBiz(dto.getSectionId(), qIds);

        // ---- 题被清空：停用，不硬删（已有作答记录里留着各自那份快照） ----
        if (qIds.isEmpty()) {
            if (exist != null && !Integer.valueOf(STATUS_FINISHED).equals(exist.getStatus())) {
                exist.setStatus(STATUS_FINISHED).setUpdateTime(now);
                examMapper.updateById(exist);
                log.info("随堂练习停用：sectionId={}, examId={}", dto.getSectionId(), exist.getId());
            }
            return exist == null ? null : exist.getId();
        }

        List<ExamItem> items = buildItems(qIds);
        List<ExamSnapshotItem> snapshot = examService.buildSnapshot(items);
        int total = snapshot.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore()).sum();

        if (exist == null) {
            Exam exam = new Exam()
                    .setName(paperName(dto))
                    .setCourseId(dto.getCourseId())
                    .setCourseName(dto.getCourseName())
                    .setSectionId(dto.getSectionId())
                    .setExamType(TYPE_PRACTICE)
                    .setStatus(STATUS_PUBLISHED)
                    .setPassScore(passScore(total))
                    .setDuration(NO_LIMIT)
                    .setItems(items)
                    .setSnapshotItems(snapshot)
                    .setPaperVersion("v1")
                    .setSnapshotAt(now)
                    .setSubmittedCount(0)
                    .setNotice(NOTICE)
                    .setCreater(dto.getOperatorId())
                    .setCreateTime(now)
                    .setUpdateTime(now);
            examMapper.insert(exam);
            log.info("随堂练习生成：sectionId={}, examId={}, 题数={}", dto.getSectionId(), exam.getId(), items.size());
            return exam.getId();
        }

        // ---- 已有：更新题目 + 重新冻结快照（配题改了，练习卷就该跟着改） ----
        exist.setName(paperName(dto))
                .setCourseId(dto.getCourseId())
                .setCourseName(dto.getCourseName())
                .setSectionId(dto.getSectionId())
                .setExamType(TYPE_PRACTICE)
                .setStatus(STATUS_PUBLISHED)
                .setPassScore(passScore(total))
                .setDuration(NO_LIMIT)
                .setItems(items)
                .setSnapshotItems(snapshot)
                .setPaperVersion(bumpVersion(exist.getPaperVersion()))
                .setSnapshotAt(now)
                .setNotice(NOTICE)
                .setUpdateTime(now);
        examMapper.updateById(exist);
        log.info("随堂练习更新：sectionId={}, examId={}, 题数={}", dto.getSectionId(), exist.getId(), items.size());
        return exist.getId();
    }

    /**
     * bizId(小节) → questionId 的关联表同步（先清后插）。
     * ⚠️ `saveQuestionBizInfoBatch` 在**空列表时直接 return**，清空场景得自己删，
     *    否则"老师把题删光"后小节上还挂着一个空的「练习」入口。
     */
    private void syncQuestionBiz(Long sectionId, List<Long> qIds) {
        if (CollUtils.isEmpty(qIds)) {
            questionBizService.remove(Wrappers.<QuestionBiz>lambdaQuery().eq(QuestionBiz::getBizId, sectionId));
            return;
        }
        List<QuestionBizDTO> list = qIds.stream()
                .map(qid -> new QuestionBizDTO().setBizId(sectionId).setQuestionId(qid))
                .collect(Collectors.toList());
        questionBizService.saveQuestionBizInfoBatch(list);
    }

    /** 引用 → 分值：分值取题库该题自己的分值（没给就按默认 5 分） */
    private List<ExamItem> buildItems(List<Long> qIds) {
        Map<Long, Integer> scoreMap = CollUtils.emptyIfNull(questionService.listByIds(qIds)).stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(Question::getId, q -> scoreOf(q), (a, b) -> a));
        List<ExamItem> items = new ArrayList<>(qIds.size());
        for (Long qid : qIds) {
            items.add(new ExamItem().setQuestionId(qid).setScore(scoreMap.getOrDefault(qid, DEFAULT_SCORE)));
        }
        return items;
    }

    private int scoreOf(Question q) {
        return q.getScore() == null || q.getScore() <= 0 ? DEFAULT_SCORE : q.getScore();
    }

    /** 练习卷不设「及格线」这个概念上的仪式，但统计要算通过率 → 按满分 60% 存 */
    private int passScore(int total) {
        return total <= 0 ? 0 : (int) Math.round(total * 0.6);
    }

    private String paperName(PracticeUpsertDTO dto) {
        if (StringUtils.isNotBlank(dto.getSectionName())) {
            return dto.getSectionName();
        }
        return StringUtils.isBlank(dto.getCourseName()) ? "随堂练习" : dto.getCourseName() + " 随堂练习";
    }

    /** v1 → v2：配题改动就让版本往上走，便于日后排查"学生考的是哪一版" */
    private String bumpVersion(String current) {
        if (StringUtils.isBlank(current) || !current.startsWith("v")) {
            return "v1";
        }
        try {
            return "v" + (Integer.parseInt(current.substring(1)) + 1);
        } catch (NumberFormatException e) {
            return "v1";
        }
    }
}
