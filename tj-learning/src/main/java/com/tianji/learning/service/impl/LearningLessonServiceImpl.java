package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.enums.LessonStatus;
import com.tianji.learning.domain.enums.PlanStatus;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-04
 */
@Service
@SuppressWarnings("ALL")
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;

    /**
     * 添加课程到课表
     * @param userId 用户id
     * @param courseIds 课程ids
     */
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量新增
        saveBatch(list);
    }

    /**
     * 查询我的课表
     * @param PageQuery  前端传来的查询参数和查询条件
     * @return         返回PageDTO,里面储存数据集合
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1.获取当前登录的用户
        Long userId = UserContext.getUser();

        //2.分页查询
        //select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0,5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        //3.查询课程信息,learning_lesson表中没有课程的详细信息，而返回值VO需要 所以需要利用课程id查询
        //3.1 获得课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //3.2 查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if(CollUtils.isEmpty(cInfoList)){
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        //3.3 把课程集合处理成map ,key是courseId,值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));


        //4. 封装VO返回
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        //4.1 循环遍历，把LearningLesson转成VO
        for (LearningLesson r : records) {
            //4.2 拷贝基础属性到vo
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            // 4.3.获取课程信息，填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    /**
     * 查询正在学习的课程
     * @return
     */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.查询学习课程列表并status=1根据最后学习时间倒序 限制为1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson == null){
            return null;
        }
        //3.将po转化为vo
        //发现vo对象缺少courseName,courseCoverUrl(封面),sections,CourseAmount,LatestSectionName,LatestSectionIndex
        LearningLessonVO vo = BeanUtils.copyProperties(lesson, LearningLessonVO.class);
        //4.查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);

        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    /**
     * 手动删除课程&退款删除课程
     * 只需要根据userid courseid删除即可
     * @param userId  用户id
     * @param courseId  课程id
     */
    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        remove(new LambdaQueryWrapper<LearningLesson>()
                    .eq(LearningLesson::getUserId, userId)
                    .eq(LearningLesson::getCourseId, courseId)
        );
    }

    /**
     * 校验当前用户是否有资格学些该课程 需要判断课表是否存在该课程/是否过期
     * @param courseId 课程id
     * @return 课表id
     */
    @Override
    public Long isLessonValid(Long courseId) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        if(userId == null||courseId == null){
            return null;
        }
        //2.查询数据库 如果存在则返回课表id 否则返回空
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        //用户不存在该课程
        if(lesson == null){
            return null;
        }
        //课程存在过期时间 并且现在时间已经过了过期时间
        if(lesson.getExpireTime() != null && LocalDateTime.now().isAfter(lesson.getExpireTime())){
            return null;
        }
        return lesson.getId();
    }

    /**
     * 根据courseId查询用户是否含有该课程 如有并返回该课程的学习情况
     * 返回 id courseId plan_status learned-sections create_time expire_time status
     * @param courseId 课程id
     * @return
     */
    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        if(userId == null||courseId == null){
            return null;
        }
        //2.跟据userid  courseid 查询
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            return null;
        }
        //3.拼接VO
        LearningLessonVO vo = BeanUtils.copyProperties(lesson, LearningLessonVO.class);
        return vo;
    }

    /**
     * 统计课程学习指定人数
     * @param courseId 课程id
     * @return 返回人数
     */
    @Override
    public Integer countLearningLessonByCourseId(Long courseId) {
        Integer count = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();
        return count;
    }

    @Override
    public LearningLesson queryLessonByCourseIdAndUserId(Long courseId, Long userId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();
        return lesson;
    }

    /**
     * 创建学习计划 实际上就是在学习课表上修改一行 有userid courseid freq
     * @param planDTO
     */
    @Override
    public void createLearningPlans(LearningPlanDTO planDTO,Integer freq) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.判断learning_lesson是否含有该课程 也就是是否买了该课程
        LearningLesson lesson = queryLessonByCourseIdAndUserId(planDTO.getCourseId(), userId);
        AssertUtils.isNotNull(lesson,"课程信息不存在");
        //3.修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    /**
     * 查询当前用户的本周学习计划
     * @param pageQuery
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.获取本周起始时间和结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        //3.封装weekFinished和weekTotalPlan 查询学习记录表
        //3.1 查询本周已学习的小节个数
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
        result.setWeekFinished(weekFinished);
        //3.2查询本周计划需要学习的小节个数
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        //3.3 TODO 本周学习积分


        //4.分页查询课程信息和学习计划信息
        //4.1 查询课程基本信息learningLesson以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time",false));
        List<LearningLesson> records = p.getRecords();
        //如果为空 返回一个空白页
        if(CollUtils.isEmpty(records)){
            result.setTotal(0L);
            result.setPages(0L);
            result.setList(CollUtils.emptyList());
            return result;
        }
        // 4.2.查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3.统计每一个课程本周已学习小节数量
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId, begin, end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        // 4.4.组装数据VO
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            // 4.4.1.拷贝基础属性到vo
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            // 4.4.2.填充课程详细信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            // 4.4.3.每个课程的本周已学习小节数量
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(), 0));
            // 4.4.4.每个课程的本周计划学习小节数量  因为这是在课表中已经记录好了 所以不需要查询 只需要赋值即可
            vo.setWeekFreq(r.getWeekFreq());
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(), p.getPages(), voList);
    }
    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 3.1.获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.3.把课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }


}
