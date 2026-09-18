package com.zhixu.exam.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.api.cache.CategoryCache;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.IdAndNumDTO;
import com.zhixu.api.dto.exam.QuestionDTO;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.constants.Constant;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.StringUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.exam.domain.dto.BatchPatchDTO;
import com.zhixu.exam.domain.dto.QuestionFormDTO;
import com.zhixu.exam.domain.po.Question;
import com.zhixu.exam.domain.po.QuestionBiz;
import com.zhixu.exam.domain.po.QuestionDetail;
import com.zhixu.exam.domain.query.QuestionPageQuery;
import com.zhixu.exam.domain.vo.IdentityVO;
import com.zhixu.exam.domain.vo.QuestionDetailVO;
import com.zhixu.exam.domain.vo.QuestionPageVO;
import com.zhixu.exam.domain.vo.QuestionVisibilityVO;
import com.zhixu.exam.domain.vo.QuestionUsageVO;
import com.zhixu.exam.mapper.ExamMapper;
import com.zhixu.exam.mapper.QuestionMapper;
import com.zhixu.exam.service.IQuestionBizService;
import com.zhixu.exam.service.IQuestionDetailService;
import com.zhixu.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.zhixu.exam.constants.ExamErrorInfo.QUESTION_NOT_EXISTS;

/**
 * <p>
 * 题目 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements IQuestionService {

    private final IQuestionDetailService detailService;
    private final IQuestionBizService bizService;
    private final UserClient userClient;
    private final CategoryCache categoryCache;
    private final ExamMapper examMapper;

    @Override
    @Transactional
    public void addQuestion(QuestionFormDTO questionDTO) {
        // 1.保存题目信息
        Question question = BeanUtils.copyBean(questionDTO, Question.class);
        // 三级分类（cateIds）是历史字段：教师端录题表单里选的是**课程**（courseId），
        // 没有分类可选 → 允许不传。传了就必须凑够 3 个（cate_id1/2/3 三列要一起写）。
        // ⚠️ 以前这里 cateIds.size() 直接取长度：不传会 NPE，
        //    而前端曾用 courseId 占位凑 3 个 → 写出不存在的分类 id →
        //    列表接口取分类名时 NPE、题库整页 500（2026-09-16 踩过）。
        List<Long> cateIds = questionDTO.getCateIds();
        if (CollUtils.isNotEmpty(cateIds)) {
            if (cateIds.size() < 3) {
                throw new BadRequestException("题目必须关联三级分类");
            }
            question.setCateId1(cateIds.get(0));
            question.setCateId2(cateIds.get(1));
            question.setCateId3(cateIds.get(2));
        }
        // 可见范围（P21）：新题**默认「仅我」**（库列默认 0），只有老师显式选「公开到平台」才公开
        boolean toPublicNew = questionDTO.getVisibility() != null && questionDTO.getVisibility() == 1;
        question.setVisibility(toPublicNew ? 1 : 0);
        question.setPublishTime(toPublicNew ? LocalDateTime.now() : null);
        save(question);

        // 2.保存详情
        QuestionDetail detail = new QuestionDetail()
                .setId(question.getId())
                .setAnalysis(questionDTO.getAnalysis())
                .setAnswer(questionDTO.getAnswer())
                .setOptions(questionDTO.getOptions());
        detailService.save(detail);
    }

    /**
     * 题目的三级分类 id（**过滤 null**）。
     * 给 CategoryCache 取名用 —— 别写成 List.of(...)：List.of 连 null 元素都不接受，
     * 一列没值就整条链抛空指针（题库列表 500 的直接原因）。
     */
    private static List<Long> cateIdsOf(Question q) {
        List<Long> ids = new ArrayList<>(3);
        if (q.getCateId1() != null) {
            ids.add(q.getCateId1());
        }
        if (q.getCateId2() != null) {
            ids.add(q.getCateId2());
        }
        if (q.getCateId3() != null) {
            ids.add(q.getCateId3());
        }
        return ids;
    }

    @Override
    public void updateQuestion(QuestionFormDTO questionDTO) {
        // 0.归属校验（P18 安全修复）：以前没有这一步，任何登录用户都能改别人的题（连答案一起改）
        Question exist = questionDTO.getId() == null ? null : getById(questionDTO.getId());
        if (exist == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        if (!Objects.equals(exist.getCreater(), UserContext.getUser())) {
            throw new BadRequestException("只能修改自己出的题");
        }
        // 可见范围（P21）：**只有显式传了、且与原值不同**才动 ——
        // 否则每次改题都会把「公开」悄悄改回「仅我」（而撤回本该过引用校验）
        if (questionDTO.getVisibility() != null
                && !Objects.equals(exist.getVisibility(), questionDTO.getVisibility())) {
            changeVisibility(exist.getId(), questionDTO.getVisibility() == 1 ? 1 : 0);
        }
        // 1.保存题目信息
        Question question = BeanUtils.copyBean(questionDTO, Question.class);
        // ⚠️ 把可见范围摘掉：它已在上面的 changeVisibility 里处理过（含撤回校验）。
        //    留着的话 updateById 会直接写库 —— 等于绕过「被引用的题不能撤回」。
        question.setVisibility(null);
        List<Long> cateIds = questionDTO.getCateIds();
        if (CollUtils.isNotEmpty(cateIds) && cateIds.size() == 3) {
            question.setCateId1(cateIds.get(0));
            question.setCateId2(cateIds.get(1));
            question.setCateId3(cateIds.get(2));
        }
        updateById(question);

        // 2.保存详情
        QuestionDetail detail = new QuestionDetail()
                .setId(question.getId())
                .setAnalysis(questionDTO.getAnalysis())
                .setAnswer(questionDTO.getAnswer())
                .setOptions(questionDTO.getOptions());
        detailService.updateById(detail);
    }

    @Override
    public void deleteQuestionById(Long id) {
        // 0.归属校验（P18 安全修复）
        Question exist = getById(id);
        if (exist == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        if (!Objects.equals(exist.getCreater(), UserContext.getUser())) {
            throw new BadRequestException("只能删除自己出的题");
        }
        // 1.查询题目和业务之间是否有关联
        int usedTimes = bizService.countUsedTimes(id);
        if (usedTimes > 0) {
            throw new BadRequestException("题目被使用中，无法删除");
        }
        // 2.删除题目
        removeById(id);
        // 3.删除详情
        detailService.removeById(id);
    }

    @Override
    public PageDTO<QuestionPageVO> queryQuestionByPage(QuestionPageQuery query) {
        // 1.分页搜索
        // 可见范围口径（P17）：
        //   visible = 公开的 ∪ 我自己出的   ← 题库列表「全部题目」用这个（看不到别人的私有题，也不会漏自己的）
        //   public  = 只看公开的            ← 组卷时「从平台题库搜索」
        //   mine    = 只看我出的            ← 「我的题目」分段
        //   不传     = 不过滤                ← 管理端 / 平台全量搜索
        Long me = UserContext.getUser();
        String scope = query.getVisibilityScope();
        Page<Question> page = lambdaQuery()
                .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
                .eq(query.getCreater() != null, Question::getCreater, query.getCreater())
                .eq(query.getStatus() != null, Question::getStatus, query.getStatus())
                .eq(query.getCourseId() != null, Question::getCourseId, query.getCourseId())
                .and("visible".equals(scope), w -> w.eq(Question::getVisibility, 1).or().eq(Question::getCreater, me))
                .eq("public".equals(scope), Question::getVisibility, 1)
                .eq("mine".equals(scope), Question::getCreater, me)
                .in(CollUtils.isNotEmpty(query.getTypes()), Question::getType, query.getTypes())
                .in(CollUtils.isNotEmpty(query.getCateIds()), Question::getCateId3, query.getCateIds())
                .like(StringUtils.isNotBlank(query.getKeyword()), Question::getName, query.getKeyword())
                .page(query.toMpPage(Constant.DATA_FIELD_NAME_UPDATE_TIME, false));
        // 2.判空
        List<Question> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.查询VO信息，包含：引用次数、提问者信息
        Set<Long> qIds = new HashSet<>();
        Set<Long> uIds = new HashSet<>();
        for (Question record : records) {
            qIds.add(record.getId());
            uIds.add(record.getUpdater());
            uIds.add(record.getCreater());
        }
        // 3.1.统计引用次数
        Map<Long, Integer> countMap = bizService.countUsedTimes(qIds);
        // 3.2.查询用户
        Map<Long, UserDTO> userMap = new HashMap<>(uIds.size());
        if (CollUtils.isNotEmpty(uIds)) {
            List<UserDTO> users = userClient.queryUserByIds(uIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 4.处理vo
        List<QuestionPageVO> list = new ArrayList<>(records.size());
        for (Question r : records) {
            // 4.1.转换为vo
            QuestionPageVO v = BeanUtils.toBean(r, QuestionPageVO.class);
            list.add(v);
            // 4.2.获取用户
            UserDTO u = userMap.get(r.getUpdater());
            v.setUpdater(u == null ? "" : u.getName());
            // 4.2.1.出题人（共享池子里要能找到题的主人，契约 §4.3）
            UserDTO cu = userMap.get(r.getCreater());
            v.setCreatorName(cu == null ? "" : cu.getName());
            v.setStatus(r.getStatus() == null ? 1 : r.getStatus());
            // 4.2.2.可见范围：0 私有（仅我）/ 1 公开（平台可见）—— 列表上要能一眼看出来（P17）
            v.setVisibility(r.getVisibility() == null ? 0 : r.getVisibility());
            // 4.3.分类
            v.setCategories(categoryCache.getCategoryNameList(cateIdsOf(r)));
            // 4.4.引用次数
            v.setUseTimes(countMap.getOrDefault(r.getId(), 0));
        }
        return PageDTO.of(page, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVisibilityVO setVisibility(Long id, Integer visibility) {
        Question q = getById(id);
        if (q == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        Long me = UserContext.getUser();
        if (!Objects.equals(q.getCreater(), me)) {
            throw new BadRequestException("这道题不是你出的，不能改它的可见范围。");
        }
        boolean toPublic = visibility != null && visibility == 1;
        changeVisibility(id, toPublic ? 1 : 0);
        List<Map<String, Object>> refs = examMapper.selectExamsUsingQuestion(id);
        // 回给前端的是**写完之后**的值：publishTime 用 now 与 changeVisibility 里写的是同一个时刻的近似
        LocalDateTime now = LocalDateTime.now();
        return new QuestionVisibilityVO()
                .setId(id)
                .setVisibility(toPublic ? 1 : 0)
                .setPublishTime(toPublic ? now : null)
                .setRefCount(refs == null ? 0 : refs.size());
    }

    /**
     * 发布 / 撤回的**唯一实现**（P21）：把范围写成 target（0 仅我 / 1 公开）。
     *
     * 撤回（公开 → 私有）前先看有没有被试卷引用：别人的卷子里会突然少一道题。
     * ⚠️ 必须用 lambdaUpdate().set()：updateById 会**忽略 null 字段**，那样撤回时 publishTime 清不掉。
     * ⚠️ 新增/编辑接口也走这里，别在别处再写一遍范围逻辑 —— 否则「撤回校验」迟早有一处漏掉。
     */
    private void changeVisibility(Long id, int target) {
        Question q = getById(id);
        if (q == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        boolean toPublic = target == 1;
        if (!toPublic && Objects.equals(q.getVisibility(), 1)) {
            List<Map<String, Object>> refs = examMapper.selectExamsUsingQuestion(id);
            if (CollUtils.isNotEmpty(refs)) {
                String names = refs.stream()
                        .map(m -> String.valueOf(m.get("name")))
                        .collect(Collectors.joining("、"));
                throw new BadRequestException("这道题已被 " + refs.size() + " 份试卷引用（" + names
                        + "）。撤回会让那些卷子少一道题，请先处理引用。");
            }
        }
        lambdaUpdate()
                .eq(Question::getId, id)
                .set(Question::getVisibility, toPublic ? 1 : 0)
                .set(Question::getPublishTime, toPublic ? LocalDateTime.now() : null)
                .update();
    }

    @Override
    public QuestionDetailVO queryQuestionDetailById(Long id) {
        // 1.查询题目
        Question q = getById(id);
        if (q == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        // 2.查询详情
        QuestionDetail detail = detailService.getById(id);
        if (detail == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        // 3.查询题目的录入者
        UserDTO u = userClient.queryUserById(q.getCreater());
        // 4.转换vo
        QuestionDetailVO v = BeanUtils.copyBean(q, QuestionDetailVO.class);
        // 4.1.详情
        v.setOptions(detail.getOptions());
        v.setAnalysis(detail.getAnalysis());
        v.setAnswer(detail.getAnswer());
        // 4.2.用户
        v.setUpdater(u == null ? "" : u.getName());
        // 4.3.分类
        v.setCategories(categoryCache.getCategoryNameList(cateIdsOf(q)));
        // 4.4.引用次数
        v.setUseTimes(bizService.countUsedTimes(id));
        return v;
    }

    @Override
    public List<QuestionDTO> queryQuestionByIds(List<Long> ids) {
        // 1.查询题目集合
        List<Question> questions = listByIds(ids);
        if (CollUtils.isEmpty(questions)) {
            return CollUtils.emptyList();
        }

        // 2.查询详情
        List<QuestionDetail> details = detailService.listByIds(ids);
        if (details == null || questions.size() != details.size()) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        Map<Long, QuestionDetail> detailMap = details.stream().collect(Collectors.toMap(QuestionDetail::getId, d -> d));

        // 3.数据转换
        List<QuestionDTO> list = new ArrayList<>(questions.size());
        for (Question q : questions) {
            // 3.1.转vo
            QuestionDTO d = BeanUtils.toBean(q, QuestionDTO.class);
            list.add(d);
            // 3.2.获取详情
            QuestionDetail detail = detailMap.get(q.getId());
            d.setOptions(detail.getOptions());
            d.setAnalysis(detail.getAnalysis());
            d.setAnswer(detail.getAnswer());
        }
        return list;
    }

    @Override
    public Map<Long, Integer> countQuestionNumOfCreater(List<Long> createrIds) {
        // 1.统计
        List<IdAndNumDTO> list = baseMapper.countQuestionOfCreater(createrIds);
        // 2.处理结果
        return IdAndNumDTO.toMap(list);
    }

    @Override
    public List<QuestionDTO> queryQuestionByBizId(Long bizId) {
        // 1.查询中间表
        List<QuestionBiz> list = bizService.lambdaQuery()
                .eq(QuestionBiz::getBizId, bizId)
                .list();
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 2.获取问题id
        List<Long> ids = list.stream().map(QuestionBiz::getQuestionId).collect(Collectors.toList());
        // 3.查询数据集合
        return queryQuestionByIds(ids);
    }

    @Override
    public Boolean checkNameValid(String name) {
        return lambdaQuery()
                .eq(Question::getName, name)
                .count()<=0;
    }

    @Override
    public Map<Long, Integer> queryQuestionScores(List<Long> ids) {
        // 1.根据id查询题目
        List<Question> questions = listByIds(ids);
        // 2.判空
        if (CollUtils.isEmpty(questions)) {
            return CollUtils.emptyMap();
        }
        return questions.stream().collect(Collectors.toMap(Question::getId, Question::getScore));
    }

    // ---- P11 教师端新增（契约 §4.2）----

    @Override
    public IdentityVO getIdentity() {
        Long userId = UserContext.getUser();
        IdentityVO vo = new IdentityVO();
        vo.setId(userId);
        // 用户名查不到时不阻断渲染（与前端降级行为一致）
        String name = "";
        try {
            UserDTO u = userClient.queryUserById(userId);
            name = u == null ? "" : u.getName();
        } catch (Exception ignored) {
        }
        vo.setName(name);
        // 学校/院系体系尚未建模（question 表只有 depId），演示口径恒为「已绑定学校」；
        // 将来接入 school 关联后此处改为真实判断
        vo.setBoundSchool(true);
        return vo;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Question q = getById(id);
        if (q == null) {
            throw new BadRequestException(QUESTION_NOT_EXISTS);
        }
        Long userId = UserContext.getUser();
        if (!q.getCreater().equals(userId)) {
            throw new BadRequestException("只能停用 / 启用自己出的题");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BadRequestException("状态取值不合法");
        }
        // 停用不删除：历史试卷有快照保护，停用只改变「今后能不能被选到」（契约 §4.3）
        lambdaUpdate().eq(Question::getId, id).set(Question::getStatus, status).update();
    }

    @Override
    public Map<String, Integer> batchPatch(BatchPatchDTO dto) {
        if (CollUtils.isEmpty(dto.getIds())) {
            throw new BadRequestException("请选择要调整的题目");
        }
        if (dto.getDifficulty() == null && dto.getStatus() == null) {
            throw new BadRequestException("至少指定一项要调整的内容");
        }
        Long userId = UserContext.getUser();
        // 硬规则：只对自己出的题生效，别人的题跳过（契约 §4.3）
        List<Question> mine = lambdaQuery()
                .in(Question::getId, dto.getIds())
                .eq(Question::getCreater, userId)
                .list();
        int changed = 0;
        if (CollUtils.isNotEmpty(mine)) {
            boolean ok = lambdaUpdate()
                    .in(Question::getId, mine.stream().map(Question::getId).collect(Collectors.toList()))
                    .set(dto.getDifficulty() != null, Question::getDifficulty, dto.getDifficulty())
                    .set(dto.getStatus() != null, Question::getStatus, dto.getStatus())
                    .update();
            changed = ok ? mine.size() : 0;
        }
        Map<String, Integer> result = new HashMap<>(2);
        result.put("changed", changed);
        result.put("skipped", dto.getIds().size() - changed);
        return result;
    }

    @Override
    public QuestionUsageVO queryUsage(Long id) {
        QuestionUsageVO vo = new QuestionUsageVO();
        // exam.items 是 JSON 引用数组，JSON_CONTAINS 只带 questionId 键即可命中（忽略 score）
        List<Map<String, Object>> papers = examMapper.selectExamsUsingQuestion(id);
        vo.setUsageCount(papers.size());
        vo.setPapers(papers);
        return vo;
    }
}
