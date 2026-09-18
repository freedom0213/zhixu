<!--
 * 教师端 · 个人资料（设计稿 T7）
 * -----------------------------------------------------------------------------
 * 这个页面回答一个问题：**别人看到的我是什么样**。
 *
 * 三件事刻意这么做：
 *   1) 左边是「别人看到的样子」（头像、昵称、身份、账号），右边才是可编辑表单 ——
 *      先看见结果，再改输入，不用改完保存才知道长什么样。
 *   2) 手机号**不在表单里**：user 表里 username 与 cell_phone 同源，改手机会连带改登录名，
 *      资料页不该顺手改掉登录方式。账号只读展示。
 *   3) 没填过的字段留空并给出「学员会看到什么」的提示，**不预填示例值** ——
 *      空就是空，管理员/讲师自己决定填什么。
 *
 * 保存走 PUT /us/teachers/profile，后端把 id 强制成登录用户（只能改自己）。
-->
<template>
  <div class="pf">
    <div class="pf-head">
      <div class="pf-head__text">
        <h2 class="s-h2">个人资料</h2>
        <p class="pf-head__sub">
          这里填的内容会出现在你的课程页上，学员报名前会看到。
        </p>
      </div>
      <div class="pf-head__act">
        <span v-if="tip.text" class="pf-tip" :class="`is-${tip.type}`">{{ tip.text }}</span>
        <button
          class="q-btn q-btn--primary"
          type="button"
          :disabled="saving || loading || !!loadError"
          @click="onSave"
        >
          {{ saving ? '保存中…' : '保存修改' }}
        </button>
      </div>
    </div>

    <p v-if="loading" class="s-card pf-state">加载中…</p>
    <p v-else-if="loadError" class="s-card pf-state pf-state--err">{{ loadError }}</p>

    <div v-else class="pf-grid">
      <!-- 左：学员看到的样子 -->
      <aside class="s-card pf-id">
        <div class="pf-avatar">
          <img v-if="form.icon" class="pf-avatar__img" :src="form.icon" :alt="form.name || '讲师'" />
          <span v-else class="pf-avatar__ph">{{ initial }}</span>
        </div>
        <p class="pf-id__name">{{ form.name || '还没填昵称' }}</p>
        <p class="pf-id__role">{{ info.roleName || '讲师' }}</p>
        <p v-if="form.job" class="pf-id__job">{{ form.job }}</p>

        <dl class="pf-id__meta">
          <div class="pf-id__row">
            <dt>账号</dt>
            <dd>{{ info.cellPhone || info.username || '—' }}</dd>
          </div>
          <div class="pf-id__row">
            <dt>加入时间</dt>
            <dd>{{ info.createTime || '—' }}</dd>
          </div>
          <div class="pf-id__row">
            <dt>所在地</dt>
            <dd>{{ regionText }}</dd>
          </div>
        </dl>

        <p class="pf-id__hint">账号与加入时间不可修改。</p>
      </aside>

      <!-- 右：可编辑表单 -->
      <section class="s-card pf-form">
        <h3 class="s-h3">基本信息</h3>

        <div class="pf-field">
          <label class="pf-field__lb" for="pf-name">昵称</label>
          <input
            id="pf-name"
            v-model="form.name"
            class="pf-in"
            type="text"
            maxlength="20"
            placeholder="学员看到的就是这个名字"
          />
          <p class="pf-field__hint">{{ (form.name || '').length }}/20</p>
        </div>

        <div class="pf-field">
          <span class="pf-field__lb">性别</span>
          <div class="q-chips">
            <button
              v-for="g in GENDERS"
              :key="g.value"
              class="q-chip"
              :class="{ 'is-on': Number(form.gender) === g.value }"
              type="button"
              @click="form.gender = g.value"
            >
              {{ g.label }}
            </button>
          </div>
        </div>

        <div class="pf-field">
          <label class="pf-field__lb" for="pf-job">职业 / 头衔</label>
          <input
            id="pf-job"
            v-model="form.job"
            class="pf-in"
            type="text"
            maxlength="50"
            placeholder="例如：Java 架构师 · 平台讲师"
          />
        </div>

        <div class="pf-field pf-field--half">
          <label class="pf-field__lb" for="pf-province">所在省 / 市</label>
          <div class="pf-field__pair">
            <input id="pf-province" v-model="form.province" class="pf-in" type="text" maxlength="20" placeholder="省" />
            <input v-model="form.city" class="pf-in" type="text" maxlength="20" placeholder="市" />
          </div>
        </div>

        <div class="pf-field pf-field--half">
          <label class="pf-field__lb" for="pf-email">邮箱 / QQ</label>
          <div class="pf-field__pair">
            <input id="pf-email" v-model="form.email" class="pf-in" type="email" maxlength="100" placeholder="邮箱" />
            <input v-model="form.qq" class="pf-in" type="text" maxlength="20" placeholder="QQ" />
          </div>
        </div>

        <div class="pf-field">
          <label class="pf-field__lb" for="pf-icon">头像地址</label>
          <input
            id="pf-icon"
            v-model="form.icon"
            class="pf-in"
            type="text"
            maxlength="255"
            placeholder="图片链接（留空则用昵称首字）"
          />
        </div>

        <div class="pf-field">
          <label class="pf-field__lb" for="pf-intro">个人简介</label>
          <textarea
            id="pf-intro"
            v-model="form.intro"
            class="pf-in pf-in--area"
            maxlength="500"
            rows="4"
            placeholder="讲讲你的经历和讲课风格，学员会读这段"
          ></textarea>
          <p class="pf-field__hint">{{ (form.intro || '').length }}/500</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { getMyProfile, saveMyProfile } from '@/api/teacher/profile';

const GENDERS = [
  { value: 1, label: '男' },
  { value: 2, label: '女' },
  { value: 0, label: '不公开' },
];

const loading = ref(true);
const saving = ref(false);
const loadError = ref('');
const info = reactive({ roleName: '', cellPhone: '', username: '', createTime: '' });

// 表单里只放接口确实返回的字段；读不到就是空，不预填假值
const form = reactive({
  name: '',
  gender: null,
  job: '',
  province: '',
  city: '',
  email: '',
  qq: '',
  icon: '',
  intro: '',
});

const tip = reactive({ type: '', text: '' });

const initial = computed(() => (form.name || '讲师').trim().slice(0, 1));
const regionText = computed(() => {
  const r = [form.province, form.city].filter(Boolean).join(' ');
  return r || '—';
});

const load = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const d = await getMyProfile();
    if (!d) {
      loadError.value = '没读到你的资料（当前登录态可能已失效），请重新登录后再试';
      return;
    }
    info.roleName = d.roleName || '';
    info.cellPhone = d.cellPhone || '';
    info.username = d.username || '';
    info.createTime = d.createTime || '';
    form.name = d.name || '';
    // 后端 gender 可能是 null —— 保持 null，别默认成「男」
    form.gender = d.gender === null || d.gender === undefined ? null : Number(d.gender);
    form.job = d.job || '';
    form.province = d.province || '';
    form.city = d.city || '';
    form.email = d.email || '';
    form.qq = d.qq || '';
    form.icon = d.icon || '';
    form.intro = d.intro || '';
  } catch (e) {
    loadError.value = e?.message || '读取资料失败';
  } finally {
    loading.value = false;
  }
};

const onSave = async () => {
  if (saving.value) return;
  saving.value = true;
  tip.text = '';
  try {
    await saveMyProfile({
      name: form.name || null,
      gender: form.gender,
      job: form.job || null,
      province: form.province || null,
      city: form.city || null,
      email: form.email || null,
      qq: form.qq || null,
      icon: form.icon || null,
      intro: form.intro || null,
    });
    tip.type = 'ok';
    tip.text = '已保存';
  } catch (e) {
    // 服务端原话直接给用户（统一 call() 抛出的中文原因）
    tip.type = 'err';
    tip.text = e?.message || '保存失败，请稍后重试';
  } finally {
    saving.value = false;
  }
};

onMounted(load);
</script>

<style lang="scss" scoped>
// ⚠️ 页面内不用与外壳同名的类（.main/.body/.card），一律 pf- 前缀
.pf {
  max-width: 1096px;
}

.pf-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  &__text {
    min-width: 0;
  }
  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: var(--s-ink-2);
  }
  &__act {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.pf-tip {
  font-size: 13px;
  line-height: 20px;

  &.is-ok {
    color: var(--s-ok);
  }
  &.is-err {
    color: var(--s-danger);
  }
}

.pf-state {
  margin: 0;
  padding: 24px;
  font-size: 13px;
  color: var(--s-ink-2);

  &--err {
    color: var(--s-danger);
  }
}

.pf-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

// ---------- 左：身份卡 ----------
.pf-id {
  padding: 24px 20px;
  text-align: center;

  &__name {
    margin: 12px 0 0;
    font-size: 17px;
    line-height: 24px;
    font-weight: 600;
    color: var(--s-ink);
    word-break: break-all;
  }
  &__role {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--sa);
  }
  &__job {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
  &__meta {
    margin: 18px 0 0;
    padding-top: 16px;
    border-top: 1px solid var(--s-hairline);
    text-align: left;
  }
  &__row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;
    line-height: 20px;

    + .pf-id__row {
      margin-top: 8px;
    }

    dt {
      flex: 0 0 auto;
      color: var(--s-ink-3);
    }
    dd {
      margin: 0;
      min-width: 0;
      color: var(--s-ink);
      text-align: right;
      word-break: break-all;
    }
  }
  &__hint {
    margin: 16px 0 0;
    font-size: 11px;
    line-height: 16px;
    color: var(--s-ink-4);
  }
}

.pf-avatar {
  width: 84px;
  height: 84px;
  margin: 0 auto;
  border-radius: 50%;
  overflow: hidden;
  background: var(--sa-soft);
  display: flex;
  align-items: center;
  justify-content: center;

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  &__ph {
    font-size: 30px;
    line-height: 1;
    font-weight: 600;
    color: var(--sa);
  }
}

// ---------- 右：表单 ----------
.pf-form {
  padding: 24px;

  .s-h3 {
    margin-bottom: 16px;
  }
}

.pf-field {
  & + .pf-field {
    margin-top: 18px;
  }

  &__lb {
    display: block;
    margin-bottom: 8px;
    font-size: 13px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
  &__hint {
    margin: 6px 0 0;
    font-size: 11px;
    line-height: 16px;
    color: var(--s-ink-4);
    text-align: right;
  }
  &__pair {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
}

.pf-in {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border: 1px solid var(--s-hairline);
  border-radius: var(--s-r-md);
  background: #fff;
  color: var(--s-ink);
  font-size: 14px;
  font-family: inherit;
  line-height: 20px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;

  &::placeholder {
    color: var(--s-ink-4);
  }
  &:hover {
    border-color: #d8d8de;
  }
  &:focus {
    outline: none;
    border-color: var(--sa);
    box-shadow: 0 0 0 3px var(--sa-soft);
  }

  &--area {
    height: auto;
    padding: 10px 14px;
    line-height: 22px;
    resize: vertical;
    min-height: 96px;
  }
}

@media (max-width: 900px) {
  .pf-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
