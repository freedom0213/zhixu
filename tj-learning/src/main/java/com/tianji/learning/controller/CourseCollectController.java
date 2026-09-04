package com.tianji.learning.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.CourseCollect;
import com.tianji.learning.mapper.CourseCollectMapper;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/collect")
@RequiredArgsConstructor
public class CourseCollectController {
    private final CourseCollectMapper mapper;
    private final CourseClient courseClient;

    @PostMapping
    public void collect(@RequestBody CollectForm form) {
        Long userId = UserContext.getUser();
        CourseCollect existing = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CourseCollect>()
                .eq("user_id", userId).eq("course_id", form.courseId));
        if (Boolean.TRUE.equals(form.collected)) {
            if (existing == null) {
                CourseCollect item = new CourseCollect(); item.setUserId(userId); item.setCourseId(form.courseId); mapper.insert(item);
            }
        } else if (existing != null) mapper.deleteById(existing.getId());
    }

    @GetMapping("/{courseId}")
    public boolean isCollected(@PathVariable Long courseId) {
        return mapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CourseCollect>()
                .eq("user_id", UserContext.getUser()).eq("course_id", courseId)) > 0;
    }

    @GetMapping("/page")
    public PageDTO<CollectVO> page(@RequestParam(defaultValue="1") Integer pageNo, @RequestParam(defaultValue="10") Integer pageSize) {
        Page<CourseCollect> page = mapper.selectPage(new Page<>(pageNo, pageSize), new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CourseCollect>()
                .eq("user_id", UserContext.getUser()).orderByDesc("create_time"));
        if (page.getRecords().isEmpty()) return PageDTO.empty(page);
        List<Long> ids = page.getRecords().stream().map(CourseCollect::getCourseId).collect(Collectors.toList());
        Map<Long, CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(ids).stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, x -> x));
        List<CollectVO> result = page.getRecords().stream().map(item -> {
            CourseSimpleInfoDTO c = courses.get(item.getCourseId()); CollectVO vo = new CollectVO(); vo.setId(item.getId()); vo.setCourseId(item.getCourseId()); vo.setCreateTime(item.getCreateTime());
            if (c != null) { vo.setCourseName(c.getName()); vo.setCourseCoverUrl(c.getCoverUrl()); vo.setSections(c.getSectionNum()); }
            return vo;
        }).collect(Collectors.toList());
        return new PageDTO<>(page.getTotal(), page.getPages(), result);
    }

    @Data public static class CollectForm { private Long courseId; private Boolean collected; }
    @Data public static class CollectVO { private Long id, courseId; private String courseName, courseCoverUrl; private Integer sections; private java.time.LocalDateTime createTime; }
}
