package com.zhixu.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AiController {
    private final KnowledgeService knowledge;
    /** 网关 AccountAuthFilter 校验 JWT 后注入的用户 ID header（见 JwtConstants.USER_HEADER）。 */
    private static final String USER_HEADER = "user-info";

    /** 从网关注入的 header 解析用户 ID；未登录返回 null。 */
    private static Long userId(String userHeader) {
        if (userHeader == null || userHeader.isBlank()) return null;
        try { return Long.parseLong(userHeader.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Map<String,Object> ok(Object data) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("code", 200); result.put("msg", "OK"); result.put("data", data);
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String,Object> onIllegalArgument(IllegalArgumentException e) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("code", 400); result.put("msg", e.getMessage()); result.put("data", null);
        return result;
    }
    @PostMapping("/file/upload") public Map<String,Object> upload(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @RequestParam MultipartFile file) throws IOException { return ok(knowledge.upload(userId(userHeader), file)); }
    @GetMapping("/file/page") public Map<String,Object> page(@RequestHeader(value = USER_HEADER, required = false) String userHeader) {
        Long uid = userId(userHeader);
        List<Map<String, Object>> list = knowledge.list(uid);
        return ok(Map.of("list", list, "total", list.size(), "chunkCount", knowledge.chunkTotal(uid)));
    }
    @GetMapping("/file/{id}") public Map<String,Object> get(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @PathVariable String id) throws IOException { return ok(knowledge.content(userId(userHeader), id)); }
    @DeleteMapping("/file/{id}") public Map<String,Object> delete(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @PathVariable String id) throws IOException { knowledge.delete(userId(userHeader), id); return ok(null); }
    @GetMapping("/file/chat") public Map<String,Object> chat(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @RequestParam(required = false) String question, @RequestParam(required = false) String message, @RequestParam(required = false) String sessionId) { return ok(Map.of("content", knowledge.chat(sessionId, userId(userHeader), question != null ? question : message))); }
    @GetMapping(value = "/file/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fileChatStream(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestParam(required = false) String question,
                                     @RequestParam(required = false) String message,
                                     @RequestParam(required = false) String sessionId) {
        return stream(sessionId, userId(userHeader), question != null ? question : message);
    }
    @GetMapping("/chat/simple") public Map<String,Object> simple(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @RequestParam(required = false) String question, @RequestParam(required = false) String message, @RequestParam(required = false) String sessionId) { return chat(userHeader, question, message, sessionId); }
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                 @RequestParam(required = false) String question,
                                 @RequestParam(required = false) String message,
                                 @RequestParam(required = false) String sessionId) {
        return stream(sessionId, userId(userHeader), question != null ? question : message);
    }
    @GetMapping("/session/list") public Map<String,Object> sessions(@RequestHeader(value = USER_HEADER, required = false) String userHeader) { return ok(knowledge.sessions(userId(userHeader))); }
    @PostMapping("/session") public Map<String,Object> create(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                                               @RequestBody(required = false) Map<String, Object> body,
                                                               @RequestParam(required = false) String name,
                                                               @RequestParam(required = false, defaultValue = "") String tag) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "新会话" : body.getOrDefault("name", "新会话"));
        String sessionTag = tag != null && !tag.isBlank() ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        return ok(knowledge.createSession(userId(userHeader), sessionName, sessionTag));
    }
    @PutMapping("/session/{id}") public Map<String,Object> update(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                                                    @PathVariable String id,
                                                                    @RequestBody(required = false) Map<String, Object> body,
                                                                    @RequestParam(required = false) String name,
                                                                    @RequestParam(required = false) String tag) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "未命名会话" : body.getOrDefault("name", "未命名会话"));
        String sessionTag = tag != null ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        return ok(knowledge.updateSession(userId(userHeader), id, sessionName, sessionTag));
    }
    @DeleteMapping("/session/{id}") public Map<String,Object> deleteSession(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @PathVariable String id) { knowledge.deleteSession(userId(userHeader), id); return ok(null); }
    @GetMapping("/chat/records") public Map<String,Object> records(@RequestHeader(value = USER_HEADER, required = false) String userHeader, @RequestParam(required = false) String sessionId) { List<Map<String,Object>> list = knowledge.records(userId(userHeader), sessionId); return ok(Map.of("list", list, "total", list.size())); }

    private SseEmitter stream(String sessionId, Long uid, String question) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.completeWithError(error));
        knowledge.streamChat(sessionId, uid, question,
                token -> {
                    try { emitter.send(SseEmitter.event().data(token)); }
                    catch (IOException error) { emitter.completeWithError(error); }
                },
                answer -> {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException error) { emitter.completeWithError(error); }
                },
                emitter::completeWithError);
        return emitter;
    }
}
