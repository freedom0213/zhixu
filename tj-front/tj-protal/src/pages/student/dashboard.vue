<!--
 * 学习首页（/student/dashboard）— 严格对齐设计稿 01
 * 设计稿规格（Ardot fileId 725009394574981，frame「01 学习首页」）：
 *   Content 1096 = 主栏 744 + 间距 32 + 右栏 320
 *   主栏（列间距 28）：Hero 744×280(圆角20) → 推荐课程 → 学习足迹 → 近期学习记录
 *   右栏（列间距 20）：基本信息卡 → 学习数据卡 → 最近学习卡（卡片圆角 18、内边距 20）
 *   区块标题 21/28 SemiBold，各区「标题 + 更多链接(13/18)」与内容间隔 16
 * 数据契约（只用真实返回字段；无对应字段的位置保留版式并以「—」占位，不编造）：
 *   - getRecommendClassList('new'|'best') → [{id,name,price,teacher,coverUrl,sections,sold}]
 *   - getMylessons() → {total,list:[{id,courseId,courseName,courseCoverUrl,sections,learnedSections,...}]}
 *   - getMyPlan() → {list:[{id,courseId,courseName,weekFreq,sections,weekLearnedSections,
 *                    learnedSections,latestLearnTime}],weekFinished,weekTotalPlan}
 *   - getSignRecords() → 打卡记录数组；getAllNotes() → {total} 笔记数
 *   注：「累计学习/日均学习时长」「考试次数/通过考试/通过率」后端无字段 → 显示「—」
 -->
<template>
  <div class="dash">
    <!-- ==================== 主栏 ==================== -->
    <div class="dash__col">
      <!-- Hero：本周精选课程（744×280，圆角 20，45° 深蓝渐变） -->
      <section class="hero">
        <template v-if="hero">
          <span class="hero__chip">本周精选</span>
          <h1 class="hero__title">{{ plain(hero.name) }}</h1>
          <p class="hero__desc">{{ heroDesc }}</p>
          <button class="hero__btn" type="button" @click="goCourse(hero.id)">立即学习</button>
        </template>
        <template v-else>
          <span class="hero__chip">本周精选</span>
          <h1 class="hero__title">开始你的第一门课</h1>
          <p class="hero__desc">挑选一门课程，进入学习页即可开始记录学习足迹。</p>
          <router-link class="hero__btn" to="/student/courses">去选课</router-link>
        </template>
      </section>

      <!-- 推荐课程 -->
      <section class="sec">
        <header class="sec__head">
          <h2 class="sec__title">推荐课程</h2>
          <router-link class="sec__link" to="/student/courses">
            全部课程
            <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="m5 2.5 4.5 4.5L5 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </router-link>
        </header>
        <div class="grid" v-loading="recLoading">
          <article
            class="cc"
            v-for="c in courses"
            :key="c.id"
            tabindex="0"
            @click="goCourse(c.id)"
            @keyup.enter="goCourse(c.id)"
          >
            <div class="cc__cover">
              <img v-if="c.coverUrl" class="cc__img" :src="c.coverUrl" :alt="plain(c.name)" @error="onCoverError" />
              <span v-else class="cc__mark">{{ mark(c.name) }}</span>
            </div>
            <h3 class="cc__name" :title="plain(c.name)">{{ plain(c.name) }}</h3>
            <p class="cc__meta">{{ courseMeta(c) }}</p>
          </article>
          <p class="empty" v-if="!recLoading && courses.length === 0">暂无推荐课程</p>
        </div>
      </section>

      <!-- 学习足迹 -->
      <section class="sec">
        <header class="sec__head">
          <h2 class="sec__title">学习足迹</h2>
          <router-link class="sec__link" to="/student/records">
            学习记录
            <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="m5 2.5 4.5 4.5L5 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </router-link>
        </header>
        <div class="s-card foot">
          <div class="foot__row">
            <div class="foot__heat">
              <div class="legend">
                <span class="legend__txt">少</span>
                <i class="cell lv0"></i><i class="cell lv1"></i><i class="cell lv2"></i><i class="cell lv3"></i>
                <span class="legend__txt">多</span>
              </div>
              <div class="heat" role="img" aria-label="近 20 周学习足迹热力图">
                <div class="heat__col" v-for="(wk, wi) in heatWeeks" :key="wi">
                  <el-tooltip
                    v-for="(cell, ci) in wk"
                    :key="ci"
                    :disabled="!cell"
                    effect="dark"
                    placement="top"
                    :content="heatTip(cell)"
                  >
                    <span class="heat__cell" :class="cell ? `lv${cell.level}` : 'is-empty'"></span>
                  </el-tooltip>
                </div>
              </div>
            </div>
            <div class="foot__stats">
              <div class="fstat" v-for="s in footStats" :key="s.label">
                <span class="fstat__label">{{ s.label }}</span>
                <span class="fstat__value">{{ s.value }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 近期学习记录 -->
      <section class="sec">
        <header class="sec__head">
          <h2 class="sec__title">近期学习记录</h2>
          <router-link class="sec__link" to="/student/records">
            全部记录
            <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="m5 2.5 4.5 4.5L5 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </router-link>
        </header>
        <div class="s-card rec" v-loading="planLoading">
          <div class="rec__row" v-for="r in recentRows" :key="r.id">
            <span class="rec__date">{{ r.date }}</span>
            <div class="rec__col">
              <span class="rec__title">{{ r.title }}</span>
              <span class="rec__sub">{{ r.sub }}</span>
            </div>
            <span class="rec__status" :class="r.done ? 'is-done' : 'is-doing'">{{ r.done ? '已完成' : '进行中' }}</span>
          </div>
          <p class="empty" v-if="!planLoading && recentRows.length === 0">
            {{ isLogin ? '还没有学习记录，去「课程中心」挑一门开始吧' : '登录后查看你的学习记录' }}
          </p>
        </div>
      </section>
    </div>

    <!-- ==================== 右栏 ==================== -->
    <aside class="dash__side">
      <!-- 基本信息 -->
      <section class="s-card rcard">
        <div class="userRow">
          <img class="userRow__avatar" :src="avatar" alt="" @error="onAvatarError" />
          <div class="userRow__col">
            <span class="userRow__greet">{{ greeting }}<template v-if="userName">，{{ userName }}</template></span>
            <span class="userRow__sub">{{ signText }}</span>
          </div>
          <button
            class="signBtn"
            :class="{ 'is-done': signed }"
            type="button"
            :disabled="signBusy || signed"
            @click="doSign"
          >
            {{ signed ? '已打卡' : signBusy ? '打卡中' : '今日打卡' }}
          </button>
        </div>
        <div class="miniStats">
          <div class="mini" v-for="m in miniStats" :key="m.label">
            <span class="mini__value">{{ m.value }}</span>
            <span class="mini__label">{{ m.label }}</span>
          </div>
        </div>
      </section>

      <!-- 学习数据 -->
      <section class="s-card rcard">
        <h3 class="rcard__title">学习数据</h3>
        <div class="bigStats">
          <div class="big" v-for="b in bigStats" :key="b.label">
            <span class="big__value">{{ b.value }}</span>
            <span class="big__label">{{ b.label }}</span>
          </div>
        </div>
        <div class="statGrid">
          <div class="gcell" v-for="g in gridStats" :key="g.label">
            <span class="gcell__value">{{ g.value }}</span>
            <span class="gcell__label">{{ g.label }}</span>
          </div>
        </div>
      </section>

      <!-- 最近学习 -->
      <section class="s-card rcard">
        <div class="rcard__head">
          <h3 class="rcard__title">最近学习</h3>
          <router-link class="rcard__link" to="/student/courses">全部课程</router-link>
        </div>
        <div class="recent">
          <div
            class="recent__row"
            v-for="r in recentCourses"
            :key="r.courseId"
            tabindex="0"
            @click="goCourse(r.courseId)"
            @keyup.enter="goCourse(r.courseId)"
          >
            <div class="recent__thumb">
              <img v-if="r.cover" class="recent__img" :src="r.cover" alt="" @error="onCoverError" />
            </div>
            <div class="recent__col">
              <span class="recent__title">{{ r.name }}</span>
              <span class="recent__meta">{{ r.meta }}</span>
            </div>
          </div>
          <p class="empty" v-if="!planLoading && recentCourses.length === 0">
            {{ isLogin ? '还没有学习记录' : '登录后可查看' }}
          </p>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import moment from 'moment';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store';
import {
  getRecommendClassList,
  getMylessons,
  getMyPlan,
  getSignRecords,
  getLearningDurations,
  pointsSign,
} from '@/api/class.js';
import { parseSignMarks, calcStreak } from '@/utils/signMarks';
import { countMyNotes } from '@/api/notes.js';
import { rememberStudentOrigin } from '@/config/loginRedirect';
import defaultAvatar from '@/assets/icon.jpeg';

const router = useRouter();
const userStore = useUserStore();

// ---------- 用户态 ----------
const isLogin = computed(() => Boolean(userStore.userInfo && userStore.userInfo.name));
const userName = computed(() => userStore.userInfo?.name || '');
const avatar = computed(() => userStore.userInfo?.icon || defaultAvatar);
const onAvatarError = (e) => { e.target.src = defaultAvatar; };
const onCoverError = (e) => { e.target.style.display = 'none'; };

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const plain = (s) => String(s || '').replace(/<\/?em>/g, '');
/** 封面水印：无真实封面时的占位字母 */
const mark = (name) => {
  const latin = plain(name).match(/[A-Za-z][A-Za-z0-9+#.]{1,5}/);
  return latin ? latin[0].toUpperCase() : plain(name).slice(0, 2);
};
const goCourse = (id) => {
  if (!id) return;
  router.push({ path: '/student/courses/detail', query: { id } });
};
const goLogin = () => { rememberStudentOrigin('/student/dashboard'); router.push('/login'); };

// ---------- 推荐课程 ----------
const recLoading = ref(true);
const courses = ref([]);
const hero = computed(() => courses.value[0] || null);
/** 接口无课程简介字段 → 用真实的讲师 / 节数 / 学习人数拼成副标题，不编造文案 */
const heroDesc = computed(() => {
  const c = hero.value;
  if (!c) return '';
  const parts = [];
  if (c.teacher) parts.push(`${c.teacher} 主讲`);
  if (c.sections) parts.push(`共 ${c.sections} 节`);
  if (c.sold) parts.push(`${c.sold} 人正在学`);
  return parts.join(' · ');
});
const courseMeta = (c) => {
  const parts = [];
  if (c.teacher) parts.push(c.teacher);
  if (c.sold) parts.push(`${c.sold} 人学习`);
  return parts.join(' · ');
};

async function loadRecommend() {
  recLoading.value = true;
  try {
    const [resNew, resBest] = await Promise.allSettled([
      getRecommendClassList('new'),
      getRecommendClassList('best'),
    ]);
    const ok = (r) => r.status === 'fulfilled' && r.value?.code == 200 && Array.isArray(r.value.data);
    const list = [...(ok(resNew) ? resNew.value.data : []), ...(ok(resBest) ? resBest.value.data : [])];
    const seen = new Set();
    courses.value = list.filter((c) => c && c.id && !seen.has(c.id) && seen.add(c.id)).slice(0, 6);
  } finally {
    recLoading.value = false;
  }
}

// ---------- 我的课程 / 学习计划 ----------
const lessons = ref([]);
const lessonsTotal = ref(0);
const lessonCoverMap = ref({});

const planList = ref([]);
const planLoading = ref(true);
const planLoaded = ref(false);

async function loadLessons() {
  if (!isLogin.value) return;
  try {
    const res = await getMylessons({ page: 1, pageSize: 20 });
    if (res?.code == 200 && res.data) {
      lessons.value = res.data.list || [];
      lessonsTotal.value = Number(res.data.total) || lessons.value.length;
      const map = {};
      lessons.value.forEach((l) => { if (l.courseId && l.courseCoverUrl) map[l.courseId] = l.courseCoverUrl; });
      lessonCoverMap.value = map;
    }
  } catch (e) { /* 静默：无数据不编造 */ }
}

async function loadPlan() {
  if (!isLogin.value) { planLoading.value = false; planLoaded.value = true; return; }
  try {
    const res = await getMyPlan({ page: 1, pageSize: 6 });
    if (res?.code == 200 && res.data) planList.value = res.data.list || [];
  } catch (e) { /* 静默 */ }
  planLoading.value = false;
  planLoaded.value = true;
}

const pct = (it) => (it.sections ? Math.round(((it.learnedSections || 0) * 100) / it.sections) : 0);
const isDone = (it) => Boolean(it.sections) && (it.learnedSections || 0) >= it.sections;

/** 近期学习记录（按最近学习时间倒序，真实时间来自 learning_lesson.latest_learn_time） */
const recentRows = computed(() =>
  [...planList.value]
    .sort((a, b) => String(b.latestLearnTime || '').localeCompare(String(a.latestLearnTime || '')))
    .slice(0, 4)
    .map((it) => ({
      id: it.id || it.courseId,
      date: it.latestLearnTime ? moment(it.latestLearnTime).format('MM-DD HH:mm') : '--',
      title: it.courseName || '未命名课程',
      sub: `已学 ${it.learnedSections || 0}/${it.sections || 0} 节`,
      done: isDone(it),
    }))
);

/** 最近学习（右栏 4 条） */
const recentCourses = computed(() =>
  [...planList.value]
    .sort((a, b) => String(b.latestLearnTime || '').localeCompare(String(a.latestLearnTime || '')))
    .slice(0, 4)
    .map((it) => ({
      courseId: it.courseId,
      name: it.courseName || '未命名课程',
      cover: lessonCoverMap.value[it.courseId] || '',
      meta: `已学 ${it.learnedSections || 0}/${it.sections || 0} 节 · ${pct(it)}%`,
    }))
);

// ---------- 打卡 / 笔记 ----------
const signed = ref(false);
const signBusy = ref(false);
const signDays = ref(0);
const signText = computed(() => {
  if (!isLogin.value) return '点击登录同步学习进度';
  if (signDays.value > 0) return `已连续打卡 ${signDays.value} 天`;
  return signed.value ? '今日已打卡' : '今天还没有打卡';
});

const noteTotal = ref(null);

async function loadNotes() {
  if (!isLogin.value) return;
  try {
    // P28：改用「我的笔记总数」接口 —— /notes/page 必须带课程/小节，拿它统计会直接 400
    const res = await countMyNotes();
    if (res?.code == 200) noteTotal.value = Number(res.data) || 0;
  } catch (e) { /* 静默 */ }
}

// ---------- 统计（右栏） ----------
const doneCount = computed(() => planList.value.filter(isDone).length);
const weekSections = computed(() => planList.value.reduce((s, it) => s + (it.weekLearnedSections || 0), 0));

const miniStats = computed(() => [
  { label: '累计学习', value: fmtDuration(durationTotal.value) },
  { label: '连续打卡', value: signDays.value ? `${signDays.value} 天` : '—' },
  { label: '学习笔记', value: noteTotal.value == null ? '—' : `${noteTotal.value} 篇` },
]);

const bigStats = computed(() => [
  { label: '已完成课程', value: isLogin.value ? String(doneCount.value) : '—' },
  { label: '笔记数量', value: noteTotal.value == null ? '—' : String(noteTotal.value) },
]);

const gridStats = computed(() => [
  { label: '参与课程', value: isLogin.value ? `${lessonsTotal.value} 门` : '—' },
  { label: '学完课程', value: isLogin.value ? `${doneCount.value} 门` : '—' },
  { label: '完成率', value: isLogin.value && lessonsTotal.value ? `${Math.round((doneCount.value * 100) / lessonsTotal.value)}%` : '—' },
  { label: '考试次数', value: '—' },                        // 后端无考试记录接口
  { label: '通过考试', value: '—' },                        // 同上
  { label: '通过率', value: '—' },                          // 同上
]);

const footStats = computed(() => [
  { label: '本周学习', value: isLogin.value ? `${weekSections.value} 节` : '—' },
  { label: '连续打卡', value: signDays.value ? `${signDays.value} 天` : '—' },
  { label: '日均学习', value: fmtDuration(durationAvg.value) },
]);

// ---------- 学习足迹热力图（近 20 周 × 7 天，格子 14px / 间距 5px） ----------
const HEAT_WEEKS = 20;
const heatWeeks = ref([]);

// ---------- 学习时长（P27）：热力图强度 = 当天学习时长 ----------
const durationMap = ref({});     // { 'YYYY-MM-DD': 秒 }
const durationTotal = ref(null); // 累计（全部时间）
const durationAvg = ref(null);   // 日均（按**有学习的天数**平均）

/** 当天学习时长 → 热力档位：0 无学习 / 1 <15 分钟 / 2 <60 分钟 / 3 ≥60 分钟 */
const heatLevel = (sec) => {
  const s = Number(sec) || 0;
  if (s <= 0) return 0;        // 真的没学过才留空
  const min = s / 60;
  if (min < 15) return 1;      // ⚠️ 有学习就算一档 —— 以前要求满 1 分钟，学了 40 秒会被显示成「未学习」
  if (min < 60) return 2;
  return 3;
};

/** 秒 → 人话（统计区与热力图提示共用同一套说法） */
const fmtDuration = (sec) => {
  if (sec == null) return '—';
  if (sec < 60) return sec > 0 ? `${sec} 秒` : '0 分钟';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} 分钟`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`;
};

/** 热力格子提示：有学习给具体时长，没有就说未学习 */
const heatTip = (cell) => {
  if (!cell) return '';
  const sec = durationMap.value[cell.date] || 0;
  return sec > 0 ? `${cell.date} · ${fmtDuration(sec)}` : `${cell.date} · 未学习`;
};

async function loadDuration() {
  if (!isLogin.value) {
    durationMap.value = {}; durationTotal.value = null; durationAvg.value = null; buildHeat();
    return;
  }
  try {
    const res = await getLearningDurations(HEAT_WEEKS * 7);
    // ⚠️ request 的响应拦截器返回的是**网关包装体** { code, msg, data } ——
    //    少取这一层 data，totalSec 恒为 undefined，页面就一直显示「—」（P28 踩过）
    const d = res && typeof res === 'object' && 'code' in res ? (res.code === 200 ? res.data : null) : res;
    const map = {};
    for (const x of (d?.days || [])) {
      map[String(x.learnDate).slice(0, 10)] = x.durationSec || 0;
    }
    durationMap.value = map;
    durationTotal.value = d?.totalSec ?? null;
    durationAvg.value = d?.avgDailySec ?? null;
    buildHeat();
  } catch (e) {
    durationMap.value = {}; durationTotal.value = null; durationAvg.value = null; buildHeat();
  }
}

/**
 * 学习足迹：强度 = **当天学习时长**（P27）。
 * 原先按「是否签到」两档 —— 签到只说明点过按钮，时长才反映真实投入；
 * 打卡本身是独立功能（连续打卡统计仍用签到数据）。
 */
function buildHeat() {
  const days = HEAT_WEEKS * 7;
  const end = moment().endOf('day');
  const start = end.clone().subtract(days - 1, 'days');
  const today = moment().format('YYYY-MM-DD');
  const pad = (start.day() + 6) % 7;
  const cells = [];
  for (let i = 0; i < pad; i++) cells.push(null);
  for (let d = start.clone(); !d.isAfter(end); d.add(1, 'days')) {
    const ds = d.format('YYYY-MM-DD');
    if (ds > today) continue;
    cells.push({ date: ds, level: heatLevel(durationMap.value[ds]) });
  }
  const weeks = [];
  for (let i = 0; i < cells.length; i += 7) {
    const chunk = cells.slice(i, i + 7);
    while (chunk.length < 7) chunk.push(null);
    weeks.push(chunk);
  }
  heatWeeks.value = weeks;
}

async function loadSign() {
  if (!isLogin.value) { buildHeat(); return; }
  try {
    const res = await getSignRecords();
    // 接口返回的是「本月每天的打卡标记」（下标 0 = 1 号），不是记录对象数组 —— 见 utils/signMarks
    const marks = res?.code == 200 && Array.isArray(res.data) ? res.data : [];
    const set = parseSignMarks(marks);
    signed.value = set.has(moment().format('YYYY-MM-DD'));
    signDays.value = calcStreak(set);
    buildHeat();
  } catch (e) {
    buildHeat();
  }
}

async function doSign() {
  if (!isLogin.value) { goLogin(); return; }
  if (signed.value || signBusy.value) return;
  signBusy.value = true;
  try {
    const res = await pointsSign({});
    if (res?.code == 200) {
      signed.value = true;
      ElMessage.success(`打卡成功${res.data ? `，+${res.data} 积分` : ''}`);
      loadSign();
    } else {
      ElMessage.error(res?.msg || '打卡失败，请稍后再试');
    }
  } catch (e) {
    ElMessage.error('打卡失败，请稍后再试');
  } finally {
    signBusy.value = false;
  }
}

onMounted(() => {
  buildHeat();
  loadDuration();
  loadRecommend();
  loadLessons();
  loadPlan();
  loadNotes();
  loadSign();
});
</script>

<style lang="scss" scoped>
// =============================================================================
// 版面：1096 = 744 + 32 + 320（对齐设计稿 Content 规格）
// =============================================================================
.dash {
  display: flex;
  gap: 32px;
  align-items: flex-start;

  &__col {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 28px;          // 设计稿主栏列间距
  }
  &__side {
    flex: 0 0 320px;
    width: 320px;
    display: flex;
    flex-direction: column;
    gap: 20px;          // 设计稿右栏列间距
  }
}

// =============================================================================
// Hero：744×280，圆角 20，45° 渐变 #0A172B → #12407A(55%) → #2485DB
// 内边距 36；chip→标题 16、标题→描述 8、描述→按钮 24
// =============================================================================
.hero {
  position: relative;
  height: 280px;
  padding: 36px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, #0a172b 0%, #12407a 55%, #2485db 100%);
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  // 纵向为 flex 主轴，高度即主轴尺寸，默认会被压缩 → 全部禁止收缩
  > * { flex: 0 0 auto; }

  &__chip {
    display: inline-flex;
    align-items: center;
    height: 28px;
    padding: 0 22px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
    font-size: 13px;
    font-weight: 500;
    line-height: 18px;
    white-space: nowrap;
  }
  &__title {
    margin: 16px 0 0;
    max-width: 470px;
    font-size: 34px;
    line-height: 44px;
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
    max-width: 470px;
    font-size: 15px;
    line-height: 24px;
    color: rgba(255, 255, 255, 0.77);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-top: 24px;
    height: 40px;
    padding: 0 26px;
    border: 0;
    border-radius: 20px;
    background: #0066cc;
    color: #fff;
    font-size: 15px;
    font-weight: 500;
    line-height: 20px;
    text-decoration: none;
    cursor: pointer;
    transition: background-color 0.16s ease;

    &:hover { background: #0071e3; }
    &:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
  }
}

// =============================================================================
// 区块：标题 21/28 SemiBold；标题行与内容间隔 16
// =============================================================================
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
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #0066cc;
    text-decoration: none;
    white-space: nowrap;

    svg { width: 14px; height: 14px; }
    &:hover { text-decoration: underline; }
  }
}

// =============================================================================
// 推荐课程：wrap 间距 16，卡片 237×192（封面 237×134 圆角 12 + 卡内间距 10）
// 设计稿卡片无底色/描边，封面与文字直接落在画布上
// =============================================================================
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  min-height: 60px;
}

.cc {
  flex: 0 0 calc((100% - 32px) / 3);
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;

  &__cover {
    position: relative;
    aspect-ratio: 237 / 134;
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
    font-size: 44px;
    line-height: 54px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.14);
    user-select: none;
  }
  &__name {
    margin: 0;
    font-size: 15px;
    line-height: 21px;
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
// 学习足迹卡：圆角 18、内边距 24；行间距 24（热力图列 + 统计列）
// 热力图：20 周 × 7 天，格子 14px、间距 5px = 375×128；圆角 3.5
// =============================================================================
.foot {
  padding: 24px;
  border-radius: 18px;

  &__row {
    display: flex;
    gap: 24px;
    align-items: flex-start;
  }
  &__heat {
    flex: 0 0 375px;
    width: 375px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  &__stats {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;

  &__txt { font-size: 12px; line-height: 16px; color: #86868b; }
  .cell {
    display: block;
    width: 12px;
    height: 12px;
    border-radius: 3px;
  }
  .lv0 { background: #edeef3; }
  .lv1 { background: #cfe3fa; }
  .lv2 { background: #7fb0e8; }
  .lv3 { background: #0066cc; }
}

.heat {
  display: flex;
  gap: 5px;

  &__col { display: flex; flex-direction: column; gap: 5px; }
  &__cell {
    display: block;
    width: 14px;
    height: 14px;
    border-radius: 3.5px;
    transition: outline-color 0.15s ease;
    outline: 1.5px solid transparent;

    &.lv0 { background: #edeef3; }
    &.lv1 { background: #cfe3fa; }
    &.lv2 { background: #7fb0e8; }
    &.lv3 { background: #0066cc; }
    &.is-empty { background: transparent; }
    &:hover { outline-color: #0066cc; }
  }
}

.fstat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;

  &__label { font-size: 13px; line-height: 18px; color: #86868b; }
  &__value { font-size: 17px; line-height: 22px; font-weight: 600; color: #1d1d1f; }
}

// =============================================================================
// 近期学习记录卡：圆角 18、内边距 20、行间距 14、行内列间距 16（日期列宽 88）
// =============================================================================
.rec {
  padding: 20px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;

  &__row {
    display: flex;
    align-items: center;
    gap: 16px;
  }
  &__date {
    flex: 0 0 88px;
    width: 88px;
    font-size: 12px;
    line-height: 18px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }
  &__col {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  &__title {
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__sub {
    font-size: 11px;
    line-height: 15px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__status {
    flex: 0 0 auto;
    font-size: 12px;
    line-height: 17px;
    font-weight: 500;

    &.is-done { color: #34c759; }
    &.is-doing { color: #ff9f0a; }
  }
}

// =============================================================================
// 右栏卡片：圆角 18、内边距 20
// =============================================================================
.rcard {
  padding: 20px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  &__title {
    margin: 0;
    font-size: 15px;
    line-height: 20px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__link {
    font-size: 12px;
    line-height: 17px;
    font-weight: 500;
    color: #0066cc;
    text-decoration: none;
    white-space: nowrap;

    &:hover { text-decoration: underline; }
  }
}

// 基本信息卡：卡内间距 16
.userRow {
  display: flex;
  align-items: center;
  gap: 12px;

  &__avatar {
    width: 52px;
    height: 52px;
    flex: 0 0 52px;
    border-radius: 50%;
    object-fit: cover;
    background: #d8dee8;
  }
  &__col {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  &__greet {
    font-size: 17px;
    line-height: 23px;
    font-weight: 600;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__sub {
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.signBtn {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 18px;
  border: 0;
  border-radius: 16px;
  background: #0066cc;
  color: #fff;
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover:not(:disabled) { background: #0071e3; }
  &:disabled { cursor: default; }
  &.is-done { background: #e8f1fc; color: #0066cc; }
}

.miniStats {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &__value { font-size: 15px; line-height: 20px; font-weight: 600; color: #1d1d1f; }
  &__label { font-size: 12px; line-height: 17px; color: #86868b; }
}

// 学习数据卡：大卡 24/30、小格 17/22；间距 12、内边距 14、圆角 12
.bigStats {
  display: flex;
  gap: 12px;
}

.big {
  flex: 1 1 0;
  min-width: 0;
  padding: 14px;
  border-radius: 12px;
  background: #f5f5f7;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__value { font-size: 24px; line-height: 30px; font-weight: 600; color: #1d1d1f; }
  &__label { font-size: 12px; line-height: 17px; color: #86868b; }
}

.statGrid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.gcell {
  flex: 0 0 calc((100% - 12px) / 2);
  padding: 14px;
  border-radius: 12px;
  background: #fafafc;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__value { font-size: 17px; line-height: 22px; font-weight: 600; color: #1d1d1f; }
  &__label { font-size: 12px; line-height: 17px; color: #86868b; }
}

// 最近学习：缩略图 48×32（圆角 6）+ 文本列，行间距 10
.recent {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__row {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;

    &:hover .recent__title { color: #0066cc; }
    &:focus-visible { outline: 2px solid #0071e3; outline-offset: 2px; border-radius: 6px; }
  }
  &__thumb {
    position: relative;
    flex: 0 0 48px;
    width: 48px;
    height: 32px;
    border-radius: 6px;
    overflow: hidden;
    background: linear-gradient(135deg, #0d2340 0%, #2f5e8f 100%);
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
    gap: 3px;
  }
  &__title {
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
  padding: 8px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// =============================================================================
// 窄屏：右栏下沉、课程栅格降为 2 列
// =============================================================================
@media (max-width: 1180px) {
  .dash {
    flex-direction: column;
    gap: 24px;

    &__side { flex: 0 0 auto; width: 100%; }
  }
  .foot__heat { flex: 0 0 auto; width: 100%; }
  .heat { overflow-x: auto; }
}

@media (max-width: 860px) {
  .cc { flex: 0 0 calc((100% - 16px) / 2); }
  .foot__row { flex-direction: column; }
}
</style>
