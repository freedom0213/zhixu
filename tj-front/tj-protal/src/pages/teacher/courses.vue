<!--
 * 教师端 · 我的课程（设计稿 T2）
 * -----------------------------------------------------------------------------
 * 课程卡只放「老师会据此做决定」的信息：有几章、多少学生、多少题、考过几场。
 * 知识点是课程级资产（录题时从这里选），所以单独展示 ——
 * 它和题库联动：题目数按课程从题库反推，与题库列表的筛选口径一致。
-->
<template>
  <div class="cs">
    <!-- 页头 -->
    <div class="cs-head">
      <div class="cs-head__text">
        <h2 class="s-h2">我的课程</h2>
        <p class="cs-head__sub">{{ list.length }} 门课 · 题目数按课程从题库反推，与题库列表口径一致</p>
      </div>
      <div class="cs-head__actions">
        <button class="q-btn" type="button" @click="$router.push('/teacher/questions')">去题库</button>
        <button class="q-btn q-btn--primary" type="button" @click="$router.push('/teacher/courses/new')">＋ 新建课程</button>
      </div>
    </div>

    <p v-if="loading" class="cs-state">加载中…</p>

    <p v-else-if="!list.length" class="cs-state">
      你还没有课程。点右上角「＋ 新建课程」建一门；已有课程没出现在这里的话，让管理员把你的讲师身份关联到该课程。
    </p>

    <div v-else class="cs-grid">
      <div v-for="c in list" :key="c.id" class="s-card cs-card">
        <p class="cs-card__name">
          {{ c.name }}
          <span v-if="c.status" class="cs-tag" :class="c.status === 'published' ? 'cs-tag--ok' : 'cs-tag--warn'">
            {{ c.status === 'published' ? '已上架' : '草稿' }}
          </span>
          <span v-if="c.edited" class="cs-tag">有未上架改动</span>
        </p>
        <p class="cs-card__meta">
          {{ c.chapters }} 章<template v-if="c.sections"> {{ c.sections }} 节</template> ·
          {{ c.students == null ? '—' : c.students }} 名学生
        </p>

        <div class="cs-card__stats">
          <div class="cs-stat">
            <strong>{{ c.questionCount == null ? '—' : c.questionCount }}</strong>
            <span>题目</span>
          </div>
          <div class="cs-stat">
            <strong>{{ c.examCount == null ? '—' : c.examCount }}</strong>
            <span>考试</span>
          </div>
          <div class="cs-stat">
            <strong>{{ c.knowledgePoints.length || '—' }}</strong>
            <span>知识点</span>
          </div>
        </div>

        <div class="cs-card__kp">
          <span v-for="k in c.knowledgePoints" :key="k" class="cs-kpTag">{{ k }}</span>
          <span v-if="!c.knowledgePoints.length" class="cs-kpTag is-empty">知识点待建</span>
        </div>

        <div class="cs-card__foot">
          <p class="cs-card__last">
            <template v-if="c.lastExam">最近考试：{{ c.lastExam.name }}（{{ statusLabel(c.lastExam.status) }}）</template>
            <template v-else>还没有组织过考试</template>
          </p>
          <div class="cs-card__ops">
            <button class="q-act" type="button" @click="openPreview(c)">预览</button>
            <button class="q-act" type="button" @click="$router.push({ path: '/teacher/courses/new', query: { id: c.id } })">
              编辑
            </button>
            <button class="q-act" type="button" @click="$router.push({ path: '/teacher/questions', query: { courseId: c.id } })">
              查题目
            </button>
            <button class="q-act q-act--muted" type="button" @click="$router.push({ path: '/teacher/exams/new', query: { course: c.id } })">
              出卷
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 课程大纲预览（只读；目录**按需拉草稿口径**，与编辑页看到的一致） -->
    <CourseOutline
      v-if="preview"
      :course="preview"
      :outline="previewOutline"
      :loading="previewLoading"
      :error="previewError"
      @close="closePreview"
      @edit="goEdit"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listMyCourses } from '@/api/teacher/dashboard';
import { getCourseDraft } from '@/api/teacher/course';
import CourseOutline from '@/pages/teacher/components/CourseOutline.vue';

const router = useRouter();
// 预览中的课程（null = 关闭）
const preview = ref(null);
// 预览用的目录（按需拉取；列表接口不返回目录明细，别放在列表项上）
const previewOutline = ref([]);
const previewLoading = ref(false);
const previewError = ref('');

const closePreview = () => {
  preview.value = null;
  previewOutline.value = [];
  previewError.value = '';
};

/**
 * 打开预览：目录按需拉**草稿口径**（`/cs/teacher/course-draft`），与编辑页看到的一致 ——
 * 含还没上架的改动，也含从正式表播种出来的已有目录（课程服务侧本来就有数据）。
 * ⚠️ 以前这里是 `preview = c`，读列表项自带的 `course.outline`，而它**恒为 null**
 *    （列表接口不返回目录明细）→ 预览永远显示「这门课还没有目录数据」。
 */
const openPreview = async (c) => {
  preview.value = c;
  previewOutline.value = [];
  previewError.value = '';
  previewLoading.value = true;
  try {
    const d = await getCourseDraft(c.id);
    previewOutline.value = d?.chapters || [];
  } catch (e) {
    previewError.value = e?.message || '目录加载失败';
  } finally {
    previewLoading.value = false;
  }
};
const goEdit = (c) => {
  preview.value = null;
  router.push({ path: '/teacher/courses/new', query: { id: c.id } });
};

const loading = ref(true);
const list = ref([]);

const statusLabel = (s) => ({ draft: '草稿', published: '已发布', marking: '批改中', closed: '已结束' }[s] || s);

onMounted(async () => {
  list.value = await listMyCourses();
  loading.value = false;
});
</script>

<style lang="scss" scoped>
.cs {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

.cs-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__text {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--s-ink-3);
  }
  &__actions {
    flex: 0 0 auto;
    display: flex;
    gap: 10px;
  }
}

.cs-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.cs-card {
  padding: 24px;

  // 状态标签：本地编辑 / 草稿 / 已上架（只在卡片标题旁，不染色整卡）
  .cs-tag {
    flex: 0 0 auto;
    font-size: 11px;
    line-height: 18px;
    font-weight: 500;
    padding: 0 6px;
    border-radius: 5px;
    background: rgba(0, 102, 204, 0.1);
    color: #0066cc;

    &--ok {
      background: rgba(29, 138, 67, 0.1);
      color: #1d8a43;
    }
    &--warn {
      background: rgba(178, 106, 0, 0.12);
      color: #b26a00;
    }
  }

  &__name {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 4px;
    font-size: 16px;
    line-height: 22px;
    font-weight: 600;
    color: var(--s-ink);
  }

  &__meta {
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__stats {
    display: flex;
    gap: 28px;
    margin: 16px 0;
    padding: 12px 16px;
    border-radius: 12px;
    background: #f5f5f7;
  }
  &__kp {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 24px;
  }
  &__foot {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 12px;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid #f0f0f2;
  }
  &__last {
    flex: 1 1 auto;
    min-width: 0;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__ops {
    flex: 0 0 auto;
    display: flex;
    gap: 4px;
  }
}

.cs-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;

  strong {
    font-size: 20px;
    font-weight: 600;
    color: var(--s-ink);
  }
  span {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.cs-kpTag {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f5f5f7;
  font-size: 11px;
  color: #6e6e73;

  // 知识点关联表未建（契约 §14.3）：如实标「待建」，不列假知识点
  &.is-empty {
    background: transparent;
    color: #a1a1a6;
    box-shadow: inset 0 0 0 1px #e5e5ea;
  }
}

.cs-state {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}
</style>
