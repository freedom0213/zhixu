package com.zhixu.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.exam.domain.po.KnowledgePoint;

import java.util.List;

public interface IKnowledgePointService extends IService<KnowledgePoint> {

    /**
     * 按课程查知识点（录题 / 筛选只能从这里选，见契约 §4）
     */
    List<KnowledgePoint> listByCourse(Long courseId);
}
