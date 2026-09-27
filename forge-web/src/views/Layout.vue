<template>
  <el-container style="min-height: 100vh">
    <el-aside width="228px" class="aside">
      <div class="brand">
        <div class="brand-mark">🔨</div>
        <div>
          <div class="brand-name">Forge</div>
          <div class="brand-sub">BUILD · DEPLOY</div>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item-group>
          <template #title>工作台</template>
          <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon>控制台</el-menu-item>
          <el-menu-item index="/deployments"><el-icon><Promotion /></el-icon>部署中心</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group>
          <template #title>交付流水线</template>
          <el-menu-item index="/projects"><el-icon><Folder /></el-icon>项目管理</el-menu-item>
          <el-menu-item index="/builds"><el-icon><Cpu /></el-icon>构建任务</el-menu-item>
          <el-menu-item index="/artifacts"><el-icon><Box /></el-icon>制品库</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group>
          <template #title>系统配置</template>
          <el-menu-item index="/environments"><el-icon><Monitor /></el-icon>环境管理</el-menu-item>
          <el-menu-item index="/credentials"><el-icon><Key /></el-icon>凭据管理</el-menu-item>
          <el-menu-item index="/settings"><el-icon><Setting /></el-icon>系统设置</el-menu-item>
        </el-menu-item-group>
      </el-menu>
      <div class="aside-foot">
        <el-avatar :size="32" class="avatar">{{ auth.username?.[0]?.toUpperCase() }}</el-avatar>
        <div class="uinfo">
          <div class="uname">{{ auth.username }}</div>
          <div class="urole">管理员</div>
        </div>
        <el-tooltip content="退出登录" placement="top">
          <el-button text circle class="logout" @click="doLogout"><el-icon><SwitchButton /></el-icon></el-button>
        </el-tooltip>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="h-title">
          <span class="h-name">{{ route.meta.title || 'Forge' }}</span>
        </div>
        <el-tag size="small" effect="plain" class="ver">v1.0</el-tag>
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
.aside {
  background: linear-gradient(180deg, #171c30 0%, #101423 100%);
  display: flex; flex-direction: column;
  position: sticky; top: 0; height: 100vh;
}
.brand { display: flex; align-items: center; gap: 10px; padding: 20px 18px 14px; }
.brand-mark {
  width: 38px; height: 38px; border-radius: 10px; font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, .55);
}
.brand-name { color: #fff; font-size: 18px; font-weight: 800; letter-spacing: -.01em; line-height: 1.1; }
.brand-sub { color: #5d657f; font-size: 9.5px; letter-spacing: .22em; margin-top: 2px; }

.menu { border-right: none; background: transparent; flex: 1; }
.menu :deep(.el-menu-item-group__title) {
  color: #565f78; font-size: 10.5px; letter-spacing: .14em;
  padding: 16px 22px 6px; text-transform: uppercase;
}
.menu :deep(.el-menu-item) {
  color: #98a0b6; margin: 2px 10px; border-radius: 8px; height: 40px;
  transition: background .15s ease, color .15s ease;
}
.menu :deep(.el-menu-item:hover) { background: rgba(255, 255, 255, .05); color: #e7eaf3; }
.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(99, 102, 241, .28), rgba(99, 102, 241, .08));
  color: #d0d2ff; position: relative;
}
.menu :deep(.el-menu-item.is-active)::before {
  content: ''; position: absolute; left: 0; top: 9px; bottom: 9px; width: 3px;
  border-radius: 3px; background: #818cf8;
}

.aside-foot {
  display: flex; align-items: center; gap: 10px;
  margin: 10px; padding: 10px 12px; border-radius: 10px;
  background: rgba(255, 255, 255, .045); border: 1px solid rgba(255, 255, 255, .06);
}
.avatar { background: linear-gradient(135deg, #6366f1, #a855f7); font-weight: 700; flex: none; }
.uinfo { flex: 1; min-width: 0; }
.uname { color: #e7eaf3; font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; }
.urole { color: #5d657f; font-size: 11px; }
.logout { color: #7d89a6; }
.logout:hover { color: #f87171; background: rgba(248, 113, 113, .12); }

.header {
  display: flex; align-items: center; justify-content: space-between;
  background: rgba(255, 255, 255, .82); backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--forge-card-border);
  position: sticky; top: 0; z-index: 5; height: 56px;
}
.h-name { font-size: 15.5px; font-weight: 700; color: var(--forge-title); }
.ver { color: #8a92a6; }
</style>
