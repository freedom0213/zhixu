<!--
 * 课程中心（/student/courses）— 严格对齐设计稿 02
 * 设计稿规格（Ardot fileId 725009394574981，frame「02 课程中心」）：
 *   Content 1096，纵向间距 32
 *   ① 推荐课程：Section Head(21/28 + 链接 13/18) + Banner Carousel 1096×200 圆角 20
 *      （chip 76×26 圆角13；标题 28/36；描述 14/22；按钮 104×38 圆角19；箭头 40×40；圆点 8×8）
 *   ② Bottom Row 间距 32：我的学习 328（卡圆角18/内边距20/间距16，行 缩略图40×28+文字）
 *      —— 2026-09-16 起按「我的课程」呈现：点一行去课程详情页（原先是直接进视频页）
 *                        ＋ 课程中心 736（卡圆角18/内边距24/间距20，搜索行44高、栅格间距16、分页 32×32）
 *   ③ 分类筛选浮层 688×自适应，圆角 16、内边距 16、间距 12，chip 高 32 圆角 16
 * 数据契约：classSeach / getClassCategorys(admin=true) / getRecommendClassList('best') / getMylessons
 * 说明：筛选浮层宽度 = 课程卡内宽 688（736 − 24×2），与设计稿一致地覆盖在课程栅格之上。
 -->
<template>
  <div class="cc">
    <!-- ① 推荐课程 + Banner 轮播 -->
    <section class="sec">
      <header class="sec__head">
        <h2 class="sec__title">推荐课程</h2>
        <a class="sec__link" href="javascript:;" @click="goSearchPage">更多</a>
      </header>

      <div class="banner" v-if="bannerList.length">
        <span class="banner__chip">推荐</span>
        <h3 class="banner__title">{{ plain(cur.name) }}</h3>
        <p class="banner__desc">{{ bannerDesc }}</p>
        <button class="banner__btn" type="button" @click="goCourse(cur.id)">立即学习</button>

        <div class="banner__arrows" v-if="bannerList.length > 1">
          <button class="arrow" type="button" aria-label="上一张" @click="stepBanner(-1)">
            <svg viewBox="0 0 12 12" fill="none" aria-hidden="true">
              <path d="m7.5 1.5 -4.5 4.5 4.5 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button class="arrow" type="button" aria-label="下一张" @click="stepBanner(1)">
            <svg viewBox="0 0 12 12" fill="none" aria-hidden="true">
              <path d="m4.5 1.5 4.5 4.5 -4.5 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>

        <div class="banner__dots" v-if="bannerList.length > 1">
          <button
            v-for="(b, i) in bannerList"
            :key="b.id"
            class="dot"
            :class="{ 'is-on': i === bannerIndex }"
            type="button"
            :aria-label="`第 ${i + 1} 张`"
            :aria-current="i === bannerIndex"
            @click="bannerIndex = i"
          ></button>
        </div>
      </div>
    </section>

    <!-- ② 我的课程 + 课程中心 -->
    <div class="row">
      <aside class="s-card myLearn">
        <div class="myLearn__head">
          <h2 class="cardTitle">我的课程</h2>
          <router-link class="cardLink" to="/student/records">学习记录</router-link>
        </div>
        <p class="empty" v-if="!isLogin">登录后同步你的课程</p>
        <p class="empty" v-else-if="myLoaded && myList.length === 0">还没有加入任何课程，去右侧挑一门吧</p>
        <div class="myLearn__list" v-else>
          <div
            class="lesson"
            v-for="item in myList"
            :key="item.courseId"
            tabindex="0"
            @click="goCourse(item.courseId)"
            @keyup.enter="goCourse(item.courseId)"
          >
            <div class="lesson__thumb">
              <img v-if="item.courseCoverUrl" class="lesson__img" :src="item.courseCoverUrl" alt="" @error="onImgError" />
            </div>
            <div class="lesson__col">
              <div class="lesson__row">
                <span class="lesson__name" :title="plain(item.courseName)">{{ plain(item.courseName) }}</span>
                <span class="lesson__pct" v-if="item.sections">{{ pct(item) }}%</span>
              </div>
              <span class="lesson__sub">已学 {{ item.learnedSections || 0 }}/{{ item.sections || 0 }} 节</span>
            </div>
          </div>
        </div>
      </aside>

      <section class="s-card pool">
        <!-- 搜索行 -->
        <div class="pool__bar">
          <form class="pool__search" @submit.prevent="applySearch">
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
              <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <input v-model.trim="keyword" type="search" placeholder="搜索课程标题、简介或老师" aria-label="搜索课程" maxlength="50" />
          </form>
          <button class="pool__go" type="button" @click="applySearch">搜索</button>
          <button class="pool__filter" :class="{ 'is-open': panelOpen }" type="button" aria-expanded="panelOpen" @click="togglePanel">
            筛选
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M2.4 4h11.2M4.8 8h6.4M6.8 12h2.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <!-- 栅格 + 悬浮筛选面板 -->
        <div class="pool__stage" ref="stageRef">
          <div class="pool__grid" v-loading="loading">
            <article
              class="kcard"
              v-for="c in courses"
              :key="c.id"
              tabindex="0"
              @click="goCourse(c.id)"
              @keyup.enter="goCourse(c.id)"
            >
              <div class="kcard__cover">
                <img v-if="c.coverUrl" class="kcard__img" :src="c.coverUrl" :alt="plain(c.name)" @error="onImgError" />
                <span v-else class="kcard__mark">{{ mark(c.name) }}</span>
              </div>
              <h3 class="kcard__name" :title="plain(c.name)">{{ plain(c.name) }}</h3>
              <p class="kcard__meta">{{ courseMeta(c) }}</p>
            </article>
            <p class="empty" v-if="!loading && courses.length === 0">没有找到符合条件的课程，换个关键词或清空筛选试试</p>
          </div>

          <transition name="fade">
            <div v-if="panelOpen" class="fpanel" role="dialog" aria-label="课程筛选">
              <div class="fpanel__row">
                <span class="fpanel__label">一级分类</span>
                <div class="fpanel__chips">
                  <button
                    v-for="opt in lv1Chips"
                    :key="opt.id"
                    class="chip"
                    :class="{ 'is-on': draft.lv1 === opt.id }"
                    type="button"
                    @click="pickLv1(opt.id)"
                  >{{ opt.name }}</button>
                </div>
              </div>
              <div class="fpanel__row">
                <span class="fpanel__label">二级分类</span>
                <div class="fpanel__chips">
                  <button class="chip" :class="{ 'is-on': !draft.lv2 }" type="button" @click="draft.lv2 = ''">全部</button>
                  <button
                    v-for="c in lv2Options"
                    :key="c.id"
                    class="chip"
                    :class="{ 'is-on': draft.lv2 === c.id }"
                    type="button"
                    @click="draft.lv2 = c.id"
                  >{{ c.name }}</button>
                  <span class="fpanel__hint" v-if="draft.lv1 === ALL && lv2Options.length === 0">请先选择一级分类</span>
                </div>
              </div>
              <div class="fpanel__foot">
                <span class="fpanel__selected">已选：{{ pickedText }}</span>
                <button class="fpanel__reset" type="button" @click="resetDraft">重置</button>
                <button class="fpanel__ok" type="button" @click="applyFilter">确定</button>
              </div>
            </div>
          </transition>
        </div>

        <div class="pool__foot" v-if="total > PAGE_SIZE">
          <SPagination v-model="pageNo" :total="total" :page-size="PAGE_SIZE" @change="search" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { classSeach, getClassCategorys, getRecommendClassList, getMylessons } from '@/api/class.js';
import { useUserStore } from '@/store';
import SPagination from '@/components/shell/SPagination.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isLogin = computed(() => Boolean(userStore.userInfo && userStore.userInfo.name));

const ALL = 'all';
const PAGE_SIZE = 6;

const plain = (s) => String(s || '').replace(/<\/?em>/g, '');
const mark = (name) => {
  const latin = plain(name).match(/[A-Za-z][A-Za-z0-9+#.]{1,5}/);
  return latin ? latin[0].toUpperCase() : plain(name).slice(0, 2);
};
const onImgError = (e) => { e.target.style.display = 'none'; };
const goCourse = (id) => { if (id) router.push({ path: '/student/courses/detail', query: { id } }); };
const goSearchPage = () => {
  // 原跳老站 /search/index（该路由已下线）：改为滚动到本页课程列表
  const el = document.querySelector('.pool');
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

// ---------- 推荐 Banner（轮播：箭头 + 圆点，数据取真实推荐课程） ----------
const bannerList = ref([]);
const bannerIndex = ref(0);
const cur = computed(() => bannerList.value[bannerIndex.value] || {});
/** 接口无课程简介字段 → 用真实讲师/节数/人数拼副标题，不编造文案 */
const bannerDesc = computed(() => {
  const c = cur.value;
  const parts = [];
  if (c.teacher) parts.push(`${c.teacher} 主讲`);
  if (c.sections) parts.push(`共 ${c.sections} 节`);
  if (c.sold) parts.push(`${c.sold} 人正在学`);
  return parts.join(' · ');
});
function stepBanner(d) {
  const n = bannerList.value.length;
  if (!n) return;
  bannerIndex.value = (bannerIndex.value + d + n) % n;
}

// ---------- 我的课程 ----------
// 曾经点一行直接进「课程学习」（视频页）：学生想先看清这门课是什么（简介/目录/笔记）很别扭，
// 而「最近学习」在学习首页已经有了 → 这里改成课程入口，点进课程详情页。
const myList = ref([]);
const myLoaded = ref(false);
const pct = (item) => Math.min(100, Math.round(((item.learnedSections || 0) * 100) / (item.sections || 1)));

// ---------- 课程搜索 ----------
const keyword = ref('');
const courses = ref([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
const courseMeta = (c) => {
  const parts = [];
  if (c.teacher) parts.push(c.teacher);
  if (c.sold) parts.push(`${c.sold} 人学习`);
  return parts.join(' · ');
};

// 分类（一级来自接口；二级随一级联动）
const categoryTree = ref([]);
const lv1Chips = computed(() => [{ id: ALL, name: '全部' }, ...categoryTree.value.map((c) => ({ id: c.id, name: c.name }))]);
const lv2Options = computed(() => {
  if (draft.lv1 === ALL) return [];
  const hit = categoryTree.value.find((c) => c.id === draft.lv1);
  return hit?.children || [];
});
const FREE_OPTS = [
  { id: '', name: '全部' },
  { id: '1', name: '免费' },
  { id: '0', name: '付费' },
];

// 面板开合 + 草稿（点「确定」才落到查询条件）
const panelOpen = ref(false);
const draft = reactive({ lv1: ALL, lv2: '', free: '' });
const applied = reactive({ lv1: ALL, lv2: '', free: '' });

function nameOf(id) {
  for (const c of categoryTree.value) {
    if (c.id === id) return c.name;
    const hit = (c.children || []).find((x) => x.id === id);
    if (hit) return hit.name;
  }
  return '';
}
const pickedText = computed(() => {
  const parts = [];
  if (draft.lv1 !== ALL) parts.push(nameOf(draft.lv1));
  if (draft.lv2) parts.push(nameOf(draft.lv2));
  const f = FREE_OPTS.find((x) => x.id === draft.free);
  if (f && f.id !== '') parts.push(f.name);
  return parts.length ? parts.join(' / ') : '全部课程';
});

function togglePanel() { panelOpen.value = !panelOpen.value; }
function pickLv1(id) {
  draft.lv1 = id;
  draft.lv2 = '';   // 换一级分类时二级清空，避免残留无效 id
}
function resetDraft() {
  draft.lv1 = ALL;
  draft.lv2 = '';
  draft.free = '';
}
function applyFilter() {
  Object.assign(applied, { lv1: draft.lv1, lv2: draft.lv2, free: draft.free });
  panelOpen.value = false;
  pageNo.value = 1;
  search();
}
function applySearch() {
  pageNo.value = 1;
  search();
}

async function search() {
  loading.value = true;
  try {
    const params = { pageNo: pageNo.value, pageSize: PAGE_SIZE };
    if (keyword.value) params.keyword = keyword.value;
    if (applied.lv1 !== ALL) params.categoryIdLv1 = applied.lv1;
    if (applied.lv2) params.categoryIdLv2 = applied.lv2;
    if (applied.free !== '') params.free = applied.free === '1';
    const res = await classSeach(params);
    if (res?.code == 200 && res.data) {
      courses.value = res.data.list || [];
      total.value = Number(res.data.total) || 0;
    } else {
      courses.value = [];
      total.value = 0;
    }
  } catch (e) {
    courses.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

const onKeydown = (e) => { if (e.key === 'Escape') panelOpen.value = false; };
onMounted(() => document.addEventListener('keydown', onKeydown));
onUnmounted(() => document.removeEventListener('keydown', onKeydown));

onMounted(async () => {
  // 顶栏全局搜索会带 keyword 进来（原 /search/index 已并入课程中心）
  const kw = String(route.query.keyword || '').trim();
  if (kw) keyword.value = kw;
  search();
  getClassCategorys()
    .then((res) => { if (res?.code == 200 && Array.isArray(res.data)) categoryTree.value = res.data; })
    .catch(() => {});
  getRecommendClassList('best')
    .then((res) => {
      if (res?.code == 200 && Array.isArray(res.data)) bannerList.value = res.data.filter((c) => c && c.id).slice(0, 3);
    })
    .catch(() => {});
  if (isLogin.value) {
    getMylessons({ page: 1, pageSize: 4 })
      .then((res) => { if (res?.code == 200 && res.data) myList.value = (res.data.list || []).slice(0, 4); })
      .catch(() => {})
      .finally(() => { myLoaded.value = true; });
  } else {
    myLoaded.value = true;
  }
});
</script>

<style lang="scss" scoped>
// =============================================================================
// 版面：Content 1096，纵向间距 32
// =============================================================================
.cc {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.row {
  display: flex;
  gap: 32px;          // 设计稿 Bottom Row 间距
  align-items: flex-start;
}

// ---------- 区块标题 ----------
.sec {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }
  &__title {
    margin: 0;
    font-size: 21px;
    line-height: 28px;
    font-weight: 600;
    letter-spacing: -0.2px;
    color: #1d1d1f;
  }
  &__link {
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #0066cc;
    text-decoration: none;
    white-space: nowrap;

    &:hover { text-decoration: underline; }
  }
}

// =============================================================================
// Banner Carousel：1096×200，圆角 20，45° 渐变
// =============================================================================
.banner {
  position: relative;
  height: 200px;
  // 设计稿内边距：左 36 / 上 30 / 下 16；chip→标题 12、标题→描述 8、描述→按钮 12
  padding: 30px 36px 16px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, #0a1a30 0%, #124a8c 55%, #1f7ad1 100%);
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  // 纵向为 flex 主轴，高度即主轴尺寸，默认会被压缩 → 全部禁止收缩
  > * { flex: 0 0 auto; }

  &__chip {
    display: inline-flex;
    align-items: center;
    height: 26px;
    padding: 0 26px;
    border-radius: 13px;
    background: rgba(255, 255, 255, 0.22);
    color: #fff;
    font-size: 12px;
    line-height: 18px;
    font-weight: 500;
    white-space: nowrap;
  }
  &__title {
    margin: 12px 0 0;
    max-width: 560px;
    font-size: 28px;
    line-height: 36px;
    font-weight: 600;
    letter-spacing: -0.2px;
    color: #fff;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__desc {
    margin: 8px 0 0;
    max-width: 560px;
    font-size: 14px;
    line-height: 22px;
    color: rgba(255, 255, 255, 0.77);
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-top: 12px;
    height: 38px;
    padding: 0 24px;
    border: 0;
    border-radius: 19px;
    background: #0066cc;
    color: #fff;
    font-size: 14px;
    line-height: 20px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover { background: #0071e3; }
  }
  &__arrows {
    position: absolute;
    right: 32px;
    top: 50%;
    transform: translateY(-50%);
    display: flex;
    gap: 12px;
  }
  &__dots {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
  }
}

.arrow {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  cursor: pointer;
  transition: background-color 0.16s ease;

  svg { width: 12px; height: 12px; }
  &:hover { background: rgba(255, 255, 255, 0.28); }
}

.dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.38);
  cursor: pointer;
  transition: background-color 0.16s ease;

  &.is-on { background: #fff; }
}

// =============================================================================
// 通用卡片标题 / 链接
// =============================================================================
.cardTitle {
  margin: 0;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  color: #1d1d1f;
}
.cardLink {
  font-size: 12px;
  line-height: 17px;
  font-weight: 500;
  color: #0066cc;
  text-decoration: none;
  white-space: nowrap;

  &:hover { text-decoration: underline; }
}
.empty {
  margin: 0;
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// =============================================================================
// 我的课程：328 宽，卡圆角 18 / 内边距 20 / 间距 16；行 缩略图 40×28
// =============================================================================
.myLearn {
  flex: 0 0 328px;
  width: 328px;
  padding: 20px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  &__list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
}

.lesson {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;

  &:hover .lesson__name { color: #0066cc; }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: 2px; border-radius: 6px; }

  &__thumb {
    position: relative;
    flex: 0 0 40px;
    width: 40px;
    height: 28px;
    border-radius: 6px;
    overflow: hidden;
    background: linear-gradient(135deg, #0a1a30 0%, #2f5e8f 100%);
  }
  &__img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  &__col {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  &__row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
  }
  &__name {
    min-width: 0;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.15s ease;
  }
  &__pct {
    flex: 0 0 auto;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }
  &__sub {
    font-size: 11px;
    line-height: 15px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// =============================================================================
// 课程中心：736 宽，卡圆角 18 / 内边距 24 / 间距 20
// =============================================================================
.pool {
  flex: 1 1 auto;
  min-width: 0;
  padding: 24px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;

  // 搜索行：搜索框 44 高圆角 22 + 搜索 78×44 + 筛选 92×44
  &__bar {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  &__search {
    flex: 1 1 auto;
    min-width: 0;
    height: 44px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 18px;
    border-radius: 22px;
    background: #f5f5f7;
    transition: box-shadow 0.16s ease, background-color 0.16s ease;

    &:focus-within { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
    svg { flex: 0 0 16px; width: 16px; height: 16px; color: #86868b; }
    input {
      flex: 1 1 auto;
      min-width: 0;
      border: 0;
      outline: 0;
      background: transparent;
      font-size: 13px;
      line-height: 16px;
      color: #1d1d1f;
      font-family: inherit;

      &::placeholder { color: #86868b; }
    }
  }
  &__go {
    flex: 0 0 auto;
    min-width: 78px;
    height: 44px;
    padding: 0 22px;
    border: 0;
    border-radius: 22px;
    background: #0066cc;
    color: #fff;
    font-size: 14px;
    line-height: 20px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover { background: #0071e3; }
  }
  &__filter {
    flex: 0 0 auto;
    min-width: 92px;
    height: 44px;
    padding: 0 18px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    border: 0;
    border-radius: 22px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 13px;
    line-height: 18px;
    cursor: pointer;
    transition: background-color 0.16s ease;

    svg { width: 16px; height: 16px; }
    &:hover { background: #dbe9fb; }
    &.is-open { background: #d2e4fa; }
  }

  &__stage {
    position: relative;
    flex: 1 1 auto;
    min-height: 200px;
  }
  &__grid {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
  }
  &__foot {
    display: flex;
    justify-content: center;
  }
}

// 课程卡：218×177（封面 218×124 圆角 12 + 卡内间距 8）
.kcard {
  flex: 0 0 calc((100% - 32px) / 3);
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;

  &__cover {
    position: relative;
    aspect-ratio: 218 / 124;
    border-radius: 12px;
    overflow: hidden;
    background: linear-gradient(135deg, #0a1a30 0%, #2f5e8f 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.18s ease;
  }
  &:hover &__cover { transform: translateY(-2px); }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: 3px; border-radius: 12px; }

  &__img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  &__mark {
    font-size: 34px;
    line-height: 42px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.14);
    user-select: none;
  }
  &__name {
    margin: 0;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: #1d1d1f;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__meta {
    margin: 0;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// =============================================================================
// 分类筛选浮层：688 宽（= 课程卡内宽），圆角 16、内边距 16、间距 12
// =============================================================================
.fpanel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 30;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(24px) saturate(160%);
  -webkit-backdrop-filter: blur(24px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 12px 40px -12px rgba(16, 24, 40, 0.22);
  display: flex;
  flex-direction: column;
  gap: 12px;

  @supports not ((backdrop-filter: blur(4px)) or (-webkit-backdrop-filter: blur(4px))) {
    background: rgba(255, 255, 255, 0.97);
  }

  &__row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
  &__label {
    flex: 0 0 60px;
    width: 60px;
    font-size: 12px;
    line-height: 32px;
    color: #6e6e73;
  }
  &__chips {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
  &__hint {
    font-size: 12px;
    line-height: 32px;
    color: #86868b;
  }
  &__foot {
    display: flex;
    align-items: center;
    gap: 8px;
    justify-content: flex-end;
  }
  &__selected {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 12px;
    line-height: 17px;
    color: #6e6e73;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__reset {
    height: 32px;
    padding: 0 22px;
    border: 0;
    border-radius: 16px;
    background: #fff;
    color: #333;
    font-size: 13px;
    line-height: 18px;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover { background: #f0f0f3; }
  }
  &__ok {
    height: 32px;
    padding: 0 22px;
    border: 0;
    border-radius: 16px;
    background: #0066cc;
    color: #fff;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover { background: #0071e3; }
  }
}

// chip：高 32、圆角 16；选中 #0066CC，未选中实心白
.chip {
  height: 32px;
  padding: 0 18px;
  border: 0;
  border-radius: 16px;
  background: #fff;
  color: #333;
  font-size: 13px;
  line-height: 18px;
  font-weight: 400;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover { background: #f2f4f8; }
  &.is-on {
    background: #0066cc;
    color: #fff;
    font-weight: 500;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

// =============================================================================
// 窄屏：两栏下沉、课程栅格 2 列
// =============================================================================
@media (max-width: 1180px) {
  .row { flex-direction: column; }
  .myLearn { flex: 0 0 auto; width: 100%; }
}

@media (max-width: 860px) {
  .kcard { flex: 0 0 calc((100% - 16px) / 2); }
  .banner { height: auto; padding: 24px; }
  .banner__arrows { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
  .kcard__cover { transition: none; }
}
</style>
