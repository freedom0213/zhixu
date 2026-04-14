package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定义一个定时任务Task
 * 这个任务方法每20s执行依次 对两个不同的业务类型进行读取最大30条的redis数据
 */
@Component
@RequiredArgsConstructor
public class likedTimesCheckTask {

    private static final List<String> BIZ_TYPES = List.of("QA","NOTE");
    private static final int MAX_BIZ_SIZE = 30;

    private final ILikedRecordService likedService;

    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes(){
        for(String bizType : BIZ_TYPES){
            likedService.readLikedTimesAndSendMessage(bizType,MAX_BIZ_SIZE);
        }


    }

}
