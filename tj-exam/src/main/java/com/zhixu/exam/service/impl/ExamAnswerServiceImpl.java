package com.zhixu.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.exam.domain.dto.ExamSubmitDTO;
import com.zhixu.exam.domain.po.Exam;
import com.zhixu.exam.domain.po.ExamRecord;
import com.zhixu.exam.domain.po.ExamRecordDetail;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import com.zhixu.exam.domain.vo.ExamRecordPageVO;
import com.zhixu.exam.domain.vo.ExamResultVO;
import com.zhixu.exam.domain.vo.ExamStartVO;
import com.zhixu.exam.domain.vo.ExamStatisticsVO;
import com.zhixu.exam.domain.vo.PendingReviewItemVO;
import com.zhixu.exam.domain.vo.PendingReviewSummaryVO;
import com.zhixu.exam.domain.vo.StudentCourseExamVO;
import com.zhixu.exam.mapper.ExamMapper;
import com.zhixu.exam.mapper.ExamRecordDetailMapper;
import com.zhixu.exam.mapper.ExamRecordMapper;
import com.zhixu.exam.service.IExamAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 作答与判分实现（p14）
 * -----------------------------------------------------------------------------
 * 判分口径（与 p14 §4 一一对应）：
 *   1) 标准答案取**作答时冻结的快照**（exam_record.snapshot_items），不读题库当前答案；
 *   2) 学生答案与标准答案都归一成「升序编号串」（如 1,3）后字符串相等即算对；
 *   3) 多选题不给部分分；得分取快照里那题的分值；
 *   4) 及格线取 exam.pass_score（不写死 60）；
 *   5) 交卷成功后对卷内每题 answer_times +1、答对再 correct_times +1 —— 只在真正交卷时做一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAnswerServiceImpl implements IExamAnswerService {

    /** 试卷状态（exam.status）：0 草稿 / 1 已发布 / 3 已停用 */
    private static final int EXAM_DRAFT = 0;
    private static final int EXAM_PUBLISHED = 1;

    /** 记录状态：0 进行中（开始但未交卷），1 已交卷，2 已复核 */
    private static final int ST_DOING = 0;
    private static final int ST_SUBMITTED = 1;
    private static final int ST_REVIEWED = 2;
    /** 卷子状态：3 = 已结束（随堂练习被停用也用它） */
    private static final int ST_FINISHED = 3;

    private final ExamMapper examMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamRecordDetailMapper examRecordDetailMapper;
    private final UserClient userClient;

    // ------------------------------------------------------------------ 开始作答

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamStartVO startBySection(Long sectionId) {
        Exam exam = examMapper.selectBySection(sectionId);
        if (exam == null) {
            throw new BadRequestException("这个小节还没有配试卷");
        }
        return start(exam.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamStartVO start(Long examId) {
        Long uid = requireUser();
        Exam exam = requirePublished(examId);
        ExamRecord old = findRecord(examId, uid);
        if (old != null) {
            if (old.getStatus() != null && old.getStatus() >= ST_SUBMITTED) {
                // 「考试只能考一次」是业务规则，这里如实拒绝（唯一键也挡了一层）
                throw new BadRequestException("你已经交过这份卷子了，只能查看答卷");
            }
            // 进行中：返回同一份快照，刷新页面不会换题、也不会新建记录
            return buildStart(exam, old);
        }
        List<ExamSnapshotItem> snapshot = snapshotOf(exam, null);
        if (CollUtils.isEmpty(snapshot)) {
            throw new BadRequestException("这份卷子里还没有题目");
        }
        LocalDateTime now = LocalDateTime.now();
        ExamRecord rec = new ExamRecord()
                .setExamId(examId)
                .setStudentId(uid)
                .setScore(0)
                .setTotalScore(sumScore(snapshot))
                .setCorrectCount(0)
                .setTotalCount(snapshot.size())
                .setPassed(0)
                .setStartTime(now)
                .setStatus(ST_DOING)
                .setPaperVersion(exam.getPaperVersion())
                // 关键：把这份卷子冻结在记录上（之后讲师重新发布不影响这一份）
                .setSnapshotItems(snapshot)
                .setCreateTime(now)
                .setUpdateTime(now);
        examRecordMapper.insert(rec);
        return buildStart(exam, rec);
    }

    // ------------------------------------------------------------------ 交卷判分

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResultVO submit(Long examId, ExamSubmitDTO dto) {
        Long uid = requireUser();
        Exam exam = requirePublished(examId);
        ExamRecord rec = findRecord(examId, uid);
        if (rec == null) {
            throw new BadRequestException("还没有开始作答这份卷子");
        }
        if (rec.getStatus() != null && rec.getStatus() >= ST_SUBMITTED) {
            // 幂等：重复交卷返回既有成绩，**不重新判分、不重复累加题库计数**
            return buildResult(exam, rec, null);
        }

        List<ExamSnapshotItem> snap = snapshotOf(exam, rec);
        Map<Long, String> mine = new HashMap<>();
        if (dto != null) {
            for (ExamSubmitDTO.Item it : CollUtils.emptyIfNull(dto.getExamDetails())) {
                if (it != null && it.getQuestionId() != null) {
                    mine.put(it.getQuestionId(), normalize(it.getAnswer()));
                }
            }
        }

        int passScore = exam.getPassScore() == null ? 60 : exam.getPassScore();
        List<ExamRecordDetail> details = new ArrayList<>(snap.size());
        List<Long> allIds = new ArrayList<>(snap.size());
        List<Long> correctIds = new ArrayList<>();
        int score = 0;
        int correct = 0;
        int total = 0;
        for (ExamSnapshotItem s : snap) {
            String std = normalize(s.getAnswer());
            String my = mine.getOrDefault(s.getQuestionId(), "");
            boolean ok = !std.isEmpty() && std.equals(my);
            int full = s.getScore() == null ? 0 : s.getScore();
            int got = ok ? full : 0;
            total += full;
            score += got;
            if (ok) {
                correct++;
                correctIds.add(s.getQuestionId());
            }
            allIds.add(s.getQuestionId());
            details.add(new ExamRecordDetail()
                    .setRecordId(rec.getId())
                    .setQuestionId(s.getQuestionId())
                    .setAnswer(my)
                    .setCorrect(ok ? 1 : 0)
                    .setScore(got)
                    .setMarked(0));
        }
        for (ExamRecordDetail d : details) {
            examRecordDetailMapper.insert(d);
        }

        LocalDateTime now = LocalDateTime.now();
        rec.setScore(score)
                .setTotalScore(total)
                .setCorrectCount(correct)
                .setTotalCount(snap.size())
                .setPassed(score >= passScore ? 1 : 0)
                .setFinishTime(now)
                .setStatus(ST_SUBMITTED)
                .setUpdateTime(now);
        examRecordMapper.updateById(rec);

        // 回写题库计数（正确率的数据源）—— 只在这一条真正交卷成功的路径上做
        if (CollUtils.isNotEmpty(allIds)) {
            if (CollUtils.isEmpty(correctIds)) {
                examRecordMapper.bumpAnswerTimesOnly(allIds);
            } else {
                examRecordMapper.bumpQuestionCounters(allIds, correctIds);
            }
        }
        examMapper.bumpSubmittedCount(examId);

        return buildResult(exam, rec, details);
    }

    @Override
    public List<StudentCourseExamVO> publishedOfCourse(Long courseId) {
        if (courseId == null) {
            throw new BadRequestException("缺少课程参数");
        }
        // 只给**已发布**的卷：草稿与已停用的不该被学生看到
        List<Exam> exams = examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getCourseId, courseId)
                .eq(Exam::getStatus, EXAM_PUBLISHED)
                .orderByAsc(Exam::getId));
        if (CollUtils.isEmpty(exams)) {
            return CollUtils.emptyList();
        }
        // 我的作答记录（一场一条，靠唯一键 uk_exam_student 保证）
        Long me = UserContext.getUser();
        Map<Long, ExamRecord> mineMap = new HashMap<>();
        if (me != null) {
            List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
            for (ExamRecord r : examRecordMapper.selectList(Wrappers.<ExamRecord>lambdaQuery()
                    .eq(ExamRecord::getStudentId, me)
                    .in(ExamRecord::getExamId, examIds))) {
                mineMap.put(r.getExamId(), r);
            }
        }
        List<StudentCourseExamVO> list = new ArrayList<>(exams.size());
        for (Exam e : exams) {
            List<ExamSnapshotItem> snap = CollUtils.emptyIfNull(e.getSnapshotItems());
            StudentCourseExamVO vo = new StudentCourseExamVO()
                    .setExamId(e.getId())
                    .setName(e.getName())
                    .setCourseId(e.getCourseId())
                    .setCourseName(e.getCourseName())
                    .setExamType(e.getExamType())
                    .setNotice(e.getNotice())
                    .setQuestionCount(snap.size())
                    // 卷面总分从快照算（与作答页 / 成绩页同一口径）
                    .setTotalScore(snap.stream().mapToInt(x -> x.getScore() == null ? 0 : x.getScore()).sum())
                    .setDuration(e.getDuration())
                    .setPassScore(e.getPassScore());
            ExamRecord r = mineMap.get(e.getId());
            if (r != null) {
                vo.setMyRecordId(r.getId())
                        .setMyStatus(r.getStatus())
                        .setMyScore(r.getScore())
                        .setMyTotalScore(r.getTotalScore())
                        .setMyPassed(r.getPassed());
            }
            list.add(vo);
        }
        return list;
    }

    // ------------------------------------------------------------------ 学生查看

    @Override
    public ExamResultVO myResult(Long examId) {
        Long uid = requireUser();
        Exam exam = requireExam(examId);
        ExamRecord rec = findRecord(examId, uid);
        if (rec == null || rec.getStatus() == null || rec.getStatus() < ST_SUBMITTED) {
            throw new BadRequestException("这场考试你还没有交卷");
        }
        return buildResult(exam, rec, null);
    }

    @Override
    public PageDTO<ExamRecordPageVO> myRecords(Integer pageNo, Integer pageSize) {
        Long uid = requireUser();
        Page<ExamRecord> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
        examRecordMapper.selectPage(page, Wrappers.<ExamRecord>lambdaQuery()
                .eq(ExamRecord::getStudentId, uid)
                .orderByDesc(ExamRecord::getFinishTime)
                .orderByDesc(ExamRecord::getId));
        List<ExamRecord> records = page.getRecords();
        Map<Long, Exam> examMap = examMapOf(records);
        List<ExamRecordPageVO> list = records.stream()
                .map(r -> toPageVO(r, examMap.get(r.getExamId())))
                .collect(Collectors.toList());
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }

    // ------------------------------------------------------------------ 讲师查看

    // ------------------------------------------------------------------ 讲师：待批改

    @Override
    public PendingReviewSummaryVO pendingReview() {
        Long me = UserContext.getUser();
        // 我建的、不是草稿的卷（草稿不可能有人交卷，列出来只会是噪音）
        List<Exam> exams = me == null ? CollUtils.emptyList()
                : examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                        .eq(Exam::getCreater, me)
                        .ne(Exam::getStatus, EXAM_DRAFT)
                        .orderByDesc(Exam::getId));
        if (CollUtils.isEmpty(exams)) {
            return new PendingReviewSummaryVO()
                    .setExamCount(0).setPendingCount(0).setSubmittedCount(0)
                    .setItems(CollUtils.emptyList());
        }
        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        // 只取"已交卷"及以后的记录：status=0 是点了开始但没交卷，不该算进待批改
        List<ExamRecord> records = examRecordMapper.selectList(Wrappers.<ExamRecord>lambdaQuery()
                .in(ExamRecord::getExamId, examIds)
                .ge(ExamRecord::getStatus, ST_SUBMITTED));

        Map<Long, List<ExamRecord>> byExam = records.stream()
                .collect(Collectors.groupingBy(ExamRecord::getExamId));

        List<PendingReviewItemVO> items = new ArrayList<>();
        for (Exam e : exams) {
            List<ExamRecord> rs = byExam.get(e.getId());
            if (CollUtils.isEmpty(rs)) {
                continue;   // 没人交卷的场次不进待批改列表
            }
            List<ExamSnapshotItem> snap = CollUtils.emptyIfNull(e.getSnapshotItems());
            int reviewed = (int) rs.stream()
                    .filter(r -> ST_REVIEWED == (r.getStatus() == null ? -1 : r.getStatus())).count();
            LocalDateTime last = rs.stream()
                    .map(r -> r.getFinishTime() == null ? r.getUpdateTime() : r.getFinishTime())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            items.add(new PendingReviewItemVO()
                    .setExamId(e.getId())
                    .setExamName(e.getName())
                    .setCourseId(e.getCourseId())
                    .setCourseName(e.getCourseName())
                    .setExamType(e.getExamType())
                    .setQuestionCount(snap.size())
                    .setTotalScore(snap.stream().mapToInt(x -> x.getScore() == null ? 0 : x.getScore()).sum())
                    .setSubmittedCount(rs.size())
                    .setReviewedCount(reviewed)
                    .setPendingCount(rs.size() - reviewed)
                    .setLastSubmitTime(last));
        }
        // 待复核多的在前；一样多时最近交卷的在前（先处理新的）
        items.sort(Comparator
                .comparingInt(PendingReviewItemVO::getPendingCount).reversed()
                .thenComparing(PendingReviewItemVO::getLastSubmitTime,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        return new PendingReviewSummaryVO()
                .setExamCount(items.size())
                .setPendingCount(items.stream().mapToInt(PendingReviewItemVO::getPendingCount).sum())
                .setSubmittedCount(items.stream().mapToInt(PendingReviewItemVO::getSubmittedCount).sum())
                .setItems(items);
    }

    @Override
    public List<ExamRecordPageVO> recordsOfExam(Long examId) {
        Exam exam = requireExam(examId);
        requireExamOwner(exam);
        List<ExamRecord> records = examRecordMapper.selectList(Wrappers.<ExamRecord>lambdaQuery()
                .eq(ExamRecord::getExamId, examId)
                .orderByDesc(ExamRecord::getScore)
                .orderByAsc(ExamRecord::getFinishTime));
        Map<Long, String> names = studentNames(records);
        return records.stream().map(r -> {
            ExamRecordPageVO vo = toPageVO(r, exam);
            vo.setStudentName(names.get(r.getStudentId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public ExamStatisticsVO statistics(Long examId) {
        Exam exam = requireExam(examId);
        requireExamOwner(exam);
        // 只计**已交卷**的学生（进行中的记录不算分母）
        List<ExamRecord> done = examRecordMapper.selectList(Wrappers.<ExamRecord>lambdaQuery()
                .eq(ExamRecord::getExamId, examId)
                .ge(ExamRecord::getStatus, ST_SUBMITTED));

        ExamStatisticsVO vo = new ExamStatisticsVO();
        vo.setExamId(examId);
        vo.setExamName(exam.getName());
        vo.setPassScore(exam.getPassScore());
        vo.setSubmittedCount(done.size());
        vo.setTotalScore(done.isEmpty() ? null : done.get(0).getTotalScore());

        String[] labels = {"0-59", "60-69", "70-79", "80-89", "90-100"};
        int[] buckets = new int[labels.length];
        if (done.isEmpty()) {
            // 没人交卷：不给 0 分 / 0% 这类假数字，留 null 让前端显示「—」
            vo.setAvgScore(null);
            vo.setPassRate(null);
            vo.setMaxScore(null);
            vo.setMinScore(null);
        } else {
            int sum = 0;
            int max = Integer.MIN_VALUE;
            int min = Integer.MAX_VALUE;
            int passCount = 0;
            for (ExamRecord r : done) {
                int s = r.getScore() == null ? 0 : r.getScore();
                int full = r.getTotalScore() == null || r.getTotalScore() <= 0 ? 100 : r.getTotalScore();
                sum += s;
                max = Math.max(max, s);
                min = Math.min(min, s);
                if (r.getPassed() != null && r.getPassed() == 1) {
                    passCount++;
                }
                int rate = (int) Math.floor(s * 100.0 / full);
                if (rate >= 90) buckets[4]++;
                else if (rate >= 80) buckets[3]++;
                else if (rate >= 70) buckets[2]++;
                else if (rate >= 60) buckets[1]++;
                else buckets[0]++;
            }
            vo.setAvgScore(round1(sum * 1.0 / done.size()));
            vo.setPassRate(round1(passCount * 100.0 / done.size()));
            vo.setMaxScore(max);
            vo.setMinScore(min);
        }
        List<ExamStatisticsVO.Bucket> dist = new ArrayList<>(labels.length);
        for (int i = 0; i < labels.length; i++) {
            dist.add(new ExamStatisticsVO.Bucket().setLabel(labels[i]).setCount(buckets[i]));
        }
        vo.setDistribution(dist);

        // 逐题正确率：题面/分值取**当前快照**（与"统计读快照"一致），
        // 答题数/答对数来自作答明细（exam_record_detail）。没人答过 → null，前端显示「—」。
        Map<Long, int[]> perQ = new HashMap<>();
        for (Map<String, Object> row : examRecordDetailMapper.countPerQuestion(examId)) {
            Long qid = ((Number) row.get("questionId")).longValue();
            int attempts = row.get("attempts") == null ? 0 : ((Number) row.get("attempts")).intValue();
            int correct = row.get("correctCount") == null ? 0 : ((Number) row.get("correctCount")).intValue();
            perQ.put(qid, new int[]{attempts, correct});
        }
        List<ExamStatisticsVO.QuestionStat> statList = new ArrayList<>();
        int order = 1;
        for (ExamSnapshotItem s : CollUtils.emptyIfNull(exam.getSnapshotItems())) {
            int[] c = perQ.getOrDefault(s.getQuestionId(), new int[]{0, 0});
            statList.add(new ExamStatisticsVO.QuestionStat()
                    .setOrder(order++)
                    .setQuestionId(s.getQuestionId())
                    .setStem(s.getStem())
                    .setType(s.getType())
                    .setScore(s.getScore())
                    .setAttempts(c[0])
                    .setCorrectCount(c[1])
                    .setCorrectRate(c[0] == 0 ? null : round1(c[1] * 100.0 / c[0])));
        }
        vo.setPerQuestion(statList);
        return vo;
    }

    @Override
    public ExamResultVO resultOfRecord(Long recordId) {
        ExamRecord rec = recordId == null ? null : examRecordMapper.selectById(recordId);
        if (rec == null) {
            throw new BadRequestException("作答记录不存在");
        }
        Exam exam = requireExam(rec.getExamId());
        // 这是讲师看学生答卷的入口，学生看自己的答卷走 myResult（另一个端点）
        requireExamOwner(exam);
        ExamResultVO vo = buildResult(exam, rec, null);
        // 讲师看答卷要知道这是谁（学生自己看自己的答卷不需要这一步）
        vo.setStudentId(rec.getStudentId());
        vo.setStudentName(studentNameOf(rec.getStudentId()));
        return vo;
    }

    /** 单个学生的显示名；查不到就返回 null（前端显示「学员」，不编名字） */
    private String studentNameOf(Long studentId) {
        if (studentId == null) {
            return null;
        }
        try {
            UserDTO u = userClient.queryUserById(studentId);
            if (u == null) {
                return null;
            }
            return u.getName() == null || u.getName().isEmpty() ? u.getUsername() : u.getName();
        } catch (Exception e) {
            log.warn("批改用例：查询学生姓名失败，studentId={}", studentId, e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long recordId) {
        ExamRecord rec = examRecordMapper.selectById(recordId);
        if (rec == null) {
            throw new BadRequestException("作答记录不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() < ST_SUBMITTED) {
            throw new BadRequestException("这份卷子还没有交卷，不能复核");
        }
        // 复核是**写**操作，同样必须是卷子的创建者
        requireExamOwner(requireExam(rec.getExamId()));
        rec.setStatus(ST_REVIEWED).setUpdateTime(LocalDateTime.now());
        examRecordMapper.updateById(rec);
    }

    // ------------------------------------------------------------------ 内部工具

    private Long requireUser() {
        Long uid = UserContext.getUser();
        if (uid == null) {
            throw new BadRequestException("请先登录");
        }
        return uid;
    }

    /**
     * 讲师侧接口的归属校验（P18 安全修复）：
     * 答卷列表 / 成绩统计 / 看某份答卷 / 复核，**只有卷子的创建者**能做。
     * 以前这些接口一个校验都没有 —— 任何登录用户（哪怕是学生）都能拿到全场成绩并改动复核状态。
     *
     * ⚠️ 目前只认 `exam.creater`。将来「协作讲师也要能批改」要改成查课程的讲师名单
     *    （需要 course-service 的接口），到时把这里换成一次 Feign 调用即可。
     */
    private void requireExamOwner(Exam exam) {
        Long me = UserContext.getUser();
        if (me == null) {
            throw new BadRequestException("请先登录");
        }
        if (!Objects.equals(exam.getCreater(), me)) {
            throw new BadRequestException("这场考试不是你创建的，不能查看或批改别人的考试");
        }
    }

    private Exam requireExam(Long examId) {
        Exam exam = examId == null ? null : examMapper.selectById(examId);
        if (exam == null) {
            throw new BadRequestException("试卷不存在");
        }
        return exam;
    }

    /** 只有发布过（有快照）的卷子能被作答；被停用的不能 */
    private Exam requirePublished(Long examId) {
        Exam exam = requireExam(examId);
        if (CollUtils.isEmpty(exam.getSnapshotItems())) {
            throw new BadRequestException("这份卷子还没有发布，暂时不能作答");
        }
        if (exam.getStatus() != null && exam.getStatus() == ST_FINISHED) {
            // 随堂练习在「老师把这一节的题清空」后会被停用（见 PracticePaperServiceImpl）；正式考试同理
            throw new BadRequestException("这份练习已停用，暂时不能作答");
        }
        return exam;
    }

    private ExamRecord findRecord(Long examId, Long studentId) {
        return examRecordMapper.selectOne(Wrappers.<ExamRecord>lambdaQuery()
                .eq(ExamRecord::getExamId, examId)
                .eq(ExamRecord::getStudentId, studentId)
                .last("LIMIT 1"));
    }

    /** 作答记录上的快照优先；历史数据没有就退回试卷当前的快照 */
    private List<ExamSnapshotItem> snapshotOf(Exam exam, ExamRecord rec) {
        if (rec != null && CollUtils.isNotEmpty(rec.getSnapshotItems())) {
            return rec.getSnapshotItems();
        }
        return CollUtils.emptyIfNull(exam.getSnapshotItems());
    }

    /**
     * 答案归一化：只留数字与逗号 → 去重 → 升序 → 拼串。
     * 学生端选项标签是 1 基（ind+1），题库 question_detail.answer 也是同口径，
     * 所以两边归一后可以直接字符串比较。空答案归成 ""。
     */
    private String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        List<Integer> nums = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return nums.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int sumScore(List<ExamSnapshotItem> snapshot) {
        return CollUtils.emptyIfNull(snapshot).stream()
                .mapToInt(s -> s.getScore() == null ? 0 : s.getScore())
                .sum();
    }

    private Double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private Map<Long, Exam> examMapOf(List<ExamRecord> records) {
        List<Long> ids = CollUtils.emptyIfNull(records).stream()
                .map(ExamRecord::getExamId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return examMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e, (a, b) -> a));
    }

    private Map<Long, String> studentNames(List<ExamRecord> records) {
        List<Long> ids = CollUtils.emptyIfNull(records).stream()
                .map(ExamRecord::getStudentId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        try {
            List<UserDTO> users = userClient.queryUserByIds(ids);
            for (UserDTO u : CollUtils.emptyIfNull(users)) {
                map.put(u.getId(), u.getName() == null || u.getName().isEmpty() ? u.getUsername() : u.getName());
            }
        } catch (Exception e) {
            // 用户服务不可用不该让批改页整页崩：姓名留空，前端显示「—」
            log.warn("批改用例：查询学生姓名失败，ids={}", ids, e);
        }
        return map;
    }

    private ExamRecordPageVO toPageVO(ExamRecord r, Exam exam) {
        ExamRecordPageVO vo = new ExamRecordPageVO();
        vo.setId(r.getId());
        vo.setExamId(r.getExamId());
        vo.setStudentId(r.getStudentId());
        vo.setExamName(exam == null ? null : exam.getName());
        vo.setCourseName(exam == null ? null : exam.getCourseName());
        vo.setExamType(exam == null ? null : exam.getExamType());
        vo.setScore(r.getScore());
        vo.setTotalScore(r.getTotalScore());
        vo.setCorrectCount(r.getCorrectCount());
        vo.setTotalCount(r.getTotalCount());
        vo.setPassed(r.getPassed());
        vo.setStatus(r.getStatus());
        vo.setStartTime(r.getStartTime());
        vo.setFinishTime(r.getFinishTime());
        if (r.getStartTime() != null && r.getFinishTime() != null) {
            vo.setDuration((int) Duration.between(r.getStartTime(), r.getFinishTime()).getSeconds());
        }
        return vo;
    }

    private ExamStartVO buildStart(Exam exam, ExamRecord rec) {
        List<ExamSnapshotItem> snap = snapshotOf(exam, rec);
        ExamStartVO vo = new ExamStartVO();
        vo.setRecordId(rec.getId());
        vo.setExamId(exam.getId());
        vo.setName(exam.getName());
        vo.setExamType(exam.getExamType());
        vo.setDuration(exam.getDuration());
        vo.setPassScore(exam.getPassScore());
        vo.setNotice(exam.getNotice());
        vo.setTotalScore(sumScore(snap));
        List<ExamStartVO.Question> qs = new ArrayList<>(snap.size());
        for (ExamSnapshotItem s : snap) {
            ExamStartVO.Question q = new ExamStartVO.Question();
            q.setId(s.getQuestionId());
            q.setType(s.getType());
            q.setName(s.getStem());
            q.setOptions(s.getOptions());
            q.setScore(s.getScore());
            qs.add(q);
        }
        vo.setQuestions(qs);
        return vo;
    }

    /** @param preloaded 交卷刚算出来的明细（避免再查一次库）；传 null 则从库里读 */
    private ExamResultVO buildResult(Exam exam, ExamRecord rec, List<ExamRecordDetail> preloaded) {
        List<ExamSnapshotItem> snap = snapshotOf(exam, rec);
        List<ExamRecordDetail> details = preloaded != null ? preloaded
                : examRecordDetailMapper.selectList(Wrappers.<ExamRecordDetail>lambdaQuery()
                        .eq(ExamRecordDetail::getRecordId, rec.getId()));
        Map<Long, ExamRecordDetail> detailMap = details.stream()
                .collect(Collectors.toMap(ExamRecordDetail::getQuestionId, d -> d, (a, b) -> a));

        ExamResultVO vo = new ExamResultVO();
        vo.setRecordId(rec.getId());
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getName());
        vo.setCourseName(exam.getCourseName());
        vo.setScore(rec.getScore());
        vo.setTotalScore(rec.getTotalScore() == null ? sumScore(snap) : rec.getTotalScore());
        vo.setCorrectCount(rec.getCorrectCount());
        vo.setTotalCount(rec.getTotalCount() == null ? snap.size() : rec.getTotalCount());
        vo.setPassed(rec.getPassed());
        vo.setPassScore(exam.getPassScore());
        vo.setStatus(rec.getStatus());
        vo.setStartTime(rec.getStartTime());
        vo.setFinishTime(rec.getFinishTime());
        if (rec.getStartTime() != null && rec.getFinishTime() != null) {
            vo.setDuration(Duration.between(rec.getStartTime(), rec.getFinishTime()).getSeconds());
        }

        List<ExamResultVO.Detail> out = new ArrayList<>(snap.size());
        int index = 1;
        for (ExamSnapshotItem s : snap) {
            ExamRecordDetail d = detailMap.get(s.getQuestionId());
            ExamResultVO.Detail dt = new ExamResultVO.Detail();
            dt.setIndex(index++);
            dt.setQuestionId(s.getQuestionId());
            dt.setType(s.getType());
            dt.setName(s.getStem());
            dt.setOptions(s.getOptions());
            dt.setMyAnswer(d == null ? "" : d.getAnswer());
            dt.setCorrectAnswer(s.getAnswer());
            dt.setAnalysis(s.getAnalysis());
            dt.setCorrect(d == null ? 0 : d.getCorrect());
            dt.setScore(d == null ? 0 : d.getScore());
            dt.setMaxScore(s.getScore());
            out.add(dt);
        }
        // 题号按记录里的顺序展示（快照本身是有序的）
        out.sort(Comparator.comparing(ExamResultVO.Detail::getIndex));
        vo.setDetails(out);
        return vo;
    }
}
