<!--
 * 订单支付页（/student/payment?orderId=）
 * 复用老项目 /pay/payment 的支付链路：
 *   getPayState(/ts/orders/{id}/status) → 订单状态 + 支付时限（status 2/5 已支付 → 成功页）
 *   getPayMethod(/ts/pay/channels) → 支付渠道；getPayUrl(/ts/pay/order) → 二维码 + 5s 轮询
 * 视觉换成学员端新风格。⚠️ pay-service 当前停止时：渠道/二维码会失败，页面如实提示，可稍后重试。
 -->
<template>
  <div class="pm">
    <section class="s-card pmCard">
      <!-- 成功横幅 -->
      <div class="pmHead">
        <div class="pmHead__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8"/>
            <path d="m7.6 12.4 3 3 5.8-6.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="pmHead__text">
          <p class="pmHead__title">订单提交成功！</p>
          <p class="pmHead__sub" v-if="orderInfo && orderInfo.payOutTime">
            支付还剩 <b class="pmHead__count">{{ countdownText }}</b>，超时后订单将自动取消
          </p>
          <p class="pmHead__sub" v-else>请尽快完成支付</p>
        </div>
        <div class="pmHead__price" v-if="orderInfo">¥{{ fenToYuan(orderInfo.payAmount) }}</div>
      </div>

      <!-- 支付方式 -->
      <div class="pmMethods" v-loading="methodLoading">
        <p class="pmMethods__title">选择支付方式</p>
        <template v-if="payMethodList.length">
          <button
            v-for="item in payMethodList"
            :key="item.id"
            class="pmMethod"
            :class="{ 'is-on': payMethod.id === item.id }"
            type="button"
            @click="chooseMethod(item)"
          >
            <img class="pmMethod__icon" :src="item.channelIcon" width="40" height="40" alt="" />
            <span class="pmMethod__name">{{ item.name }}</span>
            <span class="pmMethod__radio" aria-hidden="true"></span>
          </button>
        </template>
        <div v-else-if="!methodLoading" class="pmWarn">
          <p class="pmWarn__title">暂时获取不到支付渠道</p>
          <p class="pmWarn__desc">{{ methodError || '支付服务暂时不可用，请稍后重试。你的订单已保留。' }}</p>
          <button class="pmWarn__btn" type="button" @click="getPayMethodList">重新获取</button>
        </div>
      </div>

      <div class="pmFoot">
        <button class="pmFoot__link" type="button" @click="router.push('/student/dashboard')">返回学习首页</button>
      </div>
    </section>

    <!-- 二维码弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="`${payMethod.name || ''}支付`"
      width="360px"
      align-center
      @close="stopPolling"
    >
      <div class="qrBox" v-if="qrCodeUrl">
        <div class="qrBox__code">
          <QrcodeVue :value="qrCodeUrl" :size="200" level="M" />
        </div>
        <p class="qrBox__tip">请使用 <b>{{ payMethod.name }}</b> 扫一扫完成支付</p>
        <p class="qrBox__state">{{ paying ? '支付结果确认中…' : '二维码有效期有限，请尽快支付' }}</p>
      </div>
      <div class="qrBox qrBox--err" v-else>
        <p class="qrBox__tip">获取支付二维码失败</p>
        <p class="qrBox__state">{{ urlError || '支付服务暂时不可用，请稍后重试' }}</p>
        <button class="pmWarn__btn" type="button" @click="chooseMethod(payMethod)">重新获取二维码</button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import QrcodeVue from 'qrcode.vue';
import { getPayMethod, getPayUrl, getPayState } from '@/api/order.js';

const route = useRoute();
const router = useRouter();

const fenToYuan = (fen) => ((Number(fen) || 0) / 100).toFixed(2);

// ---- 订单状态 / 倒计时 ----
const orderInfo = ref(null);
const nowTick = ref(Date.now());
let tickTimer = null;

const remainMs = computed(() => {
  if (!orderInfo.value || !orderInfo.value.payOutTime) return 0;
  const end = new Date(String(orderInfo.value.payOutTime).replace(/-/g, '/')).getTime();
  return Math.max(0, end - nowTick.value);
});
const countdownText = computed(() => {
  const s = Math.floor(remainMs.value / 1000);
  const h = String(Math.floor(s / 3600)).padStart(2, '0');
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const sec = String(s % 60).padStart(2, '0');
  return `${h}:${m}:${sec}`;
});

const isFirstGet = ref(true);
const getPayStateData = async () => {
  try {
    const res = await getPayState({ orderId: route.query.orderId });
    if (res?.code === 200 && res.data) {
      // 已支付 / 已报名 → 成功页（老链路）
      if (res.data.status === 2 || res.data.status === 5) {
        stopPolling();
        router.push({ path: '/student/paySuccess', query: { order: res.data.id } });
        return;
      }
      if (res.data.status === 1 && isFirstGet.value) {
        isFirstGet.value = false;
        orderInfo.value = res.data;
      }
    }
  } catch (e) {
    // 状态查询失败不打断页面（服务恢复后轮询会带出结果）
  }
};

// ---- 支付渠道 ----
const payMethodList = ref([]);
const methodLoading = ref(false);
const methodError = ref('');
const payMethod = ref({});

const getPayMethodList = async () => {
  methodLoading.value = true;
  methodError.value = '';
  try {
    const res = await getPayMethod();
    if (res?.code == 200) {
      payMethodList.value = res.data || [];
    } else {
      methodError.value = res?.msg || '';
      payMethodList.value = [];
    }
  } catch (e) {
    methodError.value = '';
    payMethodList.value = [];
  } finally {
    methodLoading.value = false;
  }
};

// ---- 二维码 + 轮询 ----
const dialogVisible = ref(false);
const qrCodeUrl = ref('');
const urlError = ref('');
const paying = ref(false);
let pollTimer = null;

const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
};

const chooseMethod = async (item) => {
  if (!item || !item.channelCode) return;
  payMethod.value = item;
  qrCodeUrl.value = '';
  urlError.value = '';
  dialogVisible.value = true;
  try {
    const res = await getPayUrl({ orderId: route.query.orderId, payChannelCode: item.channelCode });
    if (res?.code == 200 && res.data) {
      qrCodeUrl.value = res.data;
      stopPolling();
      pollTimer = setInterval(getPayStateData, 5000);
    } else {
      urlError.value = res?.msg || '';
    }
  } catch (e) {
    urlError.value = '';
  }
};

onMounted(async () => {
  getPayMethodList();
  await getPayStateData();
  tickTimer = setInterval(() => { nowTick.value = Date.now(); }, 1000);
});

onBeforeUnmount(() => {
  stopPolling();
  if (tickTimer) clearInterval(tickTimer);
});
</script>

<style lang="scss" scoped>
.pm {
  display: flex;
  flex-direction: column;
}

.pmCard {
  padding: 28px;
  border-radius: 18px;
}

// ---- 成功横幅 ----
.pmHead {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-radius: 14px;
  background: #e9f7ee;

  &__icon {
    flex: 0 0 44px;
    width: 44px;
    height: 44px;
    color: #1d8a43;

    svg { width: 100%; height: 100%; }
  }
  &__text { flex: 1 1 auto; min-width: 0; }
  &__title {
    margin: 0 0 4px;
    font-size: 17px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__sub {
    margin: 0;
    font-size: 13px;
    color: #6e6e73;

    b { color: #ff3b30; font-variant-numeric: tabular-nums; }
  }
  &__count { font-weight: 700; }
  &__price {
    flex: 0 0 auto;
    font-size: 24px;
    font-weight: 700;
    color: #ff3b30;
    font-variant-numeric: tabular-nums;
  }
}

// ---- 支付方式 ----
.pmMethods {
  margin-top: 24px;
  min-height: 120px;

  &__title {
    margin: 0 0 14px;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }
}
.pmMethod {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 420px;
  padding: 14px 18px;
  margin-bottom: 12px;
  border: 0;
  border-radius: 14px;
  background: #fafafc;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
  font-family: inherit;
  cursor: pointer;
  text-align: left;
  transition: box-shadow 0.16s ease, background-color 0.16s ease;

  &:hover { background: #f3f6fb; }
  &.is-on {
    background: #f5f9ff;
    box-shadow: inset 0 0 0 1.5px #0066cc;
  }
  &__icon { flex: 0 0 40px; width: 40px; height: 40px; border-radius: 8px; }
  &__name {
    flex: 1 1 auto;
    font-size: 15px;
    font-weight: 500;
    color: #1d1d1f;
  }
  &__radio {
    flex: 0 0 18px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    border: 1.5px solid #c7c7cc;
    background: #fff;

    .is-on & {
      border-color: #0066cc;
      background: radial-gradient(circle, #0066cc 0 5px, #fff 5.5px);
    }
  }
}

// ---- 支付渠道获取失败 ----
.pmWarn {
  padding: 28px 0 20px;
  text-align: center;

  &__title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__desc {
    margin: 0 0 18px;
    font-size: 13px;
    color: #86868b;
  }
  &__btn {
    height: 36px;
    padding: 0 22px;
    border: 0;
    border-radius: 18px;
    background: #0066cc;
    color: #fff;
    font-size: 14px;
    font-family: inherit;
    cursor: pointer;

    &:hover { background: #0071e3; }
  }
}

.pmFoot {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &__link {
    border: 0;
    background: none;
    color: #0066cc;
    font-size: 14px;
    font-family: inherit;
    cursor: pointer;
    padding: 0;

    &:hover { text-decoration: underline; }
  }
}

// ---- 二维码 ----
.qrBox {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0 4px;

  &__code {
    padding: 12px;
    border-radius: 12px;
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.08);
    line-height: 0;
  }
  &__tip {
    margin: 14px 0 4px;
    font-size: 14px;
    color: #1d1d1f;

    b { color: #0066cc; }
  }
  &__state {
    margin: 0 0 12px;
    font-size: 12px;
    color: #86868b;
  }
  &--err {
    .pmWarn__btn { margin-top: 4px; }
  }
}
</style>
