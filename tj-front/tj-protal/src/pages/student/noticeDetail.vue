<!--
 * 公告详情（/student/notices/detail?id=...）— 对应设计稿 11
 * 数据来源：列表页通过 query 传入（title/content/pushTime/type/publisher），
 * 与老站 myExamDetails 的路由传参方式一致；已读标记在列表页点击时完成。
 * 设计稿的「上一篇 / 下一篇」：列表页把当前页数据写入 sessionStorage（studentNoticeList），
 * 详情页据此定位相邻条目 —— 公告接口没有「取相邻」的能力，用列表快照实现最稳妥。
 * 设计稿中的「Callout 高亮块」「相关附件」后端无对应字段，按约定不编造，此处不渲染。
 -->
<template>
  <div class="nd">
    <button class="back" type="button" @click="router.push('/student/notices')">
      <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path d="m10 4-4 4 4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      返回公告列表
    </button>

    <article class="s-card article">
      <div class="article__meta">
        <span class="article__tag" :class="isSystem ? '' : 'article__tag--note'">
          {{ isSystem ? '系统通知' : '笔记通知' }}
        </span>
        <span class="article__time">{{ info.pushTime }}</span>
      </div>

      <h1 class="article__title">{{ info.title || '公告详情' }}</h1>

      <p class="article__byline">{{ byline }}</p>

      <div class="article__divider"></div>

      <p class="article__content">{{ info.content || '暂无内容' }}</p>
    </article>

    <nav class="nav" aria-label="公告导航">
      <button
        class="navItem"
        type="button"
        :disabled="!neighbors.prev"
        :title="neighbors.prev ? neighbors.prev.title : '没有更早的公告了'"
        @click="goNeighbor(neighbors.prev)"
      >
        <span class="navItem__label">
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="m10 4-4 4 4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          上一篇
        </span>
        <span class="navItem__title">{{ neighbors.prev ? neighbors.prev.title || '未命名公告' : '已是第一篇' }}</span>
      </button>

      <button
        class="navItem navItem--next"
        type="button"
        :disabled="!neighbors.next"
        :title="neighbors.next ? neighbors.next.title : '没有更新的公告了'"
        @click="goNeighbor(neighbors.next)"
      >
        <span class="navItem__label">
          下一篇
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="m6 4 4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <span class="navItem__title">{{ neighbors.next ? neighbors.next.title || '未命名公告' : '已是最后一篇' }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { markMessageAsRead } from '@/api/message.js';

const route = useRoute();
const router = useRouter();

/** 列表页写入的当前页快照，用于定位「上一篇 / 下一篇」 */
const LIST_KEY = 'studentNoticeList';

const info = computed(() => ({
  id: route.query.id,
  title: route.query.title || '',
  content: route.query.content || '',
  pushTime: route.query.pushTime || '',
  type: route.query.type,
  publisher: route.query.publisher,
}));

const isSystem = computed(() => info.value.publisher == 0 || info.value.type == 0);

/**
 * 署名：接口没有「作者」字段，不编造人名。
 * 系统通知由平台发布，笔记通知由学习平台触发，两者都归「知序学堂」。
 */
const byline = computed(() => (isSystem.value ? '知序学堂 · 官方发布' : '知序学堂 · 学习通知'));

const neighbors = computed(() => {
  let list = [];
  try {
    const raw = sessionStorage.getItem(LIST_KEY);
    list = raw ? JSON.parse(raw) : [];
  } catch (e) {
    list = [];
  }
  if (!Array.isArray(list)) list = [];
  const idx = list.findIndex((n) => String(n?.id) === String(info.value.id));
  if (idx < 0) return { prev: null, next: null };
  return { prev: list[idx - 1] || null, next: list[idx + 1] || null };
});

async function goNeighbor(item) {
  if (!item) return;
  if (!item.isRead) {
    try {
      await markMessageAsRead(item.id);
      item.isRead = true;
    } catch (e) { /* 静默：已读状态不影响查看 */ }
  }
  // replace：避免在「上一篇/下一篇」之间来回翻时堆叠历史
  router.replace({
    path: '/student/notices/detail',
    query: {
      id: item.id,
      title: item.title || '',
      content: item.content || '',
      pushTime: item.pushTime || '',
      type: item.type ?? '',
      publisher: item.publisher ?? '',
    },
  });
}
</script>

<style lang="scss" scoped>
.nd {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  border: 0;
  background: transparent;
  padding: 6px 10px 6px 6px;
  border-radius: 10px;
  color: var(--s-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  svg { width: 16px; height: 16px; }
  &:hover { background: var(--s-canvas); color: var(--s-ink); }
}

.article {
  padding: 40px;

  &__meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }
  &__tag {
    padding: 3px 12px;
    border-radius: 999px;
    background: var(--sa-soft);
    color: var(--sa);
    font-size: 12px;
    font-weight: 500;

    &--note { background: #eefaf2; color: #34a853; }
  }
  &__time { font-size: 13px; color: var(--s-ink-3); }

  &__title {
    margin: 0 0 10px;
    font-size: 28px;
    line-height: 38px;
    font-weight: 700;
    letter-spacing: -0.2px;
    color: var(--s-ink);
  }

  &__byline {
    margin: 0 0 20px;
    font-size: 13px;
    line-height: 18px;
    color: var(--s-ink-3);
  }

  &__divider {
    height: 1px;
    margin-bottom: 20px;
    background: var(--s-divider);
  }

  &__content {
    margin: 0;
    font-size: 15px;
    line-height: 28px;
    color: var(--s-ink-2);
    word-break: break-word;
    white-space: pre-wrap;
  }
}

.nav {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.navItem {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 20px;
  text-align: left;
  border: 0;
  border-radius: 14px;
  background: var(--s-card);
  box-shadow: inset 0 0 0 1px var(--s-divider);
  cursor: pointer;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;

  &:hover:not(:disabled) {
    background: var(--s-canvas);
    box-shadow: inset 0 0 0 1px var(--sa);
  }
  &:disabled { cursor: not-allowed; opacity: 0.55; }

  &--next {
    align-items: flex-end;
    text-align: right;
  }

  &__label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--s-ink-3);

    svg { width: 14px; height: 14px; }
  }

  &__title {
    max-width: 100%;
    font-size: 14px;
    line-height: 20px;
    font-weight: 500;
    color: var(--s-ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
