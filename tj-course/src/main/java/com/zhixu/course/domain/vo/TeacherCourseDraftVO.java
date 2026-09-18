package com.zhixu.course.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 教师端建课 · 草稿视图（契约 §13 的 GET /cs/teacher/course-draft 返回体）
 * -----------------------------------------------------------------------------
 * checks（上架前校验清单）**从明细推导**，不单独存表 —— 与考试模块同一条纪律：
 * 数字只能有一个来源，否则列表、向导、上架校验三处必然互相打架。
 */
@Data
@ApiModel("建课·课程草稿")
public class TeacherCourseDraftVO {

    @ApiModelProperty("课程id；新建时为空")
    private Long courseId;

    /**
     * 课程归属人 = 主讲（当前正在编辑这门课的老师）。
     * 知序学堂是面向所有有授课能力的老师的**开放平台**，没有「校内教师名册」——
     * 所以界面上的「主讲」就是建课人本人，不能选、也不能移除；
     * 需要的是「按账号添加协作讲师」，前端靠这个 id 把自己的那条标记成不可移除。
     */
    @ApiModelProperty("主讲（课程归属人）的用户id")
    private Long ownerId;

    @ApiModelProperty("状态：draft / published")
    private String status;

    @ApiModelProperty("向导进度（1-5）")
    private Integer step;

    @ApiModelProperty("发布方式（老模型无此概念，保留字段供前端回显）")
    private String publishMode = "now";

    private Basic basic = new Basic();

    private List<Chapter> chapters = new ArrayList<>();

    private List<TeacherItem> teachers = new ArrayList<>();

    private Checks checks = new Checks();

    @Data
    @ApiModel("建课·基本信息")
    public static class Basic {
        private String name;
        @ApiModelProperty("三级分类id")
        private Long thirdCateId;
        @ApiModelProperty("分类全名（一级/二级/三级）")
        private String categoryName;
        private Integer price;
        private Integer validDays;
        @ApiModelProperty("封面地址")
        private String coverUrl;
        private String introduce;
        private String usePeople;
        private String detail;
    }

    @Data
    @ApiModel("建课·章")
    public static class Chapter {
        private Long id;
        private String title;
        private List<Section> sections = new ArrayList<>();
    }

    @Data
    @ApiModel("建课·小节")
    public static class Section {
        private Long id;
        private String title;
        @ApiModelProperty("时长文本 mm:ss")
        private String duration;
        @ApiModelProperty("是否试看")
        private Boolean preview = false;
        private Video video = new Video();
        private Quiz quiz;
    }

    @Data
    @ApiModel("建课·小节视频")
    public static class Video {
        @ApiModelProperty("none / uploading / done")
        private String status = "none";
        private String name;
        private Double sizeMB;
        private Integer durationMin;
        @ApiModelProperty("媒资id —— 「有没有视频」的**唯一**凭据（P30：以前按 name 判定，与播放端口径不一致）")
        private Long mediaId;
    }

    @Data
    @ApiModel("建课·小节配题")
    public static class Quiz {
        private Integer count;
        private Integer totalScore;
        private List<String> dist = new ArrayList<>();
    }

    @Data
    @ApiModel("建课·讲师")
    public static class TeacherItem {
        private Long id;
        private String name;
        private String role;
        private String dept;
        private Boolean certified = false;
        private Boolean isShow = true;
    }

    @Data
    @ApiModel("建课·上架前校验清单")
    public static class Checks {
        private CheckItem basic = new CheckItem();
        private CheckItem catalog = new CheckItem();
        private CheckItem video = new CheckItem();
        private CheckItem quiz = new CheckItem();
    }

    @Data
    @ApiModel("建课·校验项")
    public static class CheckItem {
        private Boolean ok = false;
        private Boolean warn = false;
        private String text = "";
        private Integer missing;
    }
}
