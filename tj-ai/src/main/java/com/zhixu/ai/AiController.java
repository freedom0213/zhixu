package com.zhixu.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AiController {
    private final KnowledgeService knowledge;
    private Map<String,Object> ok(Object data) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("code", 200); result.put("msg", "OK"); result.put("data", data);
        return result;
    }
    @PostMapping("/file/upload") public Map<String,Object> upload(@RequestParam MultipartFile file) throws IOException { return ok(knowledge.upload(file)); }
    @GetMapping("/file/page") public Map<String,Object> page() { return ok(Map.of("list", knowledge.list(), "total", knowledge.list().size())); }
    @GetMapping("/file/{id}") public Map<String,Object> get(@PathVariable String id) throws IOException { return ok(knowledge.content(id)); }
    @DeleteMapping("/file/{id}") public Map<String,Object> delete(@PathVariable String id) throws IOException { knowledge.delete(id); return ok(null); }
    @GetMapping("/file/chat") public Map<String,Object> chat(@RequestParam(required = false) String question, @RequestParam(required = false) String message, @RequestParam(required = false) String sessionId) { return ok(Map.of("content", knowledge.chat(sessionId, question != null ? question : message))); }
    @GetMapping("/chat/simple") public Map<String,Object> simple(@RequestParam(required = false) String question, @RequestParam(required = false) String message, @RequestParam(required = false) String sessionId) { return chat(question, message, sessionId); }
    @GetMapping("/session/list") public Map<String,Object> sessions() { return ok(knowledge.sessions()); }
    @PostMapping("/session") public Map<String,Object> create(@RequestBody(required = false) Map<String, Object> body,
                                                               @RequestParam(required = false) String name,
                                                               @RequestParam(required = false, defaultValue = "") String tag) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "新会话" : body.getOrDefault("name", "新会话"));
        String sessionTag = tag != null && !tag.isBlank() ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        return ok(knowledge.createSession(sessionName, sessionTag));
    }
    @PutMapping("/session/{id}") public Map<String,Object> update(@PathVariable String id,
                                                                    @RequestBody(required = false) Map<String, Object> body,
                                                                    @RequestParam(required = false) String name,
                                                                    @RequestParam(required = false) String tag) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "未命名会话" : body.getOrDefault("name", "未命名会话"));
        String sessionTag = tag != null ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        return ok(knowledge.updateSession(id, sessionName, sessionTag));
    }
    @DeleteMapping("/session/{id}") public Map<String,Object> deleteSession(@PathVariable String id) { knowledge.deleteSession(id); return ok(null); }
    @GetMapping("/chat/records") public Map<String,Object> records(@RequestParam(required = false) String sessionId) { List<Map<String,Object>> list = knowledge.records(sessionId); return ok(Map.of("list", list, "total", list.size())); }
}
