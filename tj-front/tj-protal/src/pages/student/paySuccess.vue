<!--
 * 支付成功页（/student/paySuccess?order=）
 * 替代老站静态页 /pay/success：展示真实订单号（来自支付链路），提供去课程 / 回首页入口。
 -->
<template>
  <div class="ps">
    <section class="s-card psCard">
      <div class="psIcon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.8"/>
          <path d="m7.6 12.4 3 3 5.8-6.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <h1 class="psTitle">支付成功</h1>
      <p class="psOrder">订单号：{{ orderId || '—' }}</p>
      <p class="psHint">课程已加入你的学习列表，可以开始学习了。</p>
      <div class="psActions">
        <button class="psBtn psBtn--primary" type="button" @click="router.push('/student/courses')">去我的课程</button>
        <button class="psBtn" type="button" @click="router.push('/student/dashboard')">返回学习首页</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const orderId = computed(() => route.query.order || '');
</script>

<style lang="scss" scoped>
.ps {
  display: flex;
  justify-content: center;
}
.psCard {
  width: 100%;
  max-width: 560px;
  padding: 48px 32px;
  border-radius: 18px;
  text-align: center;
}
.psIcon {
  width: 56px;
  height: 56px;
  margin: 0 auto 18px;
  color: #1d8a43;
}
.psTitle {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 600;
  color: #1d1d1f;
}
.psOrder {
  margin: 0 0 6px;
  font-size: 13px;
  color: #6e6e73;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
.psHint {
  margin: 0 0 26px;
  font-size: 13px;
  color: #86868b;
}
.psActions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
.psBtn {
  height: 40px;
  padding: 0 24px;
  border: 0;
  border-radius: 20px;
  background: #f5f5f7;
  color: #1d1d1f;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #ecedf1; }
  &--primary {
    background: #0066cc;
    color: #fff;

    &:hover { background: #0071e3; }
  }
}
</style>
