<!--
 * 学习记录（/student/records）— 严格对齐设计稿 03
 * 设计稿规格（Ardot fileId 725009394574981，frame「03 学习记录」）：
 *   Content 1096，横向间距 32
 *   左 415：Card / 学习概览（圆角 18、内边距 20、纵向间距 18）
 *           Head(标题 15/20 SemiBold + 时间范围 74×28 圆角14)
 *           → 5 条统计（标签 13/18 #86868B 左、值 15/20 SemiBold 右）
 *           → Divider 1px → 「学习足迹」13/18 SemiBold → 热力图 375×128
 *   右 649：Card / 学习明细（圆角 18、内边距 24、纵向间距 20）
 *           Head(标题 15/20 SemiBold + 搜索框 200×34 圆角17)
 *           → 日期分组 12/17 #86868B → 条目（时间列 52、13/18 Medium｜标题列｜状态 48、12/17 Medium）
 * 数据契约与降级：
 *   - getMyPlan → {list:[{courseId,courseName,sections,learnedSections,latestLearnTime}]}
 *     （真实最近学习时间来自 learning_lesson.latest_learn_time）
 *   - getMylessons → {total}（在学课程数）
 *   - getSignRecords → 打卡记录（连续打卡 / 近七天活跃天数 / 热力图）
 *   - 「累计学习时长」后端无字段 → 显示「—」，保留版式不编造
 -->
<template>
  <div class="rec">
    <!-- ============ 左：学习概览 ============ -->
    <aside class="rec__left">
      <section class="s-card ov">
        <div class="ov__head">
          <h2 class="cardTitle">学习概览</h2>
          <div class="range">
            <select v-model.number="rangeDays" class="range__sel" aria-label="统计时间范围">
              <option :value="7">近 7 天</option>
              <option :value="30">近 30 天</option>
              <option :value="90">近 90 天</option>
            </select>
            <svg class="range__caret" viewBox="0 0 10 6" fill="none" aria-hidden="true">
              <path d="m1 1.5 4 3.5 4-3.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>

        <div class="ov__stat" v-for="s in overview" :key="s.label">
          <span class="ov__label">{{ s.label }}</span>
          <span class="ov__value">{{ s.value }}</span>
        </div>

        <div class="ov__divider"></div>

        <h3 class="ov__sub">学习足迹</h3>
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
      </section>
    </aside>

    <!-- ============ 右：学习明细 ============ -->
    <section class="s-card detail">
      <div class="detail__head">
        <h2 class="cardTitle">学习明细</h2>
        <label class="detail__search">
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
            <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <input v-model.trim="keyword" type="search" placeholder="搜索课程" aria-label="搜索学习明细" maxlength="30" />
        </label>
      </div>

      <p class="empty" v-if="!isLogin">登录后可查看学习记录</p>
      <p class="empty" v-else-if="!loading && groups.length === 0">
        {{ keyword ? '没有匹配的课程' : '所选时间范围内还没有学习记录' }}
      </p>

      <div class="detail__body" v-loading="loading">
        <template v-for="group in groups" :key="group.date">
          <p class="detail__date">{{ group.date }}</p>
          <div
            class="entry"
            v-for="e in group.items"
            :key="e.key"
            tabindex="0"
            @click="goLearn(e.courseId)"
            @keyup.enter="goLearn(e.courseId)"
          >
            <span class="entry__time">{{ e.time }}</span>
            <div class="entry__col">
              <span class="entry__title" :title="e.title">{{ e.title }}</span>
              <span class="entry__sub">{{ e.sub }}</span>
            </div>
            <span class="entry__status" :class="e.done ? 'is-done' : 'is-doing'">{{ e.done ? '已完成' : '进行中' }}</span>
          </div>
        </template>
      </div>

      <div class="detail__foot" v-if="!keyword && total > PAGE_SIZE">
        <SPagination v-model="pageNo" :total="total" :page-size="PAGE_SIZE" @change="loadPlan" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import moment from 'moment';
import { useUserStore } from '@/store';
import { getMyPlan, getMylessons, getSignRecords, getLearningDurations } from '@/api/class.js';
import { parseSignMarks } from '@/utils/signMarks';
import SPagination from '@/components/shell/SPagination.vue';

const router = useRouter();
const userStore = useUserStore();
const isLogin = computed(() => Boolean(userStore.userInfo && userStore.userInfo.name));

const PAGE_SIZE = 10;
const pageNo = ref(1);
const loading = ref(false);

const plans = ref([]);
const total = ref(0);
const lessonsTotal = ref(null);
const signList = ref([]);

const keyword = ref('');
const rangeDays = ref(7);

const goLearn = (courseId) => { if (courseId) router.push({ path: '/student/learn', query: { id: courseId } }); };
const isDone = (it) => Boolean(it.sections) && (it.learnedSections || 0) >= it.sections;

// ---------- 统计 ----------
const signedDays = computed(() => new Set(signList.value.map((r) => r.day)));

/** 连续打卡：从今天（或昨天）向前逐日回溯 */
const streak = computed(() => {
  if (!signedDays.value.size) return 0;
  let d = moment();
  if (!signedDays.value.has(d.format('YYYY-MM-DD'))) d = d.subtract(1, 'days');
  let n = 0;
  while (signedDays.value.has(d.format('YYYY-MM-DD'))) { n++; d = d.subtract(1, 'days'); }
  return n;
});

/** 近七天活跃天数（真实打卡记录） */
const activeLast7 = computed(() => {
  let n = 0;
  for (let i = 0; i < 7; i++) {
    if (signedDays.value.has(moment().subtract(i, 'days').format('YYYY-MM-DD'))) n++;
  }
  return n;
});

const doneCount = computed(() => plans.value.filter(isDone).length);

const overview = computed(() => {
  const login = isLogin.value;
  return [
    { label: '累计学习时长', value: fmtDuration(durationTotal.value) },
    { label: '正在学习课程', value: login && lessonsTotal.value != null ? `${lessonsTotal.value} 门` : '—' },
    { label: '已完成课程', value: login ? `${doneCount.value} 门` : '—' },
    { label: '连续打卡', value: streak.value ? `${streak.value} 天` : (login ? '0 天' : '—') },
    { label: '近七天活跃天数', value: login ? `${activeLast7.value} 天` : '—' },
  ];
});

// ---------- 学习时长（P27）：热力图强度 = 当天学习时长 ----------
const durationMap = ref({});     // { 'YYYY-MM-DD': 秒 }
const durationTotal = ref(null); // 累计（全部时间）

/** 当天学习时长 → 热力档位：0 无学习 / 1 <15 分钟 / 2 <60 分钟 / 3 ≥60 分钟 */
const heatLevel = (sec) => {
  const s = Number(sec) || 0;
  if (s <= 0) return 0;        // 真的没学过才留空
  const min = s / 60;
  if (min < 15) return 1;      // ⚠️ 有学习就算一档 —— 以前要求满 1 分钟，学了 40 秒会被显示成「未学习」
  if (min < 60) return 2;
  return 3;
};

/** 秒 → 人话 */
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
  if (!isLogin.value) { durationMap.value = {}; durationTotal.value = null; buildHeat(); return; }
  try {
    const res = await getLearningDurations(HEAT_WEEKS * 7);
    // ⚠️ request 拦截器返回网关包装体 { code, msg, data }，必须取 .data（P28 踩过）
    const d = res && typeof res === 'object' && 'code' in res ? (res.code === 200 ? res.data : null) : res;
    const map = {};
    for (const x of (d?.days || [])) map[String(x.learnDate).slice(0, 10)] = x.durationSec || 0;
    durationMap.value = map;
    durationTotal.value = d?.totalSec ?? null;
  } catch (e) {
    durationMap.value = {};
    durationTotal.value = null;
  }
  buildHeat();
}

// ---------- 学习足迹热力图（近 20 周 × 7 天；格 14、间距 5、圆角 3.5） ----------
const HEAT_WEEKS = 20;
const heatWeeks = ref([]);

/** 强度 = 当天学习时长（P27）。原先按「是否签到」两档 —— 签到只说明点过按钮，时长才反映投入。 */
function buildHeat() {
  const days = HEAT_WEEKS * 7;
  const end = moment().endOf('day');
  const start = end.clone().subtract(days - 1, 'days');
  const today = moment().format('YYYY-MM-DD');
  const pad = (start.day() + 6) % 7;
  const cells = [];
  for (let i = 0; i < pad; i++) cells.push(null);
  for (let d = start.clone(); !d.isAfter(end); d = d.add(1, 'days')) {
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

// ---------- 学习明细（按真实最近学习时间分组） ----------
const groups = computed(() => {
  const from = moment().subtract(rangeDays.value - 1, 'days').startOf('day');
  const list = plans.value.filter((it) => {
    if (!it.latestLearnTime) return false;
    if (keyword.value && !String(it.courseName || '').includes(keyword.value)) return false;
    return moment(it.latestLearnTime).isSameOrAfter(from);
  });
  const map = new Map();
  for (const it of list) {
    const m = moment(it.latestLearnTime);
    const day = m.format('YYYY-MM-DD');
    if (!map.has(day)) map.set(day, []);
    map.get(day).push({
      key: `${it.courseId}-${day}`,
      courseId: it.courseId,
      time: m.format('HH:mm'),
      title: it.courseName || '未命名课程',
      sub: `已学 ${it.learnedSections || 0}/${it.sections || 0} 节`,
      done: isDone(it),
    });
  }
  return [...map.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([date, items]) => ({
      date,
      items: items.sort((a, b) => b.time.localeCompare(a.time)),
    }));
});

// ---------- 加载 ----------
async function loadPlan() {
  if (!isLogin.value) return;
  loading.value = true;
  try {
    const res = await getMyPlan({ page: pageNo.value, pageSize: PAGE_SIZE });
    if (res?.code == 200 && res.data) {
      plans.value = res.data.list || [];
      total.value = Number(res.data.total) || plans.value.length;
    } else {
      plans.value = [];
      total.value = 0;
    }
  } catch (e) {
    plans.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadLessons() {
  if (!isLogin.value) return;
  try {
    const res = await getMylessons({ page: 1, pageSize: 1 });
    if (res?.code == 200 && res.data) lessonsTotal.value = Number(res.data.total) || 0;
  } catch (e) { /* 静默 */ }
}

async function loadSign() {
  if (!isLogin.value) { buildHeat(); return; }
  try {
    const res = await getSignRecords();
    // 接口返回的是「本月每天的打卡标记」（下标 0 = 1 号），不是记录对象数组 —— 见 utils/signMarks
    const marks = res?.code == 200 && Array.isArray(res.data) ? res.data : [];
    signList.value = [...parseSignMarks(marks)].map((day) => ({ day }));
  } catch (e) {
    signList.value = [];
  }
  buildHeat();
}

onMounted(() => {
  buildHeat();
  loadDuration();
  loadPlan();
  loadLessons();
  loadSign();
});
</script>

<style lang="scss" scoped>
// =============================================================================
// 版面：Content 1096 = 学习概览 415 + 间距 32 + 学习明细 649
// =============================================================================
.rec {
  display: flex;
  gap: 32px;
  align-items: flex-start;

  &__left {
    flex: 0 0 415px;
    width: 415px;
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
}

.cardTitle {
  margin: 0;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  color: #1d1d1f;
}

.empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// =============================================================================
// 学习概览卡：圆角 18、内边距 20、纵向间距 18
// =============================================================================
.ov {
  padding: 20px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  &__stat {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
  }
  &__label { font-size: 13px; line-height: 18px; color: #86868b; }
  &__value { font-size: 15px; line-height: 20px; font-weight: 600; color: #1d1d1f; font-variant-numeric: tabular-nums; }
  &__divider { height: 1px; background: #f0f0f0; }
  &__sub {
    margin: 0;
    font-size: 13px;
    line-height: 18px;
    font-weight: 600;
    color: #1d1d1f;
  }
}

// 时间范围选择（设计稿 74×28 圆角 14）
.range {
  position: relative;
  flex: 0 0 auto;
  height: 28px;
  border-radius: 14px;
  background: #f5f5f7;

  &__sel {
    appearance: none;
    -webkit-appearance: none;
    height: 28px;
    padding: 0 26px 0 14px;
    border: 0;
    border-radius: 14px;
    background: transparent;
    color: #1d1d1f;
    font-size: 12px;
    line-height: 17px;
    font-family: inherit;
    cursor: pointer;
    outline: none;

    &:focus-visible { box-shadow: inset 0 0 0 1.5px #0066cc; }
  }
  &__caret {
    position: absolute;
    right: 10px;
    top: 50%;
    width: 10px;
    height: 6px;
    transform: translateY(-50%);
    color: #86868b;
    pointer-events: none;
  }
}

// 热力图：20 周 × 7 天，格 14、间距 5、圆角 3.5 = 375×128
.heat {
  display: flex;
  gap: 5px;
  width: 375px;

  &__col { display: flex; flex-direction: column; gap: 5px; }
  &__cell {
    display: block;
    width: 14px;
    height: 14px;
    border-radius: 3.5px;
    outline: 1.5px solid transparent;
    transition: outline-color 0.15s ease;

    &.lv0 { background: #edeef3; }
    &.lv1 { background: #cfe3fa; }
    &.lv2 { background: #7fb0e8; }
    &.lv3 { background: #0066cc; }
    &.is-empty { background: transparent; }
    &:hover { outline-color: #0066cc; }
  }
}

// =============================================================================
// 学习明细卡：圆角 18、内边距 24、纵向间距 20
// =============================================================================
.detail {
  flex: 1 1 auto;
  min-width: 0;
  padding: 24px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }
  // 搜索框 200×34 圆角 17
  &__search {
    flex: 0 0 200px;
    width: 200px;
    height: 34px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 14px;
    border-radius: 17px;
    background: #f5f5f7;
    transition: box-shadow 0.16s ease, background-color 0.16s ease;

    &:focus-within { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
    svg { flex: 0 0 14px; width: 14px; height: 14px; color: #86868b; }
    input {
      flex: 1 1 auto;
      min-width: 0;
      border: 0;
      outline: 0;
      background: transparent;
      font-size: 12px;
      line-height: 16px;
      color: #1d1d1f;
      font-family: inherit;

      &::placeholder { color: #86868b; }
    }
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 14px;
    min-height: 120px;
  }
  &__date {
    margin: 0;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
  }
  &__foot {
    display: flex;
    justify-content: center;
  }
}

// 条目：时间列 52 + 标题列 + 状态列 48
.entry {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;

  &:hover .entry__title { color: #0066cc; }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: 2px; border-radius: 6px; }

  &__time {
    flex: 0 0 52px;
    width: 52px;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #1d1d1f;
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
    transition: color 0.15s ease;
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
    flex: 0 0 48px;
    width: 48px;
    text-align: right;
    font-size: 12px;
    line-height: 17px;
    font-weight: 500;

    &.is-done { color: #34c759; }
    &.is-doing { color: #ff9f0a; }
  }
}

// =============================================================================
// 窄屏
// =============================================================================
@media (max-width: 1180px) {
  .rec { flex-direction: column; }
  .rec__left { flex: 0 0 auto; width: 100%; }
  .heat { overflow-x: auto; }
}

@media (max-width: 860px) {
  .detail__head { flex-direction: column; align-items: flex-start; }
  .detail__search { flex: 0 0 34px; width: 100%; }
}
</style>
