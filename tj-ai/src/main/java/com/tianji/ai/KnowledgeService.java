package com.tianji.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeService {
    private final AiProperties properties;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final ChatLanguageModel model;

    public KnowledgeService(AiProperties properties) {
        this.properties = properties;
        Path dir = Paths.get(properties.getDataDir(), "documents");
        try { Files.createDirectories(dir); Files.list(dir).filter(Files::isRegularFile).forEach(p -> {
            try { documents.put(p.getFileName().toString(), Files.readString(p)); } catch (IOException ignored) { }
        }); } catch (IOException ignored) { }
        this.model = properties.isEnabled() && !"your key".equalsIgnoreCase(properties.getApiKey())
                ? OpenAiChatModel.builder().apiKey(properties.getApiKey()).baseUrl(properties.getBaseUrl()).modelName(properties.getModel()).build()
                : null;
    }

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        if (!(name.toLowerCase().endsWith(".md") || name.toLowerCase().endsWith(".markdown") || name.toLowerCase().endsWith(".txt")))
            throw new IllegalArgumentException("仅支持 Markdown 或 TXT 文件");
        String safe = UUID.randomUUID() + "-" + Paths.get(name).getFileName();
        Path path = Paths.get(properties.getDataDir(), "documents", safe);
        Files.createDirectories(path.getParent());
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Files.writeString(path, text);
        documents.put(safe, text);
        return Map.of("id", safe, "name", name, "size", text.length());
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        documents.forEach((name, text) -> {
            int separator = name.length() > 37 && name.charAt(36) == '-' ? 36 : name.indexOf('-');
            String displayName = separator >= 0 && separator + 1 < name.length() ? name.substring(separator + 1) : name;
            result.add(Map.of("id", name, "name", displayName, "size", text.length()));
        });
        return result;
    }

    public String content(String id) throws IOException {
        String value = documents.get(id);
        if (value == null) throw new NoSuchFileException(id);
        return value;
    }

    public void delete(String id) throws IOException { documents.remove(id); Files.deleteIfExists(Paths.get(properties.getDataDir(), "documents", id)); }

    public List<Map<String, Object>> sessions() { return new ArrayList<>(sessions.values()); }
    public Map<String, Object> createSession(String name, String tag) {
        Map<String, Object> session = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        session.put("id", id); session.put("sessionId", id);
        session.put("name", name); session.put("tag", tag); sessions.put((String) session.get("id"), session); return session;
    }
    public void deleteSession(String id) { sessions.remove(id); }
    public Map<String, Object> updateSession(String id, String name, String tag) {
        Map<String, Object> s = sessions.get(id); if (s != null) { s.put("name", name); s.put("tag", tag); } return s;
    }

    public String chat(String question) {
        String context = documents.values().stream().flatMap(t -> Arrays.stream(t.split("\\n\\s*\\n")))
                .filter(s -> containsKeyword(s, question)).limit(5).reduce((a, b) -> a + "\n\n" + b).orElse("");
        if (model == null) return context.isEmpty() ? "本地 AI 尚未配置 DeepSeek API Key，请先上传相关文档并配置密钥。" : "已检索到相关知识片段：\n\n" + context;
        return model.generate("你是知序学堂课程助手。请仅根据参考资料回答问题，不确定时明确说明。\n参考资料：\n" + context + "\n问题：" + question);
    }

    private boolean containsKeyword(String text, String question) {
        for (String token : question.split("\\s+|[，。！？、]")) if (token.length() > 1 && text.contains(token)) return true;
        return false;
    }
}
