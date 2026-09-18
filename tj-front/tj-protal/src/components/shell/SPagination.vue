<!-- 分页器（设计稿复用组件）：上一页 / 页码 / 下一页，页码窗口最多 7 个 -->
<template>
  <nav v-if="totalPages > 1" class="pg" aria-label="分页">
    <button class="pg__arrow" type="button" :disabled="modelValue <= 1" aria-label="上一页" @click="go(modelValue - 1)">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
        <path d="M8.8 2.8 4.4 7l4.4 4.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>
    <button
      v-for="p in pages"
      :key="p"
      class="pg__num"
      :class="{ 'is-active': p === modelValue }"
      type="button"
      :aria-current="p === modelValue ? 'page' : undefined"
      @click="go(p)"
    >{{ p }}</button>
    <button class="pg__arrow" type="button" :disabled="modelValue >= totalPages" aria-label="下一页" @click="go(modelValue + 1)">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
        <path d="M5.2 2.8 9.6 7l-4.4 4.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: { type: Number, default: 1 },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 6 },
});
const emit = defineEmits(['update:modelValue', 'change']);

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

// 页码窗口：≤7 全显；否则首尾常显 + 当前页前后各 1
const pages = computed(() => {
  const t = totalPages.value;
  const cur = props.modelValue;
  if (t <= 7) return Array.from({ length: t }, (_, i) => i + 1);
  const set = new Set([1, t, cur, cur - 1, cur + 1].filter((p) => p >= 1 && p <= t));
  return [...set].sort((a, b) => a - b);
});

const go = (p) => {
  if (p < 1 || p > totalPages.value || p === props.modelValue) return;
  emit('update:modelValue', p);
  emit('change', p);
};
</script>

<style lang="scss" scoped>
.pg {
  display: flex;
  align-items: center;
  gap: 6px;

  &__arrow,
  &__num {
    min-width: 32px;
    height: 32px;
    padding: 0 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--s-ink-2, #6e6e73);
    font-size: 13px;
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;

    &:hover:not(:disabled):not(.is-active) {
      background: rgba(0, 0, 0, 0.045);
      color: var(--s-ink, #1d1d1f);
    }
    &:disabled {
      opacity: 0.35;
      cursor: default;
    }
    &:focus-visible {
      outline: 2px solid #0071e3;
      outline-offset: 2px;
    }
  }

  &__num.is-active {
    background: var(--sa, #0066cc);
    color: #fff;
    font-weight: 600;
  }
}
</style>
