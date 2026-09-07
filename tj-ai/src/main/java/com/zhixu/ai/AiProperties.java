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
    /** Embedding 服务独立 API Key；留空时复用 api-key（聊天与 embedding 同一服务商时）。 */
    private String embeddingApiKey = "";
    /** 切块窗口大小（字符数）。 */
    private int chunkSize = 400;
    /** 相邻窗口重叠字符数。 */
    private int chunkOverlap = 100;
    /** 向量检索召回 topK。 */
    private int topK = 5;
    /** 关键词兜底召回条数。 */
    private int keywordFallbackK = 3;
    /** Embedding 向量维度（需与模型输出一致，如 text-embedding-3-small=1536）。 */
    private int embeddingDimension = 1536;
    /** 是否启用 pgvector 持久化向量库；false 时退回本地 chunks.json。 */
    private boolean vectorStoreEnabled = true;
    /** pgvector 连接串各部分。 */
    private String pgHost = "localhost";
    private int pgPort = 5433;
    private String pgDatabase = "tianji";
    private String pgUser = "tianji";
    private String pgPassword = "tianji123";
    private String pgTable = "knowledge_chunks";
}
