<!-- 登录页面 - 用户名密码登录 -->
<template>
  <div class="loginPass">
    <el-form
      ref="formRef"
      :model="fromData"
      :rules="rules"
      label-width="0px"
      class="demo-dynamic"
    >
      <el-form-item prop="username" label="">
        <el-input v-model="fromData.username" placeholder="请输入用户名或手机号" />
      </el-form-item>
      <el-form-item prop="password" label="">
        <el-input type="pass" :show-password="true" v-model="fromData.password" placeholder="请输入密码" />
      </el-form-item>
      <el-form-item class="marg-b-10">
        <div class="fx-sb">
            <div>
                <el-checkbox v-model="fromData.rememberMe" label="7天免登录" size="large" />
            </div>
            <el-link type="primary" @click="goReset">找回密码</el-link>
        </div>
      </el-form-item>
      <el-form-item class="marg-bt-15">
        <div class="bt" @click="submitForm(formRef)">登 录</div>
      </el-form-item>
    </el-form>
    <div class="font-bt text-center" @click="goRegister">
        去注册
    </div>
  </div>
</template>
<script setup>
// 数据导入
import { reactive, ref } from "vue";
import { useRoute, useRouter } from 'vue-router'
import { userLogins, userLoginsStaff, getUserInfo } from "@/api/user"
import { useUserStore } from '@/store'
import { redirectAfterLogin } from '@/config/loginRedirect'
import { ElMessage } from "element-plus";

const emit = defineEmits(['goHandle'])
const store = useUserStore();
const router = useRouter()
const route = useRoute()

const formRef = ref();
const checked = ref(false)
// 登录参数效验
const fromData = reactive({
  username: "",
  password: "",
  type: 1
});
// 效验规则
const rules = reactive({
  username: [
    { required: true, message: "请输入正确的用户名或手机号", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入正确的密码", trigger: "blur"},
  ],
});
// 登录：学生通道优先；后端按身份拒绝（「非学生端用户」）时自动改走管理端通道；
// 成功后按「身份 + 来源」跳转 —— 学员回学员端、教师进工作台（同一个登录页，按身份分流）
const submitForm = (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if (valid) {
      try {
        let staffChannel = false;
        let res = await userLogins({ ...fromData });
        if (res.code !== 200 && String(res.msg || '').includes('非学生端')) {
          // 学生通道按身份拒绝 → 走管理端通道（教师/管理员）；通道本身就是身份依据
          staffChannel = true;
          res = await userLoginsStaff({ ...fromData });
        }
        if (res.code !== 200) {
          ElMessage({ message: res.msg || '登录失败', type: 'error' });
          return;
        }
        // 用户token写入 pinia
        store.setToken(res.data);
        // 获取用户信息
        const data = await getUserInfo();
        if (data.code === 200) {
          // 记录到store 并按身份跳转（从某端进来则回该端）
          store.setUserInfo(data.data);
          router.push(redirectAfterLogin(data.data, staffChannel));
        } else {
          ElMessage({ message: data.msg || '获取用户信息失败', type: 'error' });
        }
      } catch (err) {
        ElMessage({
          message: typeof err === 'string' ? err : '登录出错，请重新尝试',
          type: 'error'
        });
      }
    } else {
      ElMessage({
          message: '登录出错，请重新尝试',
          type: 'error'
      });
      return false;
    }
  });
};

// 去注册
const goRegister = () => {
  emit('goHandle', 'register')
}
// 去注册
const goReset = () => {
  emit('goHandle', 'reset')
}
</script>
<style lang="scss" scoped>
.loginPass {
    margin-top: 40px;
}
</style>
