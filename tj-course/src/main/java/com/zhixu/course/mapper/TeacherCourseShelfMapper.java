package com.zhixu.course.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.zhixu.course.domain.vo.TeacherMyCourseVO;

import java.util.List;

/**
 * 教师端建课 · 「上架」数据搬运专用 Mapper（草稿 → 正式表）
 * -----------------------------------------------------------------------------
 * 为什么自己搬、不走老的 upShelf：
 *   老的 upShelf → copyToShelf 会在内部校验「每个小节都必须有媒资」，
 *   而媒资依赖已停用的 media-service + 腾讯云 VOD 凭证（契约 §13.2 标注为后端待补）。
 *   教师端契约的口径是「视频 / 配题可上架后补」——两者冲突。
 *
 *   ⚠️ 老接口一行未改：管理端的严格上架流程照旧；教师端走这一层「简化上架」，
 *   只做数据搬运（草稿表 → 正式表，status=2 已上架），不校验媒资。
 *   未配视频的小节对学生隐藏 —— 这是老设计本来就有的规则。
 *
 * 写库方式统一用「显式 id 的 INSERT ... SELECT ... ON DUPLICATE KEY UPDATE」：
 * 正式表主键不是自增（由草稿 id 直接沿用），这样草稿/正式的 id 始终一致。
 *
 * 🔴🔴 **改这里之前必读**：`ON DUPLICATE KEY UPDATE` 的字段列表**必须与 INSERT 的列逐一对照** ——
 *    INSERT 里带了、UPDATE 里漏了的字段，只在"新增行"时生效；**已存在的行永远保持旧值**，
 *    而且不会报错，只会表现为"老师改了、学生端看不到"（P31 就栽在 `media_id` 上，
 *    连续两轮误判为前端/缓存问题）。加字段时请两处一起加。
 */
@Mapper
public interface TeacherCourseShelfMapper {

    // -------------------------------------------------------------------------
    // 「我的课程」：教师端列表（契约 §18）
    // -------------------------------------------------------------------------
    // 两个来源合并：
    //   A. 正式表里「我讲的课」（course_teacher 有我的记录）
    //   B. 草稿表里「我建的课」（creater = 我）—— 新建还没上架的课只存在于草稿表
    // 章数用小节/章的子查询现算，不用 section_num（那是小节数，且草稿期可能未同步）。

    @Select("SELECT c.id AS id, c.name AS name, c.cover_url AS coverUrl, c.price AS price, "
            + "'published' AS status, c.section_num AS sectionNum, "
            + "(SELECT COUNT(*) FROM course_catalogue cc WHERE cc.course_id = c.id AND cc.type = 1 AND cc.deleted = 0) AS chapterNum "
            + "FROM course c JOIN course_teacher ct ON ct.course_id = c.id "
            + "WHERE ct.teacher_id = #{teacherId} AND ct.deleted = 0 AND c.deleted = 0 "
            + "GROUP BY c.id")
    List<TeacherMyCourseVO> listShelfCoursesOfTeacher(@Param("teacherId") Long teacherId);

    @Select("SELECT d.id AS id, d.name AS name, d.cover_url AS coverUrl, d.price AS price, "
            + "CASE WHEN d.status = 2 THEN 'published' ELSE 'draft' END AS status, "
            + "d.section_num AS sectionNum, "
            + "(SELECT COUNT(*) FROM course_catalogue_draft cc WHERE cc.course_id = d.id AND cc.type = 1) AS chapterNum "
            + "FROM course_draft d WHERE d.creater = #{teacherId}")
    List<TeacherMyCourseVO> listDraftCoursesOfTeacher(@Param("teacherId") Long teacherId);

    /** 课程主体：草稿 → 正式（已有则覆盖关键字段，status=2 已上架） */
    @Insert("INSERT INTO course (id, name, course_type, cover_url, first_cate_id, second_cate_id, third_cate_id, "
            + "free, price, template_type, template_url, status, purchase_start_time, purchase_end_time, step, score, "
            + "media_duration, valid_duration, section_num, dep_id, publish_times, publish_time, "
            + "create_time, update_time, creater, updater, deleted) "
            + "SELECT d.id, d.name, d.course_type, d.cover_url, d.first_cate_id, d.second_cate_id, d.third_cate_id, "
            + "d.free, d.price, d.template_type, d.template_url, 2, d.purchase_start_time, d.purchase_end_time, 5, d.score, "
            + "d.media_duration, d.valid_duration, "
            + "(SELECT COUNT(*) FROM course_catalogue_draft c WHERE c.course_id = d.id AND c.type = 2), "
            + "d.dep_id, 1, NOW(), NOW(), NOW(), d.creater, d.updater, 0 "
            + "FROM course_draft d WHERE d.id = #{courseId} "
            // 🔴 P31：同样是"UPDATE 列表漏列"——以前改了课程类型 / 售卖时间段 / 评分 / 总时长
            //    再上架，正式表不更新（学生端看到的还是旧值）。
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), course_type = VALUES(course_type), "
            + "cover_url = VALUES(cover_url), template_type = VALUES(template_type), template_url = VALUES(template_url), "
            + "third_cate_id = VALUES(third_cate_id), first_cate_id = VALUES(first_cate_id), second_cate_id = VALUES(second_cate_id), "
            + "free = VALUES(free), price = VALUES(price), valid_duration = VALUES(valid_duration), "
            + "purchase_start_time = VALUES(purchase_start_time), purchase_end_time = VALUES(purchase_end_time), "
            + "score = VALUES(score), media_duration = VALUES(media_duration), "
            + "section_num = VALUES(section_num), status = 2, publish_time = NOW(), "
            + "updater = VALUES(updater), update_time = NOW()")
    int copyCourse(@Param("courseId") Long courseId);

    /** 课程内容（简介 / 适用人群 / 详情） */
    @Insert("INSERT INTO course_content (id, course_introduce, use_people, course_detail, dep_id, "
            + "create_time, update_time, creater, updater, deleted) "
            + "SELECT d.id, d.course_introduce, d.use_people, d.course_detail, d.dep_id, NOW(), NOW(), d.creater, d.updater, 0 "
            + "FROM course_content_draft d WHERE d.id = #{courseId} "
            + "ON DUPLICATE KEY UPDATE course_introduce = VALUES(course_introduce), use_people = VALUES(use_people), "
            + "course_detail = VALUES(course_detail), update_time = NOW()")
    int copyContent(@Param("courseId") Long courseId);

    /** 目录：先删掉草稿里已经不存在的行，再整体 upsert（章与小节的 parent 关系原样带过去） */
    @Delete("DELETE FROM course_catalogue WHERE course_id = #{courseId} "
            + "AND id NOT IN (SELECT id FROM course_catalogue_draft WHERE course_id = #{courseId})")
    int deleteStaleCatalogue(@Param("courseId") Long courseId);

    @Insert("INSERT INTO course_catalogue (id, name, trailer, course_id, type, parent_catalogue_id, media_id, video_id, "
            + "video_name, living_start_time, living_end_time, play_back, media_duration, c_index, dep_id, "
            + "create_time, update_time, creater, updater, deleted) "
            + "SELECT d.id, d.name, d.trailer, d.course_id, d.type, d.parent_catalogue_id, d.media_id, d.video_id, "
            + "d.video_name, d.living_start_time, d.living_end_time, d.play_back, d.media_duration, d.c_index, d.dep_id, "
            + "NOW(), NOW(), d.creater, d.updater, 0 "
            + "FROM course_catalogue_draft d WHERE d.course_id = #{courseId} "
            // 🔴 P31：UPDATE 列表必须与上面的 INSERT 列**逐一对照补齐**。以前这里漏了 media_id，
            //    于是已存在的小节走 UPDATE 分支时媒资不更新 —— 表现为「老师替换/删掉视频后，
            //    学生端还显示最初的视频」「新传的视频学生端看不到」（video_name 同步了、media_id 没有，
            //    反而造出"文件名是新的、媒资是旧的/空的"这种自相矛盾的半截数据）。
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), c_index = VALUES(c_index), "
            + "parent_catalogue_id = VALUES(parent_catalogue_id), type = VALUES(type), trailer = VALUES(trailer), "
            + "media_id = VALUES(media_id), video_id = VALUES(video_id), video_name = VALUES(video_name), "
            + "media_duration = VALUES(media_duration), play_back = VALUES(play_back), "
            + "living_start_time = VALUES(living_start_time), living_end_time = VALUES(living_end_time), "
            + "updater = VALUES(updater), update_time = NOW()")
    int copyCatalogue(@Param("courseId") Long courseId);

    /** 讲师：先清后插（讲师是集合语义，覆盖保存最不容易出错） */
    @Delete("DELETE FROM course_teacher WHERE course_id = #{courseId}")
    int clearShelfTeachers(@Param("courseId") Long courseId);

    // -------------------------------------------------------------------------
    // 小节配题（p15 补搬）：正式表 course_cata_subject 是**学生端「练习」入口的唯一依据**
    // -------------------------------------------------------------------------
    // catalogs 接口按它算 subjectNum，前端 `subjectNum > 0` 才在小节上显示「练习」。
    // 以前上架只搬 4 张表、漏了这张 → 老师配的题上架后学生端**一个入口都没有**（p15 §2 断口的另一半）。
    // 引用语义（题目本体在 tj_exam 不动）：本课程旧引用先清掉再整批搬，重复上架不会叠加。

    @Delete("DELETE FROM course_cata_subject WHERE course_id = #{courseId}")
    int clearShelfSectionQuiz(@Param("courseId") Long courseId);

    /** ⚠️ 目录 id 在两张表里是同一套（copyCatalogue 原样保留 id），所以 cata_id 可以直接搬 */
    @Insert("INSERT INTO course_cata_subject (course_id, cata_id, subject_id) "
            + "SELECT d.course_id, d.cata_id, d.subject_id FROM course_cata_subject_draft d "
            + "WHERE d.course_id = #{courseId}")
    int copySectionQuiz(@Param("courseId") Long courseId);
    @Insert("INSERT INTO course_teacher (course_id, teacher_id, is_show, c_index, dep_id, "
            + "create_time, update_time, creater, updater, deleted) "
            + "SELECT d.course_id, d.teacher_id, d.is_show, d.c_index, d.dep_id, NOW(), NOW(), d.creater, d.updater, 0 "
            + "FROM course_teacher_draft d WHERE d.course_id = #{courseId} AND d.deleted = 0")
    int copyTeachers(@Param("courseId") Long courseId);

    /**
     * 上架后锁定草稿目录（can_update=0）——老流程的真实语义：
     * 已上架的章/节锁定，之后新增的是「上架后的改动」，靠 c_index 排在其后。
     * ⚠️ 必须做：老查询在 status=2 时会按 can_update=0 的行计算「最大已上架序号」，
     *    若一行都没有，Optional.get() 会直接抛 NoSuchElementException（我们踩过）。
     */
    @Update("UPDATE course_catalogue_draft SET can_update = 0, update_time = NOW() WHERE course_id = #{courseId}")
    int lockDraftCatalogue(@Param("courseId") Long courseId);

    /** 草稿置为已上架（老流程用的是 status=2 SHELF） */
    @Update("UPDATE course_draft SET status = 2, update_time = NOW() WHERE id = #{courseId}")
    int markDraftPublished(@Param("courseId") Long courseId);

    // -------------------------------------------------------------------------
    // 「编辑已有课程」：首次进入时把已上架数据播种成草稿
    // -------------------------------------------------------------------------
    // 老师点「编辑」打开的是**已上架**的课（正式表里有数据、草稿表还是空的）。
    // 老流程靠前端先调复制接口；这里在读取草稿时顺手播种，行为对前端透明：
    // 打开即见到这门的现有章节目录，改完再上架覆盖。全部 can_update=1（可编辑）。

    @Select("SELECT COUNT(*) FROM course_draft WHERE id = #{courseId}")
    int countDraft(@Param("courseId") Long courseId);

    /** 播种要**逐张表**判断是否为空（原因见 getDraft 的 seedDraftIfEmpty 注释） */
    @Select("SELECT COUNT(*) FROM course_content_draft WHERE id = #{courseId}")
    int countContentDraft(@Param("courseId") Long courseId);

    @Select("SELECT COUNT(*) FROM course_teacher_draft WHERE course_id = #{courseId} AND deleted = 0")
    int countTeacherDraft(@Param("courseId") Long courseId);

    @Select("SELECT COUNT(*) FROM course WHERE id = #{courseId} AND deleted = 0")
    int countShelfCourse(@Param("courseId") Long courseId);

    @Insert("INSERT IGNORE INTO course_draft (id, name, course_type, cover_url, first_cate_id, second_cate_id, third_cate_id, "
            + "free, price, template_type, template_url, status, purchase_start_time, purchase_end_time, step, score, "
            + "media_duration, valid_duration, section_num, can_update, c_version, dep_id, create_time, update_time, creater, updater) "
            + "SELECT c.id, c.name, c.course_type, c.cover_url, c.first_cate_id, c.second_cate_id, c.third_cate_id, "
            + "c.free, c.price, c.template_type, c.template_url, c.status, c.purchase_start_time, c.purchase_end_time, "
            + "c.step, c.score, c.media_duration, c.valid_duration, c.section_num, 1, 0, c.dep_id, NOW(), NOW(), c.creater, c.updater "
            + "FROM course c WHERE c.id = #{courseId}")
    int seedDraftFromShelf(@Param("courseId") Long courseId);

    @Insert("INSERT IGNORE INTO course_content_draft (id, course_introduce, use_people, course_detail, dep_id, "
            + "create_time, update_time, creater, updater) "
            + "SELECT c.id, c.course_introduce, c.use_people, c.course_detail, c.dep_id, NOW(), NOW(), c.creater, c.updater "
            + "FROM course_content c WHERE c.id = #{courseId}")
    int seedContentFromShelf(@Param("courseId") Long courseId);

    @Insert("INSERT IGNORE INTO course_catalogue_draft (id, name, trailer, course_id, type, parent_catalogue_id, media_id, video_id, "
            + "video_name, living_start_time, living_end_time, play_back, media_duration, c_index, can_update, dep_id, "
            + "create_time, update_time, creater, updater) "
            + "SELECT c.id, c.name, c.trailer, c.course_id, c.type, c.parent_catalogue_id, c.media_id, c.video_id, "
            + "c.video_name, c.living_start_time, c.living_end_time, c.play_back, c.media_duration, c.c_index, 1, c.dep_id, "
            + "NOW(), NOW(), c.creater, c.updater "
            + "FROM course_catalogue c WHERE c.course_id = #{courseId} AND c.deleted = 0")
    int seedCatalogueFromShelf(@Param("courseId") Long courseId);

    @Insert("INSERT IGNORE INTO course_teacher_draft (course_id, teacher_id, is_show, c_index, dep_id, "
            + "create_time, update_time, creater, updater, deleted) "
            + "SELECT t.course_id, t.teacher_id, t.is_show, t.c_index, t.dep_id, NOW(), NOW(), t.creater, t.updater, 0 "
            + "FROM course_teacher t WHERE t.course_id = #{courseId} AND t.deleted = 0")
    int seedTeachersFromShelf(@Param("courseId") Long courseId);
}
