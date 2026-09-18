package com.zhixu.exam.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.StringUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.exam.domain.dto.ExamFormDTO;
import com.zhixu.exam.domain.po.Exam;
import com.zhixu.exam.domain.po.ExamItem;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import com.zhixu.exam.domain.po.Question;
import com.zhixu.exam.domain.po.QuestionDetail;
import com.zhixu.exam.domain.query.ExamPageQuery;
import com.zhixu.exam.domain.vo.ExamDetailVO;
import com.zhixu.exam.domain.vo.ExamPageVO;
import com.zhixu.exam.mapper.ExamMapper;
import com.zhixu.exam.service.IExamService;
import com.zhixu.exam.service.IQuestionDetailService;
import com.zhixu.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 考试 服务实现（契约 §9）
 * 两条硬规则在实现里落地：
 *   1. 草稿存引用（exam.items），发布才生成快照（exam.snapshotItems）
 *   2. 已发布读快照，草稿实时展开引用 —— 回看类数据永远不直接查题库
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl extends ServiceImpl<ExamMapper, Exam> implements IExamService {

    private final IQuestionService questionService;
    private final IQuestionDetailService detailService;

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;

    @Override
    @Transactional
    public Long saveExamDraft(ExamFormDTO dto) {
        Long userId = UserContext.getUser();
        Exam exam = new Exam()
                .setName(dto.getName())
                .setCourseId(dto.getCourseId())
                .setCourseName(dto.getCourseName())
                .setStatus(STATUS_DRAFT)
                .setExamType(dto.getExamType() == null ? 1 : dto.getExamType())
                .setPassScore(dto.getPassScore() == null ? 60 : dto.getPassScore())
                .setDuration(dto.getDuration() == null ? 90 : dto.getDuration())
                .setNotice(dto.getNotice())
                .setStartAt(dto.getStartAt())
                .setEndAt(dto.getEndAt())
                .setItems(dto.getItems() == null ? new ArrayList<>() : dto.getItems())
                .setCreater(userId);
        save(exam);
        return exam.getId();
    }

    @Override
    @Transactional
    public void updateExamDraft(Long id, ExamFormDTO dto) {
        Exam exam = getById(id);
        if (exam == null) {
            throw new BadRequestException("考试不存在");
        }
        checkDraftOwner(exam);
        exam.setName(dto.getName())
                .setCourseId(dto.getCourseId())
                .setCourseName(dto.getCourseName())
                .setExamType(dto.getExamType() == null ? exam.getExamType() : dto.getExamType())
                .setPassScore(dto.getPassScore() == null ? exam.getPassScore() : dto.getPassScore())
                .setDuration(dto.getDuration() == null ? exam.getDuration() : dto.getDuration())
                .setNotice(dto.getNotice())
                .setStartAt(dto.getStartAt())
                .setEndAt(dto.getEndAt())
                .setItems(dto.getItems() == null ? exam.getItems() : dto.getItems());
        updateById(exam);
    }

    @Override
    public PageDTO<ExamPageVO> queryExamPage(ExamPageQuery query) {
        // 「我的考试」= 只看我建的（P18）。以前这里没有任何归属过滤，讲师能看到全平台的卷子。
        boolean mine = Boolean.TRUE.equals(query.getMine());
        Page<Exam> page = lambdaQuery()
                .eq(mine, Exam::getCreater, UserContext.getUser())
                .eq(query.getStatus() != null, Exam::getStatus, query.getStatus())
                .and(StringUtils.isNotBlank(query.getKeyword()), w -> w
                        .like(Exam::getName, query.getKeyword())
                        .or().like(Exam::getCourseName, query.getKeyword()))
                .page(query.toMpPage("update_time", false));
        List<Exam> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<ExamPageVO> list = new ArrayList<>(records.size());
        for (Exam e : records) {
            ExamPageVO vo = new ExamPageVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setCourseName(e.getCourseName());
            vo.setStatus(e.getStatus());
            vo.setExamType(e.getExamType());
            vo.setItemCount(e.getItems() == null ? 0 : e.getItems().size());
            vo.setTotalScore(sumScore(e.getItems()));
            vo.setEndAt(e.getEndAt());
            vo.setSubmittedCount(e.getSubmittedCount());
            vo.setItems(e.getItems());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    @Override
    public Map<String, Integer> countExams(Boolean mine) {
        Map<String, Integer> result = new HashMap<>(4);
        // 计数必须与列表同口径，否则「共 3 场」点进去只有 1 条
        List<Exam> all = Boolean.TRUE.equals(mine)
                ? lambdaQuery().eq(Exam::getCreater, UserContext.getUser()).list()
                : list();
        int draft = 0;
        int published = 0;
        int closed = 0;
        for (Exam e : all) {
            Integer s = e.getStatus();
            if (s == null || s == STATUS_DRAFT) {
                draft++;
            } else if (s == 2 || s == STATUS_PUBLISHED) {
                // 已发布与批改中合并为「进行中」，与前端 chips 口径一致
                published++;
            } else {
                closed++;
            }
        }
        result.put("all", all.size());
        result.put("draft", draft);
        result.put("published", published);
        result.put("closed", closed);
        return result;
    }

    @Override
    public ExamDetailVO queryExamDetail(Long id) {
        Exam exam = getById(id);
        if (exam == null) {
            throw new BadRequestException("考试不存在");
        }
        ExamDetailVO vo = new ExamDetailVO();
        vo.setId(exam.getId());
        vo.setName(exam.getName());
        vo.setCourseId(exam.getCourseId());
        vo.setCourseName(exam.getCourseName());
        vo.setStatus(exam.getStatus());
        vo.setExamType(exam.getExamType());
        vo.setPassScore(exam.getPassScore());
        vo.setDuration(exam.getDuration());
        vo.setNotice(exam.getNotice());
        vo.setStartAt(exam.getStartAt());
        vo.setEndAt(exam.getEndAt());
        vo.setItems(exam.getItems());
        vo.setPaperVersion(exam.getPaperVersion());
        vo.setSnapshotAt(exam.getSnapshotAt());
        vo.setSubmittedCount(exam.getSubmittedCount());
        vo.setExpandedItems(expandItems(exam));
        // 🔴 防泄题（P18）：展开后的题目里**带着正确答案与解析**，非创建者一律抹掉。
        //    草稿更严格：连卷面都不给看（草稿是还没定的题，只有作者该看到）。
        Long me = UserContext.getUser();
        boolean owner = me != null && Objects.equals(exam.getCreater(), me);
        if (!owner) {
            if (exam.getStatus() == null || exam.getStatus() == STATUS_DRAFT) {
                throw new BadRequestException("这场考试还是草稿，只有创建者能查看");
            }
            if (CollUtils.isNotEmpty(vo.getExpandedItems())) {
                // ⚠️ SnapshotItemVO 的 setter 不是链式的（只有 PO 用了 @Accessors(chain)），别写成链式调用
                vo.getExpandedItems().forEach(i -> {
                    i.setAnswer(null);
                    i.setAnalysis(null);
                });
            }
        }
        return vo;
    }

    /**
     * 展开题目：已发布读快照（历史卷面不可变），草稿实时展开引用（改题库草稿跟着对）
     */
    private List<ExamDetailVO.SnapshotItemVO> expandItems(Exam exam) {
        List<ExamDetailVO.SnapshotItemVO> out = new ArrayList<>();
        if (exam.getStatus() != null && exam.getStatus() >= STATUS_PUBLISHED
                && CollUtils.isNotEmpty(exam.getSnapshotItems())) {
            for (ExamSnapshotItem s : exam.getSnapshotItems()) {
                out.add(ExamDetailVO.fromSnapshot(s, s.getScore()));
            }
            return out;
        }
        if (CollUtils.isEmpty(exam.getItems())) {
            return out;
        }
        List<Long> qIds = exam.getItems().stream().map(ExamItem::getQuestionId).collect(Collectors.toList());
        List<Question> questions = questionService.listByIds(qIds);
        Map<Long, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        List<QuestionDetail> details = detailService.listByIds(qIds);
        Map<Long, QuestionDetail> dMap = details == null ? new HashMap<>() :
                details.stream().collect(Collectors.toMap(QuestionDetail::getId, d -> d));
        for (ExamItem item : exam.getItems()) {
            Question q = qMap.get(item.getQuestionId());
            QuestionDetail d = dMap.get(item.getQuestionId());
            ExamSnapshotItem snapshot = new ExamSnapshotItem()
                    .setQuestionId(item.getQuestionId())
                    .setScore(item.getScore())
                    .setType(q == null ? 1 : q.getType())
                    .setStem(q == null ? "（题目已不存在）" : q.getName())
                    .setOptions(d == null ? null : d.getOptions())
                    .setAnswer(d == null ? "" : d.getAnswer())
                    .setAnalysis(d == null ? "" : d.getAnalysis())
                    .setDifficulty(q == null ? 2 : q.getDifficulty())
                    .setCourseId(q == null ? null : q.getCateId3());
            out.add(ExamDetailVO.fromSnapshot(snapshot, item.getScore()));
        }
        return out;
    }

    @Override
    @Transactional
    public void deleteExam(Long id) {
        Exam exam = getById(id);
        if (exam == null) {
            throw new BadRequestException("考试不存在");
        }
        checkDraftOwner(exam);
        removeById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> publishExam(Long id, ExamFormDTO settings) {
        Long userId = UserContext.getUser();
        Exam exam = getById(id);
        if (exam == null) {
            throw new BadRequestException("考试不存在");
        }
        if (!exam.getCreater().equals(userId)) {
            throw new BadRequestException("只能发布自己创建的考试");
        }
        if (CollUtils.isEmpty(exam.getItems())) {
            throw new BadRequestException("还没选任何题目，无法发布");
        }
        if (StringUtils.isBlank(exam.getName()) || exam.getCourseId() == null) {
            throw new BadRequestException("考试名称与关联课程必须填写完整");
        }

        // 冻结：把每道题当时的内容抄一份进 snapshotItems（引用 → 快照的切换点）
        List<ExamSnapshotItem> snapshot = buildSnapshot(exam.getItems());

        // 发布方式：draft = 只冻结存草稿（不打扰学生），now = 立即公开
        String mode = settings == null ? "now" : settings.getPublishMode();
        boolean asDraft = "draft".equals(mode);
        exam.setStatus(asDraft ? STATUS_DRAFT : STATUS_PUBLISHED);
        exam.setSnapshotItems(snapshot);
        exam.setPaperVersion("v1");
        exam.setSnapshotAt(LocalDateTime.now());
        if (settings != null) {
            if (settings.getStartAt() != null) {
                exam.setStartAt(settings.getStartAt());
            }
            if (settings.getEndAt() != null) {
                exam.setEndAt(settings.getEndAt());
            }
        }
        updateById(exam);

        Map<String, Object> result = new HashMap<>(4);
        result.put("ok", true);
        result.put("status", exam.getStatus());
        result.put("paperVersion", exam.getPaperVersion());
        result.put("snapshotAt", exam.getSnapshotAt());
        return result;
    }

    /**
     * 冻结快照：题干 / 选项 / 标准答案 / 解析 / 分值 / 难度 全抄一份。
     * ⚠️ 判分（p14）与回看、批改、统计**都只读这份快照** —— 题库之后怎么改都不影响历史成绩。
     * 正式考试的发布与随堂练习的生成都走这里（单一实现，别在别处再抄一遍）。
     */
    @Override
    public List<ExamSnapshotItem> buildSnapshot(List<ExamItem> items) {
        if (CollUtils.isEmpty(items)) {
            return new ArrayList<>();
        }
        List<Long> qIds = items.stream().map(ExamItem::getQuestionId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Question> questions = CollUtils.emptyIfNull(questionService.listByIds(qIds));
        Map<Long, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        List<QuestionDetail> details = CollUtils.emptyIfNull(detailService.listByIds(qIds));
        Map<Long, QuestionDetail> dMap = details.stream()
                .collect(Collectors.toMap(QuestionDetail::getId, d -> d, (a, b) -> a));

        List<ExamSnapshotItem> snapshot = new ArrayList<>(items.size());
        for (ExamItem item : items) {
            Question q = qMap.get(item.getQuestionId());
            QuestionDetail d = dMap.get(item.getQuestionId());
            snapshot.add(new ExamSnapshotItem()
                    .setQuestionId(item.getQuestionId())
                    .setScore(item.getScore())
                    .setType(q == null ? 1 : q.getType())
                    .setStem(q == null ? "" : q.getName())
                    .setOptions(d == null ? null : d.getOptions())
                    .setAnswer(d == null ? "" : d.getAnswer())
                    .setAnalysis(d == null ? "" : d.getAnalysis())
                    .setDifficulty(q == null ? 2 : q.getDifficulty())
                    .setCourseId(q == null ? null : q.getCateId3()));
        }
        return snapshot;
    }

    private void checkDraftOwner(Exam exam) {        if (exam.getStatus() == null || exam.getStatus() != STATUS_DRAFT) {
            throw new BadRequestException("只有草稿可以编辑或删除");
        }
        Long userId = UserContext.getUser();
        if (!exam.getCreater().equals(userId)) {
            throw new BadRequestException("只能操作自己创建的考试");
        }
    }

    private Integer sumScore(List<ExamItem> items) {
        if (items == null) {
            return 0;
        }
        int total = 0;
        for (ExamItem item : items) {
            total += item.getScore() == null ? 0 : item.getScore();
        }
        return total;
    }
}
