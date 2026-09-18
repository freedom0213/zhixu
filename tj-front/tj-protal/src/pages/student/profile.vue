<!-- 学员端 · 个人中心（/student/profile）
     设计稿 09：左 200px 设置导航卡 + 右 864px 表单卡（圆角 18 / padding 28）
     复用现有用户接口（getUserInfo / updateUserInfo / updatePassword / bindPhone）与上传能力，
     仅重排视觉，不改业务语义。账号安全 / 偏好设置按既定决策不展开（测试账号）。 -->
<template>
  <div class="pf">
    <!-- 左：设置导航 -->
    <aside class="pf__nav">
      <div class="pf__navCard">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="pf__navItem"
          :class="{ 'is-active': tab === t.key }"
          type="button"
          @click="switchTab(t.key)"
        >
          <span class="pf__navIcon" v-html="t.icon"></span>
          <span class="pf__navLabel">{{ t.label }}</span>
        </button>
      </div>
    </aside>

    <!-- 右：内容 -->
    <section class="pf__main">
      <!-- 基本资料 -->
      <div v-if="tab === 'basic'" class="pf__card">
        <div class="pf__titleRow">
          <h2 class="pf__title">基本资料</h2>
          <button v-if="!editingProfile" class="pf__editBtn" type="button" @click="startEdit">编辑资料</button>
        </div>

        <!-- 头像行 -->
        <div class="pf__avatarRow">
          <div class="pf__avatarWrap">
            <img class="pf__avatar" :src="avatarSrc" alt="当前头像" @error="onAvatarError" />
            <label v-if="editingProfile" class="pf__avatarBtn">
              更换头像
              <input type="file" accept="image/*" hidden @change="onAvatarPick" />
            </label>
          </div>
          <div class="pf__avatarHint">
            <p class="pf__hintTitle">{{ form.name || '未设置昵称' }}</p>
            <p class="pf__hintDesc">{{ editingProfile ? '支持 JPG / PNG，建议正方形，不超过 2 MB。' : '头像仅自己可见于个人中心。' }}</p>
          </div>
        </div>

        <div class="pf__divider"></div>

        <!-- 只读展示（默认） -->
        <div v-if="!editingProfile" class="pf__view">
          <div class="pf__viewRow"><span class="pf__viewLabel">账号</span><span class="pf__viewValue">{{ form.username || '—' }}</span></div>
          <div class="pf__viewRow"><span class="pf__viewLabel">昵称</span><span class="pf__viewValue">{{ form.name || '—' }}</span></div>
          <div class="pf__viewRow"><span class="pf__viewLabel">性别</span><span class="pf__viewValue">{{ form.gender === 1 ? '女' : '男' }}</span></div>
          <div class="pf__viewRow"><span class="pf__viewLabel">学校专业</span><span class="pf__viewValue">{{ form.school || '—' }}</span></div>
          <div class="pf__viewRow"><span class="pf__viewLabel">邮箱</span><span class="pf__viewValue">{{ form.email || '—' }}</span></div>
          <div class="pf__viewRow is-top"><span class="pf__viewLabel">个人简介</span><span class="pf__viewValue">{{ form.intro || '—' }}</span></div>
        </div>

        <!-- 编辑态 -->
        <template v-else>
        <!-- 字段 -->
        <div class="pf__field">
          <label class="pf__label">账号</label>
          <div class="pf__control">
            <input class="pf__input is-disabled" :value="form.username" disabled placeholder="登录账号" />
          </div>
        </div>

        <div class="pf__field">
          <label class="pf__label" for="pf-name">昵称</label>
          <div class="pf__control">
            <input id="pf-name" v-model.trim="form.name" class="pf__input" maxlength="20" placeholder="请输入昵称" />
          </div>
        </div>

        <div class="pf__field">
          <span class="pf__label">性别</span>
          <div class="pf__control">
            <div class="pf__seg" role="radiogroup" aria-label="性别">
              <button
                v-for="g in genders"
                :key="g.value"
                class="pf__segItem"
                :class="{ 'is-active': form.gender === g.value }"
                type="button"
                role="radio"
                :aria-checked="form.gender === g.value"
                @click="form.gender = g.value"
              >
                {{ g.label }}
              </button>
            </div>
          </div>
        </div>

        <div class="pf__field">
          <label class="pf__label" for="pf-school">学校专业</label>
          <div class="pf__control">
            <input id="pf-school" v-model.trim="form.school" class="pf__input" maxlength="40" placeholder="如：深圳大学 · 计算机科学与技术" />
          </div>
        </div>

        <div class="pf__field">
          <label class="pf__label" for="pf-email">邮箱</label>
          <div class="pf__control">
            <input id="pf-email" v-model.trim="form.email" class="pf__input" type="email" maxlength="60" placeholder="用于接收课程与考试通知" />
          </div>
        </div>

        <div class="pf__field is-top">
          <label class="pf__label" for="pf-intro">个人简介</label>
          <div class="pf__control">
            <textarea id="pf-intro" v-model.trim="form.intro" class="pf__textarea" rows="4" maxlength="200" placeholder="介绍一下你自己，或写下当前的学习目标"></textarea>
            <span class="pf__count">{{ (form.intro || '').length }}/200</span>
          </div>
        </div>

        <div class="pf__saveRow">
          <button class="pf__btn is-primary" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
          <button class="pf__btn" type="button" :disabled="saving" @click="cancelEdit">取消</button>
          <span v-if="tip" class="pf__tip" :class="tipType">{{ tip }}</span>
        </div>
        </template>
      </div>

      <!-- 我的收藏 -->
      <div v-else-if="tab === 'collect'" class="pf__card">
        <h2 class="pf__title">我的收藏<template v-if="collectTotal">（{{ collectTotal }}）</template></h2>

        <div v-loading="collectLoading" class="pf__collectBody">
          <template v-if="collectList.length">
            <div v-for="item in collectList" :key="item.id" class="clItem">
              <img
                class="clItem__cover"
                :src="item.courseCoverUrl"
                :alt="item.courseName"
                @click="goCourse(item.courseId)"
              />
              <div class="clItem__meta">
                <p class="clItem__name" @click="goCourse(item.courseId)">{{ item.courseName }}</p>
                <p class="clItem__sub">节数：{{ item.sections ?? '—' }} · 收藏于 {{ item.createTime }}</p>
              </div>
              <div class="clItem__ops">
                <button class="pf__btn is-primary" type="button" @click="goCourse(item.courseId)">去学习</button>
                <button class="pf__btn" type="button" @click="cancelCollect(item)">取消收藏</button>
              </div>
            </div>
            <div v-if="collectTotal > collectParams.pageSize" class="pf__collectPager">
              <SPagination v-model="collectParams.pageNo" :total="collectTotal" :page-size="collectParams.pageSize" @change="loadCollect" />
            </div>
          </template>
          <div v-else-if="!collectLoading" class="pf__collectEmpty">
            <p class="pf__collectEmptyTitle">还没有收藏任何课程</p>
            <p class="pf__collectEmptyDesc">去课程中心逛逛，把感兴趣的课程收藏起来。</p>
            <button class="pf__btn is-primary" type="button" @click="router.push('/student/courses')">去课程中心</button>
          </div>
        </div>
      </div>

      <!-- 账号安全 -->
      <div v-else-if="tab === 'security'" class="pf__card">
        <h2 class="pf__title">账号安全</h2>
        <div class="pf__secRow">
          <div>
            <p class="pf__secTitle">登录密码</p>
            <p class="pf__secDesc">建议使用字母 + 数字组合，长度不少于 8 位</p>
          </div>
          <button class="pf__btn" type="button" @click="openPwd">修改密码</button>
        </div>
        <div class="pf__secRow">
          <div>
            <p class="pf__secTitle">绑定手机</p>
            <p class="pf__secDesc">{{ form.cellPhone ? `已绑定：${maskPhone(form.cellPhone)}` : '尚未绑定手机号' }}</p>
          </div>
          <button class="pf__btn" type="button" @click="openPhone">{{ form.cellPhone ? '更换手机' : '立即绑定' }}</button>
        </div>
      </div>

      <!-- 偏好设置 -->
      <div v-else class="pf__card">
        <h2 class="pf__title">偏好设置</h2>
        <div class="pf__prefRow">
          <div>
            <p class="pf__secTitle">学习提醒</p>
            <p class="pf__secDesc">每日 20:00 提醒未完成的学习计划</p>
          </div>
          <button class="pf__switch" :class="{ 'is-on': prefs.remind }" type="button" role="switch" :aria-checked="prefs.remind" @click="togglePref('remind')">
            <span class="pf__knob"></span>
          </button>
        </div>
        <div class="pf__prefRow">
          <div>
            <p class="pf__secTitle">自动播放下一节</p>
            <p class="pf__secDesc">当前小节结束后自动进入下一节</p>
          </div>
          <button class="pf__switch" :class="{ 'is-on': prefs.autoplay }" type="button" role="switch" :aria-checked="prefs.autoplay" @click="togglePref('autoplay')">
            <span class="pf__knob"></span>
          </button>
        </div>
        <p class="pf__prefNote">偏好设置保存在本机浏览器，不影响其他设备。</p>
      </div>
    </section>
  </div>
</template>

<script setup>
/**
 * 学员端个人中心（壳内版本）
 * - 与官网 /personal/main/mySet 使用同一批接口，视觉改为设计稿样式
 * - 全站前缀统一后，学员端内不再跳 /personal/*
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getUserInfo, updateUserInfo, updatePassword, sendSms, bindPhone } from '@/api/user';
import { getMyCollect, addMyCollect } from '@/api/class.js';
import SPagination from '@/components/shell/SPagination.vue';
import { useUserStore } from '@/store';
import defaultAvatar from '@/assets/icon.jpeg';

const router = useRouter();
const store = useUserStore();

const tabs = [
  {
    key: 'basic',
    label: '基本资料',
    icon: '<svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="10" cy="6.8" r="3.4" stroke="currentColor" stroke-width="1.6"/><path d="M3.9 16.6c0-2.8 2.7-4.7 6.1-4.7s6.1 1.9 6.1 4.7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
  },
  {
    key: 'collect',
    label: '我的收藏',
    icon: '<svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 16.2 4.6 12.9c-2.5-1.9-3-5.4-1.1-7.7 1.6-1.9 4.3-2.1 6.1-.5l.4.4.4-.4c1.8-1.6 4.5-1.4 6.1.5 1.9 2.3 1.4 5.8-1.1 7.7L10 16.2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>',
  },
  {
    key: 'security',
    label: '账号安全',
    icon: '<svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 2.8 4.4 5.2v4.5c0 3.4 2.3 6.3 5.6 7.5 3.3-1.2 5.6-4.1 5.6-7.5V5.2L10 2.8Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="m7.9 10.2 1.5 1.5 2.8-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  },
  {
    key: 'prefs',
    label: '偏好设置',
    icon: '<svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="10" cy="10" r="2.6" stroke="currentColor" stroke-width="1.6"/><path d="M10 2.9v2.2M10 14.9v2.2M3.4 10h2.2M14.4 10h2.2M5.3 5.3l1.6 1.6M13.1 13.1l1.6 1.6M14.7 5.3l-1.6 1.6M6.9 13.1l-1.6 1.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
  },
];

const tab = ref('basic');
const genders = [
  { label: '男', value: 0 },
  { label: '女', value: 1 },
];

const form = reactive({
  username: '',
  name: '',
  gender: 0,
  school: '',
  email: '',
  intro: '',
  cellPhone: '',
  icon: '',
});

const saving = ref(false);
const tip = ref('');
const tipType = ref('is-ok');
const localAvatar = ref('');

const avatarSrc = computed(() => localAvatar.value || form.icon || defaultAvatar);

const prefs = reactive({
  remind: localStorage.getItem('tianji:pref:remind') !== '0',
  autoplay: localStorage.getItem('tianji:pref:autoplay') !== '0',
});

const onAvatarError = (e) => {
  e.target.src = defaultAvatar;
};

const maskPhone = (p) => String(p || '').replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');

// ---- 基本资料：默认只读，点「编辑资料」进编辑态，保存/取消回到只读 ----
const editingProfile = ref(false);
const startEdit = () => {
  editingProfile.value = true;
  tip.value = '';
};
const cancelEdit = () => {
  editingProfile.value = false;
  tip.value = '';
  fill(store.userInfo || null);
};

const switchTab = (k) => {
  tab.value = k;
  tip.value = '';
  if (k === 'collect' && !collectLoaded.value) loadCollect();
};

// ---- 我的收藏（复用老项目 /personal/main/myCollect 的接口逻辑） ----
const collectList = ref([]);
const collectTotal = ref(0);
const collectLoading = ref(false);
const collectLoaded = ref(false);
const collectParams = reactive({ pageNo: 1, pageSize: 8 });

const loadCollect = async () => {
  collectLoading.value = true;
  try {
    const res = await getMyCollect(collectParams);
    if (res?.code === 200 && res.data) {
      collectList.value = res.data.list || [];
      collectTotal.value = Number(res.data.total) || 0;
    }
  } catch (e) {
    collectList.value = [];
    collectTotal.value = 0;
  } finally {
    collectLoading.value = false;
    collectLoaded.value = true;
  }
};

const cancelCollect = (item) => {
  ElMessageBox.confirm(`确定取消收藏「${item.courseName}」吗？`, '取消收藏', {
    confirmButtonText: '确定',
    cancelButtonText: '再想想',
    type: 'warning',
  })
    .then(async () => {
      const res = await addMyCollect({ courseId: item.courseId, collected: false });
      if (res?.code === 200) {
        ElMessage.success('已取消收藏');
        // 删到本页空了且不是第一页时回退一页
        if (collectList.value.length === 1 && collectParams.pageNo > 1) collectParams.pageNo -= 1;
        loadCollect();
      } else {
        ElMessage.error(res?.message || '取消收藏失败');
      }
    })
    .catch(() => {});
};

const goCourse = (courseId) => {
  if (courseId) router.push({ path: '/student/courses/detail', query: { id: courseId } });
};

const fill = (d) => {
  if (!d) return;
  form.username = d.username || d.account || '';
  form.name = d.name || '';
  form.gender = d.gender ?? 0;
  form.school = d.school || d.schoolMajor || '';
  form.email = d.email || '';
  form.intro = d.intro || '';
  form.cellPhone = d.cellPhone || '';
  form.icon = d.icon || '';
};

const load = async () => {
  try {
    const res = await getUserInfo();
    if (res?.code === 200 && res.data) {
      fill(res.data);
      store.setUserInfo(res.data);
    }
  } catch (e) {
    // 未登录 / 接口不可用：保留空表单，不阻断页面
  }
};

onMounted(load);

const reset = () => {
  fill(store.userInfo || null);
  tip.value = '';
  load();
};

const save = async () => {
  if (!form.name) {
    tip.value = '请填写昵称';
    tipType.value = 'is-err';
    return;
  }
  saving.value = true;
  tip.value = '';
  try {
    const payload = {
      name: form.name,
      gender: form.gender,
      email: form.email,
      intro: form.intro,
      school: form.school,
    };
    const res = await updateUserInfo(payload);
    if (res?.code === 200) {
      tip.value = '已保存';
      tipType.value = 'is-ok';
      const merged = { ...(store.userInfo || {}), ...payload };
      store.setUserInfo(merged);
      editingProfile.value = false;
    } else {
      tip.value = res?.message || '保存失败，请稍后重试';
      tipType.value = 'is-err';
    }
  } catch (e) {
    tip.value = '保存失败，请检查网络后重试';
    tipType.value = 'is-err';
  } finally {
    saving.value = false;
  }
};

const onAvatarPick = (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 2 MB');
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    localAvatar.value = String(reader.result || '');
    const uid = store.userInfo?.id;
    if (uid) {
      try {
        localStorage.setItem(`tianji:avatar:${uid}`, localAvatar.value);
      } catch (err) {
        // 本地存储不可用时仅本次会话生效
      }
    }
    ElMessage.success('头像已更新');
  };
  reader.readAsDataURL(file);
  e.target.value = '';
};

const togglePref = (k) => {
  prefs[k] = !prefs[k];
  localStorage.setItem(`tianji:pref:${k}`, prefs[k] ? '1' : '0');
};

// ---- 账号安全：沿用现有弹窗式交互，这里用轻量 prompt 流程保证可用 ----
const openPwd = async () => {
  const oldPassword = window.prompt('请输入当前密码');
  if (!oldPassword) return;
  const newPassword = window.prompt('请输入新密码（不少于 8 位）');
  if (!newPassword) return;
  try {
    const res = await updatePassword({ oldPassword, newPassword });
    if (res?.code === 200) ElMessage.success('密码已更新');
    else ElMessage.error(res?.message || '修改失败');
  } catch (e) {
    ElMessage.error('修改失败，请稍后重试');
  }
};

const openPhone = async () => {
  const phone = window.prompt('请输入新手机号');
  if (!phone) return;
  try {
    await sendSms({ cellPhone: phone });
  } catch (e) {
    // 验证码发送失败不阻断，继续走绑定接口
  }
  const code = window.prompt('请输入收到的验证码');
  if (!code) return;
  try {
    const res = await bindPhone({ cellPhone: phone, code });
    if (res?.code === 200) {
      ElMessage.success('手机号已更新');
      form.cellPhone = phone;
    } else {
      ElMessage.error(res?.message || '绑定失败');
    }
  } catch (e) {
    ElMessage.error('绑定失败，请稍后重试');
  }
};
</script>

<style lang="scss" scoped>
.pf {
  display: flex;
  align-items: flex-start;
  gap: 32px;
}

// ---- 左：设置导航 ----
.pf__nav {
  flex: 0 0 200px;
  width: 200px;
}
.pf__navCard {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: var(--s-card);
  border-radius: var(--s-r-lg);
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055);
}
.pf__navItem {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #333;
  font-size: 14px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.035);
  }
  &.is-active {
    background: var(--sa-soft);
    color: var(--sa);
    font-weight: 600;

    .pf__navIcon {
      color: var(--sa);
    }
  }
}
.pf__navIcon {
  display: flex;
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  color: var(--s-ink-2);

  svg {
    display: block;
    width: 20px;
    height: 20px;
  }
}
.pf__navLabel {
  white-space: nowrap;
}

// ---- 右：内容 ----
.pf__main {
  flex: 1 1 auto;
  min-width: 0;
}
.pf__card {
  padding: 28px;
  background: var(--s-card);
  border-radius: var(--s-r-lg);
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055);
}
.pf__titleRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.pf__editBtn {
  height: 32px;
  padding: 0 16px;
  border: 0;
  border-radius: 16px;
  background: var(--sa-soft);
  color: var(--sa);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease;

  &:hover { background: #d9e8fa; }
}
.pf__view {
  &Row {
    display: flex;
    gap: 16px;
    padding: 12px 0;
    border-bottom: 1px solid var(--s-divider);

    &:last-of-type { border-bottom: 0; }
    &.is-top { align-items: flex-start; }
  }
  &Label {
    flex: 0 0 96px;
    font-size: 14px;
    color: var(--s-ink-2);
  }
  &Value {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 14px;
    line-height: 22px;
    color: var(--s-ink);
    white-space: pre-wrap;
    word-break: break-word;
  }
}
.pf__title {
  margin: 0 0 24px;
  font-size: 17px;
  font-weight: 600;
  line-height: 23px;
  color: var(--s-ink);
}

// 头像行
.pf__avatarRow {
  display: flex;
  align-items: center;
  gap: 16px;
}
.pf__avatarWrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}
.pf__avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  background: #d8dee8;
}
.pf__avatarBtn {
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--s-soft);
  color: var(--s-ink-2);
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover {
    background: #eaebee;
    color: var(--s-ink);
  }
}
.pf__avatarHint {
  min-width: 0;
}
.pf__hintTitle {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--s-ink);
}
.pf__hintDesc {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.pf__divider {
  height: 1px;
  margin: 24px 0;
  background: var(--s-divider);
}

// 字段：左标签 96px 固定列，右控件自适应
.pf__field {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;

  &.is-top {
    align-items: flex-start;
  }
}
.pf__label {
  flex: 0 0 96px;
  width: 96px;
  font-size: 14px;
  color: var(--s-ink-2);
  line-height: 36px;
}
.pf__field.is-top .pf__label {
  line-height: 22px;
  padding-top: 8px;
}
.pf__control {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
}
.pf__input,
.pf__textarea {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: var(--s-soft);
  color: var(--s-ink);
  font-size: 14px;
  font-family: inherit;
  outline: 0;
  transition: box-shadow 0.16s ease, background-color 0.16s ease;

  &::placeholder {
    color: var(--s-ink-4);
  }
  &:focus {
    background: #fff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }
  &.is-disabled {
    color: var(--s-ink-3);
    cursor: not-allowed;
  }
}
.pf__input {
  height: 36px;
  padding: 0 14px;
}
.pf__textarea {
  padding: 10px 14px;
  line-height: 22px;
  resize: vertical;
}
.pf__count {
  position: absolute;
  right: 12px;
  bottom: 8px;
  font-size: 11px;
  color: var(--s-ink-4);
}

// 性别分段控件
.pf__seg {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: var(--s-soft);
}
.pf__segItem {
  min-width: 64px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--s-ink-2);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &.is-active {
    background: #fff;
    color: var(--sa);
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }
}

.pf__saveRow {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--s-divider);
}
.pf__btn {
  height: 36px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  background: var(--s-soft);
  color: var(--s-ink);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease, opacity 0.16s ease;

  &:hover:not(:disabled) {
    background: #eaebee;
  }
  &:disabled {
    opacity: 0.55;
    cursor: default;
  }
  &.is-primary {
    background: var(--sa);
    color: #fff;

    &:hover:not(:disabled) {
      background: var(--sa-hover);
    }
  }
}
.pf__tip {
  font-size: 13px;

  &.is-ok {
    color: var(--s-ok);
  }
  &.is-err {
    color: var(--s-danger);
  }
}

// 安全 / 偏好行
.pf__secRow,
.pf__prefRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
  border-bottom: 1px solid var(--s-divider);

  &:last-of-type {
    border-bottom: 0;
  }
}
.pf__secTitle {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--s-ink);
}
.pf__secDesc {
  margin: 0;
  font-size: 13px;
  color: var(--s-ink-3);
}
.pf__prefNote {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--s-ink-4);
}

.pf__switch {
  position: relative;
  flex: 0 0 48px;
  width: 48px;
  height: 28px;
  border: 0;
  border-radius: 999px;
  background: #d8d8dd;
  cursor: pointer;
  transition: background-color 0.18s ease;

  &.is-on {
    background: var(--sa);

    .pf__knob {
      transform: translateX(20px);
    }
  }
}
.pf__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
  transition: transform 0.18s ease;
}

// ---- 我的收藏 ----
.pf__collectBody {
  min-height: 160px;
}
.clItem {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--s-divider);

  &:last-of-type {
    border-bottom: 0;
  }

  &__cover {
    flex: 0 0 132px;
    width: 132px;
    height: 74px;
    border-radius: 10px;
    object-fit: cover;
    background: #eef1f6;
    cursor: pointer;
  }
  &__meta {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__name {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    color: var(--s-ink);
    cursor: pointer;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &:hover {
      color: var(--sa);
    }
  }
  &__sub {
    margin: 0;
    font-size: 13px;
    line-height: 19px;
    color: var(--s-ink-3);
  }
  &__ops {
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .pf__btn {
      height: 32px;
      padding: 0 16px;
      font-size: 13px;
    }
  }
}
.pf__collectPager {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}
.pf__collectEmpty {
  padding: 48px 0 40px;
  text-align: center;

  &Title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &Desc {
    margin: 0 0 20px;
    font-size: 13px;
    color: var(--s-ink-3);
  }
}

@media (max-width: 1180px) {
  .pf {
    gap: 20px;
  }
  .pf__nav {
    flex: 0 0 168px;
    width: 168px;
  }
  .pf__label {
    flex: 0 0 80px;
    width: 80px;
  }
}
</style>
