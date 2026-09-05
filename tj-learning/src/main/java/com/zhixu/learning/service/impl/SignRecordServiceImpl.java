package com.zhixu.learning.service.impl;

import com.zhixu.common.autoconfigure.mq.RabbitMqHelper;
import com.zhixu.common.constants.MqConstants;
import com.zhixu.common.exceptions.BizIllegalException;
import com.zhixu.common.utils.BooleanUtils;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.DateUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.learning.constants.RedisConstants;
import com.zhixu.learning.domain.vo.SignResultVO;
import com.zhixu.learning.mq.message.SignInMessage;
import com.zhixu.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper MqHelper;

    /**
     * 先新增签到记录
     * 然后返回 连续签到天数 计算签到分数
     * 当然还需要发送消息 进行保存签到积分记录
     * @return  返回VO对象 连续签到了几天 签到得分
     */
    @Override
    public SignResultVO addSignRecord() {

        //1.保存签到记录
        //1.1获取用户id
        Long userId = UserContext.getUser();
        //1.2拼接redisKey
        LocalDate now = LocalDate.now();
        String Key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                            + userId
                            + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //1.3获取offSet 实际上是bitMap存储数据的偏移量 类似于数组的序号
        int offSet = now.getDayOfMonth() - 1;
        //1.4在bitMap中保存签到记录
        //说明：bitMap有类似于set的查重功能，原理就是根据传过来的key查bitMap对应offSet下原来数据状态(0/1)，
        //如果是0代表该下标下之前没有存过数据 如果是1代表之前存过数据 所以setBit参数true其实是修改为1 返回值
        //是之前老的值
        Boolean exits
                = redisTemplate.opsForValue().setBit(Key, offSet, true);
        //1.5判断是否重复 0为为重复 1为重复
        if(BooleanUtils.isTrue(exits)){
            //为true
            throw new BizIllegalException("不允许重复签到！");
        }

        //2.计算连续签到的天数
        int signDays = countSignDays(Key,now.getDayOfMonth());

        //3.计算签到得分
        int rewardPoints = 0;
        if(signDays >= 28){
            rewardPoints = 40;
        }else if(signDays >= 14){
            rewardPoints = 20;
        }else if(signDays >= 7){
            rewardPoints = 10;
        }
        //4. 保存积分明细记录 使用MQ发送消息 这里是在可以获取积分的业务中(6种)发送消息 这里以签到为例
        //然后定义监听器的时候需要定义六种不同的监听器 但是都是在定义在一个类种
        MqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1)
        );

        //5.封装返回
        SignResultVO signResultVO = new SignResultVO();
        signResultVO.setSignDays(signDays);
        signResultVO.setRewardPoints(rewardPoints);
        return signResultVO;
    }

    @Override
    public List<Integer> queryCurrentMonthRecords() {
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        List<Long> result = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(now.lengthOfMonth())).valueAt(0));
        long bits = CollUtils.isEmpty(result) || result.get(0) == null ? 0L : result.get(0);
        List<Integer> records = new java.util.ArrayList<>(now.lengthOfMonth());
        for (int day = 0; day < now.lengthOfMonth(); day++) {
            records.add((int) ((bits >> day) & 1));
        }
        return records;
    }

    private int countSignDays(String key, int len) {
        // 1.获取本月从第一天开始，到今天为止的所有签到记录  这里result是多次bitField命令集合  因为这里只进行一次 所以集合只有一个元素
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if(CollUtils.isEmpty(result)){
            return 0;
        }
        //bitField返回值是二进制数的十进制值
        int num = result.get(0).intValue();
        // 2.定义一个计数器
        int count = 0;
        // 3.循环 与1进行与运算
        while((num & 1) == 1){
            //计数器+1
            count = count + 1;
            //数字右移一位 最后一个数字被舍弃
            num >>>= 1;
        }
        return count;
    }


}
