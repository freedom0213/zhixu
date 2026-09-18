package com.zhixu.api.dto.exam;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 随堂练习卷的幂等 upsert（p15 方案 A「配题即出卷」）
 * -----------------------------------------------------------------------------
 * 由 course-service 在「建课向导第④步 配题」保存后调用：把这一节的题目引用
 * （`course_cata_subject_draft`）同步成**一张随堂练习卷**，学生端按 `section_id` 取卷读的就是它。
 *
 * 幂等键是 {@link #sectionId} —— 同一小节反复配题只会更新同一张卷，不会越配越多。
 * {@link #questionIds} 传空 = 该小节的练习**停用**（不硬删，历史作答与成绩还挂在它上面）。
 *
 * 为什么带上 {@link #operatorId}：Feign 的中继拦截器只传 request-id，**不做 user-info 透传**，
 * 考试服务里 `UserContext.getUser()` 会是 null。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@ApiModel(description = "随堂练习卷 upsert")
public class PracticeUpsertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("课程id")
    private Long courseId;

    @ApiModelProperty("课程名称（冗余，列表要用）")
    private String courseName;

    @ApiModelProperty("小节id（幂等键）")
    private Long sectionId;

    @ApiModelProperty("小节名称（当练习卷名用）")
    private String sectionName;

    @ApiModelProperty("这一节的题目id（按老师选的顺序；传空=停用该节练习）")
    private List<Long> questionIds = new ArrayList<>();

    @ApiModelProperty("操作人（当前讲师id）")
    private Long operatorId;
}
