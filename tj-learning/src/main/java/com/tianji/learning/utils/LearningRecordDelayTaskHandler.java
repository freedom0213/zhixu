package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

/**
 * 学习记录延迟任务处理器
 * 一共有四个任务
 * 一：添加播放记录到缓存redis,并添加一个延迟检测任务到DelayQueue
 * 二：查询redis中指定小节的播放记录
 * 三：删除redis中指定小节的播放记录
 * 四：异步执行DelayQueue中的延迟检测任务，检测播放进度是否变化，若无任务则写入数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
        private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static volatile boolean begin = true;

    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::handleDelayTask);
    }
    @PreDestroy
    public void destroy(){
        begin = false;
        log.debug("延迟任务停止执行！");
    }

    //四：处理延迟任务
    //  将传过来的观看记录数据与先在缓存中的数据记录进行对比，如果moment一致，代表观看时长不再更新，将先在传过来的RecordTaskData记录到数据库中
    public void handleDelayTask() {
        while(begin){
            try {
                //1.不断从延迟队列中获取到期的任务
                DelayTask<RecordTaskData> task = queue.take();
                //2.从到期任务获取数据
                RecordTaskData data = task.getData();
                //3.查询redis
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if(record == null){
                    continue;
                }
                //4.比较moment值
                if( !Objects.equals(record.getMoment(), data.getMoment())){
                    //不一致 说明用户还可能继续观看视频 继续提交着视频记录信息
                    continue;
                }

                //5.一致 持久化到数据库中
                //5.1 更新到学习记录表中 需要存储moment
                record.setFinished(null);
                recordMapper.updateById(record);
                //5.2 更新到学习课表中 LearningLesson  需要更新最新学习完小节id 时间
                LearningLesson lesson = new LearningLesson();
                lesson.setId(record.getLessonId());
                lesson.setLatestSectionId(record.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);
            } catch (InterruptedException e) {
                log.error("处理延迟任务发生异常", e);
            }
        }
    }


    //一：添加播放记录数据到缓存
    public void addLearningRecordTask(LearningRecord record){
        //1.添加数据缓存到redis
        writeRecordCache(record);
        //2.提交延迟任务
        queue.add(new DelayTask<>(new RecordTaskData(record),Duration.ofSeconds(20)));
    }

    //二：读取缓存中的播放记录 根据lessonId sectionId从而在redis中精准定位
    public LearningRecord readRecordCache(Long lessonId,Long sectionId){
        try {
            //1.获取key
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            //2.获取fieldKey
            String sectionIdString = sectionId.toString();
            //3.从redis中获取数据
            Object cacheData = redisTemplate.opsForHash().get(key, sectionIdString);
            if(cacheData == null){
                return null;
            }
            //4.数据转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常",e);
            return null;
        }
    }
    //三：删除redis中指定缓存 场景：当在判断redis是否存在数据 在有的基础上进一步判断 播放记录是否已经完成 已完成则进行删除缓存
    public void cleanRecordCache(Long lessonId,Long sectionId){
        String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
        String sectionIdString = sectionId.toString();
        redisTemplate.opsForHash().delete(key, sectionIdString);
    }


    //添加数据到缓存
    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            //添加到缓存数据是hash类型 key(lessonId)    fieldKey(sectionId)     value-json格式(moment,finished,id(记录id))
            //1.将record封装为value json字符串 将对象转化为json字符串
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            //2.确认key
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            //3.写入redis
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(),json);
            //4.添加缓存时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常",e);
        }
    }

    //内部类 json类
    @Data
    @NoArgsConstructor
    public static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = false;
        }
    }

    //延迟任务数据  也就是传递给延迟队列的时候需要提供的数据  延迟任务是为了将学习记录保存到数据库 lessonId sectionId moment
    @Data
    @NoArgsConstructor
    public static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }


}
