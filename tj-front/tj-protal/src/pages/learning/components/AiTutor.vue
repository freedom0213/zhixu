<!-- 课程AI助教：嵌入课程学习页右侧栏，基于当前课程知识库答疑 -->
<template>
  <div class="aiTutor">
    <div class="tutorHead">
      <div>
        <span class="tutorTitle">AI 助教</span>
        <span class="tutorSub">{{ courseName || '当前课程' }}</span>
      </div>
      <span v-if="kbChecked && kbReady" class="kbBadge">已接入课程知识库</span>
      <span v-else-if="kbChecked" class="kbBadge isWarn">资料建设中</span>
    </div>

    <div class="tutorTabs">
      <button type="button" :class="{ active: mode === 'chat' }" @click="mode = 'chat'">问答</button>
      <button type="button" :class="{ active: mode === 'quiz' }" @click="mode = 'quiz'">知识检测</button>
    </div>

    <template v-if="mode === 'chat'">
      <div v-if="kbChecked && !kbReady" class="kbNotice">
        本课程知识库资料尚在建设中，助教回答可能不完整。可先向助教自由提问。
      </div>

      <div v-if="kbChecked && kbReady" class="quickActions" aria-label="课程快捷操作">
        <button type="button" @click="showChapterSummary" :disabled="isLoading || isStreaming">章节总结</button>
        <button type="button" @click="generateQuestions" :disabled="isLoading || isStreaming">生成练习题</button>
      </div>

      <!-- 消息列表 -->
      <div class="tutorMessages" ref="msgBox">
        <div v-if="chatHistory.length === 0 && !isLoading" class="tutorEmpty">
          <p>你好，我是本门课程的 AI 助教 👋</p>
          <p class="tutorTip">可以问我：这一章讲了什么？这段代码为什么报错？帮我出几道练习题。</p>
        </div>
        <div class="tutorMsg" v-for="(msg, index) in chatHistory" :key="index"
             :class="msg.type === 'user' ? 'isUser' : 'isAi'">
          <div class="bubble" v-if="!(msg.type === 'ai' && msg.isTyping && !msg.content)"
               v-html="render(msg)"></div>
          <div class="typing" v-if="msg.type === 'ai' && msg.isTyping && !msg.content">
            <span></span><span></span><span></span>
          </div>
          <!-- 引用标注：本条回答依据的课程资料与章节 -->
          <div v-if="msg.type === 'ai' && !msg.isTyping && citationsOf(msg).length" class="citations">
            <div class="citeHead">📚 参考本课程资料</div>
            <div class="citeItem" v-for="(cite, ci) in citationsOf(msg)" :key="ci">
              <span class="citeDoc">{{ cite.docName }}</span>
              <span v-if="cite.section" class="citeSection">{{ cite.section }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="tutorInput">
        <input type="text" v-model="inputMessage" placeholder="向 AI 助教提问…"
               @keyup.enter="sendMessage" :disabled="isLoading" />
        <button @click="sendMessage" :disabled="isLoading || !inputMessage.trim()">发送</button>
        <button v-if="isStreaming" @click="stopStream" class="stopBtn">停止</button>
      </div>
    </template>

    <!-- 知识检测 -->
    <CourseQuiz v-else
                :courseId="courseId"
                :courseName="courseName"
                :sectionName="sectionName"
                :kbReady="kbChecked && kbReady" />
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { createUserSession, queryCourseFilePage } from '@/api/ai.js';
import { parseSourcesEvent } from '@/utils/aiSources.js';
import { decodeSseData } from '@/utils/sseData.js';
import { normalizeMarkdown } from '@/utils/markdown.js';
import MarkdownIt from 'markdown-it';
import proxy from '@/config/proxy';
import CourseQuiz from './CourseQuiz.vue';

const props = defineProps({
  courseId: { type: [String, Number], default: null },
  courseName: { type: String, default: '' },
  sectionName: { type: String, default: '' }
});

const env = import.meta.env.MODE || 'development';
const host = proxy[env].host;
const TOKEN = sessionStorage.getItem('token');

const md = new MarkdownIt({ breaks: true });

// 引用来源事件：解析逻辑统一收敛在 @/utils/aiSources.js，三个助手共用同一份实现

// 状态
const chatHistory = ref([]);      // {type:'user'|'ai', content, isTyping}
const inputMessage = ref('');
const isLoading = ref(false);
const isStreaming = ref(false);
const sessionId = ref(null);
const abortController = ref(null);
const msgBox = ref(null);
// 面板模式：chat=问答，quiz=知识检测
const mode = ref('chat');
// 课程知识库接入状态：kbChecked 表示是否已完成探测，kbReady 表示该课程是否已有知识库资料
const kbChecked = ref(false);
const kbReady = ref(false);
// 课程知识库文件名列表，用于后端未回传精确来源时的粗粒度引用标注
const kbFiles = ref([]);

// 引用标注：优先展示后端回传的精确命中章节；否则退化为「基于本课程知识库」
const citationsOf = (msg) => {
  if (msg && Array.isArray(msg.sources) && msg.sources.length) return msg.sources;
  if (kbReady.value && kbFiles.value.length) {
    return kbFiles.value.map((name) => ({ docName: name, section: '' }));
  }
  return [];
};

// 探测当前课程是否已接入知识库，用于决定展示“已接入”还是“资料建设中”
const checkKnowledge = async (courseId) => {
  kbChecked.value = false;
  kbReady.value = false;
  kbFiles.value = [];
  if (!courseId) {
    kbChecked.value = true;
    return;
  }
  try {
    const res = await queryCourseFilePage(courseId);
    if (res && res.code === 200) {
      const data = res.data || {};
      kbReady.value = (data.total ? data.total : 0) > 0;
      kbFiles.value = (Array.isArray(data.list) ? data.list : []).map((it) => it && it.name).filter(Boolean);
    }
  } catch (e) {
    kbReady.value = false;
    kbFiles.value = [];
  } finally {
    kbChecked.value = true;
  }
};

// 切换课程或小节时清理旧会话，避免上一门课程的上下文串入当前课程。
watch(() => [props.courseId, props.courseName, props.sectionName], () => {
  sessionId.value = null;
  chatHistory.value = [];
  if (abortController.value) abortController.value.abort();
  abortController.value = null;
  isLoading.value = false;
  isStreaming.value = false;
});

// 课程变化时重新探测知识库
watch(() => props.courseId, (id) => checkKnowledge(id));

onMounted(() => checkKnowledge(props.courseId));

const scrollToBottom = async () => {
  await nextTick();
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight;
};

const render = (msg) => {
  if (msg.type === 'user') {
    // 用户消息纯文本转义
    const div = document.createElement('div');
    div.textContent = msg.content;
    return div.innerHTML;
  }
  return md.render(normalizeMarkdown(msg.content || ''));
};

// 统一的流式提问：question 为发给后端的问题，displayText 为聊天区展示的用户消息
const streamAsk = async (question, displayText) => {
  if (isLoading.value || isStreaming.value) return;
  isLoading.value = true;
  isStreaming.value = true;
  chatHistory.value.push({ type: 'user', content: displayText });
  chatHistory.value.push({ type: 'ai', content: '', isTyping: true });
  await scrollToBottom();

  const controller = new AbortController();
  abortController.value = controller;
  let content = '';
  try {
    const sid = await ensureSession();
    // 走 POST 请求体：课程/小节上下文 + 提问可能较长，URL 编码后易超请求行上限（413）。
    await fetchEventSource(`${host}/ct/chat/stream`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: TOKEN || ''
      },
      body: JSON.stringify({
        message: question,
        sessionId: sid,
        assistantType: 'COURSE',
        courseId: props.courseId
      }),
      onmessage(event) {
        // 引用来源事件：后端在首个 token 之前推送命中的课程资料与章节
        const { isSources, list } = parseSourcesEvent(event);
        if (isSources) {
          const last = chatHistory.value[chatHistory.value.length - 1];
          if (last) last.sources = list;
          return;
        }
        const data = decodeSseData(event.data);
        if (!data) return;
        if (data === '[DONE]') return;
        content += data;
        chatHistory.value[chatHistory.value.length - 1].content = content;
        scrollToBottom();
      },
      onerror(err) { throw err; }
    });
  } catch (error) {
    if (error.name !== 'AbortError') {
      ElMessage.error('AI 助教请求失败: ' + (error.message || '未知错误'));
      content = content || '（请求失败，请稍后再试）';
    }
  } finally {
    const last = chatHistory.value[chatHistory.value.length - 1];
    if (last) {
      last.isTyping = false;
      if (!last.content) last.content = content || '（未收到回复）';
    }
    isLoading.value = false;
    isStreaming.value = false;
    await scrollToBottom();
  }
};

// 快捷操作：章节总结（走后端 RAG，基于当前课程知识库）
const showChapterSummary = async () => {
  const where = props.sectionName ? `我正在学习的这一小节「${props.sectionName}」` : '本课程';
  const question = `请根据本课程知识库资料，对${where}生成章节总结：包含核心知识点、重点概念和易错点，条理清晰。若没有对应小节的资料，就总结本课程中最相关的内容。`;
  await streamAsk(question, props.sectionName ? `章节总结：${props.sectionName}` : '章节总结');
};

// 快捷操作：生成练习题（走后端 RAG，基于当前课程知识库）
const generateQuestions = async () => {
  const where = props.sectionName ? `当前小节「${props.sectionName}」` : '本课程';
  const question = `请根据本课程知识库资料，围绕${where}生成 3 道练习题，包含选择题和简答题，并给出参考答案与解析。题目必须来自本课程资料。`;
  await streamAsk(question, '生成练习题');
};

const ensureSession = async () => {
  if (sessionId.value) return sessionId.value;
  const name = ('助教·' + (props.courseName || '课程')).slice(0, 8);
  const res = await createUserSession({ name }, 'COURSE', props.courseId);
  if (res.code === 200) {
    sessionId.value = res.data.sessionId;
    return sessionId.value;
  }
  throw new Error(res.msg || '创建会话失败');
};

const sendMessage = async () => {
  const text = inputMessage.value.trim();
  if (!text || isLoading.value) return;
  inputMessage.value = '';
  // 结合当前小节上下文，让助教知道学生在学哪里
  const question = props.sectionName ? `（我正在学习小节「${props.sectionName}」）${text}` : text;
  await streamAsk(question, text);
};

const stopStream = () => {
  if (abortController.value) {
    abortController.value.abort();
    abortController.value = null;
    isStreaming.value = false;
    isLoading.value = false;
  }
};

onUnmounted(() => {
  if (abortController.value) abortController.value.abort();
});
</script>

<style lang="scss" scoped>
.aiTutor {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 10px;
  box-sizing: border-box;
}

/* flex 子项必须允许收缩，否则长回答会把滚动容器撑开。 */
.tutorMessages {
  min-height: 0;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #66717d #252c33;
}
.tutorMessages::-webkit-scrollbar { width: 8px; }
.tutorMessages::-webkit-scrollbar-track { background: #252c33; border-radius: 4px; }
.tutorMessages::-webkit-scrollbar-thumb { background: #66717d; border-radius: 4px; }
.tutorMessages::-webkit-scrollbar-thumb:hover { background: #8593a1; }
.tutorHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 6px 12px;
  border-bottom: 1px solid #2a3138;
  .tutorTitle { font-weight: 600; font-size: 15px; color: #e6e8ea; }
  .tutorSub { font-size: 12px; color: #8b949e; margin-left: 8px; }
  .kbBadge { font-size: 11px; color: #7fe0bd; background: #123f33; padding: 3px 7px; border-radius: 10px; white-space: nowrap; }
  .kbBadge.isWarn { color: #f0c674; background: #4a3a12; }
}
.tutorTabs {
  display: flex;
  gap: 6px;
  padding: 10px 4px 0;
  button {
    flex: 1; height: 30px; border-radius: 8px; font-size: 13px; cursor: pointer;
    border: 1px solid #313a43; background: #232a31; color: #9aa4ae; transition: all .2s;
    &:hover { color: #cfd6dd; }
    &.active { background: #007BFF; border-color: #007BFF; color: #fff; }
  }
}
.kbNotice { margin: 10px 4px 0; padding: 8px 10px; background: #3a2f14; color: #e8c87a; border: 1px solid #5a4a1e; border-radius: 6px; font-size: 12px; line-height: 1.5; }
.quickActions { display: flex; gap: 8px; padding: 10px 4px 0; button { border: 1px solid #2f4a72; background: #1f2a3a; color: #8ab4f8; border-radius: 6px; padding: 6px 10px; font-size: 12px; cursor: pointer; transition: background .2s; &:hover { background: #26364a; } &:disabled { opacity: .55; cursor: not-allowed; } } }
/* P2 引用标注：展示本条回答依据的课程资料与章节 */
.citations {
  margin: 6px 0 0;
  padding: 7px 10px;
  background: #1d2530;
  border: 1px solid #2c3a4a;
  border-left: 3px solid #3d7eff;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.65;
  max-width: 88%;
  width: fit-content;
}
.citeHead { color: #8fa4bb; margin-bottom: 4px; white-space: nowrap; }
.citeItem { display: flex; flex-wrap: wrap; gap: 2px 8px; color: #c8d6e5; }
.citeItem + .citeItem { margin-top: 3px; }
.citeDoc { color: #9fc4ff; overflow-wrap: anywhere; }
.citeSection { color: #8b9bad; }

.tutorMessages, .tutorMessages * { box-sizing: border-box; }
.tutorMessages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 4px;
  .tutorEmpty {
    text-align: center;
    color: #9aa4ae;
    padding: 40px 10px 0;
    font-size: 14px;
    .tutorTip { font-size: 12px; color: #6e7681; margin-top: 8px; line-height: 1.7; }
  }
  .tutorMsg {
    margin-bottom: 14px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    .bubble {
      max-width: 88%;
      padding: 9px 12px;
      border-radius: 10px;
      font-size: 13px;
      line-height: 1.65;
      word-break: break-word;
      overflow-wrap: anywhere;
      :deep(p) { margin: 0 0 6px; &:last-child { margin-bottom: 0; } }
      :deep(pre) { background: #f6f8fa; padding: 8px; border-radius: 6px; overflow-x: auto; font-size: 12px; }
      :deep(code) { font-size: 12px; }
    }
  }
  .isUser { align-items: flex-end; .bubble { background: #007BFF; color: #fff; } }
  .isAi { align-items: flex-start; .bubble { background: #f5f6f8; color: #333; } }
  .typing {
    display: inline-flex; gap: 4px; padding: 8px 10px; background: #f5f6f8; border-radius: 10px;
    span { width: 6px; height: 6px; border-radius: 50%; background: #bbb; animation: blink 1.4s infinite; }
    span:nth-child(2) { animation-delay: .2s; }
    span:nth-child(3) { animation-delay: .4s; }
  }
}
.tutorInput {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #2a3138;
  input {
    flex: 1; height: 36px; border: 1px solid #313a43; border-radius: 8px; padding: 0 12px; font-size: 13px;
    background: #232a31; color: #e6e8ea;
    &::placeholder { color: #6e7681; }
    &:focus { outline: none; border-color: #007BFF; }
    &:disabled { background: #2a3038; }
  }
  button {
    height: 36px; padding: 0 16px; border: none; border-radius: 8px; background: #007BFF; color: #fff; cursor: pointer; font-size: 13px;
    &:disabled { background: #a0c4ff; cursor: not-allowed; }
    &.stopBtn { background: #f56c6c; }
  }
}
@keyframes blink { 0%,60%,100% { opacity: .4; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }
</style>
