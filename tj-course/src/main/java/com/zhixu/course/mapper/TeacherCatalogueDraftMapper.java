package com.zhixu.course.mapper;

import com.zhixu.course.domain.po.CourseCatalogueDraft;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 教师端建课 · 目录草稿专用 Mapper
 * -----------------------------------------------------------------------------
 * 为什么不直接用 CourseCatalogueDraftMapper（MyBatis-Plus 的）：
 *   全局 id 策略是 `id-type: auto`（见 deploy/nacos/configs/shared-mybatis.yaml），
 *   MP 的 insert 会把主键交给数据库自增、**忽略实体上设置的 id**。
 *   而目录是「章 → 小节」两层，小节的 parent_catalogue_id 必须等于章的 id ——
 *   一旦主键由数据库发号，父指针就对不上了（老 save() 正是栽在这里：
 *   它用 IdWorker 生成章 id 当父指针，插入后章的真实 id 却变成自增值）。
 *
 *   所以这里用显式 id 的 INSERT：**id 由我们（雪花）生成，插入时原样写入**，
 *   父子关系就天然一致；同时更新走 MP 的 updateById（按 id 精确更新），
 *   视频 / 配题这些挂在小节上的数据不会因为「整树删了重插」而丢失。
 */
@Mapper
public interface TeacherCatalogueDraftMapper {

    @Select("SELECT * FROM course_catalogue_draft WHERE course_id = #{courseId} ORDER BY type ASC, c_index ASC, id ASC")
    List<CourseCatalogueDraft> listByCourse(@Param("courseId") Long courseId);

    /** 显式 id 插入（绕开 MP 的 id-type=auto） */
    @Insert("INSERT INTO course_catalogue_draft "
            + "(id, name, trailer, course_id, type, parent_catalogue_id, media_id, video_id, video_name, "
            + " play_back, media_duration, c_index, can_update, create_time, update_time, creater, updater) "
            + "VALUES (#{id}, #{name}, #{trailer}, #{courseId}, #{type}, #{parentCatalogueId}, #{mediaId}, #{videoId}, #{videoName}, "
            + " #{playBack}, #{mediaDuration}, #{cIndex}, #{canUpdate}, NOW(), NOW(), #{creater}, #{updater})")
    int insertWithId(CourseCatalogueDraft row);

    /** 按 id 更新标题与排序（不动媒体 / 试看等其它字段） */
    @Update("UPDATE course_catalogue_draft SET name = #{name}, c_index = #{cIndex}, "
            + "parent_catalogue_id = #{parentCatalogueId}, update_time = NOW() WHERE id = #{id}")
    int updateTitleAndIndex(@Param("id") Long id, @Param("name") String name,
                            @Param("cIndex") Integer cIndex, @Param("parentCatalogueId") Long parentCatalogueId);

    @Delete("<script>DELETE FROM course_catalogue_draft WHERE id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    int deleteByIds(@Param("ids") List<Long> ids);

    // -------------------------------------------------------------------------
    // 视频 / 试看 / 配题：同样绕开老的 saveMediaInfo / saveSuject
    // -------------------------------------------------------------------------
    // 为什么：老的 saveMediaInfo 要求小节携带 media-service 的媒资 id，而 media-service
    // 本项目已停用、真上传还依赖腾讯云 VOD 凭证（契约 §13.2）。教师端向导的上传是本地
    // 模拟进度，登记的是「视频名 + 时长 + 试看」这些**真实已知的信息**；
    // 播放用的媒资文件等 VOD 接好后再补（未配视频的小节对学生隐藏 —— 老设计本就如此）。

    @Update("UPDATE course_catalogue_draft SET video_name = #{videoName}, media_duration = #{seconds}, "
            + "trailer = #{trailer}, update_time = NOW() WHERE id = #{id}")
    int updateSectionVideo(@Param("id") Long id, @Param("videoName") String videoName,
                           @Param("seconds") Integer seconds, @Param("trailer") Integer trailer);

    @Update("UPDATE course_catalogue_draft SET trailer = #{trailer}, update_time = NOW() WHERE id = #{id}")
    int updateSectionTrailer(@Param("id") Long id, @Param("trailer") Integer trailer);

    @Update("UPDATE course_catalogue_draft SET media_id = #{mediaId}, update_time = NOW() WHERE id = #{id}")
    int updateSectionMedia(@Param("id") Long id, @Param("mediaId") Long mediaId);

    @Delete("DELETE FROM course_cata_subject_draft WHERE course_id = #{courseId} AND cata_id = #{cataId}")
    int clearSectionQuiz(@Param("courseId") Long courseId, @Param("cataId") Long cataId);

    @Insert("INSERT INTO course_cata_subject_draft (id, course_id, cata_id, subject_id, create_time) "
            + "VALUES (#{id}, #{courseId}, #{cataId}, #{subjectId}, NOW())")
    int insertSectionQuiz(@Param("id") Long id, @Param("courseId") Long courseId,
                          @Param("cataId") Long cataId, @Param("subjectId") Long subjectId);

    @Select("SELECT cata_id AS cataId, COUNT(*) AS num FROM course_cata_subject_draft WHERE course_id = #{courseId} GROUP BY cata_id")
    List<java.util.Map<String, Object>> countQuizByCata(@Param("courseId") Long courseId);
}
