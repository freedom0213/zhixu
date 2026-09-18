<!--
 * 结算页（/student/settlement?courseIds=）
 * 复用老项目 /pay/settlement 的下单链路：
 *   confirmOrderInfo({courseIds}) → /ts/orders/prePlaceOrder（订单预生成 + 可用优惠券）
 *   setOrder({courseIds, orderId, couponIds?}) → /ts/orders/placeOrder → /pay/payment?orderId=（支付页仍为既有页面）
 * 视觉换成学员端新风格。金额单位：分（接口返回），展示时 /100。
 -->
<template>
  <div class="st">
    <!-- 左：课程清单 + 优惠券 -->
    <section class="s-card stMain">
      <h2 class="stTitle">确认订单信息</h2>

      <div class="stCourses" v-loading="loading">
        <template v-if="orderInfo.courses && orderInfo.courses.length">
          <div v-for="item in orderInfo.courses" :key="item.id" class="stCourse">
            <img class="stCourse__cover" :src="item.coverUrl" :alt="item.name || item.courseName" />
            <div class="stCourse__meta">
              <p class="stCourse__name">{{ item.name || item.courseName }}</p>
            </div>
            <span class="stCourse__price">¥{{ fenToYuan(item.nowPrice || item.price) }}</span>
          </div>
        </template>
        <div v-else-if="!loading" class="stEmpty">
          <p class="stEmpty__title">没有待结算的课程</p>
          <p class="stEmpty__desc">请从购物车勾选课程后进入结算。</p>
          <button class="stEmpty__btn" type="button" @click="router.push('/student/carts')">去购物车</button>
        </div>
      </div>

      <template v-if="orderInfo.courses && orderInfo.courses.length">
        <!-- 优惠券 -->
        <div class="stCoupon" v-if="discounts.length">
          <span class="stCoupon__label">优惠券</span>
          <div class="stCoupon__options">
            <button
              class="cpOpt"
              :class="{ 'is-on': couponIds === '' }"
              type="button"
              @click="chooseCoupon('')"
            >不使用优惠券</button>
            <button
              v-for="d in discounts"
              :key="d.ids"
              class="cpOpt"
              :class="{ 'is-on': couponIds === d.ids, 'is-disabled': d.disabled }"
              type="button"
              :disabled="d.disabled"
              @click="chooseCoupon(d.ids)"
            >
              {{ d.rule }}
            </button>
          </div>
        </div>
        <p v-else class="stCouponNone">暂无可用优惠券</p>

        <!-- 金额明细 -->
        <div class="stAmounts">
          <div class="stAmounts__row">
            <span>订单总价</span>
            <span>¥{{ fenToYuan(orderInfo.totalAmount) }}</span>
          </div>
          <div class="stAmounts__row" v-if="discountAmount > 0">
            <span>优惠券抵扣</span>
            <span class="is-discount">-¥{{ fenToYuan(discountAmount) }}</span>
          </div>
          <div class="stAmounts__row stAmounts__row--final">
            <span>实付金额</span>
            <span class="is-final">¥{{ payPrice }}</span>
          </div>
        </div>
      </template>
    </section>

    <!-- 右：提交栏 -->
    <aside class="s-card stSide" v-if="orderInfo.courses && orderInfo.courses.length">
      <p class="stSide__label">待支付</p>
      <p class="stSide__price">¥{{ payPrice }}</p>
      <p class="stSide__meta">
        共 {{ orderInfo.courses.length }} 门课程
        <template v-if="discountAmount > 0">，已优惠 ¥{{ fenToYuan(discountAmount) }}</template>
      </p>
      <button class="stSide__submit" type="button" :disabled="submitting" @click="submitOrder">
        {{ submitting ? '提交中…' : '提交订单' }}
      </button>
      <p class="stSide__note">提交后跳转支付页完成付款</p>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { confirmOrderInfo, setOrder } from '@/api/order.js';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitting = ref(false);
const orderInfo = ref({});
const couponIds = ref(''); // 选中的优惠券 ids（后端 discounts[].ids 逗号串）

const fenToYuan = (fen) => ((Number(fen) || 0) / 100).toFixed(2);

// 优惠券选项（老页面同款归一化：ids 数组 → 逗号串；rules → 文案）
const discounts = computed(() => {
  const list = orderInfo.value.discounts || [];
  return list.map((d) => ({
    ...d,
    ids: Array.isArray(d.ids) ? d.ids.join() : d.ids,
    rule:
      Array.isArray(d.rules) && d.rules.length > 1
        ? `叠加${d.rules.length}券：【优惠¥${((d.discountAmount || 0) / 100).toFixed(2)}】`
        : `单券：【${d.rules && d.rules[0]}】`,
  }));
});

const discountAmount = computed(() => {
  if (!couponIds.value) return 0;
  const hit = discounts.value.find((d) => d.ids === couponIds.value);
  return hit ? hit.discountAmount || 0 : 0;
});

const payPrice = computed(() => {
  const p = ((orderInfo.value.totalAmount || 0) - discountAmount.value) / 100;
  return (p < 0 ? 0 : p).toFixed(2);
});

const chooseCoupon = (ids) => {
  couponIds.value = ids;
};

const loadOrder = async () => {
  loading.value = true;
  try {
    const res = await confirmOrderInfo({ courseIds: route.query.courseIds });
    if (res?.code === 200 && res.data) {
      orderInfo.value = res.data;
    } else {
      ElMessage.error(res?.msg || '获取订单信息失败');
    }
  } catch (e) {
    ElMessage.error('获取订单信息失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

const submitOrder = async () => {
  const courses = orderInfo.value.courses || [];
  if (!courses.length) return;
  submitting.value = true;
  try {
    const params = {
      courseIds: courses.map((n) => n.courseId || n.id),
      orderId: orderInfo.value.orderId,
    };
    if (couponIds.value) params.couponIds = couponIds.value.split(',');
    const res = await setOrder(params);
    if (res?.code === 200) {
      window.dispatchEvent(new CustomEvent('cart:changed'));
      // 支付页仍为既有页面（支付链路未变）
      router.push({ path: '/student/payment', query: { orderId: res.data.orderId } });
    } else {
      ElMessage.error(res?.msg || '下单失败');
    }
  } catch (e) {
    ElMessage.error('下单失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
};

onMounted(loadOrder);
</script>

<style lang="scss" scoped>
.st {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

// ---- 左：主卡 ----
.stMain {
  flex: 1 1 auto;
  min-width: 0;
  padding: 28px;
  border-radius: 18px;
}
.stTitle {
  margin: 0 0 20px;
  font-size: 17px;
  font-weight: 600;
  line-height: 23px;
  color: var(--s-ink, #1d1d1f);
}

.stCourses {
  min-height: 120px;
}
.stCourse {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &__cover {
    flex: 0 0 128px;
    width: 128px;
    height: 72px;
    border-radius: 10px;
    object-fit: cover;
    background: #eef1f6;
  }
  &__meta {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__name {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    color: var(--s-ink, #1d1d1f);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__price {
    flex: 0 0 auto;
    font-size: 16px;
    font-weight: 700;
    color: #ff3b30;
    font-variant-numeric: tabular-nums;
  }
}

// ---- 优惠券 ----
.stCoupon {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-top: 20px;

  &__label {
    flex: 0 0 64px;
    line-height: 38px;
    font-size: 14px;
    color: var(--s-ink-2, #6e6e73);
  }
  &__options {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}
.cpOpt {
  height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 19px;
  background: #f5f5f7;
  color: #1d1d1f;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover:not(:disabled) { background: #ecedf1; }
  &.is-on {
    background: var(--sa-soft, #e8f1fc);
    color: #0066cc;
    font-weight: 600;
    box-shadow: inset 0 0 0 1.5px #0066cc;
  }
  &.is-disabled { opacity: 0.5; cursor: not-allowed; }
}
.stCouponNone {
  margin: 20px 0 0 80px;
  font-size: 13px;
  color: var(--s-ink-3, #86868b);
}

// ---- 金额 ----
.stAmounts {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    max-width: 420px;
    margin-left: auto;
    padding: 6px 0;
    font-size: 14px;
    color: var(--s-ink-2, #6e6e73);

    .is-discount { color: #ff3b30; }
    .is-final {
      font-size: 22px;
      font-weight: 700;
      color: #ff3b30;
      font-variant-numeric: tabular-nums;
    }
    &--final { color: var(--s-ink, #1d1d1f); }
  }
}

// ---- 右：提交栏 ----
.stSide {
  flex: 0 0 280px;
  padding: 28px 24px;
  border-radius: 18px;
  text-align: center;

  &__label {
    margin: 0;
    font-size: 13px;
    color: var(--s-ink-3, #86868b);
  }
  &__price {
    margin: 6px 0 4px;
    font-size: 32px;
    font-weight: 700;
    color: #ff3b30;
    font-variant-numeric: tabular-nums;
  }
  &__meta {
    margin: 0 0 20px;
    font-size: 12px;
    color: var(--s-ink-3, #86868b);
  }
  &__submit {
    width: 100%;
    height: 44px;
    border: 0;
    border-radius: 22px;
    background: #0066cc;
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
  &__note {
    margin: 12px 0 0;
    font-size: 12px;
    color: var(--s-ink-4, #aeaeb2);
  }
}

// ---- 空态 ----
.stEmpty {
  padding: 40px 0;
  text-align: center;

  &__title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink, #1d1d1f);
  }
  &__desc {
    margin: 0 0 18px;
    font-size: 13px;
    color: var(--s-ink-3, #86868b);
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

@media (max-width: 1180px) {
  .st { flex-direction: column; }
  .stSide { flex: 1 1 auto; width: 100%; }
}
</style>
