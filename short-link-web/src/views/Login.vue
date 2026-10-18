<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { register as registerApi } from '../api/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loginRef = ref()
const registerRef = ref()
const loginLoading = ref(false)
const registerLoading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirmPassword: '' })

const usernameRules = [
  { required: true, message: '请输入用户名', trigger: 'blur' },
  { min: 4, max: 20, message: '用户名长度需在 4~20 位', trigger: 'blur' }
]
const passwordRules = [
  { required: true, message: '请输入密码', trigger: 'blur' },
  { min: 6, max: 20, message: '密码长度需在 6~20 位', trigger: 'blur' }
]
const confirmRules = [
  { required: true, message: '请再次输入密码', trigger: 'blur' },
  {
    validator: (rule, value, callback) => {
      if (value !== registerForm.password) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }
]

const handleLogin = async () => {
  try {
    await loginRef.value.validate()
  } catch {
    return
  }
  loginLoading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/link')
  } catch {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    loginLoading.value = false
  }
}

const handleRegister = async () => {
  try {
    await registerRef.value.validate()
  } catch {
    return
  }
  registerLoading.value = true
  try {
    await registerApi({
      username: registerForm.username,
      password: registerForm.password
    })
    ElMessage.success('注册成功，请登录')
    loginForm.username = registerForm.username
    loginForm.password = ''
    registerForm.password = ''
    registerForm.confirmPassword = ''
    activeTab.value = 'login'
  } catch {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    registerLoading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">
          <h2>短链平台</h2>
          <p>登录后管理你的短链与访问统计</p>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginRef"
            :model="loginForm"
            :rules="{ username: usernameRules, password: passwordRules }"
            label-position="top"
            @keyup.enter="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
              />
            </el-form-item>
            <el-button class="submit-btn" type="primary" :loading="loginLoading" @click="handleLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerRef"
            :model="registerForm"
            :rules="{
              username: usernameRules,
              password: passwordRules,
              confirmPassword: confirmRules
            }"
            label-position="top"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="registerForm.username" placeholder="4~20 位用户名" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="6~20 位密码"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                show-password
                placeholder="请再次输入密码"
              />
            </el-form-item>
            <el-button
              class="submit-btn"
              type="primary"
              :loading="registerLoading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 100%);
}

.login-card {
  width: 400px;
}

.login-title h2 {
  margin: 0 0 6px;
  color: #111827;
}

.login-title p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
