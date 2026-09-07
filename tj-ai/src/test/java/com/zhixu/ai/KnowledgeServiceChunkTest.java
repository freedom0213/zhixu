package com.zhixu.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeServiceChunkTest {
    @Test
    void chunksAndKeywordSearchWorkWithoutEmbedding() throws Exception {
        Path tmp = Files.createTempDirectory("tj-ai-test-");
        AiProperties props = new AiProperties();
        props.setDataDir(tmp.toString());
        props.setApiKey(""); // 强制未启用 embedding
        KnowledgeService svc = new KnowledgeService(props);

        String md = "# Java 集合框架\n" +
                "\n" +
                "## 集合框架概述\n" +
                "\n" +
                "集合框架提供了 List、Set、Map 三种核心接口。ArrayList 是 List 接口的可变数组实现。\n" +
                "\n" +
                "## List 接口\n" +
                "\n" +
                "### ArrayList\n" +
                "\n" +
                "ArrayList 底层是数组，初始容量为 10，扩容为 1.5 倍。\n" +
                "\n" +
                "## Map 接口\n" +
                "\n" +
                "HashMap 是 Map 接口的哈希实现，JDK8 后底层是数组 + 链表 + 红黑树。\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "java集合.md", "text/markdown", md.getBytes());
        Map<String, Object> res = svc.upload(42L, file);
        assertEquals("java集合.md", res.get("name"));
        int cnt = (Integer) res.get("chunkCount");
        assertTrue(cnt >= 3, "应切出多个 chunk，实得 " + cnt);

        // 关键词检索（无 embedding 时走倒排）
        List<Map<String, Object>> ctx = svc.list(42L);
        assertEquals(1, ctx.size());
        assertTrue(((Integer) ctx.get(0).get("chunkCount")) >= 3);

        // 未登录（userId=null）时列表应为空（用户隔离）
        assertTrue(svc.list(null).isEmpty());

        // 提问 + 不调模型也能返回兜底
        String ans = svc.chat(null, 42L, "ArrayList 扩容机制");
        assertNotNull(ans);
        assertTrue(ans.contains("ArrayList"), "兜底答案应包含检索片段: " + ans);
    }
}
