<!--
 * 教师端顶栏
 * -----------------------------------------------------------------------------
 * 为什么不复用 AppTopBar：学员端顶栏写死了「优惠券 / 购物车 / 搜索课程 / 通知消息」，
 * 四处跳转全部指向 /student/*。教师端要的是「搜索课程、题目或学生 + 通知 + 消息」，
 * 控件个数和语义都不同。强行用 variant 分支把两套入口塞进一个组件，
 * 会让这个组件既认学员业务又认教师业务 —— 所以另起一个，样式原语（.tb / .tb__*）共用。
-->
<template>
  <header class="tb">
    <h1 class="tb__title">{{ title }}</h1>

    <div class="tb__right">
      <!-- 全局搜索：讲师端搜索跨「课程 / 题目 / 学生」，由外壳决定落到哪个页面 -->
      <form class="tb__search" role="search" @submit.prevent="onSearch">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
          <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input
          v-model.trim="keyword"
          type="search"
          placeholder="搜索课程、题目或学生"
          aria-label="搜索课程、题目或学生"
          maxlength="50"
        />
      </form>

      <button class="tb__btn" type="button" aria-label="通知" title="通知" @click="$emit('notify')">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M10 3.2a4.5 4.5 0 0 0-4.5 4.5c0 3.3-1.1 4.5-1.1 4.5h11.2s-1.1-1.2-1.1-4.5A4.5 4.5 0 0 0 10 3.2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
          <path d="M8.4 15.1a1.8 1.8 0 0 0 3.2 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
      </button>

      <button class="tb__btn" type="button" aria-label="师生对话" title="师生对话" @click="$emit('message')">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M3.2 9.4c0-3.2 3-5.8 6.8-5.8s6.8 2.6 6.8 5.8-3 5.8-6.8 5.8c-.7 0-1.4-.1-2-.2l-3.5 2 .9-2.6c-1.3-1-2.2-2.7-2.2-4.6Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue';

defineProps({
  title: { type: String, default: '教师工作台' },
});

const emit = defineEmits(['search', 'notify', 'message']);

const keyword = ref('');

const onSearch = () => {
  emit('search', keyword.value);
};
</script>
