<template>
  <div class="login-wrap">
    <div class="glow g1"></div>
    <div class="glow g2"></div>
    <div class="grid"></div>
    <el-card class="login-card" shadow="never">
      <div class="logo-row">
        <div class="mark">🔨</div>
        <div class="t">Forge</div>
      </div>
      <div class="s">统一构建部署平台</div>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" @keyup.enter="doLogin">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large" show-password @keyup.enter="doLogin">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%; height: 42px; font-weight: 600"
                   :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
      <div class="tip">默认账号 admin / admin123 · 首次登录后请修改密码</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('admin')
const password = ref('')
const loading = ref(false)

async function doLogin() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    const s = await login(username.value, password.value)
    auth.setSession(s.token, s.username)
    router.push('/dashboard')
  } catch (e) { /* 错误已弹 */ }
  loading.value = false
}
</script>
