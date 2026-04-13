package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.enums.QuestionStatus;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import javassist.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;

    private final UserClient userClient;

    private final SearchClient searchClient;

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    private final IInteractionReplyService replyService;
    /**
     * 用户端新增问题
     * @param questionDTO  问题详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestion(QuestionFormDTO questionDTO) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.将DTO转化为VO,并补充数据
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        //3.保存到数据库
        this.save(question);
    }

    /**
     * 用户端修改问题
     * @param questionDTO  问题详情
     */
    @Override
    public void updateQuestion(QuestionFormDTO questionDTO , Long questionId) {
        //1.判断DTO是否有数据
        if(StringUtils.isBlank(questionDTO.getTitle())
                ||StringUtils.isBlank(questionDTO.getDescription())
                ||questionDTO.getAnonymity() == null){
            //传过来的数据有空的
            throw new BadRequestException("传过来的参数存在空的");
        }
        //2.传过来的数据没有问题，查询问题
        InteractionQuestion question = getById(questionId);
        if(question == null){
            throw new BizIllegalException("找不到您提问的问题，该问题可能已被删除");
        }
        //3.修改问题
        question.setTitle(questionDTO.getTitle());
        question.setDescription(questionDTO.getDescription());
        question.setAnonymity(questionDTO.getAnonymity());
        updateById(question);
    }

    /**
     * 用户端分页查询问题
     * @param query 分页查询条件
     * @return 返回值是一个VO的page集合
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.校验参数 courseId  sectionId都不能为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if(courseId == null && sectionId == null){
            throw new BadRequestException("课程id和小节id不能都为空");
        }
        //2.进行分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))   //只查询description字段之外的字段
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)      //是否已被隐藏 如果被隐藏就直接不返回了
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        //判断是否查询到了数据
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.补充数据：最近一次回答信息和需要查询用户信息的所有用户ID集合(问题提出者id+最近一次回答信息者不匿名的id)
        //3.1 获取提问者id集合(如果匿名则不用获取)
        Set<Long> userIds = new HashSet<>();    //这个是所有问题提出者id的集合
        Set<Long> answerIds = new HashSet<>();  //这个集合是很多问题中最后一个回答者的id集合
        for(InteractionQuestion q : records){
            if( !q.getAnonymity() ){  //不匿名
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }

        //3.2根据answerIds最后一个回答者的id集合 变成一个map<Long,InteractionReply>
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(!CollUtils.isEmpty(answerIds)) {
            List<InteractionReply> replyList = replyMapper.selectBatchIds(answerIds);
            for(InteractionReply reply : replyList){
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()){  //不匿名
                    userIds.add(reply.getUserId());
                }
            }
        }

        //3.3 根据userIds先将所有可以查询到的用户包括问题提出者和问题回答者统一放在一个map集合中 然后用问题提出者时候直接调用即可
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1.将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 4.2.封装提问者信息
            if(!r.getAnonymity()){  //不匿名 返回用户图像 名字 id
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3.封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){// 匿名用户直接忽略
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }

            }
        }
        return PageDTO.of(page, voList);
    }

    /**
     * 根据id查询问题详情
     * @param id 问题id
     * @return 返回值跟分页单体返回值一样 只不过多了个description属性
     */
    @Override
    public QuestionVO getQuestionById(Long id) {
        //1.查询问题信息
        InteractionQuestion question = getById(id);
        //2.校验
        if(question == null || question.getHidden()){
            //问题信息不存在或者提出的问题被隐藏
            return null;
        }
        //3.查询用户信息
        UserDTO user = null;
        if( !question.getAnonymity() ){
            //不匿名
            user = userClient.queryUserById(question.getUserId());
        }
        //4.赋值VO
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        return vo;
    }

    /**
     * 根据id(问题id)删除提问
     * 因为我们是逻辑删除 并不是真的把数据从数据库删除了 而是更改删除状态 这样不用操作回复表 回复也不会显示出来
     * @param id 提问id
     */
    @Override
    public void deleteQuestionById(Long id) throws NotFoundException {
        Long userId = UserContext.getUser();
        //1.判断问题是否存在
        InteractionQuestion question = getById(id);
        if(question == null || question.getHidden()){
            throw new NotFoundException("提问不存在");
        }
        //2.校验问题中用户id和现在操作的用户id是否相同
        if(!Objects.equals(userId, question.getUserId())){
            throw new BizIllegalException("该问题提问者非当前用户，无法删除");
        }
        //3.删除提问
        removeById(id);
    }

    /**
     * 管理端分页查询互动问答
     * 三个注意的点：
     * 一：query参数中有courseName 字段 但是我们数据库PO中没有该字段 只有courseId  所以我们可以调用SearchClient暴漏的接口进行全局模糊查询
     * 二：在返回对象QuestionAdminVO中有三级分类 这三级分类跟课程深度绑定，所以只要我们找到问题所属的课程，就能找到三级分类，但是在api下有
     * 查找所有课程分类的集合，而课程中只包含3个分类id。因此我们需要自己从所有分类集合中找出课程有关的这三个。
     * 三：课程分类数据在很多业务中都需要查询，这样的数据如此频繁的查询，这里我们使用本地缓存Caffeine  在查找redis之前先查找本地缓存
     * 而在api/cache中已经定义好分类的本地缓存使用工具了CategoryCache，我们只用CategoryCache工具即可
     * @param query 管理端分页条件
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //1.处理课程名称 获取课程ids集合
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);  //直接返回一个空的PageDTO 里面的属性是空值
            }
        }
        //2.分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.准备vo需要的数据：用户数据、课程数据、章节数据
        Set<Long> uIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        //3.1获取各种数据的集合
        for(InteractionQuestion q : records){
            uIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        //3.2根据uIds查询用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        Map<Long,UserDTO> userMap = new HashMap<>(userDTOS.size());
        if(CollUtils.isNotEmpty(userDTOS)){
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId , u->u));
        }

        //3.3根据cIds查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if (CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        //3.4 根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);   //调用章节接口 根据章节id获取章节信息
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        // 4.封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 4.1.将PO转VO，属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            // 4.2.用户信息
            UserDTO user = userMap.get(q.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }
            // 4.3.课程信息以及分类信息
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            // 4.4.章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }

    /**
     * 管理端隐藏或显示问题
     * @param id    问题id
     * @param hidden   是否隐藏
     */
    @Override
    public void hiddenQuestionAdmin(Integer id, Boolean hidden) {
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BadRequestException("该问题不存在");
        }
        question.setHidden(hidden);
        updateById(question);
    }

    /**
     * 管理端根据id查看问题详细信息
     * @param id 问题id
     * @return QuestionAdminVO对象
     */
    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Integer id) {
        //1.查出问题集合
        InteractionQuestion question = this.lambdaQuery()
                .eq(InteractionQuestion::getId, id)
                .one();
        //查询用户ids和章、节ids
        ArrayList<Long> uIds = new ArrayList<>();
        ArrayList<Long> chapterAndSectionIds = new ArrayList<>();
        uIds.add(question.getUserId());
        chapterAndSectionIds.add(question.getSectionId());
        chapterAndSectionIds.add(question.getChapterId());

        //2.远程查询课程信息
        //2.1远程调用搜索服务查询课程id
        CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if(course == null){
            throw new BizIllegalException("该问题的课程不存在");
        }
        //3.远程批量查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if(CollUtils.isEmpty(catas)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        //4.远程批量查询用户信息
        uIds.addAll(course.getTeacherIds()); //管理端才查询教师信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户集合不存在");
        }
        Map<Long, UserDTO> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //6.封装vo
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //6.1设置课程信息
        vo.setCourseName(course.getName());
        //6.2设置用户信息
        if(userMap != null){
            vo.setUserName(userMap.get(question.getUserId()).getName());
            vo.setUserIcon(userMap.get(question.getUserId()).getIcon());
            vo.setTeacherName(userMap.get(course.getTeacherIds().get(0)).getName());
        }
        //6.3设置章节信息
        if(cataMap != null){
            vo.setSectionName(cataMap.get(question.getSectionId()));
            vo.setChapterName(cataMap.get(question.getChapterId()));
        }
        //6.4设置分类信息(使用自定义的缓存工具类获取分类缓存信息)
        List<Long> categoryIds = course.getCategoryIds();
        String categoryNames = categoryCache.getCategoryNames(categoryIds);
        vo.setCategoryName(categoryNames);
        //6.5设置被回答数量
        Integer answerTimes = replyService.lambdaQuery()
                .eq(InteractionReply::getQuestionId, id)
                .eq(InteractionReply::getAnswerId, 0L)
                .count();
        vo.setAnswerTimes(answerTimes);
        //7.修改问题状态为已查看
        boolean update = lambdaUpdate()
                .eq(InteractionQuestion::getId, id)
                .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                .update();
        if(!update){
            throw new DbException("问题状态修改失败");
        }
        return vo;
    }


}
