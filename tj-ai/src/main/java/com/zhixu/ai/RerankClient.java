package com.zhixu.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级重排客户端：直接用 JDK HttpClient 调 OpenAI 兼容 /v1/rerank 接口。
 * 不引入 langchain4j 的 rerank 模块是为了减少依赖（siliconflow、jina 等都兼容同一协议）。
 */
public class RerankClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String url;
    private final String model;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public RerankClient(AiProperties p) {
        this.model = p.getRerankModel();
        this.enabled = p.getRerankModel() != null && !p.getRerankModel().isBlank()
                && p.getApiKey() != null && !p.getApiKey().isBlank();
        if (!enabled) { this.url = null; this.apiKey = null; return; }
        String base = (p.getRerankBaseUrl() == null || p.getRerankBaseUrl().isBlank())
                ? p.getBaseUrl() : p.getRerankBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        this.url = base + "/v1/rerank";
        this.apiKey = (p.getRerankApiKey() == null || p.getRerankApiKey().isBlank())
                ? p.getApiKey() : p.getRerankApiKey();
    }

    public boolean isEnabled() { return enabled; }

    /**
     * 对候选文档按与 query 的相关性打分，返回按分数降序排列的 index 列表。
     * 若失败返回 null，调用方应退化为按原顺序使用前 topN 条。
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        if (!enabled || documents == null || documents.isEmpty() || query == null || query.isEmpty()) return null;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", Math.min(topN, documents.size()));
            body.put("return_documents", false);
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            RerankResponse parsed = MAPPER.readValue(resp.body(), RerankResponse.class);
            if (parsed.results == null || parsed.results.isEmpty()) return null;
            // 按 relevance_score 降序
            parsed.results.sort((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore));
            java.util.List<Integer> idx = new java.util.ArrayList<>();
            for (RerankItem r : parsed.results) idx.add(r.index);
            return idx;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** OpenAI 兼容 /v1/rerank 响应结构。 */
    public static final class RerankResponse {
        public List<RerankItem> results;
    }
    public static final class RerankItem {
        public Integer index;
        @JsonProperty("relevance_score")
        public Double relevanceScore;
    }
}
