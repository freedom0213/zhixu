package com.zhixu.remark.service.impl;


import com.zhixu.api.dto.remark.LikedTimesDTO;
import com.zhixu.common.autoconfigure.mq.RabbitMqHelper;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.StringUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.remark.constants.RedisConstants;
import com.zhixu.remark.domain.dto.LikeRecordFormDTO;
import com.zhixu.remark.domain.po.LikedRecord;
import com.zhixu.remark.mapper.LikedRecordMapper;
import com.zhixu.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.zhixu.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.zhixu.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;


/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;
    /**
     * 新增点赞或者删除点赞记录
     * @param likeDTO 前端传来的点赞参数 里面有点赞业务id 点赞业务类型 是否点赞(Boolean)
     */
    @Override
    public void addLikedRecord(LikeRecordFormDTO likeDTO) {
        // 1.基于前端的参数，判断是执行点赞还是取消点赞
        boolean success = likeDTO.getLiked() ? like(likeDTO) : unlike(likeDTO);
        // 2.判断是否执行成功，如果失败，则直接结束
        if (!success) {
            return;
        }
        //3.在redis中专门储存用户是否点赞的set表中统计该业务key下的点赞人数
        Long likedTimes = redisTemplate.opsForSet()
                .size(RedisConstants.LIKES_BIZ_KEY_PREFIX + likeDTO.getBizId());
        if(likedTimes == null){
            return;
        }
        //4.获取到该业务id下的点赞人数 修改Redis中zSet表下对应业务类型 业务id下的点赞总数
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + likeDTO.getBizType(),
                likeDTO.getBizId().toString(),
                likedTimes
        );


    }

    /**
     * 根据业务id查询点赞状态 根据当前用户id
     * @param bizIds 前端传来的需要查询点赞状态的id集合
     * @return 返回已点赞的id集合set
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.查询点赞状态 在redis中查询set集合 因为要查询多个业务id集合的点赞状态 而redis只能一个一个查询 但是有一个
        // RedisTemplate的pipeline 可以在一个请求中执行多次命令
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
    }

    /**
     * 定时任务方法
     * @param bizType 业务类型
     * @param maxBizSize 最大查询redis数据数量
     */
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1.读取并移除Redis中缓存的点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        // 2.数据转换
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        // 3.发送MQ消息
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list);
    }

    private boolean unlike(LikeRecordFormDTO likeDTO) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.获取key
        String Key = RedisConstants.LIKES_BIZ_KEY_PREFIX + likeDTO.getBizId();
        //3.执行SREM命令
        Long result = redisTemplate.opsForSet().remove(Key, userId.toString());
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO likeDTO) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.获取Key
        String Key = RedisConstants.LIKES_BIZ_KEY_PREFIX + likeDTO.getBizId();
        //3.执行SADD命令
        Long result = redisTemplate.opsForSet().add(Key, userId.toString());
        return result != null && result > 0;
    }
}
