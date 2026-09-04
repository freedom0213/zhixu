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
    @PostMapping("/file/upload") public Map<String,Object> upload(@RequestParam MultipartFile file) throws IOException { return knowledge.upload(file); }
    @GetMapping("/file/page") public List<Map<String,Object>> page() { return knowledge.list(); }
    @GetMapping("/file/{id}") public Map<String,Object> get(@PathVariable String id) { return Map.of("id", id); }
    @DeleteMapping("/file/{id}") public void delete(@PathVariable String id) throws IOException { knowledge.delete(id); }
    @GetMapping("/file/chat") public Map<String,String> chat(@RequestParam String question) { return Map.of("answer", knowledge.chat(question)); }
    @GetMapping("/chat/simple") public Map<String,String> simple(@RequestParam String question) { return chat(question); }
}
