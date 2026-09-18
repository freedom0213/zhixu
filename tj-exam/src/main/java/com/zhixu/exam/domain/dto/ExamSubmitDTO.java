package com.zhixu.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 交卷（p14）：学生把每题的答案一次交上来。
 * 形状沿用旧前端的载荷（questionId / answer / questionType），
 * answer 是「选项编号升序串」（如 "1,3"），与题库 question_detail.answer 同口径。
 */
@Data
@ApiModel("交卷")
public class ExamSubmitDTO {

    @ApiModelProperty("逐题作答")
    private List<Item> examDetails = new ArrayList<>();

    @Data
    public static class Item {
        @ApiModelProperty("题目id")
        private Long questionId;
        @ApiModelProperty("学生答案：选项编号升序串，如 1 或 1,3；未作答传空串")
        private String answer;
        @ApiModelProperty("题型（1单选/2多选），仅用于前端回显，判分不看它")
        private Integer questionType;
    }
}
