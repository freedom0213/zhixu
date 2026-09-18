<!--
 * 学习笔记（/student/notes）— 严格对齐设计稿 06
 * 设计稿规格（Ardot fileId 725009394574981，frame「06 学习笔记」）：
 *   Content 1096，横向间距 32
 *   左 248：Card / 笔记分类（圆角 18、内边距 16、纵向间距 8）
 *           Head(标题 15/20 SemiBold + 「新建」12/17 Medium) + 分类行（高 40、圆角 10；选中 #E8F1FC）
 *   右 816：Card / 笔记列表（圆角 18、内边距 24、纵向间距 20）
 *           Head(标题 15/20 SemiBold + 搜索框 220×36 圆角18 + 「新建笔记」88×36 圆角18)
 *           → 笔记栅格（间距 16，卡片 245×148、圆角 14、内边距 16、底色 #FAFAFC）
 *             卡内：来源标签 11/15 Medium #0066CC → 标题 14/20 SemiBold → 摘要 12/18（2 行截断）→ 元信息 11/15
 *           → 分页
 * 数据契约与降级：
 *   - getAllNotes({pageNo,pageSize}) → {list:[{id,content,createTime,authorId,authorName,
 *     authorIcon,noteMoment,isPrivate,isGathered}],total}
 *   - 接口无「所属课程」字段 → 分类用「全部笔记 / 我创建的 / 我采集的」（真实可判定），
 *     卡片来源标签同口径；标题由正文首行派生（真实内容，不编造）
 *   - 编辑 / 删除仅对自己的笔记出现，悬停时在卡片右上角显示（保持设计稿静态观感）
 -->
<template>
  <div class="nt">
    <!-- ============ 左：笔记分类（按课程） ============ -->
    <aside class="s-card catCard">
      <div class="catCard__head">
        <h2 class="cardTitle">笔记分类</h2>
        <button class="cardLink" type="button" :disabled="!courses.length" @click="openEditor(null)">新建</button>
      </div>
      <div v-loading="coursesLoading" class="catCard__body">
        <button
          v-for="c in courses"
          :key="c.courseId"
          class="folder"
          :class="{ 'is-on': String(activeCourseId) === String(c.courseId) }"
          type="button"
          :title="c.courseName"
          @click="switchCourse(c.courseId)"
        >
          <svg class="folder__icon" viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path d="M2.5 5.2a1.6 1.6 0 0 1 1.6-1.6h2.7l1.4 1.7h5.7a1.6 1.6 0 0 1 1.6 1.6v5.9a1.6 1.6 0 0 1-1.6 1.6H4.1a1.6 1.6 0 0 1-1.6-1.6V5.2Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
          </svg>
          <span class="folder__label">{{ c.courseName }}</span>
        </button>
        <p v-if="!coursesLoading && !courses.length" class="catCard__empty">
          还没有加入任何课程<br />去课程中心逛逛吧
        </p>
      </div>
    </aside>

    <!-- ============ 右：笔记列表 ============ -->
    <section class="s-card noteList">
      <div class="noteList__head">
        <h2 class="cardTitle">{{ activeCourse ? activeCourse.courseName : '我的笔记' }}</h2>
        <div class="noteList__actions">
          <label class="search">
            <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
              <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <input v-model.trim="keyword" type="search" placeholder="搜索笔记内容" aria-label="搜索笔记" maxlength="50" />
          </label>
          <button class="newBtn" type="button" :disabled="!courses.length" @click="openEditor(null)">新建笔记</button>
        </div>
      </div>

      <div class="noteList__chips" role="tablist" aria-label="笔记来源">
        <button
          v-for="f in filters"
          :key="f.value"
          class="chip"
          :class="{ 'is-on': filter === f.value }"
          type="button"
          role="tab"
          :aria-selected="filter === f.value"
          @click="filter = f.value"
        >{{ f.label }}</button>
      </div>

      <div class="grid" v-loading="loading">
        <article
          class="note"
          v-for="n in filteredList"
          :key="n.id"
          tabindex="0"
          @click="openEditor(n)"
          @keyup.enter="openEditor(n)"
        >
          <div class="note__tools" v-if="isMine(n)" @click.stop>
            <button class="tool" type="button" title="编辑" aria-label="编辑笔记" @click="openEditor(n)">
              <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M10.4 2.9a1.6 1.6 0 0 1 2.3 2.3L5.6 12.3l-3 .7.7-3 7.1-7.1Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
              </svg>
            </button>
            <button class="tool tool--danger" type="button" title="删除" aria-label="删除笔记" @click="removeNote(n)">
              <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3.5 5h9M6.5 5V3.6h3V5M5 5l.6 8h4.8L11 5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>

          <span class="note__tag">{{ n.isGathered ? '我采集的' : '我的笔记' }}</span>
          <h3 class="note__title" :title="titleOf(n)">{{ titleOf(n) }}</h3>
          <p class="note__excerpt">{{ excerptOf(n) }}</p>
          <span class="note__meta">{{ metaOf(n) }}</span>
        </article>

        <p class="empty" v-if="!loading && filteredList.length === 0">
          {{ keyword ? '没有匹配的笔记' : (isLogin ? '还没有笔记，点「新建笔记」记一条吧' : '登录后查看你的笔记') }}
        </p>
      </div>

      <div class="noteList__foot" v-if="total > params.pageSize">
        <SPagination v-model="params.pageNo" :total="total" :page-size="params.pageSize" @change="loadList" />
      </div>
    </section>

    <!-- ============ 编辑弹窗 ============ -->
    <el-dialog v-model="editorVisible" :title="editing ? '编辑笔记' : '新建笔记'" width="520px" append-to-body>
      <label v-if="!editing" class="dlgField">
        <span class="dlgField__label">所属课程</span>
        <select v-model="editorCourseId" class="dlgSelect" aria-label="选择笔记所属课程">
          <option v-for="c in courses" :key="c.courseId" :value="c.courseId">{{ c.courseName }}</option>
        </select>
      </label>
      <label v-else class="dlgField">
        <span class="dlgField__label">所属课程</span>
        <span class="dlgField__value">{{ activeCourse ? activeCourse.courseName : '—' }}</span>
      </label>
      <div class="dlgField dlgField--col">
        <span class="dlgField__label">笔记名称</span>
        <input v-model.trim="editorTitle" class="dlgInput" maxlength="40" placeholder="给笔记起个名字" aria-label="笔记名称" />
      </div>
      <el-input
        v-model="editorContent"
        type="textarea"
        :rows="8"
        maxlength="2000"
        show-word-limit
        placeholder="记录这一刻的想法…"
      />
      <el-checkbox v-model="editorPrivate" class="editorPrivate">设为私密（仅自己可见）</el-checkbox>
      <template #footer>
        <button class="dlgBtn" type="button" @click="editorVisible = false">取消</button>
        <button class="dlgBtn dlgBtn--primary" type="button" :disabled="saving" @click="saveNote">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAllNotes, addNotes, updateNotes, delNote } from '@/api/notes.js';
import { getMylessons } from '@/api/class.js';
import { useUserStore } from '@/store';
import moment from 'moment';
import SPagination from '@/components/shell/SPagination.vue';

const store = useUserStore();
const myId = computed(() => store.userInfo?.id);
const isLogin = computed(() => Boolean(store.userInfo && store.userInfo.name));

// ---- 笔记分类 = 我的课程 ----
// 后端笔记按课程维度存储（note.course_id 非空，列表接口必须带 courseId），
// 所以「分类」即用户的课程；「我采集的」= 在课程内采集别人的笔记（isGathered）。
const courses = ref([]); // [{courseId, courseName}]
const activeCourseId = ref(null);
const coursesLoading = ref(true);

const activeCourse = computed(
  () => courses.value.find((c) => String(c.courseId) === String(activeCourseId.value)) || null
);

async function loadCourses() {
  coursesLoading.value = true;
  try {
    const res = await getMylessons();
    if (res?.code == 200 && res.data) {
      const list = Array.isArray(res.data) ? res.data : res.data.list || [];
      courses.value = list.map((c) => ({ courseId: c.courseId, courseName: c.courseName || '未命名课程' }));
      if (courses.value.length && activeCourseId.value == null) {
        activeCourseId.value = courses.value[0].courseId;
      }
    }
  } catch (e) {
    courses.value = [];
  } finally {
    coursesLoading.value = false;
  }
}

function switchCourse(courseId) {
  if (String(activeCourseId.value) === String(courseId)) return;
  activeCourseId.value = courseId;
  filter.value = 'all';
  keyword.value = '';
  params.pageNo = 1;
  loadList();
}

const loading = ref(true);
const notes = ref([]);     // 当前页
const total = ref(0);
const params = reactive({ pageNo: 1, pageSize: 12 });
const keyword = ref('');
const filter = ref('all'); // 全部 / 我创建的 / 我采集的（课程内客户端筛选）
const filters = [
  { label: '全部', value: 'all' },
  { label: '我创建的', value: 'mine' },
  { label: '我采集的', value: 'gathered' },
];

const isMine = (item) => item.authorId != null && myId.value != null && String(item.authorId) === String(myId.value);

const filteredList = computed(() => {
  let list = notes.value;
  if (filter.value === 'mine') list = list.filter((n) => isMine(n) && !n.isGathered);
  else if (filter.value === 'gathered') list = list.filter((n) => n.isGathered);
  const kw = keyword.value.toLowerCase();
  if (kw) list = list.filter((n) => (n.content || '').toLowerCase().includes(kw));
  return list;
});

// 接口无独立标题字段 → 标题取正文首行（真实内容派生）
const titleOf = (n) => {
  const first = String(n.content || '').split('\n')[0].trim();
  return first.slice(0, 40) || '未命名笔记';
};
const excerptOf = (n) => {
  const lines = String(n.content || '').split('\n');
  const rest = lines.slice(1).join(' ').trim();
  return rest || lines[0] || '';
};
const metaOf = (n) => {
  const len = String(n.content || '').length;
  const day = n.createTime ? moment(n.createTime).format('MM-DD') : '--';
  return `${len} 字 · ${day}${n.isPrivate ? ' · 私密' : ''}`;
};

async function loadList() {
  if (activeCourseId.value == null) {
    notes.value = [];
    total.value = 0;
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const res = await getAllNotes({ courseId: activeCourseId.value, pageNo: params.pageNo, pageSize: params.pageSize });
    if (res?.code == 200 && res.data) {
      notes.value = res.data.list || [];
      total.value = Number(res.data.total) || 0;
    } else {
      notes.value = [];
      total.value = 0;
    }
  } catch (e) {
    notes.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

// ---------- 新建 / 编辑 / 删除 ----------
const editorVisible = ref(false);
const editing = ref(null);
const editorContent = ref('');
const editorPrivate = ref(false);
const editorCourseId = ref(null); // 新建时必选课程
const editorTitle = ref('');     // 笔记名称（存为正文首行，卡片标题即取首行）
const saving = ref(false);

function openEditor(item) {
  editing.value = item || null;
  const raw = String(item?.content || '');
  const nl = raw.indexOf('\n');
  editorTitle.value = nl > -1 ? raw.slice(0, nl).trim() : '';
  editorContent.value = nl > -1 ? raw.slice(nl + 1) : raw;
  editorPrivate.value = Boolean(item?.isPrivate);
  editorCourseId.value = activeCourseId.value;
  editorVisible.value = true;
}

async function saveNote() {
  if (!editorContent.value.trim() || saving.value) return;
  if (!editing.value && editorCourseId.value == null) {
    ElMessage.warning('请先选择所属课程');
    return;
  }
  if (!editorTitle.value) {
    ElMessage.warning('请填写笔记名称');
    return;
  }
  saving.value = true;
  const fullContent = (editorTitle.value + '\n' + editorContent.value.trim()).trim();
  try {
    if (editing.value) {
      const res = await updateNotes({ id: editing.value.id, content: fullContent, isPrivate: editorPrivate.value, courseId: activeCourseId.value });
      if (res?.code == 200) {
        ElMessage.success('笔记已更新');
      } else {
        ElMessage.error(res?.msg || '更新失败，请稍后再试');
        return;
      }
    } else {
      // 后端 note.course_id 非空 —— 不带 courseId 会 500（此前验收发现的问题即此原因）
      const res = await addNotes({
        content: fullContent,
        courseId: editorCourseId.value,
        noteMoment: 0,
        isPrivate: editorPrivate.value,
      });
      if (res?.code == 200) {
        ElMessage.success('笔记已创建');
      } else {
        ElMessage.error(res?.msg || '创建失败，请稍后再试');
        return;
      }
    }
    editorVisible.value = false;
    loadList();
  } catch (e) {
    ElMessage.error('保存失败，请稍后再试');
  } finally {
    saving.value = false;
  }
}

async function removeNote(item) {
  try {
    await ElMessageBox.confirm('确定删除这条笔记吗？删除后不可恢复。', '删除笔记', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch (e) {
    return; // 用户取消
  }
  try {
    const res = await delNote(item.id);
    if (res?.code == 200) {
      ElMessage.success('笔记已删除');
      loadList();
    } else {
      ElMessage.error(res?.msg || '删除失败，请稍后再试');
    }
  } catch (e) {
    ElMessage.error('删除失败，请稍后再试');
  }
}

onMounted(async () => {
  await loadCourses();
  loadList();
});
</script>

<style lang="scss" scoped>
// =============================================================================
// 版面：Content 1096 = 笔记分类 248 + 间距 32 + 笔记列表 816
// =============================================================================
.nt {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.cardTitle {
  margin: 0;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  color: #1d1d1f;
}
.cardLink {
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 12px;
  line-height: 17px;
  font-weight: 500;
  color: #0066cc;
  cursor: pointer;

  &:hover { text-decoration: underline; }
}
.empty {
  flex: 1 0 100%;
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  line-height: 20px;
  color: #86868b;
}

// =============================================================================
// 左：笔记分类（248，卡圆角 18 / 内边距 16 / 纵向间距 8）
// =============================================================================
.catCard {
  flex: 0 0 248px;
  width: 248px;
  padding: 16px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 4px;
  }
}

.folder {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &__icon { flex: 0 0 18px; width: 18px; height: 18px; color: #86868b; }
  &__label {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 14px;
    line-height: 18px;
    color: #333;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__count {
    flex: 0 0 auto;
    font-size: 12px;
    line-height: 16px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }

  &:hover { background: #f6f7f9; }
  &.is-on {
    background: #e8f1fc;
    .folder__icon { color: #0066cc; }
    .folder__label { color: #0066cc; font-weight: 500; }
    .folder__count { color: #0066cc; }
  }
}

// =============================================================================
// 右：笔记列表（816，卡圆角 18 / 内边距 24 / 纵向间距 20）
// =============================================================================
.noteList {
  flex: 1 1 auto;
  min-width: 0;
  padding: 24px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }
  &__actions { display: flex; align-items: center; gap: 10px; }
  &__foot { display: flex; justify-content: center; }
}

// 搜索框 220×36 圆角 18
.search {
  flex: 0 0 220px;
  width: 220px;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border-radius: 18px;
  background: #f5f5f7;
  transition: box-shadow 0.16s ease, background-color 0.16s ease;

  &:focus-within { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
  svg { flex: 0 0 14px; width: 14px; height: 14px; color: #86868b; }
  input {
    flex: 1 1 auto;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    font-size: 13px;
    line-height: 16px;
    color: #1d1d1f;
    font-family: inherit;

    &::placeholder { color: #86868b; }
  }
}

// 新建按钮 88×36 圆角 18
.newBtn {
  flex: 0 0 auto;
  min-width: 88px;
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 18px;
  background: #0066cc;
  color: #fff;
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #0071e3; }
}

// =============================================================================
// 笔记栅格：间距 16，卡片 245×148 圆角 14 内边距 16 底色 #FAFAFC
// =============================================================================
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  min-height: 120px;
}

.note {
  position: relative;
  flex: 0 0 calc((100% - 32px) / 3);
  min-height: 148px;
  padding: 16px;
  border-radius: 14px;
  background: #fafafc;
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #f3f4f8; }
  &:focus-visible { outline: 2px solid #0071e3; outline-offset: 2px; }

  &__tag {
    font-size: 11px;
    line-height: 15px;
    font-weight: 500;
    color: #0066cc;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__title {
    margin: 0;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: #1d1d1f;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__excerpt {
    margin: 0;
    min-height: 36px;
    font-size: 12px;
    line-height: 18px;
    color: #86868b;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }
  &__meta {
    margin-top: auto;
    font-size: 11px;
    line-height: 15px;
    color: #86868b;
    font-variant-numeric: tabular-nums;
  }

  // 悬停才出现自己的笔记操作，保持设计稿静态观感
  &__tools {
    position: absolute;
    top: 10px;
    right: 10px;
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }
  &:hover &__tools,
  &:focus-within &__tools { opacity: 1; }
}

.tool {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: #fff;
  color: #6e6e73;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
  transition: color 0.15s ease, background-color 0.15s ease;

  svg { width: 14px; height: 14px; }
  &:hover { color: #0066cc; }
  &--danger:hover { color: #ff3b30; }
}

// 弹窗按钮
.editorPrivate { margin-top: 12px; }
.dlgBtn {
  height: 34px;
  padding: 0 20px;
  margin-left: 8px;
  border: 0;
  border-radius: 17px;
  background: #f5f5f7;
  color: #1d1d1f;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;

  &:hover { background: #ecedf1; }
  &--primary {
    background: #0066cc;
    color: #fff;
    font-weight: 500;

    &:hover { background: #0071e3; }
    &:disabled { opacity: 0.55; cursor: default; }
  }
}

// =============================================================================
// 窄屏
// =============================================================================
@media (max-width: 1180px) {
  .nt { flex-direction: column; }
  .catCard { flex: 0 0 auto; width: 100%; }
}

@media (max-width: 980px) {
  .note { flex: 0 0 calc((100% - 16px) / 2); }
}

@media (max-width: 720px) {
  .note { flex: 0 0 100%; }
  .noteList__head { flex-direction: column; align-items: flex-start; }
  .search { flex: 0 0 36px; width: 100%; }
}

// ---- 来源筛选 chips ----
.noteList__chips {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  margin-bottom: 16px;
  border-radius: 10px;
  background: #f5f5f7;
}
.chip {
  height: 28px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #6e6e73;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &.is-on {
    background: #fff;
    color: #0066cc;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }
}

// ---- 分类空态 ----
.catCard__empty {
  margin: 8px 0 0;
  padding: 18px 4px;
  font-size: 12px;
  line-height: 20px;
  color: #86868b;
  text-align: center;
}
.catCard__body { min-height: 60px; }

// ---- 弹窗课程字段 ----
.dlgField {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  &__label {
    flex: 0 0 64px;
    font-size: 13px;
    color: #1d1d1f;
  }
  &__value {
    font-size: 13px;
    color: #6e6e73;
  }
}
.dlgInput {
  flex: 1 1 auto;
  min-width: 0;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: #f5f5f7;
  font-size: 14px;
  font-family: inherit;
  color: #1d1d1f;
  outline: 0;

  &:focus { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
}
.dlgField--col {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;

  .dlgField__label { flex: 0 0 auto; }
}
.dlgSelect {
  flex: 1 1 auto;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: 10px;
  background: #f5f5f7;
  font-size: 13px;
  font-family: inherit;
  color: #1d1d1f;
  outline: 0;
  cursor: pointer;

  &:focus { background: #fff; box-shadow: inset 0 0 0 1.5px #0066cc; }
}
</style>
