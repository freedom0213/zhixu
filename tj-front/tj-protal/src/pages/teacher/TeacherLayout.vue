<!--
 * 教师端外壳布局：左侧栏 + 顶部工具栏 + 内容区
 * -----------------------------------------------------------------------------
 * 与学员端 ShellLayout 同构，差异只有三处（都是数据/组件层，不是样式层）：
 *   1) 导航数据换成 teacherNav（8 项，含一级「题库」）
 *   2) 顶栏换成 TeacherTopBar（搜索课程/题目/学生 + 通知 + 消息）
 *   3) 品牌名显示为「教师工作台」
 *
 * 刻意不挂 GlobalAssistant：那是学员端的全局助手（面向学生答疑），教师端不引入。
 * 隐藏官网页脚沿用学员端方案 —— 给 .layoutsWrapper 加运行时类名 + 非 scoped 规则，
 * 用类名限定防止样式残留误伤其它页面。
-->
<template>
  <div class="teacherShell">
    <AppSidebar
      :items="navItems"
      nav-label="讲师工作台主导航"
      brand-name="讲师工作台"
      profile-path="/teacher/profile"
      origin-path="/teacher/dashboard"
    />
    <div class="main">
      <TeacherTopBar :title="pageTitle" @search="onSearch" @notify="goNotify" @message="goMessage" />
      <div class="body">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppSidebar from '@/components/shell/AppSidebar.vue';
import TeacherTopBar from '@/components/shell/TeacherTopBar.vue';
import navItems from '@/config/teacherNav';

const route = useRoute();
const router = useRouter();
const pageTitle = computed(() => route.meta?.title || '工作概览');

const WRAP_CLASS = 'isTeacherShell';

const setWrapClass = (on) => {
  const wrap = document.querySelector('.layoutsWrapper');
  if (!wrap) return;
  wrap.classList.toggle(WRAP_CLASS, on);
};

// 顶栏搜索落在题库（教师端唯一有全局检索面的模块），关键词透传给列表页
const onSearch = (keyword) => {
  router.push({ path: '/teacher/questions', query: keyword ? { keyword } : {} });
};
const goNotify = () => router.push('/teacher/messages');
const goMessage = () => router.push('/teacher/messages');

onMounted(() => setWrapClass(true));
onUnmounted(() => setWrapClass(false));
</script>

<style lang="scss">
/* 基础原语（.sb / .tb / .body / .s-card…）读的是容器上的 CSS 变量，两端的差异只在变量与组件 */
@import '@/style/shell.scss';
@import '@/style/teacher.scss';

/* 教师端页面隐藏官网页脚（Footer.vue 根节点专属类） */
.layoutsWrapper.isTeacherShell .siteFooter {
  display: none;
}
</style>
