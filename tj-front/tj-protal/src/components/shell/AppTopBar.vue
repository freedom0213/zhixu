<template>
  <header class="tb">
    <h1 class="tb__title">{{ title }}</h1>

    <div class="tb__right">
      <!-- 优惠券 -->
      <button
        class="tb__btn tb__btn--wide"
        type="button"
        aria-label="优惠券"
        title="优惠券"
        @click="router.push('/student/coupons')"
      >
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M3 7.6V6a1.5 1.5 0 0 1 1.5-1.5h11A1.5 1.5 0 0 1 17 6v1.6a2.4 2.4 0 0 0 0 4.8V14a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 3 14v-1.6a2.4 2.4 0 0 0 0-4.8Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
          <path d="M12.4 4.7v10.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-dasharray="2.2 2.6"/>
        </svg>
        <span class="tb__btnText">优惠券</span>
      </button>

      <!-- 我的购物车 -->
      <button
        class="tb__btn tb__cart"
        type="button"
        aria-label="我的购物车"
        title="我的购物车"
        @click="router.push('/student/carts')"
      >
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M2.8 3.2h2l1.7 9.6a1.4 1.4 0 0 0 1.4 1.2h7.2a1.4 1.4 0 0 0 1.4-1.1l1.2-5.7H5.1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="8.4" cy="16.8" r="1.2" fill="currentColor"/>
          <circle cx="14.8" cy="16.8" r="1.2" fill="currentColor"/>
        </svg>
        <span v-if="cartCount > 0" class="tb__cartBadge" :aria-label="`购物车有 ${cartCount} 门课程`">
          {{ cartCount > 99 ? '99+' : cartCount }}
        </span>
      </button>

      <!-- 全局搜索：跳转现有课程搜索页（复用 /search/index 的结果能力） -->
      <form class="tb__search" role="search" @submit.prevent="onSearch">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
          <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input v-model.trim="keyword" type="search" placeholder="搜索课程" aria-label="搜索课程" maxlength="50" />
      </form>

      <button
        class="tb__btn"
        type="button"
        aria-label="通知消息"
        title="通知消息"
        @click="router.push('/student/notices')"
      >
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 3.2a4.5 4.5 0 0 0-4.5 4.5c0 3.3-1.1 4.5-1.1 4.5h11.2s-1.1-1.2-1.1-4.5A4.5 4.5 0 0 0 10 3.2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
          <path d="M8.4 15.1a1.8 1.8 0 0 0 3.2 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup>
/**
 * 学员端顶部工具栏
 * - 页面标题由路由 meta.title 驱动（ShellLayout 传入）
 * - 搜索复用官网 /search/index；通知复用个人中心消息页
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCarts } from '@/api/order.js';

defineProps({
  title: { type: String, default: '学习首页' },
});

const router = useRouter();
const keyword = ref('');

const onSearch = () => {
  if (!keyword.value) return;
  router.push({ path: '/student/courses', query: { keyword: keyword.value } });
};

// ---- 购物车数量角标：进入学员端时拉取一次，加购/删除后由页面派发 cart:changed 刷新 ----
const cartCount = ref(0);

const loadCartCount = async () => {
  try {
    const res = await getCarts();
    if (res?.code === 200 && Array.isArray(res.data)) cartCount.value = res.data.length;
  } catch (e) {
    // 未登录或接口不可用时不显示角标
  }
};

const onCartChanged = () => loadCartCount();

onMounted(() => {
  loadCartCount();
  window.addEventListener('cart:changed', onCartChanged);
});
onBeforeUnmount(() => window.removeEventListener('cart:changed', onCartChanged));
</script>

<style lang="scss" scoped>
.tb__cart {
  position: relative;
}
.tb__cartBadge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ff3b30;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  font-weight: 600;
  text-align: center;
  pointer-events: none;
  font-variant-numeric: tabular-nums;
}
</style>
