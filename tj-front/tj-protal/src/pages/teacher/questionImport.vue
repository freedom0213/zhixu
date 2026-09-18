<!--
 * 教师端 · Excel 导入题目（设计稿 Q4）
 * -----------------------------------------------------------------------------
 * 三步：下载模板 → 上传填好的文件 → 看校验结果。看起来简单，但成败全在第三步：
 *
 *   · **逐行校验，不是整份退回**：通过的行已经入库，只有失败的行需要改。
 *   · **问题必须精确到「哪一行、哪个字段、什么问题」**（「第 5 行 · 正确答案为空」），
 *     只说「有 3 处错误」等于没说 —— 老师得自己对整份表。
 *   · **碰到已存在的题干 → 跳过**，不覆盖、不新增副本（覆盖会破坏引用它的试卷）。
 *   · 失败行支持**下载后重传**，已导入的不会重复入库。
 *
 * 「上传 Word 试卷直接解析」这条路刻意不做：老师手里的卷子题号 / 选项 / 答案标注
 * 格式极不统一，纯解析准确率低，校对时间比手录还长，最后功能会被弃用。
 * 固定模板列是唯一能稳定跑通的做法。
-->
<template>
  <div class="qi">
    <!-- 页头 -->
    <div class="qi-head">
      <div class="qi-head__text">
        <button class="qi-back" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回我的题库
        </button>
        <h2 class="s-h2">Excel 导入题目</h2>
        <p class="qi-head__sub">
          8 个字段就是模板的 8 列。下载 → 填写 → 上传 → 逐行校验；通过的行入库，有问题的行改完只重传那几行。
        </p>
      </div>
    </div>

    <!-- ① 模板 + ② 上传 -->
    <section class="s-card qi-card">
      <h3 class="qi-card__title">① 下载模板，按列填</h3>
      <p class="qi-card__desc">
        模板只有一行表头。选项之间用竖线分隔；单选答案填 <code>B</code>，多选填 <code>AB</code>。
      </p>
      <p class="qi-cols">列依次是：{{ COLUMNS.join(' · ') }}</p>
      <button class="q-btn" type="button" @click="downloadTemplate">下载模板</button>

      <div class="qi-divider"></div>

      <h3 class="qi-card__title">② 上传填好的文件</h3>
      <label
        class="qi-drop"
        :class="{ 'is-over': dragover, 'has-file': !!file }"
        @dragover.prevent="dragover = true"
        @dragleave.prevent="dragover = false"
        @drop.prevent="onDrop"
      >
        <input ref="fileInput" class="qi-drop__input" type="file" accept=".xlsx,.csv" @change="onPick" />
        <template v-if="!file">
          <span class="qi-drop__ic" v-html="uploadIcon"></span>
          <span class="qi-drop__title">拖拽 Excel 到此处，或点击选择文件</span>
          <span class="qi-drop__hint">支持 .xlsx / .csv，单次最多 200 题</span>
        </template>
        <template v-else>
          <span class="qi-file">
            <span class="qi-file__ic" v-html="sheetIcon"></span>
            <span class="qi-file__text">
              <strong>{{ file.name }}</strong>
              <em>{{ fileSize }}</em>
            </span>
            <span class="q-act" @click.prevent.stop="clearFile">重新选择</span>
          </span>
        </template>
      </label>

      <p v-if="fileError" class="qi-err">{{ fileError }}</p>

      <div class="qi-actions">
        <button class="q-btn q-btn--primary" type="button" :disabled="!file || busy" @click="runImport">
          {{ busy ? '校验中…' : '上传并校验' }}
        </button>
        <span class="qi-actions__hint">校验不会因为个别行出错而整份退回。</span>
      </div>
    </section>

    <!-- ③ 校验结果 -->
    <section v-if="result" class="s-card qi-card">
      <h3 class="qi-card__title">③ 校验结果</h3>
      <p class="qi-card__desc">
        {{ result.fileName }} · 共 {{ result.total }} 行。通过的行已经入库，只有失败的行需要改。
      </p>

      <div class="qi-stat">
        <span class="qi-chip is-ok">已入库 {{ result.success.length }} 题</span>
        <span v-if="result.failed.length" class="qi-chip is-warn">失败 {{ result.failed.length }} 行</span>
        <span v-if="result.skipped.length" class="qi-chip is-mute">跳过 {{ result.skipped.length }} 行（已存在）</span>
        <button
          v-if="result.failed.length"
          class="q-btn q-btn--sm qi-stat__btn"
          type="button"
          @click="downloadFailed"
        >
          下载失败行并重传
        </button>
      </div>

      <div v-if="result.failed.length || result.skipped.length" class="qi-table">
        <div class="qi-row qi-row--head">
          <span class="qi-c qi-c--row">行号</span>
          <span class="qi-c qi-c--type">题型</span>
          <span class="qi-c qi-c--stem">题干</span>
          <span class="qi-c qi-c--why">问题</span>
        </div>
        <div v-for="(r, i) in problemRows" :key="`${r.row}-${i}`" class="qi-row">
          <span class="qi-c qi-c--row">第 {{ r.row }} 行</span>
          <span class="qi-c qi-c--type">{{ r.type }}</span>
          <span class="qi-c qi-c--stem" :title="r.stem">{{ r.stem }}</span>
          <span class="qi-c qi-c--why" :class="r.level === 'failed' ? 'is-warn' : 'is-mute'">{{ r.reason }}</span>
        </div>
      </div>

      <p class="qi-note">
        其余 {{ result.success.length }} 题已入库，不会因失败或跳过而回滚。改完失败行再传一次，已导入的不会重复。
      </p>

      <div class="qi-divider"></div>
      <div class="qi-foot">
        <button class="q-btn" type="button" @click="resetImport">再导一份</button>
        <button class="q-btn q-btn--primary" type="button" @click="goBack">完成，返回我的题库</button>
      </div>
    </section>

    <p class="qi-dep">
      依赖说明：导入走 <code>POST /es/questions/import</code>，后端当前还没有这个接口（现在由 mock 返回示例校验结果）。
      实现时**逐行校验与三段结果（成功 / 失败 / 跳过）是硬要求**，这是整个功能可用与否的分水岭。
    </p>

    <transition name="q-fade">
      <p v-if="notice" class="q-toast" role="status">{{ notice }}</p>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { importQuestions } from '@/api/teacher/questions';

const router = useRouter();

const COLUMNS = ['题型', '题干', '选项', '正确答案', '解析', '难度', '知识点', '所属课程'];

const uploadIcon = `<svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <path d="M14 19V7.4" stroke="#0066CC" stroke-width="1.8" stroke-linecap="round"/>
  <path d="M9.4 12 14 7.4 18.6 12" stroke="#0066CC" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M6.4 20.6h15.2" stroke="#0066CC" stroke-width="1.8" stroke-linecap="round"/>
</svg>`;

const sheetIcon = `<svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <rect x="3.4" y="3" width="15.2" height="16" rx="2.4" stroke="#1D8A43" stroke-width="1.6"/>
  <path d="M3.4 8h15.2M9 8v11" stroke="#1D8A43" stroke-width="1.6"/>
</svg>`;

const file = ref(null);
const fileInput = ref(null);
const dragover = ref(false);
const fileError = ref('');
const busy = ref(false);
const result = ref(null);
const notice = ref('');
let noticeTimer = null;

// 清空时必须把 input.value 也清掉：否则「重新选择」后再挑同一个文件
// 不会触发 change（值没变），老师会以为点了没用。
const resetPicker = () => {
  file.value = null;
  if (fileInput.value) fileInput.value.value = '';
};

const toast = (text) => {
  notice.value = text;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.value = '';
  }, 2600);
};

const fileSize = computed(() => {
  if (!file.value) return '';
  const kb = file.value.size / 1024;
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(kb))} KB`;
});

// 失败与跳过分开标色，但放在同一张表里 —— 老师只关心「哪些行还要我处理」
const problemRows = computed(() => {
  if (!result.value) return [];
  return [
    ...result.value.failed.map((r) => ({ ...r, level: 'failed' })),
    ...result.value.skipped.map((r) => ({ ...r, level: 'skipped' })),
  ].sort((a, b) => a.row - b.row);
});

const pickFile = (f) => {
  fileError.value = '';
  if (!f) return;
  if (!/\.(xlsx|csv)$/i.test(f.name)) {
    fileError.value = '只支持 .xlsx / .csv。如果是老式 .xls，请另存为 .xlsx 再传。';
    return;
  }
  file.value = f;
  result.value = null;
};

const onPick = (e) => pickFile(e.target.files?.[0]);

const onDrop = (e) => {
  dragover.value = false;
  pickFile(e.dataTransfer?.files?.[0]);
};

const clearFile = () => {
  resetPicker();
  result.value = null;
  fileError.value = '';
};

const resetImport = () => {
  resetPicker();
  result.value = null;
  fileError.value = '';
};

// 下载走前端 Blob：模板与失败行都不该依赖后端（后端挂了也要能拿模板）
const download = (rows, filename) => {
  const csv = rows.map((r) => r.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n');
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

const downloadTemplate = () => {
  download(
    [
      COLUMNS,
      ['单选', 'Spring Boot 的核心注解是？', '@EnableAutoConfiguration|@SpringBootApplication|@ComponentScan|@Configuration', 'B', '自动配置由条件注解按需装配。', '容易', '自动配置', 'Spring Boot 快速上手'],
      ['多选', '下列哪些属于线程池拒绝策略？', 'AbortPolicy|CallerRunsPolicy|DiscardPolicy|BlockPolicy', 'ABC', '', '中等', '并发基础', 'Java 集合与并发编程'],
    ],
    '题库导入模板.csv'
  );
  toast('模板已下载：8 列 + 2 行示例');
};

const downloadFailed = () => {
  if (!result.value?.failed.length) return;
  download([COLUMNS, ...result.value.failed.map((r) => [r.type, r.stem, '', '', '', '', '', ''])], '导入失败行.csv');
  toast('失败行已导出，改完可以直接重传');
};

const runImport = async () => {
  if (!file.value || busy.value) return;
  busy.value = true;
  try {
    result.value = await importQuestions(file.value);
    const { success, failed, skipped } = result.value;
    toast(`校验完成：入库 ${success.length} 题、失败 ${failed.length} 行、跳过 ${skipped.length} 行`);
  } catch (e) {
    toast(e?.message || '上传失败，请重试');
  } finally {
    busy.value = false;
  }
};

const goBack = () => router.push('/teacher/questions');
</script>

<style lang="scss" scoped>
.qi {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

.qi-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
}

.qi-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--sa);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.qi-card {
  padding: 28px;

  &__title {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    color: var(--s-ink);
  }
  &__desc {
    margin: 0 0 10px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
}

.qi-cols {
  margin: 0 0 16px;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.qi-divider {
  height: 1px;
  margin: 26px 0 22px;
  background: var(--s-hairline);
}

// ---- 拖拽区 ----
.qi-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 148px;
  padding: 20px;
  border-radius: 12px;
  background: #fafafc;
  box-shadow: inset 0 0 0 1.5px #c7c7cc;
  cursor: pointer;
  transition: background-color 0.16s ease, box-shadow 0.16s ease;

  &:hover {
    background: #f5f6f8;
  }
  &.is-over {
    background: #eef5ff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }
  &.has-file {
    min-height: 88px;
    background: #f7fdf9;
    box-shadow: inset 0 0 0 1.5px #a8d9b9;
  }

  &__input {
    position: absolute;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }
  &__ic {
    display: flex;
  }
  &__title {
    font-size: 14px;
    font-weight: 500;
    color: var(--s-ink);
  }
  &__hint {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.qi-file {
  display: flex;
  align-items: center;
  gap: 12px;

  &__ic {
    display: flex;
  }
  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      font-size: 13px;
      font-weight: 500;
      color: var(--s-ink);
    }
    em {
      font-size: 12px;
      font-style: normal;
      color: var(--s-ink-3);
    }
  }
}

.qi-err {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-danger);
}

.qi-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 18px;

  &__hint {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

// ---- 校验结果 ----
.qi-stat {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;

  &__btn {
    margin-left: auto;
  }
}

.qi-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;

  &.is-ok {
    background: #e6f6ec;
    color: #1d8a43;
  }
  &.is-warn {
    background: #fff4e0;
    color: #b26a00;
  }
  &.is-mute {
    background: #f2f2f4;
    color: var(--s-ink-2);
  }
}

.qi-table {
  --qi-cols: 76px 72px minmax(180px, 1fr) 360px;
}

.qi-row {
  display: grid;
  grid-template-columns: var(--qi-cols);
  align-items: center;
  min-height: 44px;
  border-bottom: 1px solid var(--s-divider);
  font-size: 12px;
  line-height: 18px;
  color: #333;

  &--head {
    min-height: 36px;
    border-bottom-color: var(--s-hairline);
    color: var(--s-ink-3);
  }
}

.qi-c {
  padding-right: 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  &--row {
    color: var(--s-ink-2);
  }
  &--why {
    padding-right: 0;
  }
  &.is-warn {
    color: #b26a00;
  }
  &.is-mute {
    color: var(--s-ink-2);
  }
}

.qi-note {
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.qi-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.qi-dep {
  margin: 0;
  padding: 14px 18px;
  border-radius: 12px;
  background: #fff4e0;
  color: #b26a00;
  font-size: 12px;
  line-height: 20px;
}

code {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  font-size: 12px;
}

.q-toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  z-index: 80;
  transform: translateX(-50%);
  margin: 0;
  padding: 10px 20px;
  border-radius: 20px;
  background: rgba(29, 29, 31, 0.92);
  color: #fff;
  font-size: 13px;
  line-height: 20px;
}

.q-fade-enter-active,
.q-fade-leave-active {
  transition: opacity 0.2s ease;
}
.q-fade-enter-from,
.q-fade-leave-to {
  opacity: 0;
}
</style>
