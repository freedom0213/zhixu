<!--
 * 师生对话（/student/messages）— 对应设计稿 08，壳内自滚动（meta.fullHeight）
 * 数据契约（只用真实返回字段，缺失即降级，不编造）：
 *   - queryUserConversation({pageNo,pageSize}) → {list:[{id,otherUserId,otherUsername,
 *     otherAvatar,unReadCount,lastMessage,lastMessageTime}],total}
 *   - getMessageRecords({otherUserId,pageNo,pageSize}) → {list:[{id,senderId,senderIcon,content,pushTime}]}
 *   - sendMessageToUser({userId,content})
 * 设计稿对齐说明：
 *   - 会话栏 320px + 搜索框（本地过滤，接口无搜索参数）
 *   - 消息区按日期插入分隔线
 *   - 输入区工具条：表情 / 代码为真实功能（写入输入框）；图片需上传接口，接口不存在 → 置灰并提示
 *   - 会话头「更多 / 视频」无对应能力 → 置灰并提示，不做假交互
 -->
<template>
  <div class="msg">
    <!-- 左：会话栏 -->
    <aside class="msg__rail">
      <div class="msg__railHead">
        <h2 class="msg__railTitle">会话</h2>
        <span class="msg__railCount" v-if="conversations.length">{{ conversations.length }} 个</span>
      </div>

      <div class="msg__search">
        <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
          <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input
          v-model.trim="convKeyword"
          type="search"
          placeholder="搜索联系人"
          aria-label="搜索联系人"
          maxlength="30"
        />
      </div>

      <nav class="msg__convList" aria-label="会话列表" v-loading="convLoading">
        <p v-if="!filteredConversations.length && !convLoading" class="msg__railEmpty">
          {{ convKeyword
            ? '没有匹配的联系人'
            : (isLogin ? '还没有会话，在课程笔记里回复同学即可发起对话' : '登录后查看你的会话') }}
        </p>
        <button
          v-for="c in filteredConversations"
          :key="c.id"
          class="conv"
          :class="{ 'is-active': c.otherUserId === currentUserId }"
          @click="selectConv(c)"
        >
          <span class="conv__avatarWrap">
            <img class="conv__avatar" :src="c.otherAvatar || defaultAvatar" alt="" @error="onAvatarError" />
            <i class="conv__unread" v-if="c.unReadCount > 0">{{ c.unReadCount > 99 ? '99+' : c.unReadCount }}</i>
          </span>
          <span class="conv__meta">
            <span class="conv__row">
              <span class="conv__name">{{ c.otherUsername || '同学' }}</span>
              <span class="conv__time" v-if="c.lastMessageTime">{{ shortTime(c.lastMessageTime) }}</span>
            </span>
            <span class="conv__last" v-if="c.lastMessage">{{ c.lastMessage }}</span>
          </span>
        </button>
      </nav>
    </aside>

    <!-- 右：聊天面板 -->
    <section class="msg__panel">
      <template v-if="currentUserId">
        <div class="msg__head">
          <img class="msg__headAvatar" :src="currentAvatar" alt="" @error="onAvatarError" />
          <div class="msg__headInfo">
            <div class="msg__headName">{{ currentName }}</div>
            <div class="msg__headSub">私信对话</div>
          </div>
          <div class="msg__headTools">
            <button class="iconBtn" type="button" disabled title="暂未开放" aria-label="更多">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="10" cy="4.6" r="1.5" fill="currentColor"/>
                <circle cx="10" cy="10" r="1.5" fill="currentColor"/>
                <circle cx="10" cy="15.4" r="1.5" fill="currentColor"/>
              </svg>
            </button>
            <button class="iconBtn" type="button" disabled title="暂未开放" aria-label="视频通话">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <rect x="2.6" y="5.4" width="10.4" height="9.2" rx="2.2" stroke="currentColor" stroke-width="1.5"/>
                <path d="M13 9.4l4-2.2v5.6l-4-2.2v-1.2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        </div>

        <div class="msg__body" ref="bodyRef" v-loading="msgLoading">
          <p class="msg__more" v-if="hasMoreMsgs && !msgLoading">
            <button class="msg__moreBtn" type="button" @click="loadMoreMsgs">加载更早的消息</button>
          </p>
          <p class="msg__empty" v-if="!messages.length && !msgLoading">还没有消息，发一条打个招呼吧</p>

          <template v-for="row in renderedMessages" :key="row.key">
            <div class="dayDivider" v-if="row.kind === 'date'">
              <span>{{ row.label }}</span>
            </div>
            <div class="bubble" v-else :class="{ 'bubble--me': String(row.data.senderId) === String(myId) }">
              <img class="bubble__avatar" :src="row.data.senderIcon || defaultAvatar" alt="" @error="onAvatarError" />
              <div class="bubble__wrap">
                <div class="bubble__content">{{ row.data.content }}</div>
                <div class="bubble__time">{{ row.data.pushTime }}</div>
              </div>
            </div>
          </template>
        </div>

        <div class="msg__composer">
          <div class="msg__tools">
            <button class="iconBtn" type="button" disabled title="图片上传暂未开放" aria-label="发送图片">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <rect x="2.8" y="3.8" width="14.4" height="12.4" rx="2.2" stroke="currentColor" stroke-width="1.5"/>
                <circle cx="7.4" cy="8.2" r="1.3" stroke="currentColor" stroke-width="1.3"/>
                <path d="M4 14.2l4-4 3 3 2.4-2.2 2.8 2.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <button
              class="iconBtn"
              :class="{ 'is-on': codeActive }"
              type="button"
              :title="codeActive ? '退出代码块' : '插入代码块'"
              aria-label="插入代码块"
              @click="toggleCode"
            >
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <path d="m7.6 6.6-3 3.4 3 3.4M12.4 6.6l3 3.4-3 3.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <button class="iconBtn" type="button" title="插入表情" aria-label="插入表情" @click="emojiOpen = !emojiOpen">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="10" cy="10" r="7.2" stroke="currentColor" stroke-width="1.5"/>
                <path d="M7.4 11.4c.7 1.2 1.6 1.8 2.6 1.8s1.9-.6 2.6-1.8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                <circle cx="7.6" cy="8.2" r="1" fill="currentColor"/>
                <circle cx="12.4" cy="8.2" r="1" fill="currentColor"/>
              </svg>
            </button>
            <div class="emojiPop" v-if="emojiOpen">
              <button v-for="e in EMOJIS" :key="e" type="button" class="emojiPop__item" @click="insertEmoji(e)">{{ e }}</button>
            </div>
          </div>

          <div class="msg__inputRow">
            <textarea
              ref="inputRef"
              v-model="draft"
              class="msg__input"
              rows="1"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="send"
              @click="emojiOpen = false"
            ></textarea>
            <button class="msg__send" type="button" :disabled="!draft.trim() || sending" @click="send">
              {{ sending ? '发送中…' : '发送' }}
            </button>
          </div>
        </div>
      </template>

      <div class="msg__placeholder" v-else>
        <div class="msg__placeholderIcon" aria-hidden="true">
          <svg viewBox="0 0 48 48" fill="none">
            <path d="M8 14a6 6 0 0 1 6-6h20a6 6 0 0 1 6 6v14a6 6 0 0 1-6 6H20l-8 7v-7h-4a6 6 0 0 1-6-6V14Z" transform="translate(6 2)" stroke="var(--sa)" stroke-width="2.4" stroke-linejoin="round"/>
          </svg>
        </div>
        <p class="msg__placeholderText">{{ isLogin ? '选择左侧一个会话开始聊天' : '登录后查看你的师生对话' }}</p>
        <button v-if="!isLogin" class="msg__loginBtn" type="button" @click="goLogin">立即登录</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { queryUserConversation, getMessageRecords, sendMessageToUser, openChatSocket } from '@/api/message.js';
import { useUserStore } from '@/store';
import { rememberStudentOrigin } from '@/config/loginRedirect';
import defaultAvatar from '@/assets/icon.jpeg';

import moment from 'moment';

const router = useRouter();
const store = useUserStore();

/** 会话时间：今天显示 HH:mm，否则显示 MM-DD */
const shortTime = (t) => (!t ? '' : (moment(t).isSame(moment(), 'day') ? moment(t).format('HH:mm') : moment(t).format('MM-DD')));

const isLogin = computed(() => Boolean(store.userInfo && store.userInfo.name));
const myId = computed(() => store.userInfo?.id);

const goLogin = () => { rememberStudentOrigin('/student/messages'); router.push('/login'); };

// ---------- 会话列表 ----------
const convLoading = ref(true);
const conversations = ref([]);
let socket = null;
const convKeyword = ref('');

/** 接口无搜索参数，按联系人名/最后一条消息本地过滤 */
const filteredConversations = computed(() => {
  const k = convKeyword.value.toLowerCase();
  if (!k) return conversations.value;
  return conversations.value.filter((c) =>
    String(c.otherUsername || '').toLowerCase().includes(k) ||
    String(c.lastMessage || '').toLowerCase().includes(k)
  );
});

async function loadConversations() {
  if (!isLogin.value) { convLoading.value = false; return; }
  convLoading.value = true;
  try {
    const res = await queryUserConversation({ pageNo: 1, pageSize: 50 });
    if (res?.code == 200 && res.data) {
      const data = Array.isArray(res.data) ? { list: res.data } : res.data;
      conversations.value = data.list || [];
    } else {
      conversations.value = [];
    }
  } catch (e) {
    conversations.value = [];
  } finally {
    convLoading.value = false;
  }
}

// ---------- 当前会话 ----------
const currentUserId = ref(null);
const currentName = computed(() => conversations.value.find((c) => c.otherUserId === currentUserId.value)?.otherUsername || '同学');
const currentAvatar = computed(() => conversations.value.find((c) => c.otherUserId === currentUserId.value)?.otherAvatar || defaultAvatar);

// ---------- 聊天记录 ----------
const msgLoading = ref(false);
const messages = ref([]);
const msgPageNo = ref(1);
const MSG_PAGE_SIZE = 20;
const hasMoreMsgs = ref(false);
const bodyRef = ref(null);
const inputRef = ref(null);

/**
 * 按日期插入分隔线（设计稿 Messages 区的 Date Divider）。
 * 用 pushTime 的前 10 位作为日期键，不依赖 moment 的本地化格式。
 */
const renderedMessages = computed(() => {
  const out = [];
  let lastDay = '';
  messages.value.forEach((m, i) => {
    const day = String(m.pushTime || '').slice(0, 10);
    if (day && day !== lastDay) {
      lastDay = day;
      out.push({ kind: 'date', key: `d-${day}-${i}`, label: day });
    }
    out.push({ kind: 'msg', key: `m-${m.id ?? i}`, data: m });
  });
  return out;
});

async function loadMessages(append = false) {
  if (!currentUserId.value) return;
  msgLoading.value = true;
  try {
    const res = await getMessageRecords({
      otherUserId: currentUserId.value,
      pageNo: msgPageNo.value,
      pageSize: MSG_PAGE_SIZE,
    });
    if (res?.code == 200 && res.data) {
      const list = res.data.list || [];
      const total = Number(res.data.total) || 0;
      // 接口按时间正序分页（pageNo 越大越早），加载更早 = 页码递增后 prepend
      messages.value = append ? [...list, ...messages.value] : list;
      hasMoreMsgs.value = msgPageNo.value * MSG_PAGE_SIZE < total;
    } else if (!append) {
      messages.value = [];
      hasMoreMsgs.value = false;
    }
  } catch (e) {
    if (!append) { messages.value = []; hasMoreMsgs.value = false; }
  } finally {
    msgLoading.value = false;
    scrollToBottom();
  }
}

function loadMoreMsgs() {
  msgPageNo.value += 1;
  loadMessages(true);
}

function selectConv(c) {
  if (currentUserId.value === c.otherUserId) return;
  currentUserId.value = c.otherUserId;
  messages.value = [];
  msgPageNo.value = 1;
  hasMoreMsgs.value = false;
  emojiOpen.value = false;
  codeActive.value = false;
  c.unReadCount = 0; // 本地清除未读角标
  loadMessages();
}

function scrollToBottom() {
  nextTick(() => {
    const el = bodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

// ---------- 输入区 ----------
const draft = ref('');
const sending = ref(false);
const emojiOpen = ref(false);
const EMOJIS = ['😀', '😄', '😊', '🙂', '😉', '😍', '👍', '👏', '🎉', '💪', '🤔', '😅'];

/** 在光标处插入文本并保持焦点 */
function insertAtCursor(text, cursorOffset) {
  const el = inputRef.value;
  if (!el) { draft.value += text; return; }
  const start = el.selectionStart ?? draft.value.length;
  const end = el.selectionEnd ?? draft.value.length;
  draft.value = draft.value.slice(0, start) + text + draft.value.slice(end);
  nextTick(() => {
    el.focus();
    const pos = start + (cursorOffset ?? text.length);
    el.setSelectionRange(pos, pos);
  });
}

function insertEmoji(e) {
  insertAtCursor(e);
  emojiOpen.value = false;
}

const CODE_FENCE = '```\n\n```';
const codeActive = ref(false);
/**
 * 插入 ⇄ 退出代码块（切换）。
 * ⚠️ 原来只会往草稿里塞一对围栏，用户点完就「回不去」了（用户实测反馈）。
 *    现在再点一次把围栏从草稿里摘掉，回到普通输入。
 */
function toggleCode() {
  if (!codeActive.value) {
    insertAtCursor(CODE_FENCE, 4);   // 光标落进围栏里，直接开始写
    codeActive.value = true;
    return;
  }
  const i = draft.value.indexOf(CODE_FENCE);
  if (i >= 0) {
    draft.value = draft.value.slice(0, i) + draft.value.slice(i + CODE_FENCE.length);
    nextTick(() => {
      const el = inputRef.value;
      if (el) { el.focus(); el.setSelectionRange(i, i); }
    });
  }
  // 围栏已被手动删掉/改掉 → 也视为退出，按钮回到「插入」态
  codeActive.value = false;
}

async function send() {
  const content = draft.value.trim();
  if (!content || sending.value || !currentUserId.value) return;
  sending.value = true;
  emojiOpen.value = false;
  try {
    const res = await sendMessageToUser({ userId: currentUserId.value, content });
    if (res?.code == 200) {
      draft.value = '';
      codeActive.value = false;
      // 发送成功后重拉第一页，保持与服务端一致（不本地伪造消息对象）
      msgPageNo.value = 1;
      await loadMessages();
    } else {
      ElMessage.error(res?.msg || '发送失败，请稍后再试');
    }
  } catch (e) {
    // 服务端中文原话直接给用户（如「消息太长了」「对方账号不存在」）
    const body = e?.response?.data;
    ElMessage.error((typeof body === 'string' && body.trim()) || e?.message || '发送失败，请稍后再试');
  } finally {
    sending.value = false;
  }
}

const onAvatarError = (e) => { e.target.src = defaultAvatar; };

onMounted(() => {
  loadConversations();
  // 实时收信（P22）：对方发消息 → 当前会话追加气泡 + 会话列表整体刷新（服务端排序/未读最准）
  openChatSocket(onIncomingChat).then((s) => { socket = s; });
});

onBeforeUnmount(() => {
  if (socket) { try { socket.close(); } catch (e) { /* 忽略 */ } socket = null; }
});

/** 收到私信推送（别人 → 我） */
function onIncomingChat(payload) {
  const from = String(payload.otherUserId);
  if (String(currentUserId.value) === from) {
    // 正在看这个会话：追加气泡
    messages.value.push(payload.message);
    scrollToBottom();
    // ⚠️ 正在看 = 已读：后端「拉取即清未读」，补一次**轻量拉取**（只取 1 条）清掉服务端未读，
    //    再刷列表让角标消失 —— 否则角标要等接收方回复才被清掉（用户实测反馈）。
    getMessageRecords({ otherUserId: currentUserId.value, pageNo: 1, pageSize: 1 })
      .then(() => loadConversations())
      .catch(() => {});
    return;
  }
  // 会话列表重拉：排序与未读以服务端为准，不在本地再算一遍
  loadConversations();
}
</script>

<style lang="scss" scoped>
.msg {
  display: flex;
  height: 100%;
  min-height: 0;
  background: var(--s-card);
  border-radius: var(--s-r-xl);
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055);
  overflow: hidden;
}

// ---- 左：会话栏（设计稿 320px）----
.msg__rail {
  flex: 0 0 320px;
  width: 320px;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  background: #fafafc;
  border-right: 1px solid var(--s-divider);
}
.msg__railHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 12px;
}
.msg__railTitle { margin: 0; font-size: 15px; font-weight: 600; color: var(--s-ink); }
.msg__railCount { font-size: 12px; color: var(--s-ink-3); }
.msg__railEmpty { margin: 0; padding: 20px 8px; font-size: 13px; line-height: 20px; color: var(--s-ink-3); }

.msg__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  margin: 0 4px 10px;
  padding: 0 12px;
  border-radius: 18px;
  background: #eff0f4;
  transition: box-shadow 0.16s ease, background-color 0.16s ease;

  &:focus-within { background: #fff; box-shadow: inset 0 0 0 1.5px var(--sa); }
  svg { flex: 0 0 14px; width: 14px; height: 14px; color: var(--s-ink-3); }
  input {
    flex: 1 1 auto;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    font-size: 13px;
    color: var(--s-ink);
    font-family: inherit;
    &::placeholder { color: var(--s-ink-3); }
  }
}

.msg__convList {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conv {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 68px;
  padding: 10px 8px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease;

  &:hover { background: #f0f1f5; }
  &.is-active { background: var(--sa-soft); }

  &__avatarWrap { position: relative; flex: 0 0 40px; }
  &__avatar {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    object-fit: cover;
    background: #d8dee8;
  }
  &__unread {
    position: absolute;
    top: -4px;
    right: -6px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 9px;
    background: #ea4335;
    color: #fff;
    font-size: 11px;
    line-height: 18px;
    text-align: center;
    font-style: normal;
  }
  &__meta { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  &__row { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
  &__time { flex: 0 0 auto; font-size: 11px; line-height: 15px; color: var(--s-ink-3); font-variant-numeric: tabular-nums; }
  &__name {
    font-size: 14px;
    font-weight: 500;
    color: var(--s-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__last {
    font-size: 12px;
    color: var(--s-ink-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// ---- 右：聊天面板 ----
.msg__panel {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.msg__head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--s-divider);
  flex: 0 0 auto;

  &Avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; background: #d8dee8; }
  &Info { flex: 1 1 auto; min-width: 0; }
  &Name { font-size: 15px; font-weight: 600; color: var(--s-ink); }
  &Sub { margin-top: 2px; font-size: 12px; color: var(--s-ink-3); }
  &Tools { display: flex; align-items: center; gap: 6px; }
}

.iconBtn {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--s-ink-2);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  svg { width: 20px; height: 20px; }
  &:hover:not(:disabled) { background: var(--s-canvas); color: var(--s-ink); }
  &:disabled { opacity: 0.38; cursor: not-allowed; }

  // 代码块处于「插入」态：高亮提示，再点一次退出
  &.is-on { background: rgba(0, 102, 204, 0.12); color: var(--sa); }
}

.msg__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg__more { margin: 0; text-align: center; }
.msg__moreBtn {
  border: 0;
  background: transparent;
  color: var(--sa);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  &:hover { background: var(--sa-soft); }
}
.msg__empty { margin: auto; font-size: 13px; color: var(--s-ink-3); }

// 日期分隔（设计稿 Date Divider）
.dayDivider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 6px 0;

  &::before,
  &::after {
    content: '';
    flex: 1 1 auto;
    height: 1px;
    background: var(--s-divider);
  }
  span {
    flex: 0 0 auto;
    font-size: 12px;
    color: var(--s-ink-3);
    font-variant-numeric: tabular-nums;
  }
}

.bubble {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: 72%;

  &--me {
    align-self: flex-end;
    flex-direction: row-reverse;
  }

  &__avatar {
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
    border-radius: 50%;
    object-fit: cover;
    background: #d8dee8;
  }
  &__wrap { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
  &__content {
    padding: 10px 14px;
    border-radius: 14px;
    background: #f2f3f7;
    color: var(--s-ink);
    font-size: 14px;
    line-height: 22px;
    word-break: break-word;
    white-space: pre-wrap;
  }
  &__time { font-size: 11px; color: var(--s-ink-3); font-variant-numeric: tabular-nums; }

  &--me .bubble__content { background: var(--sa); color: #fff; }
  &--me .bubble__time { text-align: right; }
}

// ---- 输入区 ----
.msg__composer {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--s-divider);
}

.msg__tools { display: flex; align-items: center; gap: 4px; }

.emojiPop {
  position: absolute;
  left: 20px;
  bottom: 100%;
  z-index: 5;
  display: grid;
  grid-template-columns: repeat(6, 36px);
  gap: 2px;
  padding: 8px;
  margin-bottom: 6px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(16, 24, 40, 0.16), inset 0 0 0 1px var(--s-divider);

  &__item {
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 10px;
    background: transparent;
    font-size: 18px;
    line-height: 1;
    cursor: pointer;
    &:hover { background: var(--s-canvas); }
  }
}

.msg__inputRow { display: flex; align-items: flex-end; gap: 12px; }

.msg__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 44px;
  max-height: 120px;
  padding: 10px 14px;
  border: 0;
  border-radius: 12px;
  background: var(--s-canvas);
  resize: none;
  outline: none;
  font-size: 14px;
  line-height: 22px;
  color: var(--s-ink);
  font-family: inherit;

  &:focus { box-shadow: inset 0 0 0 1.5px var(--sa); }
  &::placeholder { color: var(--s-ink-3); }
}

.msg__send {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 20px;
  border: 0;
  border-radius: 20px;
  background: var(--sa);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover:not(:disabled) { background: var(--sa-hover); }
  &:disabled { opacity: 0.5; cursor: default; }
}

// ---- 空态 ----
.msg__placeholder {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px;

  &Icon svg { width: 48px; height: 48px; }
  &Text { margin: 0; font-size: 13px; color: var(--s-ink-3); }
}
.msg__loginBtn {
  height: 38px;
  padding: 0 22px;
  border: 0;
  border-radius: 19px;
  background: var(--sa);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  &:hover { background: var(--sa-hover); }
}

@media (max-width: 1100px) {
  .msg__rail { flex: 0 0 280px; width: 280px; }
}
@media (max-width: 900px) {
  .msg__rail { flex: 0 0 220px; width: 220px; }
  .bubble { max-width: 88%; }
}
</style>
