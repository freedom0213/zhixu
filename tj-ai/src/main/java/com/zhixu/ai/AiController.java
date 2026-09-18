package com.zhixu.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

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

    // ==================== 私人知识库（USER） ====================

    @PostMapping("/file/upload")
    public Map<String,Object> upload(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestParam MultipartFile file) throws IOException {
        return ok(knowledge.upload(userId(userHeader), file));
    }

    @GetMapping("/file/page")
    public Map<String,Object> page(@RequestHeader(value = USER_HEADER, required = false) String userHeader) {
        Long uid = userId(userHeader);
        List<Map<String, Object>> list = knowledge.list(uid);
        return ok(Map.of("list", list, "total", list.size(), "chunkCount", knowledge.chunkTotal(uid)));
    }

    @GetMapping("/file/{id}")
    public Map<String,Object> get(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                  @PathVariable String id) throws IOException {
        return ok(knowledge.content(userId(userHeader), id));
    }

    @DeleteMapping("/file/{id}")
    public Map<String,Object> delete(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @PathVariable String id) throws IOException {
        knowledge.delete(userId(userHeader), id);
        return ok(null);
    }

    // ==================== 课程知识库（COURSE，供管理端/老师上传课程资料） ====================

    @PostMapping("/course/{courseId}/file/upload")
    public Map<String,Object> uploadCourse(@PathVariable Long courseId,
                                           @RequestParam MultipartFile file) throws IOException {
        return ok(knowledge.uploadCourse(courseId, file));
    }

    @GetMapping("/course/{courseId}/file/page")
    public Map<String,Object> coursePage(@PathVariable Long courseId) {
        List<Map<String, Object>> list = knowledge.listCourse(courseId);
        return ok(Map.of("list", list, "total", list.size()));
    }

    @GetMapping("/course/{courseId}/file/{id}")
    public Map<String,Object> courseGet(@PathVariable Long courseId, @PathVariable String id) throws IOException {
        return ok(knowledge.courseContent(courseId, id));
    }

    @DeleteMapping("/course/{courseId}/file/{id}")
    public Map<String,Object> courseDelete(@PathVariable Long courseId, @PathVariable String id) throws IOException {
        knowledge.deleteCourse(courseId, id);
        return ok(null);
    }

    // ==================== 平台知识库（PLATFORM，供 GLOBAL 全局助手使用） ====================

    @PostMapping("/platform/file/upload")
    public Map<String,Object> uploadPlatform(@RequestParam MultipartFile file) throws IOException {
        return ok(knowledge.uploadPlatform(file));
    }

    @GetMapping("/platform/file/page")
    public Map<String,Object> platformPage() {
        List<Map<String, Object>> list = knowledge.listPlatform();
        return ok(Map.of("list", list, "total", list.size()));
    }

    @GetMapping("/platform/file/{id}")
    public Map<String,Object> platformGet(@PathVariable String id) throws IOException {
        return ok(knowledge.platformContent(id));
    }

    @DeleteMapping("/platform/file/{id}")
    public Map<String,Object> platformDelete(@PathVariable String id) throws IOException {
        knowledge.deletePlatform(id);
        return ok(null);
    }

    // ==================== 聊天（PRIVATE / COURSE / GLOBAL） ====================
    // 注：原非流式 `GET /file/chat` 已移除。
    //   移除原因：① 前端唯一调用方 chatByMarkdownDoc 随 /main/ai/knowledge 页面一并删除，已无调用方；
    //   ② 它与 `POST /file/chat/stream` 底层是同一套 KnowledgeService 检索+回答逻辑，仅流式/非流式之差；
    //   ③ 它返回 {content:"<AI 回答>"} 对象，而旧前端曾按数组渲染「原文片段+匹配得分」，
    //      该分支 Array.isArray(res.data) 恒为 false —— 那段 UI 从未生效过。
    //   需要非流式问答请用 `GET /chat/simple`（返回 content + sources + assistantType）。

    @GetMapping(value = "/file/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fileChatStream(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestParam(required = false) String question,
                                     @RequestParam(required = false) String message,
                                     @RequestParam(required = false) String sessionId) {
        return stream(sessionId, userId(userHeader), KnowledgeService.normalizeType(null), null,
                question != null ? question : message);
    }

    @GetMapping("/chat/simple")
    public Map<String,Object> simple(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestParam(required = false) String question,
                                     @RequestParam(required = false) String message,
                                     @RequestParam(required = false) String sessionId,
                                     @RequestParam(required = false) String assistantType,
                                     @RequestParam(required = false) Long courseId) {
        return chatByType(userHeader, question, message, sessionId, assistantType, courseId);
    }

    /** 私人助手「学习工具」非流式版本：出题（需要完整 JSON）等场景使用。 */
    @GetMapping("/chat/task")
    public Map<String,Object> chatTask(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                       @RequestParam String task,
                                       @RequestParam(required = false) String message,
                                       @RequestParam(required = false) String question,
                                       @RequestParam(required = false) String sessionId,
                                       @RequestParam(required = false) String docId) {
        return ok(knowledge.chatTask(sessionId, userId(userHeader), task,
                question != null ? question : message, docId));
    }

    /** 私人助手「学习工具」：笔记整理 / 内容检查 / 自动出题 / 学习计划。 */
    @GetMapping(value = "/chat/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatTaskStream(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestParam String task,
                                     @RequestParam(required = false) String message,
                                     @RequestParam(required = false) String question,
                                     @RequestParam(required = false) String sessionId,
                                     @RequestParam(required = false) String docId) {
        return stream(sessionId, userId(userHeader), KnowledgeService.normalizeType(null), null,
                question != null ? question : message, task, docId);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                 @RequestParam(required = false) String question,
                                 @RequestParam(required = false) String message,
                                 @RequestParam(required = false) String sessionId,
                                 @RequestParam(required = false) String assistantType,
                                 @RequestParam(required = false) Long courseId) {
        return stream(sessionId, userId(userHeader), KnowledgeService.normalizeType(assistantType), courseId,
                question != null ? question : message);
    }

    // ==================== 以下为上述流式接口的 POST 版本 ====================
    // 背景：全局助手会把「平台课程目录」等长上下文随提问一起发给模型，中文经 URL 编码后体积
    // 约膨胀 3 倍，很容易突破网关 / Tomcat 的请求行长度上限（实测返回 413 Request Entity Too Large）。
    // 因此长内容统一走请求体（JSON），URL 只承担路由，GET 版本保留以兼容既有调用。

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamPost(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestBody(required = false) Map<String,Object> body) {
        return stream(pick(body, "sessionId"), userId(userHeader),
                KnowledgeService.normalizeType(pick(body, "assistantType")), pickLong(body, "courseId"),
                pick(body, "question", "message"));
    }

    @PostMapping(value = "/file/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fileChatStreamPost(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                         @RequestBody(required = false) Map<String,Object> body) {
        return stream(pick(body, "sessionId"), userId(userHeader), KnowledgeService.normalizeType(null), null,
                pick(body, "question", "message"));
    }

    @PostMapping(value = "/chat/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatTaskStreamPost(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                         @RequestBody(required = false) Map<String,Object> body) {
        return stream(pick(body, "sessionId"), userId(userHeader), KnowledgeService.normalizeType(null), null,
                pick(body, "question", "message"), pick(body, "task"), pick(body, "docId"));
    }

    @PostMapping("/chat/task")
    public Map<String,Object> chatTaskPost(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                           @RequestBody(required = false) Map<String,Object> body) {
        return ok(knowledge.chatTask(pick(body, "sessionId"), userId(userHeader), pick(body, "task"),
                pick(body, "question", "message"), pick(body, "docId")));
    }

    @PostMapping("/chat/simple")
    public Map<String,Object> simplePost(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                         @RequestBody(required = false) Map<String,Object> body) {
        return chatByType(userHeader, pick(body, "question"), pick(body, "message"), pick(body, "sessionId"),
                pick(body, "assistantType"), pickLong(body, "courseId"));
    }

    /** 从请求体按优先级取第一个非空字段，便于 POST 请求沿用 GET 的参数命名。 */
    private static String pick(Map<String,Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static Long pickLong(Map<String,Object> body, String key) {
        return body == null ? null : toLong(body.get(key));
    }

    /** 统一的按助手类型聊天；data 中同时返回引用来源（sources）。 */
    private Map<String,Object> chatByType(String userHeader, String question, String message,
                                          String sessionId, String assistantType, Long courseId) {
        String q = question != null ? question : message;
        String type = KnowledgeService.normalizeType(assistantType);
        return ok(knowledge.chatWithSources(sessionId, userId(userHeader), type, courseId, q));
    }

    // ==================== 会话管理（按助手类型隔离） ====================

    @GetMapping("/session/list")
    public Map<String,Object> sessions(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                       @RequestParam(required = false) String assistantType) {
        return ok(knowledge.sessions(userId(userHeader), KnowledgeService.normalizeType(assistantType)));
    }

    @PostMapping("/session")
    public Map<String,Object> create(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false, defaultValue = "") String tag,
                                     @RequestParam(required = false) String assistantType,
                                     @RequestParam(required = false) Long courseId) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "新会话" : body.getOrDefault("name", "新会话"));
        String sessionTag = tag != null && !tag.isBlank() ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        String type = KnowledgeService.normalizeType(
                assistantType != null ? assistantType
                        : body == null ? null : String.valueOf(body.getOrDefault("assistantType", "")));
        Long cid = courseId != null ? courseId
                : body == null ? null : toLong(body.get("courseId"));
        return ok(knowledge.createSession(userId(userHeader), sessionName, sessionTag, type, cid));
    }

    @PutMapping("/session/{id}")
    public Map<String,Object> update(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                     @PathVariable String id,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) String tag,
                                     @RequestParam(required = false) String assistantType) {
        String sessionName = name != null ? name : String.valueOf(body == null ? "未命名会话" : body.getOrDefault("name", "未命名会话"));
        String sessionTag = tag != null ? tag : String.valueOf(body == null ? "" : body.getOrDefault("tag", ""));
        String type = KnowledgeService.normalizeType(
                assistantType != null ? assistantType
                        : body == null ? null : String.valueOf(body.getOrDefault("assistantType", "")));
        return ok(knowledge.updateSession(userId(userHeader), id, sessionName, sessionTag, type));
    }

    @DeleteMapping("/session/{id}")
    public Map<String,Object> deleteSession(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                            @PathVariable String id,
                                            @RequestParam(required = false) String assistantType) {
        knowledge.deleteSession(userId(userHeader), id, KnowledgeService.normalizeType(assistantType));
        return ok(null);
    }

    @GetMapping("/chat/records")
    public Map<String,Object> records(@RequestHeader(value = USER_HEADER, required = false) String userHeader,
                                      @RequestParam(required = false) String sessionId,
                                      @RequestParam(required = false) String assistantType) {
        List<Map<String,Object>> list = knowledge.records(userId(userHeader), sessionId,
                KnowledgeService.normalizeType(assistantType));
        return ok(Map.of("list", list, "total", list.size()));
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 发送一个流式文本片段（SSE data 字段）。
     *
     * <p>这里必须按 SSE 规范做两件事，否则**换行和空格会被浏览器端的解析器吃掉**：</p>
     * <ol>
     *   <li><b>换行要拆成多条 data 行</b>：规范规定事件内的多条 {@code data:} 行会用 {@code \n}
     *       拼接。若把含 {@code \n} 的字符串原样交给 SseEmitter，Spring 会写出一个裸换行，
     *       解析器会把它当成「空 data 行」直接丢掉。</li>
     *   <li><b>每行前面补一个空格</b>：规范规定 {@code data:} 后紧跟的一个空格会被移除。
     *       不补空格时，内容本身以空格开头（例如模型单独输出的一个空格 token）就会被剥掉；
     *       补一个空格后，被移除的正好是我们补的那个，内容原样保留。</li>
     * </ol>
     *
     * <p>症状对照：模型输出 {@code "##"}、{@code " "}、{@code "Java"} 三个片段时，若不做上述处理，
     * 前端拼出来的是 {@code "##Java"} —— 标题不再被识别，页面上直接显示 {@code ##} 原始符号；
     * 同理列表 {@code "-"}、引用 {@code ">"}、表格 {@code "|"} 全部塌成一行。</p>
     */
    /**
     * 发送一个流式文本片段（SSE data 字段）。
     *
     * <p>片段必须 **JSON 编码**后再放进 data，原因是 SSE 协议本身无法安全承载换行与前导空格：</p>
     * <ul>
     *   <li>规范规定 {@code data:} 后紧跟的一个空格会被解析器移除 —— 模型单独输出的一个空格
     *       片段（{@code " "}）会变成空字符串。</li>
     *   <li>规范规定事件内多条 {@code data:} 行才用 {@code \n} 拼接；若把含 {@code \n} 的字符串
     *       原样交给 SseEmitter，Spring 会写成一个裸换行，解析器当成「空 data 行」直接丢弃。</li>
     *   <li>更隐蔽的是：前端解析库用 {@code data ? data + "\n" + value : value} 拼接，
     *       <b>首个 data 行为空时前导换行会整段丢失</b> —— 而模型恰好常把 {@code \n} 单独作为一个片段输出。</li>
     * </ul>
     *
     * <p>症状对照：模型依次输出 {@code "##"}、{@code " "}、{@code "Java"} 时，未编码的写法前端只能拼出
     * {@code "##Java"} —— 标题不再被识别，页面直接显示 {@code ##} 原始符号；列表 {@code "-"}、
     * 引用 {@code ">"}、表格 {@code "|"} 同理，整篇回答会塌成一行。</p>
     *
     * <p>JSON 编码后片段恒为单行、且以 {@code "} 开头，前端 {@code JSON.parse} 即可无损还原。
     * 前端解码见 {@code tj-protal/src/utils/sseData.js}。</p>
     */
    private static void sendToken(SseEmitter emitter, String token) throws IOException {
        if (token == null || token.isEmpty()) return;
        emitter.send(SseEmitter.event().data(JSON.writeValueAsString(token)));
    }

    /** SSE 事件名：引用来源。data 以 SOURCES_MARKER 开头，前端可据此兜底识别。 */
    private static final String SOURCES_EVENT = "sources";
    private static final String SOURCES_MARKER = "[[ZX_SOURCES]]";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private SseEmitter stream(String sessionId, Long uid, String assistantType, Long courseId, String question) {
        return stream(sessionId, uid, assistantType, courseId, question, null, null);
    }

    private SseEmitter stream(String sessionId, Long uid, String assistantType, Long courseId, String question, String task) {
        return stream(sessionId, uid, assistantType, courseId, question, task, null);
    }

    /**
     * 统一 SSE 输出。task 为空按助手类型走通用问答；task 非空走私人助手「学习工具」模板。
     *
     * @param docId 学习工具的资料范围：某份文件的 docId / 空 / "all"（见 KnowledgeService.taskContext）
     */
    private SseEmitter stream(String sessionId, Long uid, String assistantType, Long courseId, String question,
                              String task, String docId) {
        // 出题/计划类任务回答更长，给更充裕的超时
        SseEmitter emitter = new SseEmitter(task != null ? 180_000L : 120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.completeWithError(error));
        Consumer<String> onToken = token -> {
            try { sendToken(emitter, token); }
            catch (IOException error) { emitter.completeWithError(error); }
        };
        Consumer<String> onComplete = answer -> {
            try {
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (IOException error) { emitter.completeWithError(error); }
        };
        Consumer<List<Map<String, Object>>> onSources = sources -> {
            // 检索命中为空时不发送，前端会退化为「基于本课程知识库」的粗粒度标注
            if (sources == null || sources.isEmpty()) return;
            try {
                String payload = SOURCES_MARKER + JSON.writeValueAsString(sources);
                emitter.send(SseEmitter.event().name(SOURCES_EVENT).data(payload));
            } catch (IOException error) {
                // JsonProcessingException 也属于 IOException；引用标注失败不影响正文回答
            }
        };
        if (task != null) {
            knowledge.streamTask(sessionId, uid, task, question, docId, onToken, onComplete, emitter::completeWithError, onSources);
        } else {
            knowledge.streamChat(sessionId, uid, assistantType, courseId, question, onToken, onComplete, emitter::completeWithError, onSources);
        }
        return emitter;
    }
}
