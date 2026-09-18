<!-- 私人助手 · 我的知识库抽屉
     由原 /main/ai/knowledge 独立页面并入对话页，避免「提问功能两处重复」。
     职责：资料的上传 / 查看 / 删除；不含问答（问答统一走对话页输入框）。
     ⚠️ 刻意**不提供「就地编辑」**：后端 `PUT /ct/file/update` 从未实现（调用必 405），
        所以这里不给编辑入口 —— 要改内容请「删除后重新上传」。详见 p36/p37。
     原页面里「返回原文片段 + 匹配得分」的渲染分支已移除：后端 /ct/file/chat 返回的是
     {content:"<AI 回答>"} 对象而非片段数组，该分支 Array.isArray(res.data) 恒为 false，
     属永不触发的死代码；需要看原文用「查看」即可拿到整份文档。 -->
<template>
  <aside class="kbPanel" :class="{ 'isOpen': visible }">
    <!-- 抽屉头部 -->
    <header class="kbHead">
      <div class="kbHeadText">
        <h3 class="kbTitle">我的知识库</h3>
        <p class="kbSub">{{ totalFiles }} 份资料 · Markdown / TXT</p>
      </div>
      <button class="kbClose" title="收起知识库" @click="$emit('close')">✕</button>
    </header>

    <!-- 上传入口 -->
    <div class="kbToolbar">
      <button class="kbUpload" @click="openModal()">
        <span class="kbUploadIcon">＋</span> 上传资料
      </button>
    </div>

    <!-- 资料列表 -->
    <div class="kbList" ref="listRef" @scroll="handleScroll">
      <div v-if="!fileList.length" class="kbEmpty">
        <div class="kbEmptyIcon">📄</div>
        <p class="kbEmptyTitle">还没有上传资料</p>
        <p class="kbEmptyTip">
          上传 Markdown / TXT 笔记后，就能在右侧对话里让它基于你的资料回答问题，<br />
          也可以做笔记整理、内容检查、自动出题和学习计划。
        </p>
      </div>

      <div v-for="file in fileList" :key="file.id" class="kbItem" :class="{ 'isActive': selectedFileId === file.id }"
           @click="$emit('select-file', file.id)">
        <div class="kbItemMain">
          <span class="kbFileIcon">MD</span>
          <div class="kbItemText">
            <p class="kbItemName" :title="file.name || file.fileName">{{ file.name || file.fileName || '未命名文件' }}</p>
            <p class="kbItemMeta">切割等级 {{ file.level ?? '-' }}</p>
          </div>
        </div>
        <div class="kbItemActions">
          <button class="kbAct" title="查看原文" @click.stop="viewFileContent(file.id)">查看</button>
          <button class="kbAct isDanger" title="删除资料" @click.stop="confirmDelete(file.id)">删除</button>
        </div>
      </div>

      <p v-if="fileList.length && fileList.length < totalFiles" class="kbMore">向下滚动加载更多…</p>
    </div>

    <!-- 新增资料 -->
    <el-dialog v-model="isAddModalVisible" title="上传资料" width="440px" @close="handleAddModalClose">
      <div class="kbForm">
        <div class="kbField">
          <label class="kbLabel">选择文件</label>
          <div class="kbFilePick">
            <el-upload ref="addUploadRef" action="#" :auto-upload="false" :show-file-list="false"
                       :on-change="handleAddFileChange">
              <el-button>选取文件</el-button>
            </el-upload>
            <span class="kbFileName">{{ addFormData.fileName || '未选择文件' }}</span>
          </div>
          <p class="kbHelp">支持 2MB 以内的 Markdown / TXT 文件</p>
        </div>

        <div class="kbField">
          <label class="kbLabel">切割等级</label>
          <el-input-number v-model="addFormData.level" :min="1" :max="5" />
          <p class="kbHelp">按几级标题切分文档，一般用 2 或 3 即可</p>
        </div>

        <div class="kbNote">
          建议用 <code>#</code> / <code>##</code> / <code>###</code> 三级标题分层，代码块用 <code>```</code> 围起。
          标题规范与否会直接影响检索效果。
        </div>
      </div>
      <template #footer>
        <div class="kbDialogFoot">
          <el-button @click="isAddModalVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" :disabled="uploading" @click="submitAddForm">{{ uploading ? '上传中…' : '确定上传' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看原文 -->
    <el-dialog v-model="viewFileVisible" title="文件内容" width="760px" @close="handleViewFileClose">
      <div class="kbViewHead">
        <span class="kbHelp">如需修改内容：请先删除该文件，再上传修改后的版本。</span>
      </div>
      <div class="kbViewBody">
        <VueMarkdown v-if="viewFileContentData" :source="viewFileContentData" />
        <p v-else class="kbHelp">文件内容为空。</p>
      </div>
      <template #footer>
        <div class="kbDialogFoot">
          <el-button @click="viewFileVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 删除确认 -->
    <el-dialog v-model="deleteConfirmVisible" title="删除资料" width="380px" @close="handleDeleteCancel">
      <p class="kbConfirmText">删除后该资料将从知识库移除，基于它的问答与学习工具将不再命中。确定删除吗？</p>
      <template #footer>
        <div class="kbDialogFoot">
          <el-button @click="deleteConfirmVisible = false">取消</el-button>
          <el-button type="danger" :loading="deleteLoading" :disabled="deleteLoading" @click="handleDeleteConfirm">{{ deleteLoading ? '删除中…' : '确定删除' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </aside>
</template>

<script setup>
import { ref, watch } from 'vue';
import { ElMessage, ElDialog, ElButton, ElInput, ElInputNumber, ElUpload } from 'element-plus';
import { uploadMarkdown, deleteMarkdown, queryMarkdownPage, getMarkdown } from '@/api/ai.js';
import VueMarkdown from 'vue3-markdown-it';

const props = defineProps({
  // 抽屉显隐
  visible: { type: Boolean, default: false },
  // 当前选中的资料 id（用于「资料范围」联动高亮）
  selectedFileId: { type: [Number, String], default: null }
});

const emits = defineEmits(['close', 'select-file', 'files-changed']);

// ==================== 文件列表 ====================
const fileList = ref([]);
const totalFiles = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const isLoadingMore = ref(false);
const listRef = ref(null);

const fetchFileList = async (page = 1) => {
  try {
    const response = await queryMarkdownPage({ pageNo: page, pageSize: pageSize.value });
    const list = response?.data?.list || [];
    fileList.value = page === 1 ? list : [...fileList.value, ...list];
    totalFiles.value = response?.data?.total || list.length;
  } catch (error) {
    console.error('获取文件列表失败:', error);
    ElMessage.error('获取文件列表失败: ' + (error.message || '未知错误'));
  }
};

// 首次打开抽屉时拉取列表
watch(() => props.visible, (v) => {
  if (v) {
    currentPage.value = 1;
    fetchFileList(1);
  }
}, { immediate: true });

const loadMoreFiles = async () => {
  if (isLoadingMore.value || fileList.value.length >= totalFiles.value) return;
  isLoadingMore.value = true;
  currentPage.value += 1;
  await fetchFileList(currentPage.value);
  isLoadingMore.value = false;
};

const handleScroll = () => {
  const el = listRef.value;
  if (!el) return;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 12) loadMoreFiles();
};

// 列表变化后通知父级刷新「资料范围」下拉
const refresh = async () => {
  currentPage.value = 1;
  await fetchFileList(1);
  emits('files-changed');
};

// ==================== 上传 ====================
const isAddModalVisible = ref(false);
const addFormData = ref({ file: null, fileName: '', level: 2 });
const addUploadRef = ref(null);

const openModal = () => {
  isAddModalVisible.value = true;
};

const handleAddModalClose = () => {
  addFormData.value = { file: null, fileName: '', level: 2 };
  addUploadRef.value?.clearFiles();
};

const handleAddFileChange = (file) => {
  addFormData.value.file = file.raw;
  addFormData.value.fileName = file.name;
};

const uploading = ref(false);
const submitAddForm = async () => {
  if (!addFormData.value.file) {
    ElMessage.warning('请先选择要上传的文件');
    return;
  }
  if (uploading.value) return;
  uploading.value = true;
  try {
    // 上传后端要做文本切分与向量化，大文件可能需要十几秒，期间按钮保持 loading
    const res = await uploadMarkdown(addFormData.value.file, addFormData.value.level);
    if (res.code === 200) {
      ElMessage.success('上传成功');
      isAddModalVisible.value = false;
      await refresh();
      emits('files-changed');
    } else {
      // 同名冲突等业务错误：保留弹窗，便于改名后重试
      ElMessage.error(`上传失败：${res.msg || '未知错误'}`);
    }
  } catch (error) {
    ElMessage.error(`上传失败：${error.message || '网络错误'}`);
  } finally {
    uploading.value = false;
  }
};

// ==================== 删除 ====================
const deleteConfirmVisible = ref(false);
const deletingFileId = ref(null);

const confirmDelete = (id) => {
  deletingFileId.value = id;
  deleteConfirmVisible.value = true;
};

const handleDeleteCancel = () => {
  deletingFileId.value = null;
};

const deleteLoading = ref(false);
const handleDeleteConfirm = async () => {
  if (deleteLoading.value) return;
  deleteLoading.value = true;
  try {
    // 删除要做向量清理，可能短暂卡顿，按钮保持 loading
    const res = await deleteMarkdown(deletingFileId.value);
    if (res.code === 200) {
      ElMessage.success('删除成功');
      deleteConfirmVisible.value = false;
      deletingFileId.value = null;
      await refresh();
    } else {
      ElMessage.error(`删除失败：${res.msg || '未知错误'}`);
    }
  } catch (error) {
    ElMessage.error(`删除失败：${error.message || '网络错误'}`);
  } finally {
    deleteLoading.value = false;
  }
};

// ==================== 查看原文 ====================
const viewFileVisible = ref(false);
const viewFileContentData = ref('');
const currentViewFileId = ref(null);

const viewFileContent = async (id) => {
  currentViewFileId.value = id;
  try {
    const fileData = await getMarkdown(id);
    viewFileContentData.value = fileData.data;
    viewFileVisible.value = true;
  } catch (error) {
    ElMessage.error('获取文件内容失败: ' + (error.message || '未知错误'));
  }
};

const handleViewFileClose = () => {
  viewFileContentData.value = '';
  currentViewFileId.value = null;
};

defineExpose({ refresh });
</script>

<style lang="scss" scoped>
// =============================================================================
// 我的知识库抽屉（学员端 AI 助手内）
// 风格对齐学员端设计语言：白卡 + 发丝线 + #0066CC 主色，圆角 12 / 18
// =============================================================================
.kbPanel {
  display: flex;
  flex-direction: column;
  flex: 0 0 360px;
  width: 360px;
  min-height: 0;
  background: #fff;
  border-left: 1px solid #ececf0;
}

// ---------- 头部 ----------
.kbHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 14px;
  flex: 0 0 auto;
}
.kbHeadText { min-width: 0; }
.kbTitle {
  margin: 0;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  color: #1d1d1f;
}
.kbSub {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 17px;
  color: #86868b;
}
.kbClose {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 14px;
  background: #f5f5f7;
  color: #6e6e73;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover { background: #e9eaee; color: #1d1d1f; }
}

// ---------- 上传入口 ----------
.kbToolbar { padding: 0 20px 14px; flex: 0 0 auto; }
.kbUpload {
  width: 100%;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 19px;
  background: #0066cc;
  color: #fff;
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #0071e3; }

  &Icon { font-size: 15px; line-height: 1; }
}

// ---------- 资料列表 ----------
.kbList {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kbEmpty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 32px 16px;

  &Icon { font-size: 26px; line-height: 1; margin-bottom: 12px; opacity: 0.55; }
  &Title { margin: 0 0 8px; font-size: 13px; line-height: 18px; font-weight: 500; color: #1d1d1f; }
  &Tip { margin: 0; font-size: 12px; line-height: 20px; color: #86868b; }
}

.kbItem {
  padding: 12px;
  border-radius: 12px;
  background: #fafafc;
  cursor: pointer;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;

  &:hover { background: #f3f4f8; }
  &.isActive {
    background: #e8f1fc;
    box-shadow: inset 0 0 0 1px rgba(0, 102, 204, 0.35);
  }

  &Main { display: flex; align-items: center; gap: 10px; }
  &Text { flex: 1 1 auto; min-width: 0; }
  &Name {
    margin: 0;
    font-size: 13px;
    line-height: 18px;
    font-weight: 500;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &Meta {
    margin: 3px 0 0;
    font-size: 11px;
    line-height: 15px;
    color: #86868b;
  }
  &Actions {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 10px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }
  &:hover &Actions,
  &.isActive &Actions { opacity: 1; }
}

.kbFileIcon {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #e8f1fc;
  color: #0066cc;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.kbAct {
  height: 26px;
  padding: 0 12px;
  border: 0;
  border-radius: 13px;
  background: #fff;
  color: #333;
  font-size: 12px;
  line-height: 17px;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.08);
  transition: color 0.15s ease, box-shadow 0.15s ease;

  &:hover { color: #0066cc; box-shadow: inset 0 0 0 1px rgba(0, 102, 204, 0.4); }
  &.isDanger:hover { color: #ff3b30; box-shadow: inset 0 0 0 1px rgba(255, 59, 48, 0.4); }
}

.kbMore {
  margin: 10px 0 4px;
  text-align: center;
  font-size: 12px;
  line-height: 17px;
  color: #86868b;
}

// ---------- 弹窗表单 ----------
.kbForm { display: flex; flex-direction: column; gap: 18px; }
.kbField { display: flex; flex-direction: column; gap: 8px; }
.kbLabel {
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  color: #1d1d1f;
}
.kbFilePick { display: flex; align-items: center; gap: 12px; }
.kbFileName {
  font-size: 13px;
  line-height: 18px;
  color: #86868b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kbHelp { margin: 0; font-size: 12px; line-height: 18px; color: #86868b; }
.kbNote {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f7fb;
  font-size: 12px;
  line-height: 20px;
  color: #6e6e73;

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: #e8f1fc;
    color: #0066cc;
    font-size: 11px;
  }
}
.kbDialogFoot { display: flex; justify-content: flex-end; gap: 10px; }

// 原先这里放的是「编辑此文件」按钮（右对齐）；现在换成说明文字，用 margin-right:auto 推到左侧与正文对齐
.kbViewHead { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.kbViewHead .kbHelp { margin-right: auto; }
.kbViewBody {
  max-height: 56vh;
  overflow-y: auto;
  padding: 16px 18px;
  border-radius: 12px;
  background: #fafafc;
  font-size: 14px;
  line-height: 24px;
  color: #1d1d1f;
  word-break: break-word;
}

.kbConfirmText { margin: 0; font-size: 14px; line-height: 22px; color: #1d1d1f; }

@media (max-width: 1180px) {
  .kbPanel {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    width: 320px;
    flex: 0 0 320px;
    box-shadow: -16px 0 40px -20px rgba(16, 24, 40, 0.25);
  }
}
</style>
