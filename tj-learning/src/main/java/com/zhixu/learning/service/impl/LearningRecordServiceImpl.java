package com.zhixu.learning.service.impl;

import com.zhixu.api.client.course.CourseClient;
import com.zhixu.api.dto.course.CourseFullInfoDTO;
import com.zhixu.api.dto.leanring.LearningLessonDTO;
import com.zhixu.api.dto.leanring.LearningRecordDTO;
import com.zhixu.common.exceptions.BizIllegalException;
import com.zhixu.common.exceptions.DbException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.learning.domain.dto.LearningRecordFormDTO;
import com.zhixu.learning.domain.enums.LessonStatus;
import com.zhixu.learning.domain.po.LearningLesson;
import com.zhixu.learning.domain.po.LearningRecord;
import com.zhixu.learning.domain.enums.SectionType;
import com.zhixu.learning.mapper.LearningRecordMapper;
import com.zhixu.learning.service.ILearningLessonService;
import com.zhixu.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-06
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler    taskHandler;
    /**
     * 查询用户指定课程的学习进度
     * @param courseId  课程id
     * @return  返回值是一个课程学习DTO对象
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取用户id
        Long userId = UserContext.getUser();
        //2.因为我们返回的LearningLessonDTO中既有LearningLesson中的数据也有在LearningRecord中的数据
        // 先在课表中查询出latestSectionId
        LearningLesson lesson = lessonService.queryLessonByCourseIdAndUserId(courseId,userId);
        // 未购买课程时没有课表，课程详情页应返回空学习进度而不是抛出异常。
        if (lesson == null) {
            return null;
        }
        //3.根据课表id查询出学习记录  其实学习记录表lessonRecord中都是一个课表id对应一个课程id对应一个小节id,有学习时长，学习小节是否完成等等的学习记录
        List<LearningRecord> records= lambdaQuery()
                .eq(LearningRecord::getLessonId,lesson.getId()).list();
        //4.封装记录
        LearningLessonDTO lessonDTO = new LearningLessonDTO();
        lessonDTO.setId(lesson.getId());
        lessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        lessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return lessonDTO;
    }

    /**
     * 提交学习记录
     * @param recordFormDTO  前端传过来的学习记录对象 含有课表id  此节id  此节类型 视频总时长 视频当前观看时长 提交时间
     * 因为课表也有相关课程的学习情况 所以要根据是否学完判断修改learning_lesson
     */
    @Override
    public void addLearningRecord(LearningRecordFormDTO recordFormDTO) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.判断小节类型
        boolean finished = false;
        if(recordFormDTO.getSectionType() == SectionType.VIDEO){
            //2.1 处理视频小节
            finished = handleVideoRecord(userId,recordFormDTO);
        }else{
            //2.2 处理考试小节
            finished = handleExamRecord(userId,recordFormDTO);
        }
        if (!finished) {
            // 没有新学完的小节，无需更新课表中的学习进度
            return;
        }
        // 3.处理课表数据
        handleLearningLessonsChanges(recordFormDTO);
    }

    /**
     * 根据是否有新的学习完的小节修改课程学习记录
     * @param recordFormDTO 学习记录
     */
    private void handleLearningLessonsChanges(LearningRecordFormDTO recordFormDTO) {
        // 1.查询课表
        LearningLesson lesson = lessonService.getById(recordFormDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 2.判断是否有新的完成小节
        boolean allLearned = false;

        // 3.如果有新完成的小节，则需要查询课程数据
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();
        // 5.更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    /**
     * 提交学习记录的私人方法：处理视频小节提交记录
     * 注意点：①需要判断是否有旧的学习记录 因为是第一次提交是新增记录 不是第一次是更新数据
     * ②不存在直接提交即可 因为视频是多次发送的，第一次提交的时候 一定是没有完成的
     * ③存在的话需要判断是否已经完成
     * @param userId 用户id
     * @param recordDTO 学习记录
     * @return 返回布尔值
     */
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {
        // 1.查询旧的学习记录
        LearningRecord old = queryOldRecord(recordDTO.getLessonId(), recordDTO.getSectionId());
        // 2.判断是否存在
        if (old == null) {
            // 3.不存在，则新增
            // 3.1.转换PO
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            // 3.2.填充数据
            record.setUserId(userId);
            // 3.3.写入数据库
            boolean success = save(record);
            if (!success) {
                throw new DbException("新增学习记录失败！");
            }
            return false;
        }
        // 4.存在，则更新
        // 4.1.判断是否是第一次完成 finish == false && 视频观看时常到达50% 此时finished变为true
        boolean finished = !old.getFinished() && recordDTO.getMoment() * 2 >= recordDTO.getDuration();
        if (!finished) {
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        // 4.2.更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败！");
        }
        // 4.3.清理缓存
        taskHandler.cleanRecordCache(recordDTO.getLessonId(), recordDTO.getSectionId());
        return true;
    }
    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1.查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        // 2.如果命中，直接返回
        if (record != null) {
            return record;
        }
        // 3.未命中，查询数据库
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4.写入缓存
        taskHandler.writeRecordCache(record);
        return record;
    }

    /**
     * 提交学习记录的私人方法：处理考试小节提交记录 不需要考虑是否完成学习，只要提交试卷就是代表完成此小结
     * @param userId 用户id
     * @param recordFormDTO 学习记录
     * @return 返回布尔值
     */
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordFormDTO) {
        //1.转换PTO为PO
        LearningRecord record = BeanUtils.copyBean(recordFormDTO,LearningRecord.class);
        //2.补充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordFormDTO.getCommitTime());
        //3.写入数据
        boolean save = save(record);
        if(!save){
            throw new DbException("新增考试记录失败！！");
        }
        return true;
    }
}
