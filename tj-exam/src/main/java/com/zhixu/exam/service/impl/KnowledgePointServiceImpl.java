package com.zhixu.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.exam.domain.po.KnowledgePoint;
import com.zhixu.exam.mapper.KnowledgePointMapper;
import com.zhixu.exam.service.IKnowledgePointService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint>
        implements IKnowledgePointService {

    @Override
    public List<KnowledgePoint> listByCourse(Long courseId) {
        // courseId 为空 = 页面「全部知识点」选项，返回全部
        return lambdaQuery().eq(courseId != null, KnowledgePoint::getCourseId, courseId).list();
    }
}
