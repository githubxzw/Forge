<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>控制台</h2>
        <p class="desc">构建与部署的实时概览</p>
      </div>
      <div class="actions">
        <el-button :icon="Folder" @click="$router.push('/projects')">去构建</el-button>
        <el-button :icon="Promotion" @click="$router.push('/deployments')">去部署</el-button>
        <el-button :icon="Reading" @click="$router.push('/manual')">使用手册</el-button>
      </div>
    </div>

    <el-alert v-if="s.defaultPassword" type="error" :closable="false" style="margin-bottom: 14px"
              title="安全警告：管理员仍在使用默认密码 admin123，部署到公网前请立即在右上角「系统设置」中修改" show-icon />

    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.key">
        <el-card class="stat-card" shadow="never">
          <div class="stat-row">
            <div class="ico" :class="c.tone"><el-icon><component :is="c.icon" /></el-icon></div>
            <div>
              <div class="num">{{ s[c.key] ?? '-' }}</div>
              <div class="label">{{ c.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <div class="week-row">
            <div>
              <div class="week-title">近 7 天构建</div>
              <div class="week-num"><span class="num">{{ s.builds7?.ok ?? 0 }}</span><span class="muted"> / {{ s.builds7?.total ?? 0 }} 成功</span></div>
            </div>
            <el-progress type="circle" :width="58" :stroke-width="8"
                         :percentage="pct(s.builds7)" :format="fmtPct(s.builds7)" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div class="week-row">
            <div>
              <div class="week-title">近 7 天部署</div>
              <div class="week-num"><span class="num">{{ s.deploys7?.ok ?? 0 }}</span><span class="muted"> / {{ s.deploys7?.total ?? 0 }} 成功</span></div>
            </div>
            <el-progress type="circle" :width="58" :stroke-width="8"
                         :percentage="pct(s.deploys7)" :format="fmtPct(s.deploys7)" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="list-head">
              <span>最近构建</span>
              <el-link type="primary" :underline="false" @click="$router.push('/builds')">查看全部</el-link>
            </div>
          </template>
          <el-table :data="s.recentBuilds || []" size="small">
            <template #empty><el-empty :image-size="70" description="暂无构建记录" /></template>
            <el-table-column label="项目" #="{ row }">{{ row.project?.name }}</el-table-column>
            <el-table-column prop="branch" label="分支" width="100" />
            <el-table-column label="版本" show-overflow-tooltip #="{ row }"><span class="mono">{{ row.version }}</span></el-table-column>
            <el-table-column label="状态" width="86" #="{ row }"><status-tag :status="row.status" /></el-table-column>
            <el-table-column label="时间" width="130" #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="list-head">
              <span>最近部署</span>
              <el-link type="primary" :underline="false" @click="$router.push('/deployments')">查看全部</el-link>
            </div>
          </template>
          <el-table :data="s.recentDeployments || []" size="small">
            <template #empty><el-empty :image-size="70" description="暂无部署记录" /></template>
            <el-table-column label="项目" #="{ row }">{{ row.project?.name }}</el-table-column>
            <el-table-column label="环境" width="110" #="{ row }">{{ row.environment?.name }}</el-table-column>
            <el-table-column label="制品版本" show-overflow-tooltip #="{ row }"><span class="mono">{{ row.artifact?.version }}</span></el-table-column>
            <el-table-column label="状态" width="86" #="{ row }"><status-tag :status="row.status" /></el-table-column>
            <el-table-column label="时间" width="130" #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="healthRows.length" shadow="never" style="margin-top: 14px">
      <template #header>
        <div class="list-head">
          <span>服务健康监控<span v-if="unhealthyCount" style="color: var(--el-color-danger);margin-left:8px">{{ unhealthyCount }} 项异常</span></span>
          <span class="muted">按项目健康检查配置持续探测，异常自动通知</span>
        </div>
      </template>
      <el-table :data="healthRows" size="small">
        <el-table-column label="项目" #="{ row }">{{ row.project }}</el-table-column>
        <el-table-column label="环境" width="140" #="{ row }">{{ row.environment }}</el-table-column>
        <el-table-column label="地址" show-overflow-tooltip #="{ row }"><span class="mono muted">{{ row.url }}</span></el-table-column>
        <el-table-column label="状态" width="110" #="{ row }">
          <status-tag :status="row.healthy ? 'SUCCESS' : 'FAILED'" />
        </el-table-column>
        <el-table-column label="异常始于" width="140" #="{ row }"><span class="muted">{{ row.unhealthySince ? fmt(row.unhealthySince) : '-' }}</span></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Folder, Cpu, Box, Promotion, Reading } from '@element-plus/icons-vue'
import { stats, monitorStatus } from '../api/api'
import dayjs from 'dayjs'
import StatusTag from '../components/StatusTag.vue'

const cards = [
  { key: 'projects', label: '项目', icon: Folder, tone: 'indigo' },
  { key: 'builds', label: '构建任务', icon: Cpu, tone: 'sky' },
  { key: 'artifacts', label: '制品', icon: Box, tone: 'amber' },
  { key: 'deployments', label: '部署记录', icon: Promotion, tone: 'emerald' }
]

const s = ref<any>({})
const healthRows = ref<any[]>([])
const unhealthyCount = computed(() => healthRows.value.filter(r => !r.healthy).length)
onMounted(async () => {
  s.value = await stats()
  try { healthRows.value = await monitorStatus() } catch (e) { /* 监控未启用或无权限时静默 */ }
})
const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm') : '-')

/** 无数据时圆环显示中性符号，避免误导性的 0% */
const pct = (w: any) => (w?.total ? Math.min(100, Math.round((w.ok / w.total) * 100)) : 0)
const fmtPct = (w: any) => () => (w?.total ? pct(w) + '%' : '–')
</script>

<style scoped>
.list-head { display: flex; align-items: center; justify-content: space-between; }
.week-row { display: flex; align-items: center; justify-content: space-between; }
.week-title { color: var(--forge-text-muted); font-size: 13px; margin-bottom: 8px; }
.week-num .num { font-size: 30px; font-weight: 800; }
.week-num .muted { margin-left: 6px; }
</style>
