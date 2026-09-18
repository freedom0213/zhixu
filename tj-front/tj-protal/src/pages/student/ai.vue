<!-- 学员端 · AI 助手（/student/ai）
     设计稿 10：单卡双栏 —— 左 264 会话栏（浅底）+ 右对话区（头部 / 消息流 / 底部输入）
     能力复用现有私人助手（会话 CRUD、chat/stream 流式问答、知识库文件），
     视觉按设计稿重排；壳内自滚动（meta.fullHeight），不产生外层滚动条。 -->
<template>
  <div class="ai">
    <!-- 左：会话栏 -->
    <aside class="ai__rail">
      <button class="ai__new" type="button" :disabled="creating" @click="createSession">
        <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 4.2v11.6M4.2 10h11.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
        </svg>
        {{ creating ? '创建中…' : '新建对话' }}
      </button>

      <nav class="ai__sessions" aria-label="历史对话">
        <p v-if="!sessions.length" class="ai__railEmpty">还没有对话，点上方新建开始提问</p>
        <button
          v-for="s in sessions"
          :key="s.id"
          class="ai__session"
          :class="{ 'is-active': s.id === currentId }"
          type="button"
          @click="selectSession(s.id)"
        >
          <span class="ai__sessionTitle">{{ s.title || '未命名对话' }}</span>
          <span class="ai__sessionDel" role="button" tabindex="0" aria-label="删除对话" @click.stop="removeSession(s.id)" @keyup.enter.stop="removeSession(s.id)">
            <svg width="14" height="14" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <path d="M5.4 5.4l9.2 9.2M14.6 5.4l-9.2 9.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </span>
        </button>
      </nav>
    </aside>

    <!-- 右：对话区 -->
    <section class="ai__chat">
      <header class="ai__head">
        <div>
          <h2 class="ai__headTitle">AI 学习助手</h2>
          <p class="ai__headSub">{{ kbCount ? `基于 ${kbCount} 份知识库资料作答` : '基于课程知识库与你的学习记录作答' }}</p>
        </div>
        <span class="ai__chip" :class="{ 'is-off': !kbCount }">
          <i class="ai__dot" aria-hidden="true"></i>
          {{ kbCount ? '知识库已连接' : '知识库为空' }}
        </span>
        <button
          class="ai__kbBtn"
          type="button"
          :class="{ 'is-on': kbOpen }"
          :aria-expanded="kbOpen"
          title="打开我的知识库"
          @click="kbOpen = !kbOpen"
        >
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M2.5 4.2A1.7 1.7 0 0 1 4.2 2.5h2.3l1.2 1.6h4.1a1.7 1.7 0 0 1 1.7 1.7v5.5a1.7 1.7 0 0 1-1.7 1.7H4.2a1.7 1.7 0 0 1-1.7-1.7V4.2Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
          </svg>
          知识库<template v-if="kbCount"> · {{ kbCount }}</template>
        </button>
      </header>

      <div ref="scrollRef" class="ai__messages" @scroll="onScroll">
        <!-- 空态 -->
        <div v-if="!messages.length" class="ai__empty">
          <div class="ai__emptyMark" aria-hidden="true">序</div>
          <h3 class="ai__emptyTitle">你好，我是你的 AI 学习助手</h3>
          <p class="ai__emptyDesc">问我课程里的任意知识点，或让我帮你整理笔记、检查错漏、出题和排学习计划。</p>
          <div class="ai__quick">
            <button v-for="q in quickAsks" :key="q" class="ai__quickItem" type="button" :disabled="sending" @click="ask(q)">
              {{ q }}
            </button>
          </div>
        </div>

        <!-- 消息流 -->
        <div v-for="(m, i) in messages" :key="i" class="ai__msg" :class="m.role === 'user' ? 'is-user' : 'is-ai'">
          <div v-if="m.role !== 'user'" class="ai__avatar is-ai" aria-hidden="true">序</div>
          <div class="ai__bubble" :class="m.role === 'user' ? 'is-user' : 'is-ai'">
            <div v-if="m.role === 'user'" class="ai__text">{{ m.content }}</div>
            <div v-else class="ai__text ai__md" v-html="render(m.content)"></div>
            <div v-if="m.sources && m.sources.length" class="ai__sources">
              <span class="ai__sourcesLabel">引用来源</span>
              <span v-for="(s, si) in m.sources" :key="si" class="ai__source">{{ s }}</span>
            </div>
          </div>
          <div v-if="m.role === 'user'" class="ai__avatar is-user" aria-hidden="true">
            <img :src="avatarSrc" alt="" @error="onAvatarError" />
          </div>
        </div>

        <div v-if="sending && !streamingText" class="ai__msg is-ai">
          <div class="ai__avatar is-ai" aria-hidden="true">序</div>
          <div class="ai__bubble is-ai">
            <span class="ai__typing" aria-label="正在生成"><i></i><i></i><i></i></span>
          </div>
        </div>
      </div>

      <footer class="ai__composer">
        <div class="ai__toggles">
          <button
            v-for="t in toggles"
            :key="t.key"
            class="ai__toggle"
            :class="{ 'is-on': flags[t.key] }"
            type="button"
            role="switch"
            :aria-checked="flags[t.key]"
            @click="flags[t.key] = !flags[t.key]"
          >
            {{ t.label }}
          </button>
        </div>
        <div class="ai__inputRow">
          <textarea
            v-model.trim="draft"
            class="ai__input"
            rows="1"
            maxlength="1000"
            placeholder="输入你的问题，Enter 发送 / Shift + Enter 换行"
            @keydown.enter.exact.prevent="ask()"
          ></textarea>
          <button class="ai__send" type="button" :disabled="!draft || sending" @click="ask()">{{ sending ? '生成中' : '发送' }}</button>
        </div>
      </footer>
    </section>

    <!-- 右：我的知识库抽屉（资料上传 / 查看 / 编辑 / 删除） -->
    <transition name="kbSlide">
      <KnowledgePanel
        v-if="kbOpen"
        :visible="kbOpen"
        :selectedFileId="null"
        @close="kbOpen = false"
        @files-changed="loadKb"
      />
    </transition>
  </div>
</template>

<script setup>
/**
 * 学员端 AI 助手（壳内）
 * - 会话与消息走现有接口；流式回答走 chat/stream（POST + fetchEventSource）
 * - 未登录时提问会给出明确提示并引导登录（不静默失败）
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import {
  getUserSessionList,
  createUserSession,
  deleteUserSession,
  getChatRecord,
  queryMarkdownPage,
} from '@/api/ai';
import { parseSourcesEvent, sourceDocNames } from '@/utils/aiSources';
import { decodeSseData } from '@/utils/sseData';
import { isLogin, useUserStore } from '@/store';
import proxy from '@/config/proxy';
import KnowledgePanel from './components/KnowledgePanel.vue';

const env = import.meta.env.MODE || 'development';
const host = proxy[env].host;
import defaultAvatar from '@/assets/icon.jpeg';
import { rememberStudentOrigin } from '@/config/loginRedirect';

const router = useRouter();
const store = useUserStore();

const quickAsks = ['Vue3 的响应式原理是什么？', '解释一下 JVM 垃圾回收', '帮我制定 4 周学习计划', '归纳本周学到的重点'];

const toggles = [
  { key: 'think', label: '深度思考' },
  { key: 'web', label: '联网搜索' },
  { key: 'kb', label: '课程知识库' },
];
const flags = reactive({ think: false, web: false, kb: true });

const sessions = ref([]);
const currentId = ref('');
const messages = ref([]);
const draft = ref('');
const sending = ref(false);
const streamingText = ref('');
const creating = ref(false);
const kbCount = ref(0);
const kbOpen = ref(false);
const scrollRef = ref(null);

const avatarSrc = computed(() => {
  const id = store.userInfo?.id;
  return (id && localStorage.getItem(`tianji:avatar:${id}`)) || store.userInfo?.icon || defaultAvatar;
});
const onAvatarError = (e) => {
  e.target.src = defaultAvatar;
};

// 轻量 Markdown 渲染：仅处理标题 / 加粗 / 代码 / 列表 / 换行，避免引入新依赖
const esc = (s) =>
  String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const render = (raw) => {
  let t = esc(raw);
  t = t.replace(/^###\s?(.+)$/gm, '<h4>$1</h4>');
  t = t.replace(/^##\s?(.+)$/gm, '<h3>$1</h3>');
  t = t.replace(/^#\s?(.+)$/gm, '<h3>$1</h3>');
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
  t = t.replace(/^\s*[-*]\s+(.+)$/gm, '<li>$1</li>');
  t = t.replace(/(<li>[\s\S]*?<\/li>)(?![\s\S]*?<li>)/, (m) => `<ul>${m}</ul>`);
  t = t.replace(/^\s*(\d+)\.\s+(.+)$/gm, '<li>$2</li>');
  t = t.replace(/\n{2,}/g, '</p><p>');
  t = t.replace(/\n/g, '<br/>');
  return `<p>${t}</p>`;
};

const scrollToEnd = async () => {
  await nextTick();
  const el = scrollRef.value;
  if (el) el.scrollTop = el.scrollHeight;
};
const onScroll = () => {};

const loadKb = async () => {
  try {
    const res = await queryMarkdownPage({ pageNo: 1, pageSize: 1 });
    kbCount.value = Number(res?.data?.total || 0);
  } catch (e) {
    kbCount.value = 0;
  }
};

const loadSessions = async () => {
  try {
    const res = await getUserSessionList('PRIVATE');
    const list = res?.data?.list || res?.data || [];
    sessions.value = Array.isArray(list)
      ? list.map((s) => ({ id: s.id, title: s.title || s.name || s.sessionName || '' }))
      : [];
    if (sessions.value.length) selectSession(sessions.value[0].id);
  } catch (e) {
    sessions.value = [];
  }
};

const createSession = async () => {
  creating.value = true;
  try {
    const res = await createUserSession({ title: '新对话' }, 'PRIVATE');
    const id = res?.data?.id || res?.data;
    if (id) {
      sessions.value.unshift({ id, title: '新对话' });
      currentId.value = id;
      messages.value = [];
    }
  } catch (e) {
    ElMessage.warning('新建对话失败，请稍后重试');
  } finally {
    creating.value = false;
  }
};

const selectSession = async (id) => {
  currentId.value = id;
  messages.value = [];
  try {
    const res = await getChatRecord({ sessionId: id, pageNo: 1, pageSize: 50 }, 'PRIVATE');
    const list = res?.data?.list || res?.data || [];
    messages.value = Array.isArray(list)
      ? list
          .map((m) => ({
            role: m.role === 1 || m.type === 1 || m.role === 'user' ? 'user' : 'ai',
            content: m.content || m.message || '',
          }))
          .filter((m) => m.content)
      : [];
  } catch (e) {
    messages.value = [];
  }
  scrollToEnd();
};

const removeSession = async (id) => {
  try {
    await deleteUserSession(id, 'PRIVATE');
  } catch (e) {
    // 删除失败也同步本地列表，避免交互卡住
  }
  sessions.value = sessions.value.filter((s) => s.id !== id);
  if (currentId.value === id) {
    currentId.value = '';
    messages.value = [];
  }
};

const ensureLogin = async () => {
  const ok = store.userInfo?.name ? true : await isLogin();
  if (!ok) {
    rememberStudentOrigin('/student/ai');
    router.push('/login');
    return false;
  }
  return true;
};

// 无会话时自动建一个，保证问答能被记录进历史（与既有 AI 页行为一致）
const ensureSession = async () => {
  if (currentId.value) return currentId.value;
  try {
    const res = await createUserSession({ title: draft.value?.slice(0, 20) || '新对话' }, 'PRIVATE');
    const id = res?.data?.id || res?.data;
    if (id) {
      currentId.value = id;
      sessions.value.unshift({ id, title: '新对话' });
      return id;
    }
  } catch (e) {
    // 建会话失败不阻断问答：本次提问仍可发出，只是不落历史
  }
  return undefined;
};

const ask = async (preset) => {
  const q = (preset || draft.value || '').trim();
  if (!q || sending.value) return;
  if (!(await ensureLogin())) return;

  draft.value = '';
  messages.value.push({ role: 'user', content: q });
  sending.value = true;
  streamingText.value = '';
  scrollToEnd();

  try {
    const token = sessionStorage.getItem('token') || '';
    let content = '';
    let completed = false;
    const sources = [];

    // 与既有 AI 页保持同一契约：POST + JSON body，SSE 逐 token 返回
    await fetchEventSource(`${host}/ct/chat/stream`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: token,
      },
      body: JSON.stringify({
        message: q,
        sessionId: await ensureSession(),
      }),
      async onopen(response) {
        if (!response.ok) throw new Error(`AI 服务响应 ${response.status}`);
      },
      onmessage(event) {
        // 引用来源是元数据事件，必须剔除，否则 [[ZX_SOURCES]] 会混进正文
        const { isSources, list } = parseSourcesEvent(event);
        if (isSources) {
          sourceDocNames(list).forEach((n) => sources.push(n));
          return;
        }
        const data = decodeSseData(event.data);
        if (data === '[DONE]') {
          completed = true;
          return;
        }
        if (!data) return;
        content += data;
        streamingText.value = content;
        scrollToEnd();
      },
      onclose() {
        if (!completed && !content) throw new Error('AI 流式连接意外关闭');
      },
      onerror(error) {
        throw error;
      },
    });

    messages.value.push({
      role: 'ai',
      content: content || '（未收到回复，请稍后重试）',
      sources,
    });
  } catch (e) {
    messages.value.push({ role: 'ai', content: `请求失败：${e.message || '请检查网络后重试'}` });
  } finally {
    sending.value = false;
    streamingText.value = '';
    scrollToEnd();
  }
};

onMounted(async () => {
  if (await isLogin()) {
    await Promise.all([loadSessions(), loadKb()]);
  }
});
</script>

<style lang="scss" scoped>
.ai {
  display: flex;
  height: 100%;
  min-height: 0;
  background: var(--s-card);
  border-radius: var(--s-r-xl);
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055);
  overflow: hidden;
}

// ---- 左栏 ----
.ai__rail {
  flex: 0 0 264px;
  width: 264px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #fafafc;
  border-right: 1px solid var(--s-divider);
}
.ai__new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  border: 0;
  border-radius: 12px;
  background: var(--sa);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, opacity 0.16s ease;

  &:hover:not(:disabled) {
    background: var(--sa-hover);
  }
  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
}
.ai__sessions {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
}
.ai__railEmpty {
  margin: 12px 8px;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-4);
}
.ai__session {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #333;
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
  }
  &.is-active {
    background: var(--sa-soft);
    color: var(--sa);
    font-weight: 500;
  }
}
.ai__sessionTitle {
  flex: 1 1 auto;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ai__sessionDel {
  display: none;
  flex: 0 0 16px;
  color: var(--s-ink-4);

  .ai__session:hover & {
    display: flex;
  }
  &:hover {
    color: var(--s-danger);
  }
}

// ---- 右：对话区 ----
.ai__chat {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.ai__head {
  flex: 0 0 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  border-bottom: 1px solid var(--s-divider);
}
.ai__headTitle {
  margin: 0 0 2px;
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
  color: var(--s-ink);
}
.ai__headSub {
  margin: 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--s-ink-2);
}
.ai__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: var(--sa-soft);
  color: var(--sa);
  font-size: 12px;
  white-space: nowrap;

  &.is-off {
    background: var(--s-soft);
    color: var(--s-ink-3);

    .ai__dot {
      background: var(--s-ink-4);
    }
  }
}
.ai__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sa);
}

.ai__messages {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  background: #f7f8fa;
}

.ai__empty {
  margin: auto;
  max-width: 520px;
  text-align: center;
}
.ai__emptyMark {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border-radius: 16px;
  background: var(--sa);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  line-height: 52px;
}
.ai__emptyTitle {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--s-ink);
}
.ai__emptyDesc {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 21px;
  color: var(--s-ink-2);
}
.ai__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.ai__quickItem {
  padding: 8px 14px;
  border: 0;
  border-radius: 999px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--s-hairline);
  color: var(--s-ink-2);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.16s ease, box-shadow 0.16s ease;

  &:hover:not(:disabled) {
    color: var(--sa);
    box-shadow: inset 0 0 0 1px var(--sa);
  }
  &:disabled {
    opacity: 0.55;
    cursor: default;
  }
}

.ai__msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  &.is-user {
    justify-content: flex-end;
  }
}
.ai__avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  background: #d8dee8;

  &.is-ai {
    background: var(--sa);
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    text-align: center;
    line-height: 32px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}
.ai__bubble {
  max-width: 640px;
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 22px;

  &.is-ai {
    background: #fff;
    color: var(--s-ink);
    box-shadow: inset 0 0 0 1px var(--s-hairline);
  }
  &.is-user {
    background: var(--sa);
    color: #fff;
  }
}
.ai__text {
  word-break: break-word;
}
.ai__md {
  :deep(h3),
  :deep(h4) {
    margin: 10px 0 6px;
    font-size: 15px;
    font-weight: 600;
  }
  :deep(p) {
    margin: 0 0 8px;
  }
  :deep(ul) {
    margin: 6px 0;
    padding-left: 18px;
  }
  :deep(li) {
    margin-bottom: 4px;
  }
  :deep(code) {
    padding: 1px 5px;
    border-radius: 4px;
    background: #f0f1f4;
    font-size: 13px;
  }
  :deep(strong) {
    font-weight: 600;
  }
}
.ai__sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--s-hairline);
}
.ai__sourcesLabel {
  font-size: 11px;
  color: var(--s-ink-3);
}
.ai__source {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--sa-soft);
  color: var(--sa);
  font-size: 11px;
}

.ai__typing {
  display: inline-flex;
  gap: 4px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--s-ink-4);
    animation: aiBlink 1.2s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}
@keyframes aiBlink {
  0%,
  60%,
  100% {
    opacity: 0.25;
  }
  30% {
    opacity: 1;
  }
}

.ai__composer {
  flex: 0 0 auto;
  padding: 16px 20px 20px;
  border-top: 1px solid var(--s-divider);
  background: #fff;
}
.ai__toggles {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.ai__toggle {
  height: 28px;
  padding: 0 14px;
  border: 0;
  border-radius: 14px;
  background: var(--s-soft);
  color: var(--s-ink-2);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &.is-on {
    background: var(--sa-soft);
    color: var(--sa);
  }
}
.ai__inputRow {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
.ai__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 56px;
  padding: 16px;
  border: 0;
  border-radius: 14px;
  background: var(--s-soft);
  color: var(--s-ink);
  font-size: 14px;
  line-height: 24px;
  font-family: inherit;
  resize: none;
  outline: 0;
  transition: box-shadow 0.16s ease, background-color 0.16s ease;

  &::placeholder {
    color: var(--s-ink-4);
  }
  &:focus {
    background: #fff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }
}
.ai__send {
  flex: 0 0 80px;
  height: 56px;
  border: 0;
  border-radius: 14px;
  background: var(--sa);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, opacity 0.16s ease;

  &:hover:not(:disabled) {
    background: var(--sa-hover);
  }
  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}

@media (max-width: 1180px) {
  .ai__rail {
    flex: 0 0 200px;
    width: 200px;
  }
  .ai__bubble {
    max-width: 520px;
  }
}
// 知识库按钮（与「知识库已连接」chip 并排）
.ai__kbBtn {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 16px;
  background: #f5f5f7;
  color: #333;
  font-size: 12px;
  line-height: 17px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  svg { width: 14px; height: 14px; }
  &:hover { background: #e9eaee; }
  &.is-on {
    background: #0066cc;
    color: #fff;
  }
}

// 知识库抽屉滑入
.kbSlide-enter-active,
.kbSlide-leave-active { transition: transform 0.24s ease, opacity 0.24s ease; }
.kbSlide-enter-from,
.kbSlide-leave-to { transform: translateX(16px); opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .kbSlide-enter-active,
  .kbSlide-leave-active { transition: none; }
}
</style>
