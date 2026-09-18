package com.zhixu.exam.domain.vo;

import com.zhixu.exam.domain.po.ExamItem;
import com.zhixu.exam.domain.po.ExamSnapshotItem;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 考试详情（契约 §9 GET /exams/{id}）
 * 已发布的考试必须先读快照（snapshotItems），读不到才回退引用 —— 回看类页面禁止直接查题库。
 * expandedItems 输出契约形状（字符串枚举 / 字母答案），前端零适配。
 * </p>
 */
@Data
@ApiModel(description = "考试详情")
public class ExamDetailVO {

    private Long id;

    private String name;

    private Long courseId;

    private String courseName;

    @ApiModelProperty("状态，0：草稿，1：已发布，2：批改中，3：已结束")
    private Integer status;

    private Integer examType;

    private Integer passScore;

    private Integer duration;

    private String notice;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @ApiModelProperty("引用原文（草稿可编辑）")
    private List<ExamItem> items;

    @ApiModelProperty("试卷版本，发布后为 v1")
    private String paperVersion;

    @ApiModelProperty("冻结时间")
    private LocalDateTime snapshotAt;

    private Integer submittedCount;

    @ApiModelProperty("展开后的题目（已发布读快照，草稿实时展开引用）")
    private List<SnapshotItemVO> expandedItems;

    /**
     * <p>
     * 展开条目 —— 契约形状：type/difficulty 用字符串枚举，答案是字母，选项带 key。
     * 与前端 mock（expandedItems）完全一致，切换后端页面零改动。
     * </p>
     */
    @Data
    @ApiModel(description = "展开的题目条目")
    public static class SnapshotItemVO {

        private Long questionId;

        private Integer score;

        @ApiModelProperty("single | multi")
        private String type;

        private String stem;

        private List<OptionVO> options;

        @ApiModelProperty("正确答案字母，如 [\"A\"] 或 [\"A\",\"C\"]")
        private List<String> answer;

        private String analysis;

        @ApiModelProperty("easy | medium | hard")
        private String difficulty;

        private List<String> knowledgePoints;

        private Long courseId;
    }

    @Data
    @ApiModel(description = "选项")
    public static class OptionVO {

        private String key;

        private String text;

        public static OptionVO of(int index, String text) {
            OptionVO vo = new OptionVO();
            vo.setKey(String.valueOf((char) ('A' + index)));
            vo.setText(text);
            return vo;
        }
    }

    /**
     * 快照条目 → 契约形状 VO
     */
    public static SnapshotItemVO fromSnapshot(ExamSnapshotItem s, int score) {
        SnapshotItemVO vo = new SnapshotItemVO();
        vo.setQuestionId(s.getQuestionId());
        vo.setScore(score);
        vo.setType(s.getType() != null && s.getType() == 2 ? "multi" : "single");
        vo.setStem(s.getStem());
        vo.setOptions(toOptions(s.getOptions()));
        vo.setAnswer(toLetters(s.getAnswer()));
        vo.setAnalysis(s.getAnalysis());
        vo.setDifficulty(toDiff(s.getDifficulty()));
        vo.setKnowledgePoints(s.getKnowledgePoints() == null ? new ArrayList<>() : s.getKnowledgePoints());
        vo.setCourseId(s.getCourseId());
        return vo;
    }

    public static List<OptionVO> toOptions(List<String> options) {
        List<OptionVO> list = new ArrayList<>();
        if (options == null) {
            return list;
        }
        for (int i = 0; i < options.size(); i++) {
            list.add(OptionVO.of(i, options.get(i)));
        }
        return list;
    }

    /**
     * 数字编号答案（"1" 或 "1,3"）→ 字母（["A"] / ["A","C"]）
     */
    public static List<String> toLetters(String answer) {
        List<String> letters = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return letters;
        }
        for (String part : answer.split(",")) {
            try {
                int n = Integer.parseInt(part.trim());
                if (n >= 1 && n <= 26) {
                    letters.add(String.valueOf((char) ('A' + n - 1)));
                }
            } catch (NumberFormatException ignored) {
                // 容错：非数字编号原样保留，避免静默丢答案
                letters.add(part.trim());
            }
        }
        return letters;
    }

    public static String toDiff(Integer d) {
        if (d == null) {
            return "medium";
        }
        if (d == 1) {
            return "easy";
        }
        if (d == 3) {
            return "hard";
        }
        return "medium";
    }
}
