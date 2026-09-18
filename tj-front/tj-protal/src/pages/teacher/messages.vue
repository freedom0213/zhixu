<!--
 * 教师端 · 师生对话（设计稿 T6）
 * -----------------------------------------------------------------------------
 * 与学生端 /student/messages 同一套接口与 WS（api/message.js），两端互为收发。
 * 页面刻意做成**教师壳的同构页**而不是抽公共组件：
 *   · 学生端那页深耦合学员外壳的登录引导（rememberStudentOrigin / 跳 /login）；
 *   · 教师端多了「＋ 新对话」（按账号解析任意用户）—— 学生端发起路径走「课程页联系讲师」，后续再说。
 *
 * 铁律沿用聚合页：未读 / 排序**以服务端为准**（收到推送就重拉会话列表），前端不自己算第二份。
-->
<template>
  <div class="tmsg">
    <!-- 左：会话栏 -->
    <aside class="tmsg__rail s-card">
      <div class="tmsg__railHead">
        <h3 class="tmsg__railTitle">师生对话</h3>
        <button class="q-btn" type="button" @click="newDialog = true">＋ 新对话</button>
      </div>
      <label class="tmsg__search">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
          <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input v-model.trim="convKeyword" type="search" placeholder="搜索联系人或消息" aria-label="搜索会话" />
      </label>

      <p v-if="convLoading" class="tmsg__railEmpty">加载中…</p>
      <p v-else-if="!filteredConversations.length" class="tmsg__railEmpty">
        还没有会话。点右上角「＋ 新对话」，输入对方的平台账号发起。
      </p>
      <nav v-else class="tmsg__convList">
        <button
          v-for="c in filteredConversations"
          :key="c.id"
          class="tconv"
          :class="{ 'is-active': String(c.otherUserId) === String(currentUserId) }"
          type="button"
          @click="selectConv(c)"
        >
          <span class="tconv__avatarWrap">
            <img class="tconv__avatar" :src="c.otherAvatar || defaultAvatar" alt="" @error="onAvatarError" />
            <i class="tconv__unread" v-if="c.unReadCount > 0">{{ c.unReadCount > 99 ? '99+' : c.unReadCount }}</i>
          </span>
          <span class="tconv__meta">
            <span class="tconv__row">
              <span class="tconv__name">{{ c.otherUsername || '用户' }}</span>
              <span class="tconv__time" v-if="c.lastMessageTime">{{ shortTime(c.lastMessageTime) }}</span>
            </span>
            <span class="tconv__last" v-if="c.lastMessage">{{ c.lastMessage }}</span>
          </span>
        </button>
      </nav>
    </aside>

    <!-- 右：聊天面板 -->
    <section class="tmsg__panel s-card">
      <template v-if="currentUserId">
        <div class="tmsg__head">
          <img class="tmsg__headAvatar" :src="currentAvatar" alt="" @error="onAvatarError" />
          <div>
            <div class="tmsg__headName">{{ currentName }}</div>
            <div class="tmsg__headSub">私信对话</div>
          </div>
        </div>

        <div class="tmsg__body" ref="bodyRef">
          <p class="tmsg__more" v-if="hasMoreMsgs && !msgLoading">
            <button class="tmsg__moreBtn" type="button" @click="loadMoreMsgs">加载更早的消息</button>
          </p>
          <p class="tmsg__empty" v-if="!messages.length && !msgLoading">还没有消息，发一条打个招呼吧</p>

          <template v-for="row in renderedMessages" :key="row.key">
            <div class="tmsg__day" v-if="row.kind === 'date'"><span>{{ row.label }}</span></div>
            <div class="tbubble" v-else :class="{ 'tbubble--me': String(row.data.senderId) === String(myId) }">
              <img class="tbubble__avatar" :src="row.data.senderIcon || defaultAvatar" alt="" @error="onAvatarError" />
              <div class="tbubble__wrap">
                <div class="tbubble__content">{{ row.data.content }}</div>
                <div class="tbubble__time">{{ row.data.pushTime }}</div>
              </div>
            </div>
          </template>
        </div>

        <div class="tmsg__composer">
          <div class="tmsg__tools">
            <button class="iconBtn" type="button" disabled title="图片上传暂未开放" aria-label="发送图片">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <rect x="2.8" y="3.8" width="14.4" height="12.4" rx="2.2" stroke="currentColor" stroke-width="1.5"/>
                <circle cx="7.4" cy="8.2" r="1.3" stroke="currentColor" stroke-width="1.3"/>
                <path d="M4 14.2l4-4 3 3 2.4-2.2 2.8 2.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <button class="iconBtn" type="button" :title="emojiOpen ? '收起表情' : '表情'" aria-label="表情" @click="emojiOpen = !emojiOpen">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <circle cx="10" cy="10" r="7.4" stroke="currentColor" stroke-width="1.5"/>
                <circle cx="7.4" cy="8.4" r="1.1" fill="currentColor"/>
                <circle cx="12.6" cy="8.4" r="1.1" fill="currentColor"/>
                <path d="M6.8 12.2c.9 1 2 1.5 3.2 1.5s2.3-.5 3.2-1.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
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
                <path d="m7 6-3.6 4L7 14M13 6l3.6 4L13 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
          <div class="tmsg__emoji" v-if="emojiOpen">
            <button v-for="e in EMOJIS" :key="e" type="button" class="tmsg__emojiItem" @click="insertEmoji(e)">{{ e }}</button>
          </div>
          <div class="tmsg__inputRow">
            <textarea
              ref="inputRef"
              v-model="draft"
              class="tmsg__input"
              rows="2"
              placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <button class="q-btn q-btn--primary" type="button" :disabled="sending || !draft.trim()" @click="send">
              {{ sending ? '发送中…' : '发送' }}
            </button>
          </div>
        </div>
      </template>

      <div class="tmsg__placeholder" v-else>
        <p>从左侧选择一个会话，或点「＋ 新对话」输入对方账号发起。</p>
      </div>
    </section>

    <!-- 新对话：按账号解析（任意用户：学生 / 讲师都可以） -->
    <div class="tmsg__mask" v-if="newDialog" @click.self="newDialog = false">
      <div class="tmsg__dialog s-card">
        <h3 class="tmsg__dialogTitle">发起新对话</h3>
        <p class="tmsg__dialogHint">输入对方在平台上的账号（用户名或手机号）。会话在第一条消息发出后才真正建立。</p>
        <input
          v-model.trim="newAccount"
          class="tmsg__dialogInput"
          type="text"
          placeholder="例如：demo"
          aria-label="对方账号"
          @keydown.enter="startNewConversation"
        />
        <p class="tmsg__dialogErr" v-if="newDialogErr">{{ newDialogErr }}</p>
        <div class="tmsg__dialogActions">
          <button class="q-btn" type="button" @click="newDialog = false">取消</button>
          <button class="q-btn q-btn--primary" type="button" :disabled="lookingUp || !newAccount" @click="startNewConversation">
            {{ lookingUp ? '查找中…' : '开始对话' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  queryUserConversation,
  getMessageRecords,
  sendMessageToUser,
  openChatSocket,
} from '@/api/message.js';
import { lookupChatUser } from '@/api/message.js';
import defaultAvatar from '@/assets/icon.jpeg';
import moment from 'moment';

// ---------- 会话列表 ----------
const convLoading = ref(true);
const conversations = ref([]);
const convKeyword = ref('');
let socket = null;

const shortTime = (t) => (!t ? '' : (moment(t).isSame(moment(), 'day') ? moment(t).format('HH:mm') : moment(t).format('MM-DD')));

const filteredConversations = computed(() => {
  const k = convKeyword.value.toLowerCase();
  if (!k) return conversations.value;
  return conversations.value.filter((c) =>
    String(c.otherUsername || '').toLowerCase().includes(k) ||
    String(c.lastMessage || '').toLowerCase().includes(k)
  );
});

async function loadConversations() {
  convLoading.value = true;
  try {
    const res = await queryUserConversation({ pageNo: 1, pageSize: 50 });
    const data = res?.code == 200 ? (Array.isArray(res.data) ? { list: res.data } : res.data) : null;
    conversations.value = data?.list || [];
  } catch (e) {
    conversations.value = [];
  } finally {
    convLoading.value = false;
  }
}

// ---------- 当前会话 ----------
const currentUserId = ref(null);
const currentName = computed(() => conversations.value.find((c) => String(c.otherUserId) === String(currentUserId.value))?.otherUsername || '用户');
const currentAvatar = computed(() => conversations.value.find((c) => String(c.otherUserId) === String(currentUserId.value))?.otherAvatar || defaultAvatar);
const myId = computed(() => {
  try { return JSON.parse(sessionStorage.getItem('userInfo') || '{}').id; } catch (e) { return null; }
});

// ---------- 聊天记录 ----------
const msgLoading = ref(false);
const messages = ref([]);
const msgPageNo = ref(1);
const MSG_PAGE_SIZE = 20;
const hasMoreMsgs = ref(false);
const bodyRef = ref(null);
const inputRef = ref(null);

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
      // 第 1 页最新、页内正序；加载更早 = 页码递增后 prepend
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
  if (String(currentUserId.value) === String(c.otherUserId)) return;
  currentUserId.value = c.otherUserId;
  messages.value = [];
  msgPageNo.value = 1;
  hasMoreMsgs.value = false;
  emojiOpen.value = false;
  codeActive.value = false;
  c.unReadCount = 0;
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

function insertAtCursor(text, cursorOffset) {
  const el = inputRef.value;
  if (!el) { draft.value += text; return; }
  const start = el.selectionStart ?? draft.value.length;
  const end = el.selectionEnd ?? draft.value.length;
  draft.value = draft.value.slice(0, start) + text + draft.value.slice(end);
  nextTick(() => {
    const pos = start + (cursorOffset ?? text.length);
    el.setSelectionRange(pos, pos);
  });
}
function insertEmoji(e) { insertAtCursor(e); emojiOpen.value = false; }

const CODE_FENCE = '```\n\n```';
const codeActive = ref(false);
/**
 * 插入 ⇄ 退出代码块（切换）。
 * ⚠️ 原来只会往草稿里塞一对围栏，用户点完就「回不去」了 —— 草稿里留着 ```，
 *    看起来像被锁进了代码模式（用户实测反馈）。现在再点一次把围栏摘掉，回到普通输入。
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
      msgPageNo.value = 1;
      await loadMessages();
      await loadConversations();
    } else {
      ElMessage.error(res?.msg || '发送失败，请稍后再试');
    }
  } catch (e) {
    const body = e?.response?.data;
    ElMessage.error((typeof body === 'string' && body.trim()) || e?.message || '发送失败，请稍后再试');
  } finally {
    sending.value = false;
  }
}

// ---------- 新对话 ----------
const newDialog = ref(false);
const newAccount = ref('');
const newDialogErr = ref('');
const lookingUp = ref(false);

async function startNewConversation() {
  if (lookingUp.value || !newAccount.value) return;
  lookingUp.value = true;
  newDialogErr.value = '';
  try {
    // ⚠️ 网关会把 user-service 的裸 JSON 包成 R（{code,msg,data}）→ 真身在 data 里。
    //    之前直接读 u.id 永远是 undefined → 输对账号也报「没找到」（用户实测踩中）。
    const res = await lookupChatUser(newAccount.value);
    const u = res?.data ?? res;
    if (!u?.id) throw new Error(res?.msg || '没找到这个账号');
    const mine = conversations.value.find((c) => String(c.otherUserId) === String(u.id));
    if (mine) {
      selectConv(mine);          // 已有会话 → 直接选中
    } else {
      // 会话在第一条消息发出后才落库；这里先在本地挂一个空会话占位
      conversations.value.unshift({
        id: `new-${u.id}`,
        otherUserId: u.id,
        otherUsername: u.name || u.username,
        otherAvatar: u.icon || null,
        lastMessage: '',
        lastMessageTime: null,
        unReadCount: 0,
      });
      selectConv({ otherUserId: u.id });
    }
    newDialog.value = false;
    newAccount.value = '';
  } catch (e) {
    newDialogErr.value = e?.message || '没找到这个账号';
  } finally {
    lookingUp.value = false;
  }
}

const onAvatarError = (e) => { e.target.src = defaultAvatar; };

// ---------- 实时收信 ----------
function onIncomingChat(payload) {
  if (String(payload.otherUserId) === String(currentUserId.value)) {
    messages.value.push(payload.message);
    scrollToBottom();
    // ⚠️ 正在看这个会话 = 已读。后端「拉取即清未读」，所以补一次**轻量拉取**（只取 1 条）
    //    把服务端未读清掉，再刷列表让角标消失 —— 否则角标会一直挂着，
    //    直到接收方回复触发 send 后的重拉才清（用户实测反馈）。
    getMessageRecords({ otherUserId: currentUserId.value, pageNo: 1, pageSize: 1 })
      .then(() => loadConversations())
      .catch(() => {});
    return;
  }
  loadConversations();
}

onMounted(() => {
  loadConversations();
  openChatSocket(onIncomingChat).then((s) => { socket = s; });
});

onBeforeUnmount(() => {
  if (socket) { try { socket.close(); } catch (e) { /* 忽略 */ } socket = null; }
});
</script>

<style lang="scss" scoped>
// ⚠️ 类名全带 tmsg-/tconv-/tbubble- 前缀，避免与外壳（.main/.body）撞名
.tmsg {
  display: flex;
  height: calc(100vh - 64px - 80px); // 顶栏 64 + .body 上下 padding 40×2
  min-height: 480px;
  gap: 16px;
  max-width: 1096px;
  margin: 0 auto;
}

// ---- 左：会话栏 ----
.tmsg__rail {
  flex: 0 0 320px;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  background: #fafafc;
  overflow: hidden;
}
.tmsg__railHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 12px;
}
.tmsg__railTitle { margin: 0; font-size: 15px; font-weight: 600; color: var(--s-ink); }
.tmsg__railEmpty { margin: 0; padding: 20px 8px; font-size: 13px; line-height: 20px; color: var(--s-ink-3); }

.tmsg__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  margin: 0 4px 10px;
  padding: 0 12px;
  border-radius: 18px;
  background: #eff0f4;

  &:focus-within { background: #fff; box-shadow: inset 0 0 0 1.5px var(--sa); }
  svg { flex: 0 0 14px; width: 14px; height: 14px; color: var(--s-ink-3); }
  input {
    flex: 1 1 auto; min-width: 0; border: 0; outline: 0; background: transparent;
    font-size: 13px; color: var(--s-ink); font-family: inherit;
    &::placeholder { color: var(--s-ink-3); }
  }
}

.tmsg__convList {
  flex: 1 1 auto; min-height: 0; overflow-y: auto;
  display: flex; flex-direction: column; gap: 2px;
}

.tconv {
  display: flex; align-items: center; gap: 10px;
  width: 100%; min-height: 64px; padding: 10px 8px;
  border: 0; border-radius: 12px; background: transparent;
  cursor: pointer; text-align: left;
  transition: background-color 0.15s ease;

  &:hover { background: #f0f1f5; }
  &.is-active { background: rgba(0, 102, 204, 0.08); }

  &__avatarWrap { position: relative; flex: 0 0 40px; }
  &__avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; background: #eef0f4; }
  &__unread {
    position: absolute; top: -4px; right: -6px;
    min-width: 17px; height: 17px; padding: 0 4px;
    border-radius: 9px; background: #e5484d; color: #fff;
    font-size: 10px; font-style: normal; line-height: 17px; text-align: center;
  }
  &__meta { flex: 1 1 auto; min-width: 0; }
  &__row { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
  &__name {
    font-size: 13px; font-weight: 500; color: var(--s-ink);
    overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
  }
  &__time { flex: 0 0 auto; font-size: 11px; color: var(--s-ink-3); }
  &__last {
    display: block; margin-top: 2px; font-size: 12px; line-height: 17px; color: var(--s-ink-2);
    overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
  }
}

// ---- 右：聊天面板 ----
.tmsg__panel {
  flex: 1 1 auto; min-width: 0;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.tmsg__head {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--s-divider);
}
.tmsg__headAvatar { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; background: #eef0f4; }
.tmsg__headName { font-size: 14px; font-weight: 600; color: var(--s-ink); }
.tmsg__headSub { font-size: 12px; color: var(--s-ink-3); }

.tmsg__body {
  flex: 1 1 auto; min-height: 0;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex; flex-direction: column; gap: 12px;
}
.tmsg__more { margin: 0; text-align: center; }
.tmsg__moreBtn {
  border: 0; background: transparent; cursor: pointer;
  font-size: 12px; color: var(--sa); font-family: inherit;
}
.tmsg__empty { margin: auto; font-size: 13px; color: var(--s-ink-3); }
.tmsg__day {
  align-self: center;
  font-size: 11px; color: var(--s-ink-3);
  background: #f2f3f7; border-radius: 10px; padding: 2px 10px;
}
.tbubble {
  display: flex; gap: 10px; max-width: 78%;

  &__avatar { flex: 0 0 32px; width: 32px; height: 32px; border-radius: 50%; object-fit: cover; background: #eef0f4; }
  &__wrap { min-width: 0; }
  &__content {
    display: inline-block;
    padding: 9px 12px;
    border-radius: 4px 14px 14px 14px;
    background: #f2f3f7;
    font-size: 13px; line-height: 20px; color: var(--s-ink);
    white-space: pre-wrap; word-break: break-word;
  }
  &__time { margin-top: 4px; font-size: 11px; color: var(--s-ink-3); }

  &--me {
    align-self: flex-end; flex-direction: row-reverse;
    .tbubble__content { background: var(--sa, #0066cc); color: #fff; border-radius: 14px 4px 14px 14px; }
    .tbubble__time { text-align: right; }
  }
}

.tmsg__composer { border-top: 1px solid var(--s-divider); padding: 10px 20px 14px; }
.tmsg__tools { display: flex; gap: 6px; }
.iconBtn {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 0; border-radius: 8px; background: transparent;
  color: var(--s-ink-2); cursor: pointer;
  svg { width: 18px; height: 18px; }
  &:hover:not(:disabled) { background: #f0f1f5; color: var(--s-ink); }
  &:disabled { opacity: 0.45; cursor: not-allowed; }

  // 代码块处于「插入」态：高亮提示，再点一次退出
  &.is-on { background: rgba(0, 102, 204, 0.12); color: var(--sa); }
}
.tmsg__emoji {
  display: flex; flex-wrap: wrap; gap: 4px;
  margin-top: 8px; padding: 8px;
  border: 1px solid var(--s-divider); border-radius: 10px; background: #fafafc;
}
.tmsg__emojiItem {
  border: 0; background: transparent; cursor: pointer;
  font-size: 18px; line-height: 26px; padding: 0 4px; border-radius: 6px;
  &:hover { background: #f0f1f5; }
}
.tmsg__inputRow { display: flex; align-items: flex-end; gap: 10px; margin-top: 8px; }
.tmsg__input {
  flex: 1 1 auto; min-height: 44px; max-height: 120px;
  padding: 10px 12px;
  border: 1px solid var(--s-divider); border-radius: 12px;
  outline: none; resize: none;
  font-size: 13px; line-height: 20px; color: var(--s-ink); font-family: inherit;
  &:focus { border-color: var(--sa); }
}

.tmsg__placeholder {
  margin: auto; text-align: center;
  font-size: 13px; color: var(--s-ink-3);
  p { margin: 0; }
}

// ---- 新对话弹层 ----
.tmsg__mask {
  position: fixed; inset: 0; z-index: 60;
  background: rgba(16, 24, 40, 0.4);
  display: flex; align-items: center; justify-content: center;
}
.tmsg__dialog { width: 380px; padding: 20px 22px; }
.tmsg__dialogTitle { margin: 0; font-size: 16px; font-weight: 600; color: var(--s-ink); }
.tmsg__dialogHint { margin: 8px 0 12px; font-size: 12px; line-height: 18px; color: var(--s-ink-2); }
.tmsg__dialogInput {
  width: 100%; height: 38px; padding: 0 12px;
  border: 1px solid var(--s-divider); border-radius: 10px;
  outline: none; font-size: 13px; color: var(--s-ink);
  &:focus { border-color: var(--sa); }
}
.tmsg__dialogErr { margin: 8px 0 0; font-size: 12px; color: #c0392b; }
.tmsg__dialogActions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
</style>
