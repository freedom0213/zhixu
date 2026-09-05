package com.zhixu.search.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.search.domain.query.CoursePageQuery;
import com.zhixu.search.domain.vo.CourseVO;

import java.util.List;

public interface ISearchService {

    List<CourseVO> queryCourseByCateId(Long cateLv2Id);

    List<CourseVO> queryBestTopN();

    List<CourseVO> queryNewTopN();

    List<CourseVO> queryFreeTopN();

    PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query);

    List<Long> queryCoursesIdByName(String keyword);

    List<String> completeSuggest(String keyword);

    List<String> querySearchHistory();

    void deleteSearchHistory(String keyword);

    void clearSearchHistory();
}
