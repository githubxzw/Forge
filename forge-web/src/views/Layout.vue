<template>
  <el-container style="min-height: 100vh">
    <el-aside width="200px" class="aside">
      <div class="logo">🔨 Forge</div>
      <el-menu :default-active="activeMenu" router background-color="#1f2937" text-color="#cbd5e1"
               active-text-color="#409eff" style="border-right: none">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon>控制台</el-menu-item>
        <el-menu-item index="/projects"><el-icon><Folder /></el-icon>项目管理</el-menu-item>
        <el-menu-item index="/builds"><el-icon><Cpu /></el-icon>构建任务</el-menu-item>
        <el-menu-item index="/artifacts"><el-icon><Box /></el-icon>制品库</el-menu-item>
        <el-menu-item index="/deployments"><el-icon><Promotion /></el-icon>部署中心</el-menu-item>
        <el-menu-item index="/environments"><el-icon><Monitor /></el-icon>环境管理</el-menu-item>
        <el-menu-item index="/credentials"><el-icon><Key /></el-icon>凭据管理</el-menu-item>
        <el-menu-item index="/settings"><el-icon><Setting /></el-icon>系统设置</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title || 'Forge' }}</span>
        <el-dropdown @command="cmd => cmd === 'logout' && doLogout()">
          <span class="user"><el-icon><User /></el-icon> {{ auth.username }} <el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main style="padding: 0"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeMenu = computed(() => {
  if (route.path.startsWith('/projects')) return '/projects'
  return route.path
})
const doLogout = () => { auth.logout(); router.push('/login') }
</script>

<style scoped>
.aside { background: #1f2937; }
.logo { color: #fff; font-size: 20px; font-weight: 700; padding: 20px 18px; letter-spacing: 1px; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #e4e7ed; }
.title { font-size: 16px; font-weight: 600; }
.user { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; color: #606266; }
</style>
