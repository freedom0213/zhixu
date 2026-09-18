<!--
 * 优惠券（/student/coupons）
 * 两个页签：全部优惠券（可领取，getCollectableCoupon + getCoupon 领取）/
 *          我的优惠券（getMyCoupon，未使用/已使用/已过期 + 兑换码 exchangeCoupon）
 * 复用老项目 /main/coupon 与 /personal/main/myCoupon 的接口逻辑，视觉换成学员端新风格。
 * 优惠券类型（与后端约定）：1 每满减 / 2 折扣 / 3 无门槛 / 4 满减 / 5 折扣；
 * rule 文案由 api/class.js 的 formatRule 生成（老项目同款）。
 -->
<template>
  <div class="cp">
    <section class="s-card cpCard">
      <div class="cpCard__head">
        <div class="cpTabs" role="tablist" aria-label="优惠券">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="cpTab"
            :class="{ 'is-on': tab === t.key }"
            type="button"
            role="tab"
            :aria-selected="tab === t.key"
            @click="switchTab(t.key)"
          >
            {{ t.label }}
          </button>
        </div>

        <!-- 我的优惠券：状态筛选 + 兑换码 -->
        <div v-if="tab === 'mine'" class="cpCard__headRight">
          <div class="statusSeg" role="tablist" aria-label="优惠券状态">
            <button
              v-for="s in statusTabs"
              :key="s.value"
              class="statusSeg__item"
              :class="{ 'is-on': status === s.value }"
              type="button"
              role="tab"
              :aria-selected="status === s.value"
              @click="switchStatus(s.value)"
            >
              {{ s.label }}
            </button>
          </div>
          <div class="codeBox">
            <input
              v-model.trim="code"
              class="codeBox__input"
              maxlength="20"
              placeholder="输入兑换码"
              aria-label="兑换码"
              @keyup.enter="doExchange"
            />
            <button class="codeBox__btn" type="button" :disabled="exchanging || !code" @click="doExchange">
              {{ exchanging ? '兑换中…' : '兑换' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ---- 全部优惠券 ---- -->
      <div v-if="tab === 'all'" class="cpGrid" v-loading="allLoading">
        <template v-if="allList.length">
          <article v-for="item in allList" :key="item.id" class="coupon">
            <div class="coupon__left" :class="{ 'is-rate': isRate(item) }">
              <p class="coupon__value">
                <template v-if="isRate(item)">{{ item.discountValue / 10 }}<i>折</i></template>
                <template v-else>¥<i>{{ item.discountValue / 100 }}</i></template>
              </p>
              <p class="coupon__rule">{{ item.rule || formatRule(item) }}</p>
            </div>
            <div class="coupon__right">
              <p class="coupon__name">{{ item.name }}</p>
              <p class="coupon__meta">适用范围：{{ item.specific ? '指定课程' : '全部课程' }}</p>
              <p class="coupon__meta">有效日期：{{ termText(item) }}</p>
              <button
                v-if="item.received"
                class="coupon__btn is-use"
                type="button"
                @click="router.push('/student/courses')"
              >去使用</button>
              <button
                v-else
                class="coupon__btn"
                type="button"
                :disabled="!item.available || receiving === item.id"
                @click="receive(item)"
              >
                {{ !item.available ? '已领完' : receiving === item.id ? '领取中…' : '立即领取' }}
              </button>
            </div>
          </article>
        </template>
        <div v-else-if="!allLoading" class="emptyBox">
          <p class="emptyBox__title">暂无可领取的优惠券</p>
          <p class="emptyBox__desc">上新时会第一时间出现在这里。</p>
        </div>
      </div>

      <!-- ---- 我的优惠券 ---- -->
      <div v-else class="cpGrid" v-loading="mineLoading">
        <template v-if="mineList.length">
          <article
            v-for="item in mineList"
            :key="item.id"
            class="coupon"
            :class="{ 'is-disabled': status !== 1 }"
          >
            <div class="coupon__left" :class="{ 'is-rate': isRate(item) }">
              <p class="coupon__value">
                <template v-if="isRate(item)">{{ item.discountValue / 10 }}<i>折</i></template>
                <template v-else>¥<i>{{ item.discountValue / 100 }}</i></template>
              </p>
              <p class="coupon__rule">{{ item.rule || formatRule(item) }}</p>
            </div>
            <div class="coupon__right">
              <p class="coupon__name">{{ item.name }}</p>
              <p class="coupon__meta">适用范围：{{ item.specific ? '指定课程' : '全部课程' }}</p>
              <p class="coupon__meta">有效日期：{{ termText(item) }}</p>
              <span class="coupon__statusTag" :class="'is-s' + status">{{ statusText(status) }}</span>
            </div>
          </article>
        </template>
        <div v-else-if="!mineLoading" class="emptyBox">
          <p class="emptyBox__title">{{ emptyTitle }}</p>
          <p class="emptyBox__desc">去「全部优惠券」看看有什么可以领的。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import moment from 'moment';
import { getCollectableCoupon, getCoupon, getMyCoupon, exchangeCoupon, formatRule } from '@/api/class.js';
import { useRouter } from 'vue-router';

const tabs = [
  { key: 'all', label: '全部优惠券' },
  { key: 'mine', label: '我的优惠券' },
];
const tab = ref('all');
const router = useRouter();

// 折扣券（2/5 显示折扣，其余显示金额）
const isRate = (item) => item.discountType == 2 || item.discountType == 5;

const termText = (item) =>
  item.termDays ? `${item.termDays} 天` : item.termEndTime ? moment(item.termEndTime).format('YYYY-MM-DD') : '—';

// ---- 全部优惠券 ----
const allList = ref([]);
const allLoading = ref(false);
const receiving = ref(null);

const loadAll = async () => {
  allLoading.value = true;
  try {
    const res = await getCollectableCoupon();
    if (res?.code == 200 && res.data) {
      const list = Array.isArray(res.data) ? res.data : res.data.list || [];
      allList.value = list.map((d) => ({ ...d, rule: d.rule || formatRule(d) }));
    }
  } catch (e) {
    allList.value = [];
  } finally {
    allLoading.value = false;
  }
};

const receive = async (item) => {
  receiving.value = item.id;
  try {
    const res = await getCoupon({ id: item.id });
    if (res?.code == 200) {
      ElMessage.success('领取成功');
      item.received = true;
    } else {
      ElMessage.error(res?.msg || '领取失败');
    }
  } catch (e) {
    ElMessage.error('领取失败，请稍后重试');
  } finally {
    receiving.value = null;
  }
};

// ---- 我的优惠券 ----
const statusTabs = [
  { label: '未使用', value: 1 },
  { label: '已使用', value: 2 },
  { label: '已过期', value: 3 },
];
const status = ref(1);
const mineList = ref([]);
const mineLoading = ref(false);
const code = ref('');
const exchanging = ref(false);

const statusText = (s) => ({ 1: '未使用', 2: '已使用', 3: '已过期' }[s] ?? '—');
const emptyTitle = computed(() => ({ 1: '还没有可用的优惠券', 2: '暂无已使用的优惠券', 3: '暂无已过期的优惠券' }[status.value] || '暂无优惠券'));

const loadMine = async () => {
  mineLoading.value = true;
  try {
    const res = await getMyCoupon({ status: status.value, pageNo: 1, pageSize: 100 });
    if (res?.code == 200 && res.data) {
      mineList.value = (res.data.list || []).map((d) => ({ ...d, rule: d.rule || formatRule(d) }));
    }
  } catch (e) {
    mineList.value = [];
  } finally {
    mineLoading.value = false;
  }
};

const switchStatus = (v) => {
  status.value = v;
  loadMine();
};

// 兑换码
const doExchange = async () => {
  if (!code.value) return;
  exchanging.value = true;
  try {
    const res = await exchangeCoupon({ code: code.value });
    if (res?.code == 200) {
      ElMessage.success('兑换成功，可在「未使用」中查看');
      code.value = '';
      status.value = 1;
      loadMine();
    } else {
      ElMessage.error(res?.msg || '兑换失败，请核对兑换码');
    }
  } catch (e) {
    ElMessage.error('兑换失败，请稍后重试');
  } finally {
    exchanging.value = false;
  }
};

// ---- 页签切换 ----
const switchTab = (k) => {
  tab.value = k;
};

onMounted(() => {
  loadAll();
  loadMine();
});
</script>

<style lang="scss" scoped>
.cp {
  display: flex;
  flex-direction: column;
}

.cpCard {
  padding: 24px;
  border-radius: 18px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 20px;
    flex-wrap: wrap;
  }
  &__headRight {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }
}

.cpTabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: #f5f5f7;
}
.cpTab {
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

.statusSeg {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: #f5f5f7;

  &__item {
    height: 28px;
    padding: 0 14px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: #6e6e73;
    font-size: 13px;
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
}

.codeBox {
  display: inline-flex;
  gap: 8px;

  &__input {
    width: 180px;
    height: 34px;
    padding: 0 12px;
    border: 0;
    border-radius: 17px;
    background: #f5f5f7;
    font-size: 13px;
    font-family: inherit;
    outline: 0;

    &:focus { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
  }
  &__btn {
    height: 34px;
    padding: 0 16px;
    border: 0;
    border-radius: 17px;
    background: #0066cc;
    color: #fff;
    font-size: 13px;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.55; cursor: not-allowed; }
  }
}

// ---- 优惠券卡（票券样式） ----
.cpGrid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  min-height: 200px;
  align-content: start;
}
.coupon {
  display: flex;
  border-radius: 14px;
  overflow: hidden;
  background: #fafafc;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.05);

  &.is-disabled { opacity: 0.55; }

  &__left {
    flex: 0 0 172px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    padding: 20px 14px;
    background: linear-gradient(135deg, #0066cc, #3a94e8);
    color: #fff;
    position: relative;

    // 票券打孔
    &::before, &::after {
      content: '';
      position: absolute;
      right: -7px;
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: #fff;
    }
    &::before { top: -7px; }
    &::after { bottom: -7px; }

    &.is-rate { background: linear-gradient(135deg, #b26a00, #e8a23a); }
  }
  &__value {
    margin: 0;
    font-weight: 700;
    line-height: 1;
    font-variant-numeric: tabular-nums;

    i {
      font-style: normal;
      font-size: 30px;
    }
    font-size: 16px;
  }
  &__rule {
    margin: 0;
    font-size: 11px;
    line-height: 15px;
    color: rgba(255, 255, 255, 0.85);
    text-align: center;
  }

  &__right {
    flex: 1 1 auto;
    min-width: 0;
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
  }
  &__name {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 21px;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__meta {
    margin: 0 0 4px;
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__btn {
    margin-top: auto;
    align-self: flex-start;
    height: 32px;
    padding: 0 18px;
    border: 0;
    border-radius: 16px;
    background: #0066cc;
    color: #fff;
    font-size: 13px;
    font-weight: 500;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
    &.is-use { background: #e8f1fc; color: #0066cc; }
  }
  &__statusTag {
    margin-top: auto;
    align-self: flex-start;
    display: inline-flex;
    align-items: center;
    height: 26px;
    padding: 0 12px;
    border-radius: 13px;
    font-size: 12px;

    &.is-s1 { background: #e8f1fc; color: #0066cc; }
    &.is-s2 { background: #f0f1f5; color: #86868b; }
    &.is-s3 { background: #f0f1f5; color: #86868b; }
  }
}

// ---- 空态 ----
.emptyBox {
  grid-column: 1 / -1;
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

@media (max-width: 1180px) {
  .cpGrid { grid-template-columns: 1fr; }
}
</style>
