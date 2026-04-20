package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import feign.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    //${ew.customSqlSegment}表示一个占位符 用于在sql语句中加上wrapper条件 而ew.customSqlSegment值是ew 为了让myBatis-Plus扫描到ew.customSqlSegment
    //就需要加上@Param(Constants.WRAPPER)注解
    @Select("SELECT SUM(points) FROM points_record ${ew.customSqlSegment}")
    Integer queryUserPointsByTypeAndDate(@Param(Constants.WRAPPER) QueryWrapper<PointsRecord> wrapper);
    @Select("SELECT type , SUM(points) AS points FROM points_record ${ew.customSqlSegment} ORDER BY type")
    List<PointsRecord> queryUserPointsByDate(@Param(Constants.WRAPPER) QueryWrapper<PointsRecord> qw);
}
