<!-- 学员端外壳布局：左侧栏 + 顶部工具栏 + 内容区 -->
<template>
  <div class="studentShell">
    <AppSidebar />
    <div class="main">
      <AppTopBar :title="pageTitle" />
      <div class="body">
        <router-view />
      </div>
    </div>
    <!-- 全局助手悬浮球（assistantType=GLOBAL）—— 学员端所有页面右下角常驻 -->
    <GlobalAssistant />
  </div>
</template>

<script setup>
/**
 * 学员端 Shell
 * - 路由驱动：meta.title 决定顶栏标题，顶栏「通知」等入口复用现有个人中心页面
 * - 外壳自带平底色（--s-canvas），滚动时侧栏/顶栏 sticky 吸边
 * - 隐藏官网页脚：沿用 AI 页方案 —— 进入时给 .layoutsWrapper 加运行时类名，
 *   卸载移除，并用非 scoped 规则 + 类名限定防止样式残留误伤其它页面
 * - 「壳内自滚动」模式（.noSiteFooter）：师生对话等类 App 双栏页需要
 *   固定视口 + 内部滚动；其余页面整页自然滚动，不做高度链（本项目踩过两次坑）
 */
import { computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import AppSidebar from '@/components/shell/AppSidebar.vue';
import AppTopBar from '@/components/shell/AppTopBar.vue';
import GlobalAssistant from './components/GlobalAssistant.vue';

const route = useRoute();
const pageTitle = computed(() => route.meta?.title || '学习首页');

const WRAP_CLASS = 'isStudentShell';
const APP_MODE_CLASS = 'isStudentApp';

const setWrapClass = (on) => {
  const wrap = document.querySelector('.layoutsWrapper');
  if (!wrap) return;
  wrap.classList.toggle(WRAP_CLASS, on);
  wrap.classList.toggle(APP_MODE_CLASS, on && route.meta?.fullHeight === true);
};

onMounted(() => setWrapClass(true));
onUnmounted(() => setWrapClass(false));
</script>

<style lang="scss">
@import '@/style/shell.scss';

/* 学员端页面隐藏官网页脚（Footer.vue 根节点专属类） */
.layoutsWrapper.isStudentShell .siteFooter {
  display: none;
}

/* 类 App 双栏页：外壳占满视口，滚动交给页面内部 */
.layoutsWrapper.isStudentShell.isStudentApp .studentShell {
  height: 100vh;
  overflow: hidden;

  .body {
    overflow-y: auto;
    min-height: 0;
  }
}
</style>
