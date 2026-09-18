<!--
 * 公告与新闻（/student/notices）
 * 左上角切换「公告 / 新闻」两个界面：
 *   公告 — 设计稿 07：置顶头条卡 + 筛选 chips + 列表卡（数据来自 queryUserInbox）
 *   新闻 — 复用老项目学术资讯实现（`src/config/news.json` 静态快照 200 条，
 *          老项目 pages/main/news.vue 亦是同一数据源），此处按新项目风格重排为
 *          「分类 chips + 左列表 + 右详情」
 * 数据契约：
 *   - queryUserInbox({pageNo,pageSize}) → {list:[{id,title,content,pushTime,isRead,publisher,type}],total}
 *   - markMessageAsRead(id) / markAllMessageAsRead()
 *   - getNewsList() / getNewsById(id) ← 前端静态 JSON（api/news.js）
 -->
<template>
  <div class="np">
    <!-- 左上角：公告 / 新闻 切换 -->
    <div class="np__tabs" role="tablist" aria-label="公告与新闻切换">
      <button
        v-for="t in TABS"
        :key="t.value"
        class="chip"
        :class="{ 'is-on': mode === t.value }"
        type="button"
        role="tab"
        :aria-selected="mode === t.value"
        @click="switchMode(t.value)"
      >{{ t.label }}</button>
    </div>

    <!-- ==================== 公告 ==================== -->
    <template v-if="mode === 'notice'">
      <!-- 置顶公告（最新未读，无未读取最新一条） -->
      <section
        class="featured"
        v-if="featured"
        role="button"
        tabindex="0"
        @click="goDetail(featured)"
        @keyup.enter="goDetail(featured)"
      >
        <div class="featured__tag">
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M2 6.5v3l2 .4V6.1L2 6.5Z" fill="currentColor"/>
            <path d="M4.5 6v4l6 3V3l-6 3Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
            <path d="M11 6.5a1.8 1.8 0 0 1 0 3M6 11.5l.8 2.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          {{ featured.isRead ? '最新公告' : '未读公告' }}
        </div>
        <h2 class="featured__title">{{ featured.title || '系统公告' }}</h2>
        <p class="featured__sub">{{ featured.content }}</p>
        <span class="featured__date">{{ featured.pushTime }}</span>
      </section>

      <div class="bar">
        <div class="chips" role="tablist" aria-label="公告筛选">
          <button
            v-for="c in noticeChips"
            :key="c.value"
            type="button"
            role="tab"
            :aria-selected="filter === c.value"
            class="chip"
            :class="{ 'is-on': filter === c.value }"
            @click="switchFilter(c.value)"
          >
            {{ c.label }}
            <span class="chip__count" v-if="c.count != null">{{ c.count }}</span>
          </button>
        </div>
        <button class="markAll" type="button" v-if="unreadCount > 0" @click="markAll">全部标为已读</button>
      </div>

      <section class="s-card list">
        <div v-loading="loading" class="list__rows">
          <div
            class="row"
            v-for="item in rows"
            :key="item.id"
            role="button"
            tabindex="0"
            @click="goDetail(item)"
            @keyup.enter="goDetail(item)"
          >
            <span class="row__date">{{ shortTime(item.pushTime) }}</span>
            <div class="row__col">
              <div class="row__title">
                {{ item.title || (item.type == 0 ? '系统通知' : '笔记通知') }}
                <span class="row__dot" v-if="!item.isRead" aria-label="未读"></span>
              </div>
              <p class="row__sub">{{ item.content }}</p>
            </div>
            <span class="row__tag" v-if="item.publisher == 0">系统</span>
            <svg class="row__chevron" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="m6 4 4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <p class="empty" v-if="!loading && rows.length === 0">暂无公告</p>
        </div>
        <div class="list__foot" v-if="total > params.pageSize">
          <SPagination v-model="params.pageNo" :total="total" :page-size="params.pageSize" @change="loadList" />
        </div>
      </section>
    </template>

    <!-- ==================== 新闻 ==================== -->
    <template v-else>
      <div class="bar">
        <div class="chips" role="tablist" aria-label="新闻分类">
          <button
            v-for="cat in newsCategories"
            :key="cat"
            type="button"
            role="tab"
            :aria-selected="newsCat === cat"
            class="chip"
            :class="{ 'is-on': newsCat === cat }"
            @click="switchCat(cat)"
          >{{ cat }}</button>
        </div>
        <span class="bar__hint" v-if="newsUpdatedAt">更新于 {{ newsUpdatedAt }}</span>
      </div>

      <div class="newsRow">
        <!-- 左：新闻列表 -->
        <section class="s-card newsList">
          <article
            class="nItem"
            v-for="n in newsPageItems"
            :key="n.id"
            :class="{ 'is-active': curNews && curNews.id === n.id }"
            tabindex="0"
            @click="selectNews(n)"
            @keyup.enter="selectNews(n)"
          >
            <div class="nItem__top">
              <span class="nTag">{{ n.category }}</span>
              <span class="nTag nTag--en" v-if="n.lang === 'en'">EN</span>
              <span class="nItem__date">{{ n.date }}</span>
            </div>
            <h3 class="nItem__title">{{ n.title }}</h3>
            <p class="nItem__sum">{{ n.summary }}</p>
            <span class="nItem__src">{{ n.source }}</span>
          </article>
          <p class="empty" v-if="newsPageItems.length === 0">该分类下暂无新闻</p>
          <div class="newsList__foot" v-if="newsTotalPages > 1">
            <SPagination v-model="newsPageNo" :total="newsFiltered.length" :page-size="NEWS_PAGE_SIZE" />
          </div>
        </section>

        <!-- 右：新闻详情 -->
        <section class="s-card newsDetail" v-if="curNews">
          <div class="newsDetail__meta">
            <span class="nTag">{{ curNews.category }}</span>
            <span class="newsDetail__src">{{ curNews.source }} · {{ curNews.date }}</span>
          </div>
          <h2 class="newsDetail__title">{{ curNews.title }}</h2>
          <div class="newsDetail__divider"></div>
          <p class="newsDetail__body">{{ curNews.body || curNews.summary }}</p>
          <a
            class="newsDetail__link"
            v-if="curNews.url"
            :href="curNews.url"
            target="_blank"
            rel="noopener noreferrer"
          >查看原文 ↗</a>
        </section>
        <section class="s-card newsDetail newsDetail--empty" v-else>
          <p class="empty">选择左侧一条新闻查看详情</p>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { queryUserInbox, markMessageAsRead, markAllMessageAsRead } from '@/api/message.js';
import { getNewsList } from '@/api/news.js';
import moment from 'moment';
import SPagination from '@/components/shell/SPagination.vue';

const router = useRouter();

const TABS = [
  { label: '公告', value: 'notice' },
  { label: '新闻', value: 'news' },
];
const mode = ref('notice');

// =============================================================================
// 公告
// =============================================================================
const loading = ref(true);
const list = ref([]);
const total = ref(0);
const params = reactive({ pageNo: 1, pageSize: 10 });
const filter = ref('all');

const noticeChips = computed(() => [
  { label: '全部', value: 'all' },
  { label: '系统通知', value: 'system' },
  { label: '笔记通知', value: 'note' },
  { label: '未读', value: 'unread', count: list.value.filter((m) => !m.isRead).length },
]);

const rows = computed(() => {
  let l = list.value;
  if (filter.value === 'system') l = l.filter((m) => m.type == 0);
  else if (filter.value === 'note') l = l.filter((m) => m.type != 0);
  else if (filter.value === 'unread') l = l.filter((m) => !m.isRead);
  return l;
});

const unreadCount = computed(() => list.value.filter((m) => !m.isRead).length);

const featured = computed(() => {
  const firstUnread = list.value.find((m) => !m.isRead);
  return firstUnread || list.value[0] || null;
});

const shortTime = (t) => (t ? moment(t).format('YYYY-MM-DD') : '--');

async function loadList() {
  loading.value = true;
  try {
    const res = await queryUserInbox({ pageNo: params.pageNo, pageSize: params.pageSize });
    if (res?.code == 200 && res.data) {
      const data = Array.isArray(res.data) ? { list: res.data, total: res.data.length } : res.data;
      list.value = data.list || [];
      total.value = Number(data.total) || list.value.length;
    } else {
      list.value = [];
      total.value = 0;
    }
  } catch (e) {
    list.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function switchFilter(v) {
  if (filter.value === v) return;
  filter.value = v;
}

async function goDetail(item) {
  // 进入详情时标记已读（未读才请求，避免多余调用）
  if (!item.isRead) {
    try {
      await markMessageAsRead(item.id);
      item.isRead = true;
    } catch (e) { /* 静默：已读状态不影响查看 */ }
  }
  saveListSnapshot();
  router.push({
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

/** 详情页据此定位相邻公告；同页多次点击只保留最后一次的快照 */
function saveListSnapshot() {
  try {
    const snap = rows.value.map((m) => ({
      id: m.id,
      title: m.title || '',
      content: m.content || '',
      pushTime: m.pushTime || '',
      type: m.type ?? '',
      publisher: m.publisher ?? '',
      isRead: !!m.isRead,
    }));
    sessionStorage.setItem('studentNoticeList', JSON.stringify(snap));
  } catch (e) { /* 隐私模式下 sessionStorage 可能不可写，静默降级 */ }
}

async function markAll() {
  try {
    const res = await markAllMessageAsRead();
    if (res?.code == 200) {
      ElMessage.success('已全部标为已读');
      loadList();
    } else {
      ElMessage.error(res?.msg || '操作失败，请稍后再试');
    }
  } catch (e) {
    ElMessage.error('操作失败，请稍后再试');
  }
}

// =============================================================================
// 新闻（复用老项目静态数据源）
// =============================================================================
const NEWS_PAGE_SIZE = 8;
const newsItems = ref([]);
const newsCategories = ref(['全部']);
const newsUpdatedAt = ref('');
const newsCat = ref('全部');
const newsPageNo = ref(1);
const curNews = ref(null);

const newsFiltered = computed(() => {
  if (newsCat.value === '全部') return newsItems.value;
  return newsItems.value.filter((n) => n.category === newsCat.value);
});
const newsTotalPages = computed(() => Math.ceil(newsFiltered.value.length / NEWS_PAGE_SIZE) || 1);
const newsPageItems = computed(() => {
  const start = (newsPageNo.value - 1) * NEWS_PAGE_SIZE;
  return newsFiltered.value.slice(start, start + NEWS_PAGE_SIZE);
});

function switchCat(cat) {
  if (newsCat.value === cat) return;
  newsCat.value = cat;
  newsPageNo.value = 1;
  curNews.value = newsFiltered.value[0] || null;
}

function selectNews(n) {
  curNews.value = n;
}

async function loadNews() {
  try {
    const res = await getNewsList();
    if (res?.code == 200 && res.data) {
      newsItems.value = res.data.items || [];
      newsUpdatedAt.value = res.data.updatedAt || '';
      if (Array.isArray(res.data.categories) && res.data.categories.length) {
        newsCategories.value = res.data.categories;
      }
      curNews.value = newsItems.value[0] || null;
    }
  } catch (e) { /* 静态数据源，异常时不编造内容 */ }
}

function switchMode(v) {
  mode.value = v;
}

onMounted(() => {
  loadList();
  loadNews();
});
</script>

<style lang="scss" scoped>
.np {
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__tabs {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

// =============================================================================
// 通用 chip（与筛选 chips 同一套语言：高 32、圆角 16，选中 #0066CC）
// =============================================================================
.chip {
  height: 32px;
  padding: 0 18px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 16px;
  background: #fff;
  box-shadow: inset 0 0 0 1px #ececf0;
  color: #333;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;

  &:hover { background: #f6f7f9; }
  &.is-on {
    background: #0066cc;
    box-shadow: inset 0 0 0 1px #0066cc;
    color: #fff;
    font-weight: 500;
  }

  &__count {
    font-size: 12px;
    line-height: 16px;
    opacity: 0.7;
    font-variant-numeric: tabular-nums;
  }
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  &__hint { font-size: 12px; line-height: 17px; color: #86868b; white-space: nowrap; }
}
.chips { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }

.markAll {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 16px;
  border: 0;
  border-radius: 16px;
  background: #e8f1fc;
  color: #0066cc;
  font-size: 12px;
  line-height: 17px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #dbe9fb; }
}

.empty {
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// =============================================================================
// 公告：置顶头条卡（176 高、圆角 20）
// =============================================================================
.featured {
  position: relative;
  min-height: 176px;
  padding: 32px 36px;
  border-radius: 20px;
  background: linear-gradient(135deg, #0a1a30 0%, #1a5092 100%);
  color: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(10, 26, 48, 0.25);
  }

  &__tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 26px;
    padding: 0 14px;
    border-radius: 13px;
    background: rgba(255, 255, 255, 0.2);
    font-size: 12px;
    line-height: 18px;
    font-weight: 500;
    white-space: nowrap;

    svg { width: 14px; height: 14px; }
  }
  &__title {
    margin: 14px 0 6px;
    max-width: 700px;
    font-size: 24px;
    line-height: 32px;
    font-weight: 600;
    letter-spacing: -0.2px;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__sub {
    margin: 0;
    max-width: 700px;
    font-size: 14px;
    line-height: 22px;
    color: rgba(255, 255, 255, 0.77);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__date {
    margin-top: auto;
    font-size: 12px;
    line-height: 17px;
    color: rgba(255, 255, 255, 0.7);
    font-variant-numeric: tabular-nums;
  }
}

// =============================================================================
// 公告列表卡（内边距 8、行高 72）
// =============================================================================
.list {
  padding: 8px;

  &__rows { min-height: 120px; }
  &__foot {
    display: flex;
    justify-content: flex-end;
    padding: 12px 16px;
    border-top: 1px solid var(--s-divider);
  }
}

.row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 72px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover { background: var(--s-canvas); }

  &__date {
    flex: 0 0 88px;
    width: 88px;
    font-size: 12px;
    line-height: 17px;
    color: var(--s-ink-3);
    font-variant-numeric: tabular-nums;
  }
  &__col { flex: 1 1 auto; min-width: 0; }
  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: var(--s-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__dot {
    flex: 0 0 6px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #ea4335;
  }
  &__sub {
    margin: 3px 0 0;
    font-size: 12px;
    line-height: 17px;
    color: var(--s-ink-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__tag {
    flex: 0 0 auto;
    padding: 2px 8px;
    border-radius: 6px;
    background: var(--s-soft, #f5f5f7);
    font-size: 11px;
    line-height: 15px;
    color: var(--s-ink-3);
  }
  &__chevron { flex: 0 0 16px; width: 16px; height: 16px; color: var(--s-ink-3); }
}

// =============================================================================
// 新闻：左列表 415 + 间距 32 + 右详情 649（与「学习记录」同一版式）
// =============================================================================
.newsRow {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.newsList {
  flex: 0 0 415px;
  width: 415px;
  padding: 8px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;

  &__foot {
    display: flex;
    justify-content: flex-end;
    padding: 12px 12px 8px;
    border-top: 1px solid var(--s-divider);
  }
}

.nItem {
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover { background: var(--s-canvas); }
  &.is-active { background: #e8f1fc; }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: -2px; }

  &__top {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
  }
  &__date {
    margin-left: auto;
    font-size: 11px;
    line-height: 15px;
    color: var(--s-ink-3);
    font-variant-numeric: tabular-nums;
  }
  &__title {
    margin: 0 0 6px;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: var(--s-ink);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__sum {
    margin: 0 0 8px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2, #6e6e73);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__src { font-size: 11px; line-height: 15px; color: var(--s-ink-3); }
}

.nTag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 6px;
  background: #e8f1fc;
  color: #0066cc;
  font-size: 11px;
  line-height: 15px;
  font-weight: 500;
  white-space: nowrap;

  &--en { background: #f5f5f7; color: #86868b; }
}

.newsDetail {
  flex: 1 1 auto;
  min-width: 0;
  padding: 32px;
  border-radius: 18px;

  &--empty { display: flex; align-items: center; justify-content: center; min-height: 240px; }

  &__meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }
  &__src { font-size: 12px; line-height: 17px; color: var(--s-ink-3); }
  &__title {
    margin: 0;
    font-size: 22px;
    line-height: 32px;
    font-weight: 600;
    letter-spacing: -0.2px;
    color: var(--s-ink);
  }
  &__divider {
    height: 1px;
    margin: 18px 0;
    background: #f0f0f0;
  }
  &__body {
    margin: 0;
    font-size: 15px;
    line-height: 28px;
    color: var(--s-ink-2, #6e6e73);
    white-space: pre-wrap;
    word-break: break-word;
  }
  &__link {
    display: inline-block;
    margin-top: 20px;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #0066cc;
    text-decoration: none;

    &:hover { text-decoration: underline; }
  }
}

// =============================================================================
// 窄屏
// =============================================================================
@media (max-width: 1180px) {
  .newsRow { flex-direction: column; }
  .newsList { flex: 0 0 auto; width: 100%; }
}
</style>
