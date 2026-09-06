package com.zhixu.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tj.ai")
public class AiProperties {
    private boolean enabled = true;
    private String apiKey = "your key";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash";
    private String dataDir = "/data/ai";
    /** Embedding 模型名（OpenAI 兼容）；留空则不启用向量检索，降级到关键词。 */
    private String embeddingModel = "deepseek-embedding";
    /** Embedding 接口地址，默认与 baseUrl 同源 + /v1。 */
    private String embeddingBaseUrl = "";
    /** 切块窗口大小（字符数）。 */
    private int chunkSize = 400;
    /** 相邻窗口重叠字符数。 */
    private int chunkOverlap = 100;
    /** 向量检索召回 topK。 */
    private int topK = 5;
    /** 关键词兜底召回条数。 */
    private int keywordFallbackK = 3;
}
