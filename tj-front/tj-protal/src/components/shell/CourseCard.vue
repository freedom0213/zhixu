<!-- 课程卡片（设计稿组件卡）：深色渐变封面占位 + 课程代号水印；course.coverUrl 存在时直接显示真实封面 -->
<template>
  <article class="ck" tabindex="0" @click="goDetail" @keyup.enter="goDetail">
    <div class="ck__cover" :class="`ck__cover--${tone}`">
      <img v-if="course.coverUrl" class="ck__img" :src="course.coverUrl" :alt="course.name" />
      <template v-else>
        <span v-if="code" class="ck__code">{{ code }}</span>
        <span v-if="isFree" class="ck__free">免费</span>
      </template>
    </div>
    <div class="ck__body">
      <h3 class="ck__name" :title="plainName">{{ plainName }}</h3>
      <div class="ck__meta">
        <span v-if="course.teacher">{{ course.teacher }}</span>
        <span v-if="course.sections">共 {{ course.sections }} 节</span>
        <span v-if="course.sold">{{ course.sold }} 人在学</span>
      </div>
      <div class="ck__foot">
        <span class="ck__price" :class="{ 'ck__price--free': isFree }">{{ isFree ? '免费' : `¥${priceYuan}` }}</span>
        <span class="ck__go">查看课程</span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  course: { type: Object, required: true },
});
const router = useRouter();

// 色调：按课程 id 稳定取模，同一门课颜色固定（Apple 深色灰阶，无彩噪）
const TONES = ['ink', 'slate', 'navy', 'dim'];
const tone = computed(() => TONES[Math.abs(props.course.id || 0) % TONES.length]);

// 接口返回的课程名可能带 <em> 高亮标签（搜索场景），此处按纯文本渲染
const plainName = computed(() => String(props.course.name || '').replace(/<\/?em>/g, ''));

// 课程代号水印：取英文名首词或名称前两字（仅作封面占位装饰）
const code = computed(() => {
  const latin = plainName.value.match(/[A-Za-z][A-Za-z0-9+#.]{1,6}/);
  if (latin) return latin[0].toUpperCase();
  return plainName.value.slice(0, 2);
});

const isFree = computed(() => Number(props.course.price) === 0);
const priceYuan = computed(() => ((Number(props.course.price) || 0) / 100).toFixed(0));

// 与官网课程卡一致：详情路由用 query.id
const goDetail = () => router.push({ path: '/details', query: { id: props.course.id } });
</script>

<style lang="scss" scoped>
.ck {
  display: flex;
  flex-direction: column;
  border-radius: var(--s-r-lg, 18px);
  background: var(--s-card, #fff);
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.18s ease;

  &:hover {
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055), 0 8px 24px -8px rgba(16, 24, 40, 0.16);

    .ck__go {
      color: var(--sa, #0066cc);
    }
  }
  &:focus-visible {
    outline: 2px solid #0071e3;
    outline-offset: 2px;
  }

  &__cover {
    position: relative;
    aspect-ratio: 16 / 9;
    display: flex;
    align-items: flex-start;
    justify-content: flex-end;
    padding: 12px;
    overflow: hidden;

    &--ink   { background: linear-gradient(145deg, #2b2b2f 0%, #48484d 100%); }
    &--slate { background: linear-gradient(145deg, #3a3f47 0%, #5b6270 100%); }
    &--navy  { background: linear-gradient(145deg, #1c3a5e 0%, #2f5e8f 100%); }
    &--dim   { background: linear-gradient(145deg, #4a4442 0%, #6e6660 100%); }
  }
  &__img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  &__code {
    font-size: 26px;
    font-weight: 800;
    letter-spacing: 0.5px;
    color: rgba(255, 255, 255, 0.22);
    user-select: none;
  }
  &__free {
    position: absolute;
    left: 12px;
    bottom: 12px;
    padding: 2px 10px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.9);
    color: #1d1d1f;
    font-size: 11px;
    font-weight: 600;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 14px 16px 16px;
  }
  &__name {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    line-height: 21px;
    color: var(--s-ink, #1d1d1f);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__meta {
    display: flex;
    gap: 10px;
    font-size: 12px;
    color: var(--s-ink-3, #86868b);
    min-height: 18px;
    white-space: nowrap;
    overflow: hidden;
  }
  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 4px;
  }
  &__price {
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink, #1d1d1f);

    &--free {
      color: var(--sa, #0066cc);
    }
  }
  &__go {
    font-size: 12px;
    color: var(--s-ink-3, #86868b);
    transition: color 0.15s ease;
  }
}
</style>
