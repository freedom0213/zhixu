<template>
  <aside class="sb">
    <!-- 品牌 -->
    <div class="sb__logo">
      <span class="sb__mark" v-html="logoMark"></span>
      <span class="sb__name">{{ brandName }}</span>
    </div>

    <!-- 主导航 -->
    <nav class="sb__nav" :aria-label="navLabel">
      <router-link
        v-for="item in list"
        :key="item.key"
        :to="item.path"
        class="sb__item"
        :class="{ 'is-active': isActive(item) }"
        :aria-current="isActive(item) ? 'page' : undefined"
      >
        <span class="sb__ic" v-html="item.icon"></span>
        <span class="sb__label">{{ item.label }}</span>
        <span v-if="badges[item.key]" class="sb__badge">{{ badges[item.key] > 99 ? '99+' : badges[item.key] }}</span>
      </router-link>
    </nav>

    <!-- 用户块 -->
    <div
      class="sb__user"
      role="button"
      tabindex="0"
      :aria-label="`进入个人中心：${displayName}`"
      @click="goProfile"
      @keyup.enter="goProfile"
      @keyup.space.prevent="goProfile"
    >
      <img class="sb__avatar" :src="avatar" alt="" @error="onAvatarError" />
      <div class="sb__uinfo">
        <span class="sb__uname">{{ displayName }}</span>
        <span class="sb__ustatus">{{ statusText }}</span>
      </div>
      <span class="sb__chev" v-html="chevronIcon"></span>
    </div>

    <!-- 退出登录（P22-fix）：两端共用侧栏，已登录才显示。以前两端都没有退出入口 -->
    <button v-if="info && info.name" class="sb__signout" type="button" @click="signOut">
      <span class="sb__signoutIc" v-html="signoutIcon"></span>
      <span class="sb__label">退出登录</span>
    </button>
  </aside>
</template>

<script setup>
/**
 * 端侧左侧导航（学员端 / 教师端共用）
 * - 选中态由路由驱动（浅底药丸 + 主色文字 + 主色图标）
 * - 「端」的差异全部通过 props 注入：导航数据 / 导航无障碍名 / 品牌名 /
 *   个人中心路径 / 登录回跳兜底路径。默认值即学员端原行为，故学员端无需改动。
 * - 用户信息优先读 pinia / sessionStorage，读不到时降级为「未登录」，不阻断渲染
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore, isLogin } from '@/store';
import { getUserInfo } from '@/api/user';
import defaultAvatar from '@/assets/icon.jpeg';
import studentNav from '@/config/studentNav';
import { rememberStudentOrigin } from '@/config/loginRedirect';

const route = useRoute();
const router = useRouter();
const store = useUserStore();

const logoMark = `<svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <rect width="32" height="32" rx="9" fill="var(--sa)"/>
  <path d="M16 10.4c-1.9-1.4-4.4-2.1-6.9-2.1-.9 0-1.7.1-2.3.3v12.2c.6-.2 1.4-.3 2.3-.3 2.5 0 5 .7 6.9 2.1 1.9-1.4 4.4-2.1 6.9-2.1.9 0 1.7.1 2.3.3V8.6c-.6-.2-1.4-.3-2.3-.3-2.5 0-5 .7-6.9 2.1Z" fill="#FFFFFF"/>
  <path d="M16 10.4v12.2" stroke="#0066CC" stroke-width="1.3"/>
</svg>`;

const chevronIcon = `<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <path d="M6.2 3.8 10.4 8l-4.2 4.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`;

const signoutIcon = `<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <path d="M6.5 2.5H4.2A1.7 1.7 0 0 0 2.5 4.2v7.6a1.7 1.7 0 0 0 1.7 1.7h2.3M10 5.2 12.8 8 10 10.8M12.6 8H6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
</svg>`;

// 未读角标：暂由父层传入，未提供时不渲染（不伪造数据）
//
// 两端差异全部走 props（默认值 = 学员端原行为，故学员端一行不改也能跑）：
//   items       导航数据（不传则用学员端导航）
//   navLabel    导航区无障碍名称
//   brandName   品牌名（教师端显示「教师工作台」）
//   profilePath 本端个人中心路径
//   originPath  未登录时的登录回跳兜底路径
const props = defineProps({
  badges: { type: Object, default: () => ({}) },
  items: { type: Array, default: null },
  navLabel: { type: String, default: '学员端主导航' },
  brandName: { type: String, default: '知序学堂' },
  profilePath: { type: String, default: '/student/profile' },
  originPath: { type: String, default: '/student/dashboard' },
});

const list = computed(() => (props.items && props.items.length ? props.items : studentNav));

const info = ref(store.userInfo || null);

const displayName = computed(() => info.value?.name || '未登录');
const statusText = computed(() => {
  // 打卡数据依赖后端接口，接口未就绪时降级为引导文案（不编造天数）
  if (info.value?.signContinuousDays) return `已连续打卡 ${info.value.signContinuousDays} 天`;
  return info.value ? '进入个人中心' : '点击登录';
});
const avatar = computed(() => {
  const id = info.value?.id;
  return (id && localStorage.getItem(`tianji:avatar:${id}`)) || info.value?.icon || defaultAvatar;
});

const onAvatarError = (e) => {
  e.target.src = defaultAvatar;
};

// 两端各自的导航一律使用本端前缀（/student/*、/teacher/*），不需要别名高亮映射
const isActive = (item) => route.path === item.path || route.path.startsWith(`${item.path}/`);

// 退出登录：清登录态（token + userInfo，store.logout 会一并清 sessionStorage）→ 去登录页
const signOut = () => {
  store.logout();
  info.value = null;
  router.push('/login');
};

// 未登录：去登录页（记住来源，登录成功后回本端）；已登录：进本端个人中心
const goProfile = () => {
  if (!info.value || !info.value.name) {
    rememberStudentOrigin(props.originPath);
    router.push('/login');
    return;
  }
  router.push(props.profilePath);
};

onMounted(async () => {
  if (info.value) return;
  try {
    if (await isLogin()) {
      const res = await getUserInfo();
      if (res?.code === 200 && res.data) {
        info.value = res.data;
        store.setUserInfo(res.data);
      }
    }
  } catch (e) {
    // 静默失败：侧栏用户块降级展示，不影响其余导航
  }
});
</script>
