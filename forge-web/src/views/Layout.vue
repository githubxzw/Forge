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
          <el-menu-item index="/manual"><el-icon><Reading /></el-icon>使用手册</el-menu-item>
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
        <div class="h-crumb">
          <span class="h-brand">Forge</span>
          <span class="h-sep">/</span>
          <span class="h-name">{{ route.meta.title || '控制台' }}</span>
        </div>
        <div class="h-actions">
          <el-tooltip :content="mode === 'dark' ? '切换到日间模式' : '切换到夜间模式'" placement="bottom">
            <button class="theme-toggle" :aria-label="mode === 'dark' ? '切换到日间模式' : '切换到夜间模式'" @click="toggle">
              <el-icon v-if="mode === 'dark'"><Sunny /></el-icon>
              <el-icon v-else><Moon /></el-icon>
            </button>
          </el-tooltip>
          <el-tag size="small" effect="plain" class="ver">v1.1</el-tag>
        </div>
      </el-header>
      <el-main style="padding: 0"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTheme } from '../theme'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { mode, toggle } = useTheme()
const activeMenu = computed(() => {
  if (route.path.startsWith('/projects')) return '/projects'
  return route.path
})
const doLogout = () => { auth.logout(); router.push('/login') }
</script>

<style scoped>
.aside {
  background: var(--forge-aside-grad);
  border-right: 1px solid var(--forge-aside-border);
  display: flex; flex-direction: column;
  position: sticky; top: 0; height: 100vh;
  transition: background .25s ease, border-color .25s ease;
}
.brand { display: flex; align-items: center; gap: 10px; padding: 20px 18px 14px; }
.brand-mark {
  width: 38px; height: 38px; border-radius: 10px; font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 0 20px -4px rgba(99, 102, 241, .9);
}
.brand-name { color: var(--el-text-color-primary); font-size: 18px; font-weight: 800; letter-spacing: -.01em; line-height: 1.1; }
.brand-sub { color: var(--forge-text-faint); font-size: 9.5px; letter-spacing: .22em; margin-top: 2px; }

.menu { border-right: none; background: transparent; flex: 1; }
.menu :deep(.el-menu-item-group__title) {
  color: var(--forge-menu-group); font-size: 11px; letter-spacing: .12em;
  padding: 16px 22px 6px; text-transform: uppercase;
}
.menu :deep(.el-menu-item) {
  color: var(--forge-menu-text); margin: 3px 10px; border-radius: 8px; height: 42px;
  transition: background .15s ease, color .15s ease;
}
.menu :deep(.el-menu-item:hover) { background: var(--forge-menu-hover-bg); color: var(--forge-menu-text-hover); }
.menu :deep(.el-menu-item.is-active) {
  background: var(--forge-menu-active-grad);
  color: var(--forge-menu-active-text); position: relative;
  box-shadow: inset 0 0 0 1px var(--forge-menu-active-border);
}
.menu :deep(.el-menu-item.is-active)::before {
  content: ''; position: absolute; left: 0; top: 9px; bottom: 9px; width: 3px;
  border-radius: 3px; background: var(--forge-menu-bar); box-shadow: 0 0 8px var(--forge-menu-bar-glow);
}

.aside-foot {
  display: flex; align-items: center; gap: 10px;
  margin: 10px; padding: 10px 12px; border-radius: 10px;
  background: var(--forge-foot-bg); border: 1px solid var(--forge-foot-border);
}
.avatar { background: linear-gradient(135deg, #6366f1, #a855f7); font-weight: 700; flex: none; }
.uinfo { flex: 1; min-width: 0; }
.uname { color: var(--el-text-color-primary); font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; }
.urole { color: var(--forge-text-faint); font-size: 11px; }
.logout { color: var(--forge-text-muted); }
.logout:hover { color: #f87171; background: rgba(248, 113, 113, .12); }

.header {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--forge-header-bg); backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--forge-aside-border);
  position: sticky; top: 0; z-index: 5; height: 56px;
  transition: background .25s ease, border-color .25s ease;
}
.h-crumb { display: flex; align-items: center; gap: 8px; min-width: 0; }
.h-brand { font-size: 13px; font-weight: 700; color: var(--forge-text-muted); letter-spacing: .02em; }
.h-sep { color: var(--forge-text-faint); font-size: 12px; }
.h-name { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.h-actions { display: flex; align-items: center; gap: 10px; }
.ver { color: var(--forge-text-muted); }
</style>
