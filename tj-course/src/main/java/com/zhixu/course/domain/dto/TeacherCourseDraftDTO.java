package com.zhixu.course.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 教师端建课 · 契约请求体集合（契约 §13）
 * -----------------------------------------------------------------------------
 * 为什么单独一套 DTO：老接口（courses/baseInfo/save 等）是给旧管理端表单用的，
 * 字段与教师端向导的领域模型并不一一对应。这里保持契约形状不变，
 * 由 TeacherCourseDraftServiceImpl 负责「契约形状 ↔ 老模型」的转换。
 */
public class TeacherCourseDraftDTO {

    /** 步骤① 基本信息 */
    @Data
    @ApiModel("建课·基本信息")
    public static class BasicSaveDTO {
        @ApiModelProperty("课程id；为空 = 新建")
        private Long courseId;
        @ApiModelProperty("课程名称")
        @NotBlank(message = "课程名称不能为空")
        private String name;
        @ApiModelProperty("三级分类id（分类体系见 /cs/categorys/all）")
        @NotNull(message = "请选择课程分类")
        private Long thirdCateId;
        @ApiModelProperty("封面地址（前端可传 dataURL 或对象存储地址）")
        private String coverUrl;
        @ApiModelProperty("价格（分）")
        private Integer price;
        @ApiModelProperty("有效期（天）")
        private Integer validDays;
        @ApiModelProperty("简介")
        private String introduce;
        @ApiModelProperty("适用人群")
        private String usePeople;
        @ApiModelProperty("课程详情")
        private String detail;
    }

    /** 步骤② 目录整体保存（章 → 小节两层，增删改排序收拢成一个数组） */
    @Data
    @ApiModel("建课·目录保存")
    public static class CatalogSaveDTO {
        @ApiModelProperty("课程id")
        @NotNull(message = "课程id不能为空")
        private Long courseId;
        @ApiModelProperty("章列表")
        private List<ChapterDTO> chapters = new ArrayList<>();
    }

    @Data
    @ApiModel("建课·章")
    public static class ChapterDTO {
        @ApiModelProperty("章id；新增传 null（占位 id 会被忽略）")
        private Long id;
        @ApiModelProperty("章标题")
        private String title;
        @ApiModelProperty("小节列表")
        private List<SectionDTO> sections = new ArrayList<>();
    }

    @Data
    @ApiModel("建课·小节")
    public static class SectionDTO {
        @ApiModelProperty("小节id；新增传 null（占位 id 会被忽略）")
        private Long id;
        @ApiModelProperty("小节标题")
        private String title;
    }

    /** 步骤③ 小节视频登记 / 试看开关 */
    @Data
    @ApiModel("建课·小节视频登记")
    public static class SectionVideoDTO {
        @ApiModelProperty("课程id")
        private Long courseId;
        @ApiModelProperty("媒资id（P23 本地上传后由 media-service 返回；删除视频时传 null）")
        private Long mediaId;
        @ApiModelProperty("视频名")
        private String videoName;
        @ApiModelProperty("时长（分钟）")
        private Integer durationMin;
        @ApiModelProperty("真实时长（秒）—— 前端读视频元数据得到；优先于 durationMin，避免分钟取整丢失精度")
        private Integer durationSec;
        @ApiModelProperty("文件大小（MB）")
        private Double sizeMB;
        @ApiModelProperty("是否试看；不传则保持原值")
        private Boolean preview;
    }

    /** 步骤④ 小节配题（引用题库题目，不复制） */
    @Data
    @ApiModel("建课·小节配题")
    public static class SectionQuizDTO {
        @ApiModelProperty("课程id")
        private Long courseId;
        @ApiModelProperty("题目id列表（来自题库 /es/questions/page）")
        private List<Long> subjectIds = new ArrayList<>();
    }

    /** 步骤⑤ 讲师增删 */
    @Data
    @ApiModel("建课·讲师保存")
    public static class TeacherSaveDTO {
        @ApiModelProperty("课程id")
        @NotNull(message = "课程id不能为空")
        private Long courseId;
        @ApiModelProperty("讲师id（用户id）")
        @NotNull(message = "讲师id不能为空")
        private Long teacherId;
    }
}
