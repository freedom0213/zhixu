package com.tianji.ai;

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
    @GetMapping("/file/chat") public Map<String,Object> chat(@RequestParam(required = false) String question, @RequestParam(required = false) String message) { return ok(Map.of("content", knowledge.chat(question != null ? question : message))); }
    @GetMapping("/chat/simple") public Map<String,Object> simple(@RequestParam(required = false) String question, @RequestParam(required = false) String message) { return chat(question, message); }
}
