package com.zhixu.learning.mapper;

import com.zhixu.api.dto.IdAndNumDTO;
import com.zhixu.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-06
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    List<IdAndNumDTO> countLearnedSections(Long userId, LocalDateTime begin, LocalDateTime end);
}
