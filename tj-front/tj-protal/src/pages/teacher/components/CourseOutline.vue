<!--
 * 课程大纲预览抽屉
 * -----------------------------------------------------------------------------
 * 为什么需要它：课程卡上只有「N 章」这个数字，要看目录结构就得进编辑向导 ——
 * 而编辑是「改」的语境，光想看一眼不该进那里。所以列表页给一个只读预览：
 *
 *   1) 课程规模（章 / 小节 / 题目 / 知识点 / 考试 / 学生）
 *   2) 目录树（每节的视频状态、练习题、试看开关）—— 一眼看出配齐了没有
 *   3) 没有本地目录数据时如实说明，并给「去编辑」入口（不编造章节内容）
 * 数据来源：**由父组件按需传进来的 `outline`**（`/cs/teacher/course-draft` 的 chapters，
 * 与建课向导第 ② 步看到的目录完全一致，含未上架的改动）。
 * ⚠️ 别再读列表项自带的字段：课程列表接口**不返回目录明细**，那样会让预览永远显示
 *    「这门课还没有目录」——这正是曾经的现象（已修）。
-->
<template>
  <div class="co" role="dialog" aria-modal="true" aria-label="课程预览" @click.self="$emit('close')">
    <aside class="co__panel">
      <header class="co__head">
        <div class="co__headText">
          <h3 class="co__title">{{ course.name }}</h3>
          <p class="co__sub">
            {{ course.chapters }} 章<template v-if="course.sections"> · {{ course.sections }} 小节</template>
            <template v-if="course.status"> · {{ course.status === 'published' ? '已上架' : '草稿' }}</template>
          </p>
        </div>
        <button class="co__close" type="button" aria-label="关闭预览" @click="$emit('close')">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
            <path d="M5.6 5.6l8.8 8.8M14.4 5.6l-8.8 8.8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </header>

      <div class="co__body">
        <!-- 规模：拿不到来源的数字显示「—」，不写 0 冒充（学生数 / 考试场次暂无聚合接口） -->
        <ul class="co__stats">
          <li class="co__stat"><strong>{{ course.chapters ?? '—' }}</strong><span>章节</span></li>
          <li class="co__stat"><strong>{{ course.sections ?? '—' }}</strong><span>小节</span></li>
          <li class="co__stat"><strong>{{ course.questionCount ?? '—' }}</strong><span>题目</span></li>
          <li class="co__stat"><strong>{{ course.knowledgePoints.length || '—' }}</strong><span>知识点</span></li>
          <li class="co__stat"><strong>{{ course.examCount ?? '—' }}</strong><span>考试</span></li>
          <li class="co__stat"><strong>{{ course.students ?? '—' }}</strong><span>学生</span></li>
        </ul>

        <!-- 目录树 -->
        <template v-if="outline && outline.length">
          <p class="co__label">课程目录</p>
          <ol class="co__chs">
            <li v-for="(ch, ci) in outline" :key="ch.id" class="co__ch">
              <div class="co__chTitle">
                <span class="co__chNo">{{ ci + 1 }}</span>
                <span>{{ ch.title || '未命名章节' }}</span>
                <span class="co__chCount">{{ ch.sections.length }} 节</span>
              </div>
              <ul class="co__secs">
                <li v-for="s in ch.sections" :key="s.id" class="co__sec">
                  <span class="co__secName">{{ s.title || '未命名小节' }}</span>
                  <span class="co__tags">
                    <em v-if="s.duration" class="co__tag">{{ s.duration }}</em>
                    <em class="co__tag" :class="videoClass(s)">{{ videoText(s) }}</em>
                    <!-- P17 起不再有「小节配练习」这条线，这里只保留视频状态与试看 -->
                    <em v-if="s.preview" class="co__tag co__tag--ok">试看</em>
                  </span>
                </li>
              </ul>
            </li>
          </ol>
        </template>

        <!-- 无目录数据：三种情况分开说，且不编造章节 -->
        <div v-else class="co__empty">
          <template v-if="loading">
            <p class="co__emptyTitle">正在读取课程目录…</p>
          </template>
          <template v-else-if="error">
            <p class="co__emptyTitle">目录读取失败</p>
            <p class="co__emptyText">{{ error }}</p>
          </template>
          <template v-else>
            <p class="co__emptyTitle">这门课还没有目录</p>
            <p class="co__emptyText">
              章节与小节要在建课向导的第 ② 步「课程目录」里建立 —— 建完保存，这里立刻就能看到。
              点下面的「去编辑」即可开始。
            </p>
          </template>
        </div>
      </div>

      <footer class="co__foot">
        <button class="q-btn" type="button" @click="$emit('close')">关闭</button>
        <button class="q-btn q-btn--primary" type="button" @click="$emit('edit', course)">去编辑</button>
      </footer>
    </aside>
  </div>
</template>

<script setup>
defineProps({
  course: { type: Object, required: true },
  /** 目录明细（建课向导 chapters 的形状）；由父组件按需拉取后传入 */
  outline: { type: Array, default: null },
  loading: { type: Boolean, default: false },
  /** 拉取失败时由父组件传入服务端原话；有值时优先于空态展示 */
  error: { type: String, default: '' },
});
defineEmits(['close', 'edit']);

const videoText = (s) => {
  const st = s.video?.status;
  if (st === 'done') return '视频已上传';
  if (st === 'uploading') return '上传中';
  return '未传视频';
};
const videoClass = (s) => {
  const st = s.video?.status;
  if (st === 'done') return 'co__tag--ok';
  if (st === 'uploading') return 'co__tag--warn';
  return 'co__tag--muted';
};
</script>

<style lang="scss" scoped>
.co {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(16, 24, 40, 0.28);
  display: flex;
  justify-content: flex-end;

  &__panel {
    width: 560px;
    max-width: 92vw;
    height: 100%;
    background: #fff;
    display: flex;
    flex-direction: column;
    box-shadow: -12px 0 32px rgba(16, 24, 40, 0.12);
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 24px 24px 16px;
    border-bottom: 1px solid rgba(16, 24, 40, 0.06);
  }
  &__headText {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__title {
    margin: 0;
    font-size: 18px;
    line-height: 26px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__sub {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__close {
    flex: 0 0 auto;
    width: 32px;
    height: 32px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--s-ink-3);
    cursor: pointer;

    &:hover {
      background: rgba(16, 24, 40, 0.05);
    }
  }

  &__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    padding: 20px 24px;
  }

  &__stats {
    list-style: none;
    margin: 0 0 20px;
    padding: 0;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }
  &__stat {
    padding: 12px;
    border-radius: 12px;
    background: #fafafc;
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.05);

    strong {
      display: block;
      font-size: 18px;
      line-height: 24px;
      font-weight: 600;
      color: var(--s-ink);
    }
    span {
      display: block;
      margin-top: 2px;
      font-size: 11px;
      color: var(--s-ink-3);
    }
  }

  &__label {
    margin: 0 0 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__chs {
    list-style: none;
    margin: 0;
    padding: 0;
  }
  &__ch + &__ch {
    margin-top: 14px;
  }
  &__chTitle {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__chNo {
    flex: 0 0 auto;
    width: 20px;
    height: 20px;
    border-radius: 6px;
    background: rgba(0, 102, 204, 0.1);
    color: #0066cc;
    font-size: 11px;
    line-height: 20px;
    text-align: center;
  }
  &__chCount {
    margin-left: auto;
    font-size: 11px;
    font-weight: 400;
    color: var(--s-ink-3);
  }
  &__secs {
    list-style: none;
    margin: 8px 0 0;
    padding: 0;
  }
  &__sec {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 8px;

    & + & {
      margin-top: 2px;
    }
    &:hover {
      background: #fafafc;
    }
  }
  &__secName {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 12px;
    color: var(--s-ink-2);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__tags {
    flex: 0 0 auto;
    display: flex;
    gap: 6px;
  }
  &__tag {
    font-style: normal;
    font-size: 11px;
    line-height: 18px;
    padding: 0 6px;
    border-radius: 5px;
    background: rgba(16, 24, 40, 0.05);
    color: var(--s-ink-3);

    &--ok {
      background: rgba(29, 138, 67, 0.1);
      color: #1d8a43;
    }
    &--warn {
      background: rgba(178, 106, 0, 0.12);
      color: #b26a00;
    }
    &--muted {
      background: rgba(16, 24, 40, 0.04);
      color: var(--s-ink-4, #9a9aa0);
    }
  }

  &__empty {
    padding: 20px 0;
    text-align: center;
  }
  &__emptyTitle {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__emptyText {
    margin: 0;
    font-size: 12px;
    line-height: 20px;
    color: var(--s-ink-3);
  }

  &__foot {
    flex: 0 0 auto;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding: 16px 24px;
    border-top: 1px solid rgba(16, 24, 40, 0.06);
  }
}
</style>
