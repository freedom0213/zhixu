package com.zhixu.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构化知识片段（chunk）。
 * 对应一次上传的文档被切块后的最小检索单元。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    /** chunk 唯一 ID（全局）。 */
    private String chunkId;
    /** 来源文档 ID（upload 时生成的 safe 文件名）。 */
    private String docId;
    /** 来源文档原始显示名。 */
    private String docName;
    /** 标题面包屑：["Java 基础", "集合框架"]。 */
    private List<String> sectionPath;
    /** 当前 ##/### 标题（一级标题不进 sectionPath，写不进 heading）。 */
    private String heading;
    /** 在原文中的字符起始偏移。 */
    private int startOffset;
    /** 在原文中的字符结束偏移（不含）。 */
    private int endOffset;
    /** chunk 正文（喂 embedding 和回显）。 */
    private String text;
    /** 抽取出的关键词（小写、去停用词）。 */
    private List<String> keywords;
    /** 估算 token 数（用 charLen/2 近似）。 */
    private int tokenCount;
    /** Embedding 向量（来自 embedding 模型）；未启用 embedding 时为 null。 */
    private float[] embedding;
}
