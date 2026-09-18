<!--
 * 我的积分（/student/points）
 * 复用老项目 4 个积分页面的接口逻辑，统一收敛为一个页签页：
 *   积分清单（getTodayPoints + getUserCurrentPoints + pointsSign 签到）
 *   学霸天梯榜（getSeasons /ls/boards?season=0 本赛季榜）
 *   积分商城（getMallItems + exchangeItem 兑换，需收货地址与手机号）
 *   兑换记录（queryExchangeRecordsByUser + cancelExchangeRecordById，仅「待发货」可取消）
 * 说明：历史赛季接口 /ls/boards/seasons/list 后端不存在（404），故只展示本赛季。
 -->
<template>
  <div class="pt">
    <!-- ============ 顶部：积分概览卡 ============ -->
    <section class="s-card sumCard">
      <div class="sumCard__left">
        <p class="sumCard__label">当前积分</p>
        <p class="sumCard__value">{{ myPoints }}</p>
        <p class="sumCard__today">今日已获 {{ todayTotal }} 积分</p>
      </div>
      <div class="sumCard__right">
        <button
          class="sumCard__sign"
          type="button"
          :disabled="signed || signing"
          @click="doSign"
        >
          <svg viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path d="M15 9A6 6 0 1 1 3 9a6 6 0 0 1 12 0Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="m6.2 9.2 1.9 1.9 3.7-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ signed ? '今日已签到' : signing ? '签到中…' : '每日签到 +1' }}
        </button>
        <p class="sumCard__hint">签到、学习、问答、笔记、评价均可获得积分</p>
      </div>
    </section>

    <!-- ============ 页签卡 ============ -->
    <section class="s-card tabCard">
      <div class="tabCard__tabs" role="tablist" aria-label="积分功能">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ 'is-on': tab === t.key }"
          type="button"
          role="tab"
          :aria-selected="tab === t.key"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- ---- 积分清单 ---- -->
      <div v-if="tab === 'list'" class="pane" v-loading="todayLoading">
        <template v-if="accessList.length">
          <div v-for="a in accessList" :key="a.type" class="accRow">
            <div class="accRow__info">
              <p class="accRow__type">{{ a.type }}</p>
              <p class="accRow__desc">今日 {{ a.points }} / {{ a.maxPoints > 0 ? a.maxPoints : '不限' }} 积分</p>
            </div>
            <div class="accRow__bar" role="progressbar" :aria-valuenow="a.points" :aria-valuemax="a.maxPoints || undefined">
              <div class="accRow__barInner" :style="{ width: barWidth(a) }"></div>
            </div>
            <span class="accRow__points" :class="{ 'is-done': a.maxPoints > 0 && a.points >= a.maxPoints }">
              +{{ a.points }}
            </span>
          </div>
        </template>
        <div v-else-if="!todayLoading" class="emptyBox">
          <p class="emptyBox__title">今天还没有获得积分</p>
          <p class="emptyBox__desc">签到或开始学习，赚取今日第一笔积分。</p>
        </div>
      </div>

      <!-- ---- 学霸天梯榜 ---- -->
      <div v-else-if="tab === 'rank'" class="pane" v-loading="rankLoading">
        <template v-if="board">
          <div class="myRank">
            <span class="myRank__label">我的本赛季排名</span>
            <span class="myRank__rank">第 {{ board.rank ?? '—' }} 名</span>
            <span class="myRank__points">{{ board.points ?? 0 }} 积分</span>
          </div>
          <div v-for="(r, i) in board.boardList || []" :key="i" class="rankRow" :class="{ 'is-me': r.rank === board.rank }">
            <span class="rankRow__no" :class="'is-top' + (i < 3 ? i + 1 : '')">{{ r.rank }}</span>
            <span class="rankRow__name">{{ r.name || '匿名学员' }}</span>
            <span class="rankRow__points">{{ r.points }} 积分</span>
          </div>
          <p v-if="!(board.boardList || []).length" class="emptyBox__desc">本赛季暂无上榜数据</p>
        </template>
        <div v-else-if="!rankLoading" class="emptyBox">
          <p class="emptyBox__title">天梯榜数据不可用</p>
          <p class="emptyBox__desc">请稍后重试。</p>
        </div>
      </div>

      <!-- ---- 积分商城 ---- -->
      <div v-else-if="tab === 'mall'" class="pane" v-loading="mallLoading">
        <template v-if="mallList.length">
          <div class="mallGrid">
            <article v-for="m in mallList" :key="m.id" class="mallItem">
              <div class="mallItem__icon">
                <img v-if="m.icon" :src="m.icon" :alt="m.name" />
                <svg v-else viewBox="0 0 32 32" fill="none" aria-hidden="true">
                  <path d="M6 12.5 16 6l10 6.5v11a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-11Z" stroke="#0066cc" stroke-width="1.8" stroke-linejoin="round"/>
                  <path d="M6 12.5 16 19l10-6.5M16 19v7" stroke="#0066cc" stroke-width="1.8" stroke-linejoin="round"/>
                </svg>
              </div>
              <p class="mallItem__name">{{ m.name }}</p>
              <p class="mallItem__desc">{{ m.description || '积分兑换商品' }}</p>
              <div class="mallItem__foot">
                <span class="mallItem__points">{{ m.points }} 积分</span>
                <span class="mallItem__stock">库存 {{ m.stock }}</span>
              </div>
              <button
                class="mallItem__btn"
                type="button"
                :disabled="m.stock <= 0 || myPoints < m.points"
                @click="openExchange(m)"
              >
                {{ m.stock <= 0 ? '已兑完' : myPoints < m.points ? '积分不足' : '立即兑换' }}
              </button>
            </article>
          </div>
        </template>
        <div v-else-if="!mallLoading" class="emptyBox">
          <p class="emptyBox__title">商城暂无可兑换商品</p>
          <p class="emptyBox__desc">敬请期待上新。</p>
        </div>
      </div>

      <!-- ---- 兑换记录 ---- -->
      <div v-else class="pane" v-loading="recordLoading">
        <template v-if="recordList.length">
          <div class="recHead">
            <span class="recHead__c1">商品</span>
            <span class="recHead__c2">消耗积分</span>
            <span class="recHead__c3">兑换时间</span>
            <span class="recHead__c4">状态</span>
            <span class="recHead__c5">快递单号</span>
            <span class="recHead__c6">操作</span>
          </div>
          <div v-for="r in recordList" :key="r.id" class="recRow">
            <span class="recRow__c1" :title="r.itemName">{{ r.itemName }}</span>
            <span class="recRow__c2">{{ r.pointsUsed }}</span>
            <span class="recRow__c3">{{ r.createTime }}</span>
            <span class="recRow__c4">
              <span class="recStatus" :class="'is-s' + r.status">{{ statusText(r.status) }}</span>
            </span>
            <span class="recRow__c5">{{ r.expressNumber || '—' }}</span>
            <span class="recRow__c6">
              <button
                v-if="r.status === 0"
                class="recCancel"
                type="button"
                @click="cancelExchange(r)"
              >取消兑换</button>
              <span v-else class="recRow__none">—</span>
            </span>
          </div>
          <div v-if="recordTotal > recordParams.pageSize" class="pagerRow">
            <SPagination v-model="recordParams.pageNo" :total="recordTotal" :page-size="recordParams.pageSize" @change="loadRecords" />
          </div>
        </template>
        <div v-else-if="!recordLoading" class="emptyBox">
          <p class="emptyBox__title">暂无兑换记录</p>
          <p class="emptyBox__desc">去积分商城看看有什么可以兑换的。</p>
        </div>
      </div>
    </section>

    <!-- ============ 兑换弹窗 ============ -->
    <el-dialog
      v-model="exchangeVisible"
      :title="`兑换「${exchangeTarget?.name || ''}」`"
      width="420px"
      :close-on-click-modal="false"
      align-center
    >
      <div class="exForm">
        <p class="exForm__tip">
          本次兑换将消耗 <b>{{ exchangeTarget?.points }}</b> 积分（当前 {{ myPoints }} 分）
        </p>
        <label class="exForm__label" for="ex-address">收货地址</label>
        <input id="ex-address" v-model.trim="exchangeForm.address" class="exForm__input" maxlength="80" placeholder="请输入收货地址" />
        <label class="exForm__label" for="ex-phone">联系手机号</label>
        <input id="ex-phone" v-model.trim="exchangeForm.phone" class="exForm__input" maxlength="11" placeholder="用于接收发货通知" />
      </div>
      <template #footer>
        <button class="dlgBtn" type="button" @click="exchangeVisible = false">取消</button>
        <button class="dlgBtn dlgBtn--primary" type="button" :disabled="exchanging" @click="doExchange">
          {{ exchanging ? '兑换中…' : '确认兑换' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getTodayPoints,
  getUserCurrentPoints,
  pointsSign,
  getSeasons,
  getMallItems,
  exchangeItem,
  queryExchangeRecordsByUser,
  cancelExchangeRecordById,
} from '@/api/class.js';
import SPagination from '@/components/shell/SPagination.vue';

const tabs = [
  { key: 'list', label: '积分清单' },
  { key: 'rank', label: '学霸天梯榜' },
  { key: 'mall', label: '积分商城' },
  { key: 'records', label: '兑换记录' },
];
const tab = ref('list');

// ---- 概览：当前积分 / 今日积分 / 签到 ----
const myPoints = ref(0);
const todayList = ref([]);
const todayLoading = ref(false);
const signed = ref(false);
const signing = ref(false);

const todayTotal = computed(() => todayList.value.reduce((s, t) => s + (t.points || 0), 0));

// 积分类型完整列表（与老项目一致），接口只返回已有记录的类型，这里补全展示
const ACCESS_TYPES = ['课程学习', '每日签到', '课程问答', '课程笔记', '课程评价'];
const accessList = computed(() => {
  const map = {};
  todayList.value.forEach((t) => { map[t.type] = t; });
  const merged = ACCESS_TYPES.map((type) => map[type] || { type, points: 0, maxPoints: 0 });
  // 接口若返回了列表外的新类型，也如实展示
  todayList.value.forEach((t) => {
    if (!ACCESS_TYPES.includes(t.type)) merged.push(t);
  });
  return merged;
});

const barWidth = (a) => {
  if (!a.maxPoints || a.maxPoints <= 0) return a.points > 0 ? '100%' : '0%';
  return Math.min(100, Math.round((a.points / a.maxPoints) * 100)) + '%';
};

const loadPoints = async () => {
  try {
    const res = await getUserCurrentPoints();
    if (res?.code === 200) myPoints.value = Number(res.data) || 0;
  } catch (e) { /* 保留 0 */ }
};

const loadToday = async () => {
  todayLoading.value = true;
  try {
    const res = await getTodayPoints();
    if (res?.code === 200 && Array.isArray(res.data)) {
      todayList.value = res.data;
      // 今日签到类已有积分 → 视为已签到
      signed.value = res.data.some((t) => t.type === '每日签到' && t.points > 0);
    }
  } catch (e) {
    todayList.value = [];
  } finally {
    todayLoading.value = false;
  }
};

const doSign = async () => {
  signing.value = true;
  try {
    const res = await pointsSign();
    if (res?.code === 200) {
      ElMessage.success('签到成功，积分 +1');
      signed.value = true;
    } else {
      // 后端「不允许重复签到」也视为已签到
      signed.value = true;
      ElMessage.info(res?.msg || '今日已签到');
    }
    loadPoints();
    loadToday();
  } catch (e) {
    ElMessage.error('签到失败，请稍后重试');
  } finally {
    signing.value = false;
  }
};

// ---- 学霸天梯榜（本赛季；历史赛季接口后端不存在） ----
const board = ref(null);
const rankLoading = ref(false);
const rankLoaded = ref(false);

const loadBoard = async () => {
  rankLoading.value = true;
  try {
    const res = await getSeasons({ season: 0, pageNo: 1, pageSize: 10 });
    if (res?.code === 200 && res.data) board.value = res.data;
  } catch (e) {
    board.value = null;
  } finally {
    rankLoading.value = false;
    rankLoaded.value = true;
  }
};

// ---- 积分商城 ----
const mallList = ref([]);
const mallLoading = ref(false);
const mallLoaded = ref(false);

const loadMall = async () => {
  mallLoading.value = true;
  try {
    const res = await getMallItems({ pageNo: 1, pageSize: 20 });
    if (res?.code === 200 && res.data) mallList.value = res.data.list || [];
  } catch (e) {
    mallList.value = [];
  } finally {
    mallLoading.value = false;
    mallLoaded.value = true;
  }
};

// 兑换
const exchangeVisible = ref(false);
const exchangeTarget = ref(null);
const exchanging = ref(false);
const exchangeForm = reactive({ address: '', phone: '' });

const openExchange = (item) => {
  exchangeTarget.value = item;
  exchangeForm.address = '';
  exchangeForm.phone = '';
  exchangeVisible.value = true;
};

const doExchange = async () => {
  if (!exchangeForm.address) { ElMessage.warning('请填写收货地址'); return; }
  if (!/^1\d{10}$/.test(exchangeForm.phone)) { ElMessage.warning('请填写 11 位手机号'); return; }
  exchanging.value = true;
  try {
    const res = await exchangeItem({
      itemId: exchangeTarget.value.id,
      address: exchangeForm.address,
      phone: exchangeForm.phone,
    });
    if (res?.code === 200) {
      ElMessage.success('兑换成功');
      exchangeVisible.value = false;
      loadPoints();
      loadMall();
      recordLoaded.value = false; // 下次进入记录页时刷新
    } else {
      ElMessage.error(res?.msg || res?.message || '兑换失败');
    }
  } catch (e) {
    ElMessage.error('兑换失败，请稍后重试');
  } finally {
    exchanging.value = false;
  }
};

// ---- 兑换记录 ----
const recordList = ref([]);
const recordTotal = ref(0);
const recordLoading = ref(false);
const recordLoaded = ref(false);
const recordParams = reactive({ pageNo: 1, pageSize: 10 });

const statusText = (s) => ({ 0: '待发货', 1: '已发货', 2: '已完成', 3: '已取消' }[s] ?? '未知');

const loadRecords = async () => {
  recordLoading.value = true;
  try {
    const res = await queryExchangeRecordsByUser(recordParams);
    if (res?.code === 200 && res.data) {
      recordList.value = res.data.list || [];
      recordTotal.value = Number(res.data.total) || 0;
    }
  } catch (e) {
    recordList.value = [];
    recordTotal.value = 0;
  } finally {
    recordLoading.value = false;
    recordLoaded.value = true;
  }
};

const cancelExchange = (r) => {
  ElMessageBox.confirm(`确定取消兑换「${r.itemName}」吗？积分将退回。`, '取消兑换', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning',
  })
    .then(async () => {
      const res = await cancelExchangeRecordById(r.id);
      if (res?.code === 200) {
        ElMessage.success('已取消兑换');
        loadPoints();
        loadRecords();
      } else {
        ElMessage.error(res?.msg || '取消失败');
      }
    })
    .catch(() => {});
};

// ---- 页签切换（按需加载） ----
const switchTab = (k) => {
  tab.value = k;
  if (k === 'rank' && !rankLoaded.value) loadBoard();
  if (k === 'mall' && !mallLoaded.value) loadMall();
  if (k === 'records' && !recordLoaded.value) loadRecords();
};

onMounted(() => {
  loadPoints();
  loadToday();
});
</script>

<style lang="scss" scoped>
.pt {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

// ---- 顶部概览卡 ----
.sumCard {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  background: linear-gradient(135deg, #0a1a30 0%, #12407a 55%, #1f7ad1 100%);
  border-radius: 20px;
  box-shadow: none;

  &__label {
    margin: 0 0 4px;
    font-size: 13px;
    line-height: 18px;
    color: rgba(255, 255, 255, 0.72);
  }
  &__value {
    margin: 0;
    font-size: 40px;
    line-height: 48px;
    font-weight: 700;
    letter-spacing: -0.5px;
    color: #fff;
    font-variant-numeric: tabular-nums;
  }
  &__today {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 18px;
    color: rgba(255, 255, 255, 0.72);
  }
  &__right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 10px;
  }
  &__sign {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    height: 40px;
    padding: 0 22px;
    border: 0;
    border-radius: 20px;
    background: #fff;
    color: #0066cc;
    font-size: 14px;
    font-weight: 600;
    font-family: inherit;
    cursor: pointer;
    transition: opacity 0.16s ease, transform 0.16s ease;

    svg { width: 16px; height: 16px; }
    &:hover:not(:disabled) { transform: translateY(-1px); }
    &:disabled { opacity: 0.72; cursor: default; }
  }
  &__hint {
    margin: 0;
    font-size: 12px;
    line-height: 17px;
    color: rgba(255, 255, 255, 0.55);
  }
}

// ---- 页签卡 ----
.tabCard {
  padding: 24px;
  border-radius: 18px;

  &__tabs {
    display: inline-flex;
    gap: 4px;
    padding: 4px;
    margin-bottom: 20px;
    border-radius: 12px;
    background: #f5f5f7;
  }
}
.tab {
  height: 34px;
  padding: 0 18px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #6e6e73;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &.is-on {
    background: #fff;
    color: #0066cc;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }
}

.pane {
  min-height: 200px;
}

// ---- 积分清单 ----
.accRow {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &:last-of-type { border-bottom: 0; }

  &__info { flex: 0 0 200px; }
  &__type {
    margin: 0 0 3px;
    font-size: 15px;
    font-weight: 600;
    line-height: 21px;
    color: var(--s-ink, #1d1d1f);
  }
  &__desc {
    margin: 0;
    font-size: 12px;
    line-height: 17px;
    color: var(--s-ink-3, #86868b);
  }
  &__bar {
    flex: 1 1 auto;
    height: 8px;
    border-radius: 4px;
    background: #f0f1f5;
    overflow: hidden;
  }
  &__barInner {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg, #0066cc, #3a94e8);
    transition: width 0.3s ease;
  }
  &__points {
    flex: 0 0 64px;
    text-align: right;
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
    color: var(--s-ink, #1d1d1f);
    font-variant-numeric: tabular-nums;

    &.is-done { color: #34a853; }
  }
}

// ---- 天梯榜 ----
.myRank {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #e8f1fc;

  &__label {
    flex: 1 1 auto;
    font-size: 14px;
    color: #1d1d1f;
  }
  &__rank {
    font-size: 16px;
    font-weight: 700;
    color: #0066cc;
  }
  &__points {
    font-size: 13px;
    color: #6e6e73;
    font-variant-numeric: tabular-nums;
  }
}
.rankRow {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-radius: 10px;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &:last-of-type { border-bottom: 0; }
  &.is-me { background: #f5f9ff; }

  &__no {
    flex: 0 0 40px;
    text-align: center;
    font-size: 15px;
    font-weight: 600;
    color: #86868b;
    font-variant-numeric: tabular-nums;

    &.is-top1 { color: #d4a017; }
    &.is-top2 { color: #8a8f98; }
    &.is-top3 { color: #b0792f; }
  }
  &__name {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 14px;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__points {
    flex: 0 0 auto;
    font-size: 14px;
    font-weight: 600;
    color: #0066cc;
    font-variant-numeric: tabular-nums;
  }
}

// ---- 积分商城 ----
.mallGrid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.mallItem {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: 14px;
  background: #fafafc;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 72px;
    margin-bottom: 14px;
    border-radius: 10px;
    background: #eef4fc;

    img { max-width: 48px; max-height: 48px; }
    svg { width: 36px; height: 36px; }
  }
  &__name {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 21px;
    color: #1d1d1f;
  }
  &__desc {
    margin: 0 0 12px;
    font-size: 12px;
    line-height: 18px;
    color: #86868b;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__foot {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 14px;
  }
  &__points {
    font-size: 16px;
    font-weight: 700;
    color: #0066cc;
    font-variant-numeric: tabular-nums;
  }
  &__stock {
    font-size: 12px;
    color: #86868b;
  }
  &__btn {
    height: 36px;
    border: 0;
    border-radius: 18px;
    background: #0066cc;
    color: #fff;
    font-size: 14px;
    font-weight: 500;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { background: #d8d9de; cursor: not-allowed; }
  }
}

// ---- 兑换记录 ----
.recHead,
.recRow {
  display: grid;
  grid-template-columns: minmax(0, 2fr) 90px 170px 90px minmax(0, 1.2fr) 90px;
  align-items: center;
  gap: 12px;
}
.recHead {
  padding: 0 8px 10px;
  font-size: 12px;
  color: #86868b;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));
}
.recRow {
  padding: 14px 8px;
  font-size: 13px;
  color: #1d1d1f;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &:last-of-type { border-bottom: 0; }

  &__c1 {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__c2 { font-variant-numeric: tabular-nums; }
  &__c3, &__c5 { color: #6e6e73; font-size: 12px; }
  &__none { color: #c7c7cc; }
}
.recStatus {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 12px;
  font-size: 12px;

  &.is-s0 { background: #fff4e0; color: #b26a00; }
  &.is-s1 { background: #e8f1fc; color: #0066cc; }
  &.is-s2 { background: #e6f6ec; color: #1d8a43; }
  &.is-s3 { background: #f0f1f5; color: #86868b; }
}
.recCancel {
  height: 28px;
  padding: 0 12px;
  border: 0;
  border-radius: 14px;
  background: #f5f5f7;
  color: #ff3b30;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;

  &:hover { background: #ffeceb; }
}
.pagerRow {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}

// ---- 空态 ----
.emptyBox {
  padding: 56px 0 48px;
  text-align: center;

  &__title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__desc {
    margin: 0;
    font-size: 13px;
    color: #86868b;
  }
}

// ---- 兑换弹窗 ----
.exForm {
  &__tip {
    margin: 0 0 16px;
    font-size: 13px;
    line-height: 19px;
    color: #6e6e73;

    b { color: #0066cc; }
  }
  &__label {
    display: block;
    margin: 12px 0 6px;
    font-size: 13px;
    color: #1d1d1f;
  }
  &__input {
    width: 100%;
    height: 38px;
    padding: 0 12px;
    border: 0;
    border-radius: 10px;
    background: #f5f5f7;
    font-size: 14px;
    font-family: inherit;
    outline: 0;

    &:focus { box-shadow: inset 0 0 0 1.5px #0066cc; background: #fff; }
  }
}
.dlgBtn {
  height: 34px;
  padding: 0 18px;
  border: 0;
  border-radius: 17px;
  background: #f5f5f7;
  color: #1d1d1f;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;

  &:hover { background: #ecedf1; }
  &--primary {
    background: #0066cc;
    color: #fff;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.6; cursor: default; }
  }
}

@media (max-width: 1180px) {
  .mallGrid { grid-template-columns: repeat(2, 1fr); }
  .accRow__info { flex: 0 0 160px; }
}
</style>
