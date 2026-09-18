<!-- 全局AI助手：首页右下角悬浮聊天窗（平台助手，assistantType=GLOBAL） -->
<template>
  <div class="gaWrap">
    <!-- 悬浮窗 -->
    <transition name="fadeUp">
      <div class="gaWindow" v-if="visible">
        <div class="gaHead">
          <div class="gaTitle">
            <span class="gaName">知序学堂 AI 助手</span>
            <span class="gaSub">选课 / 学习规划 / 平台使用</span>
          </div>
          <div class="gaActions">
            <button class="gaIconBtn" title="新对话" @click="newConversation">＋</button>
            <button class="gaIconBtn" title="收起" @click="visible = false">✕</button>
          </div>
        </div>
        <div class="gaMessages" ref="msgBox">
          <div v-if="chatHistory.length === 0 && !isLoading" class="gaEmpty">
            <p>你好，我是知序学堂 AI 助手 👋</p>
            <p class="gaTip">我可以帮助你了解课程、制定学习路线。</p>
            <div class="gaQuick">
              <span v-for="q in quickQuestions" :key="q" @click="ask(q)">{{ q }}</span>
            </div>
          </div>
          <div class="gaMsg" v-for="(msg, index) in chatHistory" :key="index"
               :class="msg.type === 'user' ? 'isUser' : 'isAi'">
            <div class="bubbleCol">
              <div class="bubble" v-html="render(msg)"></div>
              <div class="typing" v-if="msg.type === 'ai' && msg.isTyping && !msg.content">
                <span></span><span></span><span></span>
              </div>
              <!-- 引用来源：后端回传命中的平台资料（只展示文档名） -->
              <div class="citations" v-if="msg.type === 'ai' && citationsOf(msg).length">
                <span class="citeTitle">📚 参考平台资料</span>
                <span class="citeDoc" v-for="name in citationsOf(msg)" :key="name">{{ name }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="gaInput">
          <input type="text" v-model="inputMessage" placeholder="有什么可以帮你？"
                 @keyup.enter="sendMessage" :disabled="isLoading" />
          <button @click="sendMessage" :disabled="isLoading || !inputMessage.trim()">发送</button>
          <button v-if="isStreaming" @click="stopStream" class="stopBtn">停止</button>
        </div>
        <div class="gaFoot">
          <span @click="goPrivateAssistant">需要管理自己的知识库？去「私人助手」→</span>
        </div>
      </div>
    </transition>

    <!-- 悬浮按钮 -->
    <div class="gaFab" role="button" tabindex="0" @click="toggle" @keyup.enter="toggle" :title="visible ? '收起 AI 助手' : '打开 AI 助手'" :aria-expanded="visible">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M4 11.2c0-3.9 3.6-7 8-7s8 3.1 8 7-3.6 7-8 7c-.9 0-1.8-.13-2.6-.37L5.2 19.6l1.1-3C4.8 15.4 4 13.4 4 11.2Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>
        <path d="M8.9 10.8h.01M12 10.8h.01M15.1 10.8h.01" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { createUserSession } from '@/api/ai.js';
import { getClassCategorys, getCourseSimpleInfoList } from '@/api/class.js';
import { parseSourcesEvent, sourceDocNames } from '@/utils/aiSources.js';
import { decodeSseData } from '@/utils/sseData.js';
import { normalizeMarkdown } from '@/utils/markdown.js';
import MarkdownIt from 'markdown-it';
import proxy from '@/config/proxy';

const env = import.meta.env.MODE || 'development';
const host = proxy[env].host;
const TOKEN = sessionStorage.getItem('token');
const router = useRouter();

const md = new MarkdownIt({ breaks: true });

const visible = ref(false);
const chatHistory = ref([]);
const inputMessage = ref('');
const isLoading = ref(false);
const isStreaming = ref(false);
const sessionId = ref(null);
const abortController = ref(null);
const msgBox = ref(null);
// 平台课程目录缓存：数据来自 tj-course 数据库，属于「实时数据」而非静态知识库文档。
// 打开悬浮窗时强制刷新；否则 30 秒内复用缓存，保证新增/下架课程能较快被助手感知。
const courseCatalog = ref('');
const catalogLoadedAt = ref(0);
const CATALOG_TTL = 30 * 1000;
const catalogUpdatedText = ref('');

// 快捷功能按钮（与需求文档《知序课堂AI聊天改造2.md》全局助手一节保持一致）
const quickQuestions = [
  '推荐学习课程',
  'Java学习路线',
  'Python学习路线',
  '如何购买课程',
  '平台功能介绍'
];

const scrollToBottom = async () => {
  await nextTick();
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight;
};

const render = (msg) => {
  if (msg.type === 'user') {
    const div = document.createElement('div');
    div.textContent = msg.content;
    return div.innerHTML;
  }
  return md.render(normalizeMarkdown(msg.content || ''));
};

// 引用来源文档名（去重）。后端检索命中为空时返回空数组，不展示标注。
const citationsOf = (msg) => sourceDocNames(msg && msg.sources);

/** 生成「数据截至」时间戳，让模型知道自己拿到的是实时数据。 */
const stamp = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

/**
 * 加载平台课程目录（30 秒缓存，打开悬浮窗时强制刷新）。
 * 数据来源：tj-course 数据库（/cs/categorys/all 分类树 + /cs/simpleInfo/list 课程列表），
 * 是数据库里真实记录的课目，每次查询都反映平台当前在售的课程，因此助手能"实时"知道有哪些课。
 */
const loadCourseCatalog = async (force = false) => {
  const now = Date.now();
  if (!force && courseCatalog.value && now - catalogLoadedAt.value < CATALOG_TTL) return courseCatalog.value;
  try {
    // request 工具返回完整响应体 {code, msg, data}，需要取 .data
    const cateRes = await getClassCategorys({ admin: false });
    const cateTree = cateRes?.data || cateRes || [];
    // 递归收集三级分类：id -> 名称
    const cateNameMap = {};
    const thirdCataIds = [];
    const walk = (nodes, parentNames) => {
      if (!Array.isArray(nodes)) return;
      for (const node of nodes) {
        const path = [...parentNames, node.name];
        if (Array.isArray(node.children) && node.children.length > 0) {
          walk(node.children, path);
        } else {
          // 叶子节点 = 三级分类
          cateNameMap[node.id] = path.join(' / ');
          thirdCataIds.push(node.id);
        }
      }
    };
    walk(cateTree, []);
    if (thirdCataIds.length === 0) {
      courseCatalog.value = '（平台暂无课程分类数据）';
      catalogLoadedAt.value = now;
      catalogUpdatedText.value = stamp();
      return courseCatalog.value;
    }
    // 按三级分类查数据库里的真实课程
    const courseRes = await getCourseSimpleInfoList(thirdCataIds);
    const courses = courseRes?.data || courseRes || [];
    if (!Array.isArray(courses) || courses.length === 0) {
      courseCatalog.value = '（平台暂无上架课程数据）';
      catalogLoadedAt.value = now;
      catalogUpdatedText.value = stamp();
      return courseCatalog.value;
    }
    const lines = [];
    const seen = new Set();
    for (const c of courses) {
      if (!c || !c.name || seen.has(c.id)) continue;
      seen.add(c.id);
      const cate = cateNameMap[c.thirdCateId] ? `（${cateNameMap[c.thirdCateId]}）` : '';
      const price = c.free || c.price === 0 ? '免费' : (c.price != null ? `${c.price / 100}元` : '');
      lines.push(`- 《${c.name}》${cate}${price ? ' ' + price : ''}${c.sectionNum ? ` 共${c.sectionNum}小节` : ''}`);
    }
    courseCatalog.value = lines.length > 0
      ? lines.slice(0, 60).join('\n')
      : '（平台暂无上架课程数据）';
    catalogLoadedAt.value = now;
    catalogUpdatedText.value = stamp();
  } catch (e) {
    console.error('加载课程目录失败:', e);
    courseCatalog.value = courseCatalog.value || '（平台课程数据加载失败，请稍后重试）';
  }
  return courseCatalog.value;
};

const toggle = () => {
  visible.value = !visible.value;
  // 每次打开悬浮窗强制刷新课程目录，保证助手拿到的是平台当前课程
  if (visible.value) loadCourseCatalog(true);
};
defineExpose({ toggle });

const newConversation = async () => {
  stopStream();
  chatHistory.value = [];
  sessionId.value = null;
};

const ensureSession = async () => {
  if (sessionId.value) return sessionId.value;
  const res = await createUserSession({ name: '全局助手' }, 'GLOBAL', null);
  if (res.code === 200) {
    sessionId.value = res.data.sessionId;
    return sessionId.value;
  }
  throw new Error(res.msg || '创建会话失败');
};

const ask = (text) => {
  inputMessage.value = text;
  sendMessage();
};

const sendMessage = async () => {
  const text = inputMessage.value.trim();
  if (!text || isLoading.value) return;
  isLoading.value = true;
  inputMessage.value = '';
  chatHistory.value.push({ type: 'user', content: text });
  chatHistory.value.push({ type: 'ai', content: '', isTyping: true });
  await scrollToBottom();

  const controller = new AbortController();
  abortController.value = controller;
  isStreaming.value = true;
  let content = '';
  try {
    const sid = await ensureSession();
    // 注入平台课程目录，让全局助手能基于平台当前真实课程来推荐 / 规划路线。
    // 目录带「数据截至」时间戳，并在发送前按 30 秒 TTL 自动刷新，保证时效性。
    const catalog = await loadCourseCatalog();
    const augmented = catalog
      ? `【平台课程目录】数据截至 ${catalogUpdatedText.value || stamp()}，以下为知序学堂当前在售课程（分类 / 价格 / 节数），推荐与路线规划必须基于此列表，不要推荐列表中不存在的课程：\n${catalog}\n\n【用户问题】\n${text}`
      : text;
    // 目录注入后提问会很长，中文经 URL 编码膨胀约 3 倍，容易超出请求行长度上限（413）。
    // 因此走 POST 请求体：URL 只保留路由，正文放在 body。
    await fetchEventSource(`${host}/ct/chat/stream`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: TOKEN || ''
      },
      body: JSON.stringify({
        message: augmented,
        sessionId: sid,
        assistantType: 'GLOBAL'
      }),
      onmessage(event) {
        // 引用来源事件属元数据，必须剔除，否则 [[ZX_SOURCES]] 标记会显示在回答里
        const { isSources, list } = parseSourcesEvent(event);
        if (isSources) {
          const last = chatHistory.value[chatHistory.value.length - 1];
          if (last) last.sources = list;
          return;
        }
        // 后端片段经 JSON 编码，需解码后才能还原换行与空格
        const data = decodeSseData(event.data);
        if (data === '[DONE]') return;
        if (!data) return;
        content += data;
        chatHistory.value[chatHistory.value.length - 1].content = content;
        scrollToBottom();
      },
      onerror(err) { throw err; }
    });
  } catch (error) {
    if (error.name !== 'AbortError') {
      ElMessage.error('AI 助手请求失败: ' + (error.message || '未知错误'));
      content = content || '（请求失败，请稍后再试）';
    }
  } finally {
    const last = chatHistory.value[chatHistory.value.length - 1];
    last.isTyping = false;
    if (!last.content) last.content = content || '（未收到回复）';
    isLoading.value = false;
    isStreaming.value = false;
    await scrollToBottom();
  }
};

const stopStream = () => {
  if (abortController.value) {
    abortController.value.abort();
    abortController.value = null;
    isStreaming.value = false;
    isLoading.value = false;
  }
};

const goPrivateAssistant = () => {
  stopStream();
  // 清空悬浮窗的对话，避免跳过去后误以为这些是私人助手的历史
  chatHistory.value = [];
  sessionId.value = null;
  visible.value = false;
  router.push('/student/ai');
};

onUnmounted(() => {
  if (abortController.value) abortController.value.abort();
});
</script>

<style lang="scss" scoped>
// =============================================================================
// 全局助手（学员端全站）— 右下角悬浮球 + 小窗聊天
// 风格对齐学员端：白卡 + 发丝线 + #0066CC 主色，圆角 14 / 18
// =============================================================================
.gaWrap {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 9000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
}

// ---------- 悬浮球 ----------
.gaFab {
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #0071e3 0%, #0066cc 100%);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 10px 28px -8px rgba(0, 102, 204, 0.55), 0 2px 8px rgba(16, 24, 40, 0.12);
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  svg { width: 26px; height: 26px; }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 34px -8px rgba(0, 102, 204, 0.6), 0 2px 10px rgba(16, 24, 40, 0.14);
  }
  &:active { transform: translateY(0); }
  &:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
}

// ---------- 聊天小窗 ----------
.gaWindow {
  width: 400px;
  height: 560px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 24px 64px -16px rgba(16, 24, 40, 0.32), 0 2px 10px rgba(16, 24, 40, 0.08);
  box-shadow: 0 24px 64px -16px rgba(16, 24, 40, 0.32), inset 0 0 0 1px #ececf0;
}

.gaHead {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 14px;
  border-bottom: 1px solid #ececf0;
}
.gaTitle { min-width: 0; }
.gaName {
  display: block;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  color: #1d1d1f;
}
.gaSub {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  line-height: 15px;
  color: #86868b;
}
.gaActions { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }
.gaIconBtn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 14px;
  background: #f5f5f7;
  color: #6e6e73;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover { background: #e9eaee; color: #1d1d1f; }
}

// ---------- 消息区 ----------
.gaMessages {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.gaEmpty {
  text-align: center;
  padding: 24px 8px;
  font-size: 13px;
  line-height: 20px;
  color: #1d1d1f;

  .gaTip { margin: 6px 0 0; font-size: 12px; line-height: 18px; color: #86868b; }
}
.gaQuick {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;

  span {
    padding: 6px 12px;
    border-radius: 14px;
    background: #fff;
    box-shadow: inset 0 0 0 1px #ececf0;
    font-size: 12px;
    line-height: 18px;
    color: #333;
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;

    &:hover { background: #e8f1fc; color: #0066cc; box-shadow: inset 0 0 0 1px rgba(0, 102, 204, 0.35); }
  }
}

.gaMsg {
  display: flex;
  justify-content: flex-start;
  &.isUser { justify-content: flex-end; }
}
.bubbleCol {
  max-width: 84%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  background: #fff;
  box-shadow: inset 0 0 0 1px #ececf0;
  font-size: 13px;
  line-height: 22px;
  color: #1d1d1f;
  word-break: break-word;

  :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { margin: 6px 0; padding-left: 20px; }
  :deep(li) { margin: 2px 0; }
  :deep(code) {
    padding: 1px 5px;
    border-radius: 4px;
    background: #f0f2f5;
    font-size: 12px;
  }
  :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    border-radius: 10px;
    background: #f5f6f8;
    overflow-x: auto;
  }
  :deep(pre code) { padding: 0; background: transparent; }
  :deep(a) { color: #0066cc; }

  .isUser & {
    background: #0066cc;
    box-shadow: none;
    color: #fff;

    :deep(p), :deep(li), :deep(td), :deep(a), :deep(code) { color: #fff; }
    :deep(code) { background: rgba(255, 255, 255, 0.18); }
  }
}

.typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 2px;

  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #c2c6ce;
    animation: gaBlink 1.2s infinite ease-in-out;

    &:nth-child(2) { animation-delay: 0.18s; }
    &:nth-child(3) { animation-delay: 0.36s; }
  }
}
@keyframes gaBlink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}

.citations {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;

  .citeTitle { font-size: 11px; line-height: 15px; color: #86868b; }
  .citeDoc {
    padding: 2px 8px;
    border-radius: 6px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 11px;
    line-height: 15px;
  }
}

// ---------- 输入区 ----------
.gaInput {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #ececf0;
  background: #fff;

  input {
    flex: 1 1 auto;
    min-width: 0;
    height: 36px;
    padding: 0 14px;
    border: 0;
    border-radius: 18px;
    background: #f5f5f7;
    font-size: 13px;
    color: #1d1d1f;
    font-family: inherit;
    outline: none;
    transition: box-shadow 0.16s ease, background-color 0.16s ease;

    &:focus { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
    &::placeholder { color: #86868b; }
  }

  button {
    flex: 0 0 auto;
    height: 36px;
    padding: 0 18px;
    border: 0;
    border-radius: 18px;
    background: #0066cc;
    color: #fff;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.5; cursor: default; }

    &.stopBtn { background: #f5f5f7; color: #333; }
    &.stopBtn:hover { background: #e9eaee; }
  }
}

.gaFoot {
  flex: 0 0 auto;
  padding: 8px 16px 12px;
  background: #fff;
  text-align: center;

  span {
    font-size: 11px;
    line-height: 15px;
    color: #0066cc;
    cursor: pointer;

    &:hover { text-decoration: underline; }
  }
}

// ---------- 出入场 ----------
.fadeUp-enter-active,
.fadeUp-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.fadeUp-enter-from,
.fadeUp-leave-to { opacity: 0; transform: translateY(12px); }

@media (max-width: 720px) {
  .gaWrap { right: 16px; bottom: 16px; }
  .gaWindow { width: calc(100vw - 32px); height: 70vh; }
}

@media (prefers-reduced-motion: reduce) {
  .fadeUp-enter-active,
  .fadeUp-leave-active { transition: none; }
  .gaFab { transition: none; }
  .typing span { animation: none; }
}
</style>
