package com.zhixu.course.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixu.api.client.exam.ExamClient;
import com.zhixu.api.client.media.MediaClient;
import com.zhixu.api.dto.media.MediaMetaDTO;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.exam.PracticeUpsertDTO;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.course.constants.CourseStatus;
import com.zhixu.course.domain.dto.CataSaveDTO;
import com.zhixu.course.domain.dto.CataSubjectDTO;
import com.zhixu.course.domain.dto.CourseBaseInfoSaveDTO;
import com.zhixu.course.domain.dto.CourseMediaDTO;
import com.zhixu.course.domain.dto.CourseTeacherSaveDTO;
import com.zhixu.course.domain.dto.TeacherCourseDraftDTO;
import com.zhixu.course.domain.po.CourseCatalogueDraft;
import com.zhixu.course.domain.po.CourseDraft;
import com.zhixu.course.domain.vo.CourseBaseInfoVO;
import com.zhixu.course.domain.vo.CourseSaveVO;
import com.zhixu.course.domain.vo.CourseTeacherVO;
import com.zhixu.course.domain.vo.TeacherCourseDraftVO;
import com.zhixu.course.domain.vo.TeacherMyCourseVO;
import com.zhixu.course.mapper.CourseCatalogueDraftMapper;
import com.zhixu.course.mapper.CourseDraftMapper;
import com.zhixu.course.mapper.TeacherCatalogueDraftMapper;
import com.zhixu.course.mapper.TeacherCourseShelfMapper;
import com.zhixu.course.service.ICourseCataSubjectDraftService;
import com.zhixu.course.service.ICourseDraftService;
import com.zhixu.course.service.ICourseTeacherDraftService;
import com.zhixu.course.service.ITeacherCourseDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师端建课 · 契约适配实现（契约 §13）
 * -----------------------------------------------------------------------------
 * 三条实现纪律（都写进代码，避免以后被"顺手改坏"）：
 *   1) **不新造业务实现**：全部转调既有 draft service，老站课程模型（草稿→上架 copyToShelf）原样复用。
 *   2) **id 归属校验**：前端传上来的章/小节 id 只有在「确实属于本课程」时才按更新处理，
 *      否则一律当新增（防止本地占位 id 撞进数据库主键）。
 *   3) **checks 从明细推导**：不存汇总，读一次草稿现算，保证校验清单与页面所见一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherCourseDraftServiceImpl implements ITeacherCourseDraftService {

    private final ICourseDraftService courseDraftService;
    private final ICourseTeacherDraftService courseTeacherDraftService;
    private final CourseDraftMapper courseDraftMapper;
    private final CourseCatalogueDraftMapper catalogueDraftMapper;
    private final TeacherCatalogueDraftMapper teacherCatalogueDraftMapper;
    private final TeacherCourseShelfMapper teacherCourseShelfMapper;
    private final ICourseCataSubjectDraftService courseCataSubjectDraftService;
    private final UserClient userClient;
    /** p15 方案 A「配题即出卷」：把这一节的题目引用同步成一张随堂练习卷 */
    private final ExamClient examClient;

    private final MediaClient mediaClient;

    /**
     * 编辑已有（已上架）课程：草稿**逐张表**补种（缺哪张补哪张）。
     * -----------------------------------------------------------------------------
     * ⚠️ 为什么不写成「草稿课程行不存在 → 四张表一起播种」：
     *    2026-09-16 实测踩到 —— 草稿是**四张表**，任何一张都可能单独有/没有数据。
     *    当时的状态是 `course_content_draft` 有行、`course_catalogue_draft` 空行，
     *    整体播种在第二步撞主键 DuplicateKey 抛 500；而 `getDraft` 是**读接口、没有事务**，
     *    第一步已经落库 → 留下"半播种"死状态：course_draft 有了行，于是守卫再也不播种，
     *    **老师打开编辑页永远看到空目录**（就是你早先报的「编辑看不到章节」，这是它的更深一层）。
     *    现在：逐张表计数 + 种子语句 INSERT IGNORE，任何中间状态都能自愈。
     */
    private void seedDraftIfEmpty(Long courseId) {
        if (teacherCourseShelfMapper.countDraft(courseId) == 0) {
            teacherCourseShelfMapper.seedDraftFromShelf(courseId);
        }
        if (teacherCourseShelfMapper.countContentDraft(courseId) == 0) {
            teacherCourseShelfMapper.seedContentFromShelf(courseId);
        }
        if (CollUtils.isEmpty(teacherCatalogueDraftMapper.listByCourse(courseId))) {
            teacherCourseShelfMapper.seedCatalogueFromShelf(courseId);
        }
        if (teacherCourseShelfMapper.countTeacherDraft(courseId) == 0) {
            teacherCourseShelfMapper.seedTeachersFromShelf(courseId);
        }
    }

    // ------------------------------------------------------------------ 读草稿

    @Override
    public TeacherCourseDraftVO getDraft(Long courseId) {
        TeacherCourseDraftVO vo = new TeacherCourseDraftVO();
        if (courseId == null) {
            // 新建底稿：空基本信息 + 当前登录老师作为默认主讲
            Long uid = UserContext.getUser();
            vo.setOwnerId(uid);
            if (uid != null) {
                TeacherCourseDraftVO.TeacherItem self = new TeacherCourseDraftVO.TeacherItem();
                self.setId(uid);
                // 默认主讲 = 当前登录老师本人。姓名向 user-service 取真实值（拿不到才退回占位），
                // 这样第 5 步「讲师与发布」显示的是真名而不是「我」。
                self.setName(currentUserName(uid));
                self.setRole("主讲");
                self.setCertified(true);
                vo.getTeachers().add(self);
            }
            vo.setChecks(buildChecks(vo));
            return vo;
        }

        vo.setCourseId(courseId);
        // 编辑已有（已上架）课程：草稿缺哪张表就补哪张（对前端透明，打开即见现有内容）
        if (teacherCourseShelfMapper.countShelfCourse(courseId) > 0) {
            seedDraftIfEmpty(courseId);
        }
        CourseDraft draft = courseDraftMapper.selectById(courseId);
        if (draft != null) {
            vo.setStep(draft.getStep() == null ? 1 : draft.getStep());
            vo.setStatus(CourseStatus.SHELF.equals(draft.getStatus()) ? "published" : "draft");
        }

        CourseBaseInfoVO info = courseDraftService.getCourseBaseInfo(courseId, false);
        if (info != null) {
            TeacherCourseDraftVO.Basic b = vo.getBasic();
            b.setName(info.getName());
            b.setThirdCateId(info.getThirdCateId());
            b.setCategoryName(info.getCateNames());
            b.setPrice(info.getPrice());
            b.setValidDays(info.getValidDuration());
            b.setCoverUrl(info.getCoverUrl());
            b.setIntroduce(info.getIntroduce());
            b.setUsePeople(info.getUsePeople());
            b.setDetail(info.getDetail());
        }

        // 目录树由**我们自己的草稿行**构建：
        // 老查询在「已上架」状态下会按 can_update=0 的锁定行算最大序号，数据稍有出入就抛
        // NoSuchElementException（我们踩过两次）。适配层自己建树，形状可控、也不受老逻辑影响。
        //
        // ⚠️ 顺序一律按 c_index（listByCourse 已按 type, c_index 排好，这里保持插入序）：
        //    早前按 id 排序 → 老师用 ↑↓ 调整过顺序后重进页面，顺序会被 id 大小打乱（id 是升序雪花）。
        List<CourseCatalogueDraft> rows = teacherCatalogueDraftMapper.listByCourse(courseId);
        Map<Long, CourseCatalogueDraft> chapterRows = new LinkedHashMap<>();
        Map<Long, List<CourseCatalogueDraft>> childrenOf = new HashMap<>();
        for (CourseCatalogueDraft row : rows) {
            if (row.getType() != null && row.getType() == 1) {
                chapterRows.put(row.getId(), row);
                continue;
            }
            Long parent = row.getParentCatalogueId() == null ? 0L : row.getParentCatalogueId();
            childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(row);
        }
        Map<Long, Integer> quizMap = new HashMap<>();
        for (Map<String, Object> item : teacherCatalogueDraftMapper.countQuizByCata(courseId)) {
            quizMap.put(((Number) item.get("cataId")).longValue(), ((Number) item.get("num")).intValue());
        }
        for (CourseCatalogueDraft row : chapterRows.values()) {
            TeacherCourseDraftVO.Chapter chapter = new TeacherCourseDraftVO.Chapter();
            chapter.setId(row.getId());
            chapter.setTitle(row.getName());
            List<CourseCatalogueDraft> children = childrenOf.get(row.getId());
            if (children != null) {
                children.sort(Comparator.comparing(CourseCatalogueDraft::getCIndex,
                        Comparator.nullsLast(Integer::compareTo)));
                for (CourseCatalogueDraft sec : children) {
                    chapter.getSections().add(toSection(sec, quizMap.get(sec.getId())));
                }
            }
            vo.getChapters().add(chapter);
        }

        // 「主讲 = 课程归属人」：能编辑这门课的老师就是它的主讲（开放平台没有校内名册）。
        // ⚠️ 即使库里没有也要**落一条**：只在内存里补的话，界面上的主讲在库中并不存在，
        //    上架后 course_teacher 会是空的 —— 课程一个讲师都没有。
        Long ownerId = UserContext.getUser();
        vo.setOwnerId(ownerId);
        if (ownerId != null && (teacherCourseShelfMapper.countDraft(courseId) > 0
                || teacherCourseShelfMapper.countShelfCourse(courseId) > 0)) {
            ensureOwnerAsTeacher(courseId, ownerId);
        }

        List<CourseTeacherVO> teachers = courseTeacherDraftService.queryTeacherOfCourse(courseId, false);
        if (CollUtils.isNotEmpty(teachers)) {
            for (CourseTeacherVO t : teachers) {
                TeacherCourseDraftVO.TeacherItem item = new TeacherCourseDraftVO.TeacherItem();
                item.setId(t.getId());
                item.setName(t.getName());
                // 老模型的 role 取的是用户档案里的「职位」（course_teacher 表没有角色列），如实映射；
                // 岗位为空就是空，前端显示「—」。
                item.setRole(t.getJob());
                item.setDept(t.getIntroduce());
                item.setIsShow(t.getIsShow());
                vo.getTeachers().add(item);
            }
        }

        vo.setChecks(buildChecks(vo));
        return vo;
    }

    // ------------------------------------------------------------------ 步骤①

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveBasic(TeacherCourseDraftDTO.BasicSaveDTO dto) {
        CourseBaseInfoSaveDTO save = new CourseBaseInfoSaveDTO();
        save.setId(dto.getCourseId());
        save.setName(dto.getName() == null ? null : dto.getName().trim());
        save.setThirdCateId(dto.getThirdCateId());
        save.setCoverUrl(dto.getCoverUrl());
        Integer price = dto.getPrice() == null ? 0 : dto.getPrice();
        save.setPrice(price);
        save.setFree(price <= 0);
        save.setValidDuration(dto.getValidDays());
        save.setIntroduce(dto.getIntroduce());
        save.setUsePeople(dto.getUsePeople());
        save.setDetail(dto.getDetail());
        // 老模型要求购买时间窗；教师端向导没有这个字段 —— 用「当前时间 + 有效期」如实推导
        LocalDateTime now = LocalDateTime.now();
        save.setPurchaseStartTime(now);
        save.setPurchaseEndTime(now.plusDays(dto.getValidDays() == null ? 365 : dto.getValidDays()));

        CourseSaveVO vo = courseDraftService.save(save);
        return vo == null ? dto.getCourseId() : vo.getId();
    }

    // ------------------------------------------------------------------ 步骤②

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherCourseDraftVO saveCatalog(TeacherCourseDraftDTO.CatalogSaveDTO dto) {
        Long courseId = dto.getCourseId();
        List<CourseCatalogueDraft> existing = teacherCatalogueDraftMapper.listByCourse(courseId);
        Set<Long> existingIds = existing.stream().map(CourseCatalogueDraft::getId).collect(Collectors.toSet());
        Set<Long> keep = new HashSet<>();

        int chIndex = 1;
        for (TeacherCourseDraftDTO.ChapterDTO ch : dto.getChapters()) {
            Long chapterId = resolvableId(ch.getId(), existingIds);
            if (chapterId == null) {
                // 新增章：id 由我们（雪花）生成 —— 它与小节的 parent_catalogue_id 天然一致
                chapterId = IdWorker.getId();
                CourseCatalogueDraft row = newDraftRow(chapterId, courseId, 1, ch.getTitle(), 0L, chIndex);
                teacherCatalogueDraftMapper.insertWithId(row);
            } else {
                teacherCatalogueDraftMapper.updateTitleAndIndex(chapterId, ch.getTitle(), chIndex, 0L);
            }
            keep.add(chapterId);

            int secIndex = 1;
            for (TeacherCourseDraftDTO.SectionDTO sec : ch.getSections()) {
                Long sectionId = resolvableId(sec.getId(), existingIds);
                if (sectionId == null) {
                    sectionId = IdWorker.getId();
                    CourseCatalogueDraft row = newDraftRow(sectionId, courseId, 2, sec.getTitle(), chapterId, secIndex);
                    teacherCatalogueDraftMapper.insertWithId(row);
                } else {
                    // 按 id 精确更新：挂在这一节上的视频 / 试看 / 配题都不会被冲掉
                    teacherCatalogueDraftMapper.updateTitleAndIndex(sectionId, sec.getTitle(), secIndex, chapterId);
                }
                keep.add(sectionId);
                secIndex++;
            }
            chIndex++;
        }

        // 删除本次目录里不存在的行（增量删除，而不是整树删了重插）
        List<Long> toRemove = existingIds.stream().filter(id -> !keep.contains(id)).collect(Collectors.toList());
        if (!toRemove.isEmpty()) {
            teacherCatalogueDraftMapper.deleteByIds(toRemove);
        }

        courseDraftService.updateStep(courseId, 2); // 2 = CATALOGUE
        courseCataSubjectDraftService.deleteNotInCataIdList(courseId);
        return getDraft(courseId);
    }

    /**
     * 保证课程归属人（=主讲）出现在讲师列表里，排在第一位。
     * 只在缺失时写一次；已存在的（含历史课程）不动，避免每次读都写库。
     */
    private void ensureOwnerAsTeacher(Long courseId, Long ownerId) {
        List<CourseTeacherVO> current = courseTeacherDraftService.queryTeacherOfCourse(courseId, false);
        List<CourseTeacherSaveDTO.TeacherInfo> infos = new ArrayList<>();
        boolean exists = false;
        for (CourseTeacherVO t : CollUtils.emptyIfNull(current)) {
            CourseTeacherSaveDTO.TeacherInfo info = new CourseTeacherSaveDTO.TeacherInfo();
            info.setId(t.getId());
            info.setIsShow(t.getIsShow() == null || t.getIsShow());
            infos.add(info);
            if (ownerId.equals(t.getId())) {
                exists = true;
            }
        }
        if (exists) {
            return;
        }
        // 主讲排在最前（老模型的 c_index 就是数组顺序）
        CourseTeacherSaveDTO.TeacherInfo self = new CourseTeacherSaveDTO.TeacherInfo();
        self.setId(ownerId);
        self.setIsShow(true);
        infos.add(0, self);
        courseTeacherDraftService.save(wrapTeachers(courseId, infos));
    }

    /** 只有「确实属于本课程的已有 id」才复用，否则当新增（返回 null） */
    private Long resolvableId(Long id, Set<Long> existingIds) {
        return id != null && existingIds.contains(id) ? id : null;
    }

    private CourseCatalogueDraft newDraftRow(Long id, Long courseId, int type, String name, Long parentId, int index) {
        CourseCatalogueDraft row = new CourseCatalogueDraft();
        row.setId(id);
        row.setCourseId(courseId);
        row.setType(type);
        row.setName(name);
        row.setParentCatalogueId(parentId);
        row.setCIndex(index);
        row.setTrailer(0);
        row.setPlayBack(0);
        row.setMediaDuration(0);
        row.setCanUpdate(true);
        row.setCreater(UserContext.getUser());
        row.setUpdater(UserContext.getUser());
        return row;
    }

    // ------------------------------------------------------------------ 步骤③

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveSectionVideo(Long sectionId, TeacherCourseDraftDTO.SectionVideoDTO dto) {
        CourseCatalogueDraft section = requireSection(sectionId, dto.getCourseId());
        String videoName = dto.getVideoName();
        int trailer = dto.getPreview() == null ? section.getTrailer() : (dto.getPreview() ? 1 : 0);

        // ---------- 情况一：清除视频（videoName 为空）----------
        if (videoName == null || videoName.isBlank()) {
            // 文件名与媒资**必须同时清掉** —— 只清一个就会留下「有名字、没媒资」的半截数据（P30）
            teacherCatalogueDraftMapper.updateSectionVideo(sectionId, null, 0, trailer);
            teacherCatalogueDraftMapper.updateSectionMedia(sectionId, null);
            courseDraftService.updateStep(dto.getCourseId(), 3);
            return formatDuration(null);
        }

        // ---------- 情况二：登记视频 ----------
        // P30：以前「有没有视频」在三处用了三套判定（前端本地状态 / video_name / media_id），
        //      于是讲师端说「已上传」、学生端说「没视频」。现在统一以 **media_id** 为准，
        //      而且**不允许写出没有媒资的登记**——宁可明确失败，也不留半截数据让两端打架。
        Long mediaId = dto.getMediaId();
        MediaMetaDTO meta = null;
        // 1) 前端带来的 id 必须先**校验真实存在** —— 否则传个不存在的 id 照样落库，
        //    学生端播放时就报「媒资不存在」，等于换了一种半截数据。
        if (mediaId != null) {
            try {
                meta = mediaClient.findMetaById(mediaId);
            } catch (Exception e) {
                log.warn("校验媒资 {} 失败：{}", mediaId, e);
            }
            if (meta == null) {
                log.warn("媒资 {} 不存在，改为按文件名反查：{}", mediaId, videoName);
                mediaId = null;
            }
        }
        // 2) 没带 / 带了无效 id → 按文件名反查（前端漏传、旧版页面、预读失败等）
        if (mediaId == null) {
            try {
                meta = mediaClient.latestByFilename(videoName);
            } catch (Exception e) {
                log.warn("按文件名反查媒资失败：{}", videoName, e);
                meta = null;
            }
            if (meta != null && meta.getId() != null) {
                mediaId = meta.getId();
            }
        }
        if (mediaId == null) {
            log.warn("登记视频失败：小节 {} 的文件 {} 在媒资库中找不到对应记录", sectionId, videoName);
            throw new BadRequestException("视频未完成上传，请重新上传该小节的视频");
        }
        // 3) 时长以**媒资记录的真实值**为准（前端预读视频元数据可能失败，那时传上来的是 0）
        Integer realSeconds = (meta != null && meta.getDuration() != null && meta.getDuration() > 0)
                ? Math.round(meta.getDuration()) : null;

        int seconds;
        if (realSeconds != null) {
            seconds = realSeconds;
        } else if (dto.getDurationSec() != null && dto.getDurationSec() > 0) {
            seconds = dto.getDurationSec();
        } else {
            seconds = dto.getDurationMin() == null ? 0 : dto.getDurationMin() * 60;
        }

        teacherCatalogueDraftMapper.updateSectionVideo(sectionId, videoName, seconds, trailer);
        teacherCatalogueDraftMapper.updateSectionMedia(sectionId, mediaId);
        courseDraftService.updateStep(dto.getCourseId(), 3); // 3 = MEDIA
        return formatDuration(seconds / 60);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleSectionPreview(Long sectionId, Long courseId) {
        CourseCatalogueDraft section = requireSection(sectionId, courseId);
        boolean next = !toBool(section.getTrailer());
        teacherCatalogueDraftMapper.updateSectionTrailer(sectionId, next ? 1 : 0);
        return next;
    }

    // ------------------------------------------------------------------ 步骤④

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSectionQuiz(Long sectionId, TeacherCourseDraftDTO.SectionQuizDTO dto) {
        CourseCatalogueDraft section = requireSection(sectionId, dto.getCourseId());
        // 引用语义：先清掉这一节旧的引用，再写新的（题目本体在 tj_exam，不动）
        teacherCatalogueDraftMapper.clearSectionQuiz(dto.getCourseId(), sectionId);
        if (CollUtils.isNotEmpty(dto.getSubjectIds())) {
            // 逐行插入：每行一个独立雪花 id（早前一版所有行共用一个 id → DuplicateKey）
            for (Long subjectId : dto.getSubjectIds()) {
                teacherCatalogueDraftMapper.insertSectionQuiz(IdWorker.getId(), dto.getCourseId(), sectionId, subjectId);
            }
        }
        courseDraftService.updateStep(dto.getCourseId(), 4); // 4 = SUBJECT

        // ------------------------------------------------------------------
        // 配题即出卷（p15 方案 A）：把这一节的题目引用同步成一张随堂练习卷。
        // 学生端 `POST /es/exams/section/{id}/start` 读的是 `exam.section_id`，
        // **不调这一步，老师配的题就只是"写进一个没人读的地方"**（p15 §2 的断口）。
        // 放在同一个事务里：卷子建不出来就整体回滚，老师会看到报错，
        // 不会出现「界面显示配好了、学生却一道题都没有」。
        // ------------------------------------------------------------------
        CourseDraft draft = courseDraftMapper.selectById(dto.getCourseId());
        try {
            examClient.upsertPracticePaper(new PracticeUpsertDTO()
                    .setCourseId(dto.getCourseId())
                    .setCourseName(draft == null ? null : draft.getName())
                    .setSectionId(sectionId)
                    .setSectionName(section == null ? null : section.getName())
                    .setQuestionIds(dto.getSubjectIds())
                    .setOperatorId(UserContext.getUser()));
        } catch (Exception e) {
            log.error("配题：同步随堂练习卷失败 courseId={} sectionId={}", dto.getCourseId(), sectionId, e);
            throw new BadRequestException("配题没有保存：同步随堂练习卷失败（考试服务不可用），请稍后重试");
        }
    }

    // ------------------------------------------------------------------ 步骤⑤

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherCourseDraftVO.TeacherItem addTeacher(TeacherCourseDraftDTO.TeacherSaveDTO dto) {
        List<CourseTeacherVO> current = courseTeacherDraftService.queryTeacherOfCourse(dto.getCourseId(), false);
        List<CourseTeacherSaveDTO.TeacherInfo> infos = new ArrayList<>();
        boolean exists = false;
        for (CourseTeacherVO t : CollUtils.emptyIfNull(current)) {
            CourseTeacherSaveDTO.TeacherInfo info = new CourseTeacherSaveDTO.TeacherInfo();
            info.setId(t.getId());
            info.setIsShow(t.getIsShow() == null || t.getIsShow());
            infos.add(info);
            if (dto.getTeacherId().equals(t.getId())) {
                exists = true;
            }
        }
        if (!exists) {
            CourseTeacherSaveDTO.TeacherInfo info = new CourseTeacherSaveDTO.TeacherInfo();
            info.setId(dto.getTeacherId());
            info.setIsShow(true);
            infos.add(info);
        }
        courseTeacherDraftService.save(wrapTeachers(dto.getCourseId(), infos));
        courseDraftService.updateStep(dto.getCourseId(), 5); // 5 = TEACHER

        TeacherCourseDraftVO.TeacherItem item = new TeacherCourseDraftVO.TeacherItem();
        item.setId(dto.getTeacherId());
        item.setIsShow(true);
        // 姓名 / 岗位 / 简介都在用户库里，这里**不编造**：
        // 前端用刚选中的那个人的真实信息展示，重新读草稿时由 queryTeacherOfCourse 带出真实值。
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTeacher(Long courseId, Long teacherId) {
        Long ownerId = UserContext.getUser();
        if (ownerId != null && ownerId.equals(teacherId)) {
            // 主讲就是课程归属人，移除它等于这门课没有作者
            throw new BadRequestException("课程归属人（主讲）不能移除");
        }
        List<CourseTeacherVO> current = courseTeacherDraftService.queryTeacherOfCourse(courseId, false);
        List<CourseTeacherSaveDTO.TeacherInfo> infos = new ArrayList<>();
        for (CourseTeacherVO t : CollUtils.emptyIfNull(current)) {
            if (teacherId.equals(t.getId())) {
                continue;
            }
            CourseTeacherSaveDTO.TeacherInfo info = new CourseTeacherSaveDTO.TeacherInfo();
            info.setId(t.getId());
            info.setIsShow(t.getIsShow() == null || t.getIsShow());
            infos.add(info);
        }
        courseTeacherDraftService.save(wrapTeachers(courseId, infos));
        courseDraftService.updateStep(courseId, 5); // 5 = TEACHER
    }

    // ------------------------------------------------------------------ 上架

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherCourseDraftVO.Checks publish(Long courseId) {
        TeacherCourseDraftVO draft = getDraft(courseId);
        TeacherCourseDraftVO.Checks checks = draft.getChecks();
        if (!Boolean.TRUE.equals(checks.getBasic().getOk()) || !Boolean.TRUE.equals(checks.getCatalog().getOk())) {
            throw new BadRequestException("上架前校验未通过："
                    + (!Boolean.TRUE.equals(checks.getBasic().getOk()) ? "基本信息 " : "")
                    + (!Boolean.TRUE.equals(checks.getCatalog().getOk()) ? "课程目录" : ""));
        }
        // 简化上架（契约口径：视频 / 配题可上架后补）：
        // 不走老的 checkBeforeUpShelf + upShelf（它们要求每节都有媒资，
        // 而媒资依赖已停用的 media-service + 腾讯云 VOD）。这里只做数据搬运。
        teacherCourseShelfMapper.copyCourse(courseId);
        teacherCourseShelfMapper.copyContent(courseId);
        teacherCourseShelfMapper.deleteStaleCatalogue(courseId);
        teacherCourseShelfMapper.copyCatalogue(courseId);
        // 小节配题也要搬：正式表的 course_cata_subject 是**学生端「练习」入口的唯一依据**
        // （catalogs 按它算 subjectNum，前端 >0 才显示「练习」按钮）。以前漏搬这张表，
        // 结果是"老师配了题、上架后学生端连入口都没有"（p15 §2 断口的另一半）。
        teacherCourseShelfMapper.clearShelfSectionQuiz(courseId);
        teacherCourseShelfMapper.copySectionQuiz(courseId);
        teacherCourseShelfMapper.clearShelfTeachers(courseId);
        teacherCourseShelfMapper.copyTeachers(courseId);
        teacherCourseShelfMapper.lockDraftCatalogue(courseId);
        teacherCourseShelfMapper.markDraftPublished(courseId);
        draft.setStatus("published");
        return checks;
    }

    // ------------------------------------------------------------------ 我的课程

    @Override
    public List<TeacherMyCourseVO> listMyCourses() {
        Long uid = UserContext.getUser();
        if (uid == null) {
            return new ArrayList<>();
        }
        // 以正式表「我讲的课」为底，再用草稿表补两类：① 还没上架的新课 ② 上架后又有改动的课
        Map<Long, TeacherMyCourseVO> merged = new LinkedHashMap<>();
        for (TeacherMyCourseVO vo : teacherCourseShelfMapper.listShelfCoursesOfTeacher(uid)) {
            merged.put(vo.getId(), vo);
        }
        for (TeacherMyCourseVO vo : teacherCourseShelfMapper.listDraftCoursesOfTeacher(uid)) {
            TeacherMyCourseVO exist = merged.get(vo.getId());
            if (exist == null) {
                merged.put(vo.getId(), vo);
            } else if ("draft".equals(vo.getStatus())) {
                // 已上架、但草稿表里还有未上架的改动 —— 如实标出来，前端显示「有未上架改动」
                exist.setEditing(true);
                exist.setChapterNum(vo.getChapterNum());
                exist.setSectionNum(vo.getSectionNum());
            }
        }
        return new ArrayList<>(merged.values());
    }

    // ------------------------------------------------------------------ 内部工具

    /** 当前登录老师的真实姓名；user-service 不可用时退回「用户{id}」，不编造名字 */
    private String currentUserName(Long uid) {
        try {
            UserDTO u = userClient.queryUserById(uid);
            if (u != null) {
                if (u.getName() != null && !u.getName().isEmpty()) {
                    return u.getName();
                }
                if (u.getUsername() != null && !u.getUsername().isEmpty()) {
                    return u.getUsername();
                }
            }
        } catch (Exception e) {
            log.warn("查询当前用户姓名失败，讲师用占位名。userId={}", uid, e);
        }
        return "用户" + uid;
    }

    /** 只认「确实属于本课程」的 id，其余当新增 —— 防止前端占位 id 撞主键 */
    private Long existingId(Long id, Long courseId) {
        if (id == null) {
            return null;
        }
        CourseCatalogueDraft row = catalogueDraftMapper.selectById(id);
        if (row == null || !courseId.equals(row.getCourseId())) {
            return null;
        }
        return id;
    }

    private CourseCatalogueDraft requireSection(Long sectionId, Long courseId) {
        CourseCatalogueDraft section = sectionId == null ? null : catalogueDraftMapper.selectById(sectionId);
        if (section == null || (courseId != null && !courseId.equals(section.getCourseId()))) {
            throw new BadRequestException("小节不存在或不属于该课程：" + sectionId);
        }
        return section;
    }

    private CourseTeacherSaveDTO wrapTeachers(Long courseId, List<CourseTeacherSaveDTO.TeacherInfo> infos) {
        CourseTeacherSaveDTO save = new CourseTeacherSaveDTO();
        save.setId(courseId);
        save.setTeachers(infos);
        return save;
    }

    private TeacherCourseDraftVO.Section toSection(CourseCatalogueDraft row, Integer quizCount) {
        TeacherCourseDraftVO.Section s = new TeacherCourseDraftVO.Section();
        s.setId(row.getId());
        s.setTitle(row.getName());
        s.setPreview(toBool(row.getTrailer()));

        // 视频：**以媒资（media_id）为唯一凭据**
        // ⚠️ P30：以前这里看的是 video_name，于是出现「讲师端说已上传、学生端说没视频」——
        //    学生端播放必须有 media_id，而它可能缺失（旧版页面漏传 / 历史半截数据）。
        //    判定口径必须和播放端一致，否则两端永远各说各话。
        Long mediaId = row == null ? null : row.getMediaId();
        String videoName = row == null ? null : row.getVideoName();
        Integer seconds = row == null ? null : row.getMediaDuration();
        if (mediaId != null) {
            s.getVideo().setStatus("done");
            s.getVideo().setName(videoName);
            s.getVideo().setMediaId(mediaId);   // 回传给前端，目录保存往返时不丢
            if (seconds != null && seconds > 0) {
                s.getVideo().setDurationMin(seconds / 60);
                s.setDuration(formatDuration(seconds / 60));
            }
        } else if (videoName != null && !videoName.isEmpty()) {
            // 半截数据（有文件名、没媒资）：**不谎报「已上传」**，如实显示为未配视频，
            // 讲师重传即可 —— 重传现在会被严格校验，不会再产生这种状态。
            log.warn("小节 {} 只有文件名没有媒资（video_name={}），按「未配视频」处理", row.getId(), videoName);
        }

        if (quizCount != null && quizCount > 0) {
            TeacherCourseDraftVO.Quiz quiz = new TeacherCourseDraftVO.Quiz();
            quiz.setCount(quizCount);
            s.setQuiz(quiz);
        }
        return s;
    }

    /** 校验清单：全部从明细推导 */
    private TeacherCourseDraftVO.Checks buildChecks(TeacherCourseDraftVO vo) {
        TeacherCourseDraftVO.Checks checks = new TeacherCourseDraftVO.Checks();

        boolean basicOk = vo.getBasic().getName() != null && !vo.getBasic().getName().isEmpty()
                && vo.getBasic().getThirdCateId() != null;
        checks.getBasic().setOk(basicOk);
        checks.getBasic().setText(basicOk ? "基本信息已填写完整" : "基本信息未填写完整（名称 / 分类必填）");

        List<TeacherCourseDraftVO.Section> all = vo.getChapters().stream()
                .flatMap(c -> c.getSections().stream()).collect(Collectors.toList());
        boolean catalogOk = !all.isEmpty();
        checks.getCatalog().setOk(catalogOk);
        checks.getCatalog().setText(catalogOk
                ? String.format("课程目录已建立（%d 章 %d 节）", vo.getChapters().size(), all.size())
                : "还没有建立课程目录");

        long withVideo = all.stream().filter(s -> "done".equals(s.getVideo().getStatus())).count();
        int missing = all.size() - (int) withVideo;
        checks.getVideo().setOk(catalogOk && missing == 0);
        checks.getVideo().setWarn(missing > 0);
        checks.getVideo().setMissing(missing);
        checks.getVideo().setText(all.isEmpty() ? "暂无小节"
                : String.format("视频已绑定（%d / %d 节）", withVideo, all.size()));

        // ⚠️ P17（用户 2026-09-16 决策）：取消「小节配题 → 随堂练习」这条线，只保留试卷。
        //    这里**不再构造 quiz 项** —— 前端按 `[basic, catalog, video].filter(Boolean)` 渲染，
        //    少一项不会出问题（Jackson 配了 NON_NULL，null 字段整个 key 不出现）。
        return checks;
    }

    private boolean toBool(Integer v) {
        return v != null && v == 1;
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return null;
        }
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private <T> List<T> single(T item) {
        List<T> list = new ArrayList<>();
        list.add(item);
        return list;
    }
}
