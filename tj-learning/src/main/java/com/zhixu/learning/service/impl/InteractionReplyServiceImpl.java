package com.zhixu.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.exceptions.DbException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.learning.domain.dto.ReplyDTO;
import com.zhixu.learning.domain.enums.QuestionStatus;
import com.zhixu.learning.domain.po.InteractionQuestion;
import com.zhixu.learning.domain.po.InteractionReply;
import com.zhixu.learning.domain.query.ReplyPageQuery;
import com.zhixu.learning.domain.vo.ReplyVO;
import com.zhixu.learning.mapper.InteractionQuestionMapper;
import com.zhixu.learning.mapper.InteractionReplyMapper;
import com.zhixu.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-10
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;
    //private final RemarkClient remarkClient;


    /**
     * 用户端新增回答或者评论
     * @param replyDTO 回答或者评论参数
     */
    @Override
    public void addReplyOrAnswer(ReplyDTO replyDTO) {
        //1.先评论或者回答保存到评论回答表中 因为评论或者回答在一个表中
        Long userId = UserContext.getUser();
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        boolean save = save(reply);
        if(!save){
            //false
            throw new DbException("数据库保存回答/评论失败");
        }
        InteractionQuestion question = questionMapper.selectById(replyDTO.getQuestionId());
        //2.判断是回答还是评论 判断依据是是否含有answer_id 回复的上级回答id
        //2.1 如果是回答 则修改问题回答次数 最新回答id
        if(replyDTO.getAnswerId() == null && replyDTO.getTargetReplyId() == null){ //上级回答为空或者上级评论为空
            question.setAnswerTimes(question.getAnswerTimes() + 1);
            question.setLatestAnswerId(replyDTO.getAnswerId());
        }else{
            //2.2 如果是评论 则修改评论次数
            InteractionReply byId = getById(replyDTO.getAnswerId());
            byId.setReplyTimes(byId.getReplyTimes() + 1);
            updateById(byId);
        }
        //3.如果是学生 则更新问题状态为未查看
        if(replyDTO.getIsStudent()){
            question.setStatus(QuestionStatus.UN_CHECK);
            questionMapper.updateById(question);
        }
    }
/*
    @Override
    public PageDTO<ReplyVO> queryReplyOrAnswerPage(ReplyPageQuery query, Boolean isAdmin) {
        //校验QuestionId和AnswerId都为空
        Long answerId = query.getAnswerId();
        Long questionId = query.getQuestionId();
        if(answerId == null && questionId == null){
            throw new BadRequestException("错误参数，被回答id和问题id不能都为NULL");
        }
        //1.查询问题下的回答列表 或者 回答下的评论列表
        Page<InteractionReply> page = lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                //查询回答列表(在问题详情页面触发)，或查询评论列表(在点击评论后触发)
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                //不查询被隐藏的回答或者评论 todo //一个待优化的地方，这里是不查询出被隐藏的回答或者评论，优点是提高了数据库效率，缺点就是子级如果要保留，那他将无法获取被评论者的id。如果想要连同子级的评论一起隐藏，那么就需要在这里查出此条，在vo处排除掉父级是此条的评论
                .eq(!isAdmin, InteractionReply::getHidden, Boolean.FALSE)
                //先按照点赞数量倒叙，相同点赞数量再按照创建时间倒叙
                .page(query.toMpPage(new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false), new OrderItem(DATA_FIELD_NAME_CREATE_TIME, false)));
        List<InteractionReply> replyList = page.getRecords();
        if(CollUtils.isEmpty(replyList)){
            return PageDTO.empty(page);
        }
        //2.远程批量查询用户信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        for (InteractionReply rep : replyList) {
            //2.1添加回复者id
            userIds.add(rep.getUserId());
            //2.2添加被回复者id(如果只添加回复者id，可能会丢失回复者信息，比如查询评论列表时，replyList的数据是该回答下的，但回答者的信息会丢失)
            if(query.getAnswerId()!=null){
                userIds.add(rep.getTargetUserId());
            }
            //2.3添加评论的id
            replyIds.add(rep.getId());
        }
        Map<Long, UserDTO> userDTOMap = userClient.queryUserByIds(userIds).stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.查询用户点赞状态(入参是当前分页的评论id集合，出参是当前分页被点过赞的评论id集合)
        Set<Long> bizLiked = remarkClient.getLikedStatusByBizList(replyIds);
        //4.封装vo
        List<ReplyVO> vos = BeanUtils.copyList(replyList, ReplyVO.class);
        List<ReplyVO> collect = vos.stream().map(i -> {
            //4.1设置用户信息
            //如果该回答或评论非匿名则设置用户信息
            if (!i.getAnonymity() || isAdmin) {
                UserDTO userDTO = userDTOMap.get(i.getUserId());
                if (userDTO != null) {
                    i.setUserIcon(userDTO.getIcon());
                    i.setUserName(userDTO.getName());
                    i.setUserType(userDTO.getType());
                }
            }
            //4.2设置被评论对象用户信息
            //如果该评论的上一级还是评论，那么设置该评论的评论对象用户信息(注意不是字段一定不要是TargetUserId的值，必须是TargetReplyId才能表示评论的对象也是评论)
            //由于我是先将replyList复制到了List<ReplyVO>，因此丢失了上级用户id，所以自己新增了TargetReplyId和TargetUserId字段
            if (i.getTargetReplyId() != 0) {
                Long upId = i.getTargetReplyId();
                //得到上一级的评论
                InteractionReply upReply = getById(upId);
                //上一级评论不是匿名才设置TargetUserName
                if(!upReply.getAnonymity() || isAdmin){
                    UserDTO userDTO = userDTOMap.get(upReply.getUserId());
                    if (userDTO != null) {
                        i.setTargetUserName(userDTO.getName());
                    }
                }
            }
            //4.3设置用户点赞信息(当前用户是否对该评论点过赞)
            i.setLiked(bizLiked.contains(i.getId()));
            return i;
        }).collect(Collectors.toList());
        return PageDTO.of(page, collect);
    }
*/

}
