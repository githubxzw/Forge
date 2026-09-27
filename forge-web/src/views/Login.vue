<template>
  <div class="wrap">
    <el-card class="card">
      <div class="brand">🔨 Forge</div>
      <div class="sub">统一构建部署平台</div>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" @keyup.enter="doLogin" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large" show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
      <div class="tip muted">默认账号 admin / admin123（可用 FORGE_ADMIN_* 环境变量覆盖）</div>
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

<style scoped>
.wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1f2937, #374151); }
.card { width: 360px; padding: 10px 6px; }
.brand { text-align: center; font-size: 26px; font-weight: 700; margin-top: 10px; }
.sub { text-align: center; color: #909399; margin: 6px 0 24px; }
.tip { text-align: center; margin-top: 14px; }
</style>
