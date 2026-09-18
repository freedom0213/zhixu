<!--
 * 课程详情（/student/courses/detail?id=）— 学员端新风格
 * 版式（沿用学员端设计语言）：版心 1096 = 主栏 744 + 间距 32 + 右栏 320
 *   ① 面包屑（分类路径）→ ② 课程信息卡（封面 + 名称 + 课程数/有效期/评分 + 收藏/分享）
 *   ③ 价格卡（价格 + 马上学习）→ ④ 主栏页签（课程简介 / 课程目录）+ 右栏（常见问题 / 猜你喜欢）
 * 数据契约：
 *   - getClassDetails(id) → {id,name,coverUrl,cataTotalNum,coureScore,validDuration,introduce,
 *                            usePeople,detail,cateNames,price,free,purchaseEndTime,studyNum}
 *   - getClassTeachers(id) → [{id,name,isShow}]
 *   - getClassList(id) → [{id,name,index,type,sections:[{id,name,index,type,trailer,mediaDuration}]}]
 *   - enrolledFreeCourse(id) 免费课入课；putCarts({courseId}) 付费课加购
 *   - addMyCollect({courseId,collected}) / isCollect(courseId)
 *   - getRecommendClassList('best') → 猜你喜欢
 *   常见问题为沿用老项目的静态问答（pages/classDetails/index.vue 中的 askData），非接口数据。
 -->
<template>
  <div class="cd">
    <!-- 面包屑 -->
    <nav class="crumb" aria-label="位置">
      <router-link class="crumb__link" to="/student/courses">课程中心</router-link>
      <template v-for="(c, i) in crumbs" :key="c + i">
        <span class="crumb__sep">/</span>
        <span class="crumb__cur">{{ c }}</span>
      </template>
    </nav>

    <!-- 课程信息卡 -->
    <section class="s-card head" v-loading="loading">
      <div class="head__cover">
        <img v-if="info.coverUrl" :src="info.coverUrl" :alt="info.name" @error="onImgError" />
        <span v-else class="head__mark">{{ mark }}</span>
      </div>

      <div class="head__body">
        <h1 class="head__title">{{ info.name || '课程详情' }}</h1>
        <p class="head__cate" v-if="info.cateNames">{{ info.cateNames }}</p>

        <div class="metrics">
          <div class="metric">
            <span class="metric__value">{{ info.cataTotalNum || 0 }}</span>
            <span class="metric__label">课程节数</span>
          </div>
          <div class="metric">
            <span class="metric__value">{{ info.validDuration ? info.validDuration + ' 个月' : '长期' }}</span>
            <span class="metric__label">有效期</span>
          </div>
          <div class="metric">
            <span class="metric__value">{{ info.coureScore != null ? Number(info.coureScore).toFixed(1) : '—' }}</span>
            <span class="metric__label">课程评分</span>
          </div>
          <div class="metric">
            <span class="metric__value">{{ info.studyNum || 0 }}</span>
            <span class="metric__label">在学人数</span>
          </div>
        </div>

        <div class="head__actions">
          <button class="ghostBtn" type="button" :class="{ 'is-on': collected }" @click="toggleCollect">
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M8 13.2 3.6 9.4a2.7 2.7 0 0 1 3.7-3.9L8 6.2l.7-.7a2.7 2.7 0 0 1 3.7 3.9L8 13.2Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
            </svg>
            {{ collected ? '已收藏' : '收藏' }}
          </button>
          <button class="ghostBtn" type="button" @click="share">
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="12" cy="4" r="1.8" stroke="currentColor" stroke-width="1.3"/>
              <circle cx="4" cy="8" r="1.8" stroke="currentColor" stroke-width="1.3"/>
              <circle cx="12" cy="12" r="1.8" stroke="currentColor" stroke-width="1.3"/>
              <path d="M5.6 7.1 10.4 4.9M5.6 8.9l4.8 2.2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
            分享
          </button>
        </div>
      </div>
    </section>

    <!-- 价格卡 -->
    <section class="s-card price">
      <div class="price__left">
        <span class="price__num" :class="{ 'is-free': info.free }">{{ priceText }}</span>
        <span class="price__hint" v-if="!info.free && deadline">有效期至 {{ deadline }}</span>
        <span class="price__hint" v-else-if="info.free">本课程免费学习</span>
      </div>
      <button class="price__btn" type="button" :disabled="buying" @click="startLearn">
        {{ buying ? '处理中…' : (canLearn ? '马上学习' : '立即购买') }}
      </button>
    </section>

    <!-- 主栏 + 右栏 -->
    <div class="row">
      <section class="s-card tabCard">
        <div class="tabs" role="tablist">
          <button
            v-for="t in TABS"
            :key="t.value"
            class="tab"
            :class="{ 'is-on': tab === t.value }"
            type="button"
            role="tab"
            :aria-selected="tab === t.value"
            @click="tab = t.value"
          >{{ t.label }}</button>
        </div>

        <!-- 课程简介 -->
        <div class="tabBody" v-if="tab === 'about'">
          <div class="block" v-if="info.introduce">
            <h3 class="block__title">课程简介</h3>
            <p class="block__text">{{ info.introduce }}</p>
          </div>
          <div class="block" v-if="info.usePeople">
            <h3 class="block__title">适学人群</h3>
            <p class="block__text">{{ info.usePeople }}</p>
          </div>
          <div class="block" v-if="info.detail">
            <h3 class="block__title">课程详情</h3>
            <div class="block__html" v-html="info.detail"></div>
          </div>
          <div class="block" v-if="teachers.length">
            <h3 class="block__title">授课老师</h3>
            <div class="teachers">
              <div class="teacher" v-for="t in teachers" :key="t.id">
                <span class="teacher__avatar">{{ (t.name || '师').slice(0, 1) }}</span>
                <span class="teacher__name">{{ t.name }}</span>
              </div>
            </div>
          </div>
          <p class="empty" v-if="!info.introduce && !info.usePeople && !info.detail">暂无课程简介</p>
        </div>

        <!-- 课程目录 -->
        <div class="tabBody" v-else-if="tab === 'catalogue'">
          <p class="empty" v-if="!chapters.length">课程目录建设中</p>
          <div class="chapter" v-for="ch in chapters" :key="ch.id">
            <h3 class="chapter__title">
              <span class="chapter__index">第 {{ ch.index }} 章</span>
              {{ ch.name }}
            </h3>
            <button
              class="section"
              v-for="sec in ch.sections"
              :key="sec.id"
              type="button"
              @click="goLearn(sec.id)"
            >
              <svg class="section__play" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <circle cx="8" cy="8" r="6.4" stroke="currentColor" stroke-width="1.3"/>
                <path d="M6.6 5.6v4.8L10.4 8 6.6 5.6Z" fill="currentColor"/>
              </svg>
              <span class="section__name">{{ sec.name }}</span>
              <!-- P25：免费课程不存在「试看」—— 整门课都能看，标出个别小节反而误导 -->
              <span class="section__try" v-if="sec.trailer && !info.free">试看</span>
              <span class="section__dur" v-if="sec.mediaDuration">{{ dur(sec.mediaDuration) }}</span>
            </button>
          </div>
        </div>

        <!-- 课程试卷（P17）：讲师针对本课程发布的考试，学生从这里直接进答题页 -->
        <div class="tabBody" v-else-if="tab === 'papers'">
          <CourseExamList :courseId="courseId" />
        </div>

        <!-- 课程笔记：查看本课程的公开笔记，可采集 -->
        <div class="tabBody" v-else-if="tab === 'notes'">
          <div v-loading="notesLoading" class="cdNotes">
            <template v-if="courseNotes.length">
              <div v-for="n in courseNotes" :key="n.id" class="cdNote">
                <img class="cdNote__avatar" :src="n.authorIcon" alt="" @error="e => e.target.style.visibility = 'hidden'" />
                <div class="cdNote__main">
                  <p class="cdNote__head">
                    <b class="cdNote__author">{{ n.authorName || '同学' }}</b>
                    <span class="cdNote__time">{{ n.createTime }}</span>
                    <span v-if="n.isGathered" class="cdNote__tag">已采集</span>
                  </p>
                  <p class="cdNote__content">{{ n.content }}</p>
                </div>
                <button
                  v-if="!isMyNote(n)"
                  class="cdNote__gather"
                  type="button"
                  :disabled="gathering === n.id"
                  @click="gatherNote(n)"
                >{{ gathering === n.id ? '采集中…' : n.isGathered ? '已采集' : '采集' }}</button>
              </div>
            </template>
            <p class="empty" v-else-if="!notesLoading">本课程还没有公开笔记，去学习页记一条吧</p>
          </div>
        </div>
      </section>

      <aside class="side">
        <section class="s-card sideCard">
          <h3 class="sideCard__title">常见问题</h3>
          <div class="faq" v-for="(f, i) in FAQ" :key="i">
            <p class="faq__q">{{ f.ask }}</p>
            <p class="faq__a">{{ f.answer }}</p>
          </div>
        </section>

        <section class="s-card sideCard">
          <h3 class="sideCard__title">猜你喜欢</h3>
          <div
            class="like"
            v-for="c in likes"
            :key="c.id"
            tabindex="0"
            @click="goDetail(c.id)"
            @keyup.enter="goDetail(c.id)"
          >
            <div class="like__thumb">
              <img v-if="c.coverUrl" :src="c.coverUrl" alt="" @error="onImgError" />
            </div>
            <div class="like__col">
              <span class="like__name" :title="plain(c.name)">{{ plain(c.name) }}</span>
              <span class="like__meta">{{ likeMeta(c) }}</span>
            </div>
          </div>
          <p class="empty" v-if="!likes.length">暂无推荐</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getClassDetails, getClassTeachers, getClassList } from '@/api/classDetails.js';
import { getRecommendClassList, getCourseLearning, addMyCollect, isCollect } from '@/api/class.js';
import { enrolledFreeCourse } from '@/api/order.js';
import { getAllNotes, notesGathers } from '@/api/notes.js';
import { ElMessageBox } from 'element-plus';
import { useUserStore } from '@/store';
import { rememberStudentOrigin } from '@/config/loginRedirect';
import CourseExamList from '@/pages/student/components/CourseExamList.vue';

const route = useRoute();
const router = useRouter();
const store = useUserStore();
const isLogin = computed(() => Boolean(store.userInfo && store.userInfo.name));

const TABS = [
  { label: '课程简介', value: 'about' },
  { label: '课程目录', value: 'catalogue' },
  { label: '课程笔记', value: 'notes' },
  { label: '课程试卷', value: 'papers' },
];
const tab = ref('about');

// ---- 课程笔记：查看本课程笔记 + 采集他人公开笔记 ----
const courseNotes = ref([]);
const notesLoading = ref(false);
const notesLoaded = ref(false);
const gathering = ref(null);

const isMyNote = (n) => {
  const myId = store.userInfo && store.userInfo.id;
  return n.authorId != null && myId != null && String(n.authorId) === String(myId);
};

async function loadCourseNotes() {
  notesLoading.value = true;
  try {
    const res = await getAllNotes({ courseId: courseId.value, pageNo: 1, pageSize: 50 });
    if (res?.code == 200 && res.data) {
      courseNotes.value = res.data.list || [];
    }
  } catch (e) {
    courseNotes.value = [];
  } finally {
    notesLoading.value = false;
    notesLoaded.value = true;
  }
}

async function gatherNote(n) {
  try {
    await ElMessageBox.confirm(`采集「${(n.content || '').split('\n')[0].slice(0, 20)}」到我的笔记吗？`, '采集笔记', {
      confirmButtonText: '采集',
      cancelButtonText: '取消',
      type: 'info',
    });
  } catch (e) {
    return;
  }
  gathering.value = n.id;
  try {
    const res = await notesGathers(n.id);
    if (res?.code == 200) {
      ElMessage.success('采集成功，可在「学习笔记 → 我采集的」查看');
      n.isGathered = true;
    } else {
      ElMessage.error(res?.msg || '采集失败');
    }
  } catch (e) {
    ElMessage.error('采集失败，请稍后重试');
  } finally {
    gathering.value = null;
  }
}

watch(tab, (v) => {
  if (v === 'notes' && !notesLoaded.value) loadCourseNotes();
});

const loading = ref(true);
const info = ref({});
const teachers = ref([]);
const chapters = ref([]);
const likes = ref([]);
const enrolled = ref(false);
const buying = ref(false);
const collected = ref(false);

const courseId = computed(() => route.query.id);

const plain = (s) => String(s || '').replace(/<\/?em>/g, '');
const mark = computed(() => {
  const latin = plain(info.value.name).match(/[A-Za-z][A-Za-z0-9+#.]{1,4}/);
  return latin ? latin[0].toUpperCase() : plain(info.value.name).slice(0, 2);
});
const onImgError = (e) => { e.target.style.display = 'none'; };

const crumbs = computed(() => (info.value.cateNames ? String(info.value.cateNames).split('/') : []));
const priceText = computed(() => {
  if (info.value.free || Number(info.value.price) === 0) return '免费';
  return `¥${(Number(info.value.price) / 100).toFixed(2)}`;
});
const deadline = computed(() => (info.value.purchaseEndTime ? String(info.value.purchaseEndTime).slice(0, 10) : ''));
const canLearn = computed(() => info.value.free || enrolled.value);

const dur = (sec) => {
  const s = Number(sec) || 0;
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`;
};
const likeMeta = (c) => {
  const parts = [];
  if (c.teacher) parts.push(c.teacher);
  if (c.sold) parts.push(`${c.sold} 人学习`);
  return parts.join(' · ') || '优质课程';
};

// 沿用老项目 classDetails/index.vue 的静态问答（非接口数据）
const FAQ = [
  { ask: '如何查看已购课程？', answer: '请用购课账号登录，点击【我的学习】进入。' },
  { ask: '课程购买后可以更换吗？', answer: '如需更换课程请咨询客服为您确认是否可以更换。' },
  { ask: '无法登录怎么办？', answer: '请更换不同浏览器。' },
  { ask: '课程过期了怎么办？', answer: '课程过期无法观看了哦，请在有效期内进行观看课程。' },
];

const goDetail = (id) => { if (id) router.push({ path: '/student/courses/detail', query: { id } }); };
/** 进入学习页（新风格 /student/learn） */
const goLearn = (sectionId) => {
  if (!isLogin.value) { rememberStudentOrigin(route.fullPath); router.push('/login'); return; }
  router.push({ path: '/student/learn', query: { id: courseId.value, sectionId } });
};

async function toggleCollect() {
  if (!isLogin.value) { rememberStudentOrigin(route.fullPath); router.push('/login'); return; }
  const next = !collected.value;
  try {
    const res = await addMyCollect({ courseId: courseId.value, collected: next });
    if (res?.code == 200) {
      collected.value = next;
      ElMessage.success(next ? '收藏成功' : '已取消收藏');
    } else {
      ElMessage.error(res?.msg || '操作失败，请稍后再试');
    }
  } catch (e) {
    ElMessage.error('操作失败，请稍后再试');
  }
}

async function share() {
  const url = window.location.href;
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('课程链接已复制');
  } catch (e) {
    ElMessage.info(url);
  }
}

async function startLearn() {
  if (!isLogin.value) { rememberStudentOrigin(route.fullPath); router.push('/login'); return; }
  if (canLearn.value) {
    goLearn(chapters.value[0]?.sections?.[0]?.id);
    return;
  }
  buying.value = true;
  try {
    const { putCarts } = await import('@/api/order.js');
    const res = await putCarts({ courseId: courseId.value });
    if (res?.code == 200) {
      router.push('/student/carts');
      window.dispatchEvent(new CustomEvent('cart:changed'));
    } else {
      ElMessage.error(res?.msg || '加入购物车失败，请稍后再试');
    }
  } catch (e) {
    ElMessage.error('加入购物车失败，请稍后再试');
  } finally {
    buying.value = false;
  }
}

async function load() {
  const id = courseId.value;
  if (!id) return;
  loading.value = true;
  try {
    const [d, t, c, l] = await Promise.allSettled([
      getClassDetails(id),
      getClassTeachers(id),
      getClassList(id),
      getRecommendClassList('best'),
    ]);
    if (d.status === 'fulfilled' && d.value?.code == 200) info.value = d.value.data || {};
    if (t.status === 'fulfilled' && t.value?.code == 200) teachers.value = t.value.data || [];
    if (c.status === 'fulfilled' && c.value?.code == 200) chapters.value = c.value.data || [];
    if (l.status === 'fulfilled' && l.value?.code == 200) {
      likes.value = (l.value.data || []).filter((x) => x && x.id && String(x.id) !== String(id)).slice(0, 4);
    }
  } finally {
    loading.value = false;
  }
  loadEnrolled();
  loadCollect();
}

/** 是否已购：有学习记录即视为已购 */
async function loadEnrolled() {
  if (!isLogin.value) return;
  try {
    const res = await getCourseLearning(courseId.value);
    enrolled.value = res?.code == 200 && !!res.data;
  } catch (e) { /* 未购或无记录，保持 false */ }
}

async function loadCollect() {
  if (!isLogin.value) return;
  try {
    const res = await isCollect(courseId.value);
    collected.value = res?.data === true || res?.data?.collected === true;
  } catch (e) { /* 静默 */ }
}

watch(courseId, (v, o) => { if (v && v !== o) { tab.value = 'about'; load(); } });
onMounted(load);
</script>

<style lang="scss" scoped>
.cd {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

// ---------- 面包屑 ----------
.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 18px;

  &__link {
    color: #0066cc;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
  &__sep { color: #c2c6ce; }
  &__cur { color: #86868b; }
}

// ---------- 课程信息卡 ----------
.head {
  display: flex;
  gap: 24px;
  padding: 24px;
  border-radius: 18px;

  &__cover {
    position: relative;
    flex: 0 0 260px;
    width: 260px;
    height: 164px;
    border-radius: 12px;
    overflow: hidden;
    background: linear-gradient(135deg, #0a1a30 0%, #2f5e8f 100%);
    display: flex;
    align-items: center;
    justify-content: center;

    img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
  }
  &__mark {
    font-size: 40px;
    line-height: 50px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.16);
    user-select: none;
  }

  &__body {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  &__title {
    margin: 0;
    font-size: 24px;
    line-height: 32px;
    font-weight: 600;
    letter-spacing: -0.3px;
    color: #1d1d1f;
  }
  &__cate {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
  }
  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: auto;
    padding-top: 16px;
  }
}

.metrics {
  display: flex;
  align-items: flex-start;
  gap: 32px;
  margin-top: 20px;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &__value { font-size: 17px; line-height: 22px; font-weight: 600; color: #1d1d1f; font-variant-numeric: tabular-nums; }
  &__label { font-size: 12px; line-height: 17px; color: #86868b; }
}

.ghostBtn {
  height: 36px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 18px;
  background: #f5f5f7;
  color: #333;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  svg { width: 14px; height: 14px; }
  &:hover { background: #e9eaee; }
  &.is-on { background: #e8f1fc; color: #0066cc; }
}

// ---------- 价格卡 ----------
.price {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 24px;
  border-radius: 18px;

  &__left { display: flex; align-items: baseline; gap: 14px; min-width: 0; }
  &__num {
    font-size: 28px;
    line-height: 36px;
    font-weight: 600;
    color: #0066cc;
    font-variant-numeric: tabular-nums;

    &.is-free { color: #34c759; }
  }
  &__hint { font-size: 12px; line-height: 17px; color: #86868b; }
  &__btn {
    flex: 0 0 auto;
    height: 44px;
    padding: 0 32px;
    border: 0;
    border-radius: 22px;
    background: #0066cc;
    color: #fff;
    font-size: 15px;
    line-height: 20px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.6; cursor: default; }
  }
}

// ---------- 主栏 + 右栏 ----------
.row {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.tabCard {
  flex: 1 1 auto;
  min-width: 0;
  padding: 24px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  border-bottom: 1px solid #ececf0;
}
.tab {
  position: relative;
  padding: 0 0 12px;
  border: 0;
  background: transparent;
  color: #6e6e73;
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
  transition: color 0.16s ease;

  &:hover { color: #1d1d1f; }
  &.is-on {
    color: #0066cc;
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      border-radius: 1px;
      background: #0066cc;
    }
  }
}

.tabBody { display: flex; flex-direction: column; gap: 24px; }
.block {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__title { margin: 0; font-size: 15px; line-height: 20px; font-weight: 600; color: #1d1d1f; }
  &__text { margin: 0; font-size: 14px; line-height: 24px; color: #6e6e73; }
  &__html {
    font-size: 14px;
    line-height: 24px;
    color: #6e6e73;
    word-break: break-word;

    :deep(p) { margin: 0 0 10px; &:last-child { margin-bottom: 0; } }
    :deep(img) { max-width: 100%; border-radius: 12px; }
    :deep(h1), :deep(h2), :deep(h3) { font-size: 15px; line-height: 22px; color: #1d1d1f; margin: 14px 0 8px; }
    :deep(ul), :deep(ol) { padding-left: 20px; }
  }
}

.teachers { display: flex; flex-wrap: wrap; gap: 12px; }
.teacher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 8px 8px;
  border-radius: 999px;
  background: #fafafc;

  &__avatar {
    width: 28px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 12px;
    font-weight: 600;
  }
  &__name { font-size: 13px; line-height: 18px; color: #1d1d1f; }
}

// ---------- 课程目录 ----------
.chapter {
  display: flex;
  flex-direction: column;
  gap: 8px;

  & + & { margin-top: 18px; }

  &__title {
    margin: 0 0 4px;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__index { color: #86868b; font-weight: 500; margin-right: 6px; }
}

.section {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover { background: #f5f7fb; }
  &:hover .section__name { color: #0066cc; }

  &__play { flex: 0 0 16px; width: 16px; height: 16px; color: #0066cc; }
  &__name {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 13px;
    line-height: 18px;
    color: #333;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.15s ease;
  }
  &__try {
    flex: 0 0 auto;
    padding: 1px 7px;
    border-radius: 5px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 11px;
    line-height: 15px;
  }
  &__dur {
    flex: 0 0 auto;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }
}

// ---------- 右栏 ----------
.side {
  flex: 0 0 320px;
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.sideCard {
  padding: 20px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__title { margin: 0; font-size: 15px; line-height: 20px; font-weight: 600; color: #1d1d1f; }
}

.faq {
  display: flex;
  flex-direction: column;
  gap: 3px;

  &__q { margin: 0; font-size: 13px; line-height: 18px; font-weight: 500; color: #1d1d1f; }
  &__a { margin: 0; font-size: 12px; line-height: 18px; color: #86868b; }
  & + & { padding-top: 12px; border-top: 1px solid #f2f2f5; }
}

.like {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;

  &:hover .like__name { color: #0066cc; }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: 2px; border-radius: 6px; }

  &__thumb {
    position: relative;
    flex: 0 0 48px;
    width: 48px;
    height: 32px;
    border-radius: 6px;
    overflow: hidden;
    background: linear-gradient(135deg, #0d2340 0%, #2f5e8f 100%);

    img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
  }
  &__col { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  &__name {
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.15s ease;
  }
  &__meta {
    font-size: 11px;
    line-height: 15px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// ---------- 窄屏 ----------
@media (max-width: 1180px) {
  .row { flex-direction: column; }
  .side { flex: 0 0 auto; width: 100%; }
}

@media (max-width: 860px) {
  .head { flex-direction: column; }
  .head__cover { flex: 0 0 auto; width: 100%; height: 190px; }
  .metrics { flex-wrap: wrap; gap: 20px 28px; }
  .price { flex-direction: column; align-items: stretch; gap: 14px; }
  .price__btn { width: 100%; }
}

// ---- 课程笔记页签 ----
.cdNotes { min-height: 140px; }
.cdNote {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &:last-of-type { border-bottom: 0; }

  &__avatar {
    flex: 0 0 36px;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    object-fit: cover;
    background: #eef1f6;
  }
  &__main { flex: 1 1 auto; min-width: 0; }
  &__head {
    margin: 0 0 4px;
    display: flex;
    align-items: baseline;
    gap: 10px;
  }
  &__author {
    font-size: 14px;
    color: #1d1d1f;
  }
  &__time {
    font-size: 12px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }
  &__tag {
    display: inline-flex;
    align-items: center;
    height: 20px;
    padding: 0 8px;
    border-radius: 10px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 11px;
  }
  &__content {
    margin: 0;
    font-size: 13px;
    line-height: 20px;
    color: #424245;
    white-space: pre-wrap;
    word-break: break-word;
    display: -webkit-box;
    -webkit-line-clamp: 4;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__gather {
    flex: 0 0 auto;
    height: 30px;
    padding: 0 14px;
    border: 0;
    border-radius: 15px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #d9e8fa; }
    &:disabled { opacity: 0.55; cursor: not-allowed; }
  }
}
</style>
