package com.zhixu.learning.mq;

import com.zhixu.common.constants.MqConstants;
import com.zhixu.learning.domain.enums.PointsRecordType;
import com.zhixu.learning.mq.message.SignInMessage;
import com.zhixu.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService pointsRecordService;

    //监听新增互动问答的监听器
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId){
        pointsRecordService.addPointsRecord(userId, 5 , PointsRecordType.QA);
    }
    //监听签到事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key =  MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage message){
        pointsRecordService.addPointsRecord(message.getUserId(), message.getPoints(), PointsRecordType.SIGN);
    }


}
