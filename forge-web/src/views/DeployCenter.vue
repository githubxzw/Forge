<template>
  <div class="page">
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>🚀 一键部署</template>
      <el-form inline>
        <el-form-item label="项目">
          <el-select v-model="dProject" style="width: 200px" placeholder="选择项目" @change="dArtifact = ''">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="dEnv" style="width: 200px" placeholder="选择环境">
            <el-option v-for="e in environments" :key="e.id" :label="`${e.name} (${e.kind})`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="制品">
          <el-select v-model="dArtifact" style="width: 320px" placeholder="选择制品" :disabled="!dProject">
            <el-option v-for="a in deployableArtifacts" :key="a.id"
                       :label="`${a.type} · ${a.version}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="deploying" :disabled="!dProject || !dEnv || !dArtifact" @click="doDeploy">部署</el-button>
        </el-form-item>
      </el-form>
      <div class="muted">环境类型决定可用制品：SSH → 前端包/后端包；DOCKER → 镜像；K8S → Chart</div>
    </el-card>

    <div class="toolbar">
      <el-select v-model="projectId" placeholder="全部项目" clearable style="width: 200px" @change="load">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column label="项目" width="130"><template #="{ row }">{{ row.project?.name }}</template></el-table-column>
      <el-table-column label="环境" width="130">
        <template #="{ row }">{{ row.environment?.name }} <span class="muted">({{ row.environment?.kind }})</span></template>
      </el-table-column>
      <el-table-column prop="artifact.version" label="制品版本" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90"><template #="{ row }"><status-tag :status="row.status" /></template></el-table-column>
      <el-table-column label="回滚自" width="90">
        <template #="{ row }"><el-tag v-if="row.rollbackOf" size="small" type="warning">回滚</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="100" />
      <el-table-column label="结果" min-width="180">
        <template #="{ row }"><span class="mono muted">{{ resultSummary(row) }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="160"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #="{ row }">
          <el-button size="small" text type="primary" @click="openLog(row)">日志</el-button>
          <el-button v-if="row.status === 'SUCCESS'" size="small" text type="warning" @click="rollback(row)">回滚</el-button>
          <el-button v-if="row.status === 'RUNNING'" size="small" text type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 12px; text-align: right">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page + 1" @current-change="pg => { page = pg - 1; load() }" />
    </div>

    <el-drawer v-model="drawer" title="部署日志" size="55%" destroy-on-close>
      <log-view v-if="currentId" kind="deploy" :id="currentId" :log-url="deployLogUrl(currentId)" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  listProjects, listEnvironments, listArtifacts, listDeployments,
  startDeploy, rollbackDeploy, cancelDeploy, deployLogUrl
} from '../api/api'
import StatusTag from '../components/StatusTag.vue'
import LogView from '../components/LogView.vue'

const route = useRoute()
const projects = ref<any[]>([])
const environments = ref<any[]>([])
const artifacts = ref<any[]>([])
const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const total = ref(0)
const projectId = ref<string | null>(null)

const dProject = ref('')
const dEnv = ref('')
const dArtifact = ref('')
const deploying = ref(false)
const drawer = ref(false)
const currentId = ref('')
let timer: any = null

const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')

const deployableArtifacts = computed(() => {
  const env = environments.value.find(e => e.id === dEnv.value)
  const allowed: Record<string, string[]> = { SSH: ['TAR', 'JAR'], DOCKER: ['IMAGE'], K8S: ['CHART'] }
  const kinds = env ? allowed[env.kind] : ['TAR', 'JAR', 'IMAGE', 'CHART']
  return artifacts.value.filter(a => a.project?.id === dProject.value && kinds.includes(a.type))
})

const resultSummary = (row: any) => {
  const r = row.result || {}
  const parts: string[] = []
  if (r.version || r.image) parts.push(r.image || r.version)
  if (r.revision) parts.push('rev ' + r.revision)
  if (r.healthy === true) parts.push('健康✓')
  if (r.skipped) parts.push('未配置健康检查')
  if (row.message) parts.push(row.message)
  return parts.join(' · ')
}

async function load() {
  loading.value = true
  const p = await listDeployments(projectId.value, null, page.value, 20)
  rows.value = p.content || []
  total.value = p.totalElements || 0
  loading.value = false
}

async function doDeploy() {
  deploying.value = true
  try {
    const dep = await startDeploy(dProject.value, dEnv.value, dArtifact.value)
    ElMessage.success('部署任务已提交')
    page.value = 0
    await load()
    openLog(dep)
  } catch (e) {}
  deploying.value = false
}

async function rollback(row: any) {
  await ElMessageBox.confirm(
    `回滚「${row.project?.name}」在「${row.environment?.name}」的这次部署？将回退到上一个成功版本。`, '回滚确认', { type: 'warning' })
  const dep = await rollbackDeploy(row.id)
  ElMessage.success('回滚任务已提交')
  await load()
  openLog(dep)
}

async function cancel(row: any) {
  await cancelDeploy(row.id)
  ElMessage.success('已发送取消请求')
  load()
}

function openLog(row: any) {
  currentId.value = row.id
  drawer.value = true
  poll(row.id)
}

function poll(id: string) {
  clearInterval(timer)
  timer = setInterval(async () => {
    await load()
    const row = rows.value.find(r => r.id === id)
    if (!row || row.status !== 'RUNNING') clearInterval(timer)
  }, 5000)
}

onMounted(async () => {
  projects.value = await listProjects()
  environments.value = await listEnvironments()
  artifacts.value = (await listArtifacts(null, null, 0, 100)).content || []
  await load()
  // 支持从制品库带参跳转
  if (route.query.artifactId) {
    dArtifact.value = route.query.artifactId as string
    dProject.value = (route.query.projectId as string) || ''
  }
})
onUnmounted(() => clearInterval(timer))
</script>
