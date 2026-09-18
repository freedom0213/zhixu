<!--
 * 题目预览抽屉
 * -----------------------------------------------------------------------------
 * 为什么需要它：题库是平台共用的池子，老师能**看到**别人的题，但改不了。
 * 那么在「引用别人的题之前」必须能看清内容 —— 光看列表里一行题干是不够的
 * （引用了一道答案写错的题，既改不了、也不知道该找谁）。
 *
 * 所以抽屉里同时给出三样东西：
 *   1) 完整题目（题干 / 选项含答案标记 / 解析 / 元信息）
 *   2) 使用数据（被几份试卷用过 —— 判断这道题值不值得复用的依据）
 *   3) 按权限分叉的操作（自己的题 → 编辑 · 停用；别人的题 → 引用）
-->
<template>
  <div class="qp" role="dialog" aria-modal="true" aria-label="题目预览" @click.self="$emit('close')">
    <aside class="qp__panel">
      <header class="qp__head">
        <h3 class="qp__title">题目预览</h3>
        <button class="qp__close" type="button" aria-label="关闭预览" @click="$emit('close')">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M5.6 5.6l8.8 8.8M14.4 5.6l-8.8 8.8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </header>

      <div class="qp__body">
        <p class="qp__label">题干</p>
        <p class="qp__stem">{{ question.stem }}</p>

        <p class="qp__label">选项（<span class="qp__ok">{{ answerText }}</span> 为正确答案）</p>
        <ul class="qp__opts">
          <li
            v-for="opt in question.options"
            :key="opt.key"
            class="qp__opt"
            :class="{ 'is-right': question.answer.includes(opt.key) }"
          >
            <span class="qp__optKey">{{ opt.key }}</span>
            <span class="qp__optText">{{ opt.text }}</span>
          </li>
        </ul>

        <p class="qp__label">解析</p>
        <p class="qp__analysis">{{ question.analysis || '（未填写解析 —— 不影响判分，但学生交卷后看不到讲解）' }}</p>

        <p class="qp__label">题目信息</p>
        <p class="qp__meta">
          {{ TYPE_LABEL[question.type] }} · {{ DIFF_LABEL[question.difficulty] }} ·
          {{ question.knowledgePoints.join(' / ') || '未设知识点' }} · {{ question.courseName }}
        </p>
        <p class="qp__meta">出题人：{{ question.creatorName }} · 创建于 {{ question.createdAt }}</p>
        <p class="qp__usage">
          被 {{ question.usageCount }} 份试卷用过
          <span v-if="question.status === 'disabled'" class="qp__off">· 当前已停用</span>
        </p>
      </div>

      <footer class="qp__foot">
        <button class="q-btn" type="button" @click="$emit('close')">关闭</button>
        <template v-if="own">
          <button class="q-btn" type="button" @click="$emit('toggle-status', question)">
            {{ question.status === 'enabled' ? '停用' : '启用' }}
          </button>
          <button class="q-btn q-btn--primary" type="button" @click="$emit('edit', question)">编辑</button>
        </template>
        <button v-else class="q-btn q-btn--primary" type="button" @click="$emit('cite', question)">引用进试卷</button>
      </footer>
    </aside>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { TYPE_LABEL, DIFF_LABEL } from '@/config/teacherDict';

const props = defineProps({
  question: { type: Object, required: true },
  // 是否是自己出的题 —— 决定页脚展示「编辑 / 停用」还是「引用」
  own: { type: Boolean, default: false },
});

defineEmits(['close', 'edit', 'toggle-status', 'cite']);

const answerText = computed(() => (props.question.answer || []).join(''));
</script>

<style lang="scss" scoped>
.qp {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.28);

  &__panel {
    width: 440px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #fff;
    box-shadow: -18px 0 40px -12px rgba(0, 0, 0, 0.22);
  }

  &__head {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 20px 24px;
    border-bottom: 1px solid var(--s-hairline);
  }
  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__close {
    margin-left: auto;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 50%;
    background: var(--s-soft);
    color: var(--s-ink-2);
    cursor: pointer;

    &:hover {
      background: #eaebee;
      color: var(--s-ink);
    }
  }

  &__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    padding: 20px 24px 24px;
  }

  &__label {
    margin: 18px 0 6px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);

    &:first-child {
      margin-top: 0;
    }
  }
  &__ok {
    color: #1d8a43;
    font-weight: 600;
  }

  &__stem {
    margin: 0;
    font-size: 14px;
    line-height: 22px;
    color: var(--s-ink);
  }

  &__opts {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  &__opt {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 10px;
    border-radius: 8px;
    background: var(--s-soft);
    font-size: 13px;
    line-height: 20px;
    color: #333;

    &.is-right {
      background: #eafaf0;
      color: #1d8a43;
      font-weight: 500;
    }
  }
  &__optKey {
    flex: 0 0 18px;
    font-weight: 600;
  }
  &__optText {
    flex: 1 1 auto;
    min-width: 0;
  }

  &__analysis {
    margin: 0;
    font-size: 13px;
    line-height: 20px;
    color: var(--s-ink-2);
  }
  &__meta {
    margin: 0 0 4px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
  &__usage {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 18px;
    font-weight: 500;
    color: var(--sa);
  }
  &__off {
    color: var(--s-ink-3);
    font-weight: 400;
  }

  &__foot {
    flex: 0 0 auto;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding: 16px 24px;
    border-top: 1px solid var(--s-hairline);
  }
}
</style>
