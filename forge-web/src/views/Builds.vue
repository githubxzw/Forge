<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>构建任务</h2>
        <p class="desc">全部构建记录与实时日志</p>
      </div>
    </div>
    <div class="toolbar">
      <el-select v-model="projectId" placeholder="全部项目" clearable style="width: 220px" @change="load">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" size="default">
      <el-table-column label="项目" width="140"><template #="{ row }">{{ row.project?.name }}</template></el-table-column>
      <el-table-column prop="branch" label="分支" width="100" />
      <el-table-column prop="version" label="版本" min-width="190" show-overflow-tooltip />
      <el-table-column label="目标" min-width="180">
        <template #="{ row }"><el-tag v-for="t in row.targets" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="84"><template #="{ row }"><status-tag :status="row.status" /></template></el-table-column>
      <el-table-column label="耗时" width="76"><template #="{ row }" ><span class="muted num">{{ duration(row) }}</span></template></el-table-column>
      <el-table-column label="时间" width="145"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
      <el-table-column label="操作" width="180">
        <template #="{ row }">
          <el-button size="small" text type="primary" @click="openLog(row)">日志</el-button>
          <el-button v-if="row.status === 'RUNNING'" size="small" text type="danger" @click="cancel(row)">取消</el-button>
          <el-button v-if="row.status === 'FAILED' || row.status === 'SUCCESS'" size="small" text @click="rebuild(row)">重新构建</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="60" description="还没有构建任务，去项目详情发起一次构建吧" />
      </template>
    </el-table>
    <div style="margin-top: 12px; text-align: right">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page + 1" @current-change="pg => { page = pg - 1; load() }" />
    </div>

    <el-drawer v-model="drawer" :title="'构建日志'" size="55%" destroy-on-close append-to-body>
      <log-view v-if="currentId" kind="build" :id="currentId" :log-url="buildLogUrl(currentId)" />
      <div style="margin-top: 8px" v-if="currentRow">
        <status-tag :status="currentRow.status" />
        <span class="muted" style="margin-left: 8px" v-if="currentRow.message">{{ currentRow.message }}</span>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { listBuilds, listProjects, cancelBuild, startBuild, getBuild, buildLogUrl } from '../api/api'
import StatusTag from '../components/StatusTag.vue'
import LogView from '../components/LogView.vue'

const route = useRoute()
const projects = ref<any[]>([])
const projectId = ref<string | null>(null)
const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const total = ref(0)
const drawer = ref(false)
const currentId = ref('')
const currentRow = ref<any>(null)
let timer: any = null

const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')
const duration = (row: any) => {
  if (!row.startedAt || !row.finishedAt) return '-'
  const sec = Math.round((new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime()) / 1000)
  return sec >= 60 ? Math.floor(sec / 60) + 'm' + (sec % 60) + 's' : sec + 's'
}

async function load() {
  loading.value = true
  const p = await listBuilds(projectId.value, page.value, 20)
  rows.value = p.content || []
  total.value = p.totalElements || 0
  loading.value = false
  scheduleAuto()
}

/** 有进行中/排队的任务时自动刷新列表 */
let autoTimer: any = null
function scheduleAuto() {
  clearInterval(autoTimer)
  if (rows.value.some(r => r.status === 'RUNNING' || r.status === 'PENDING')) {
    autoTimer = setInterval(async () => {
      const p = await listBuilds(projectId.value, page.value, 20)
      rows.value = p.content || []
      scheduleAuto()
    }, 5000)
  }
}

function openLog(row: any) {
  currentId.value = row.id
  currentRow.value = row
  drawer.value = true
  pollStatus()
}

function pollStatus() {
  clearInterval(timer)
  timer = setInterval(async () => {
    if (!currentRow.value || currentRow.value.status === 'SUCCESS' || currentRow.value.status === 'FAILED' || currentRow.value.status === 'CANCELLED') {
      clearInterval(timer); return
    }
    try { currentRow.value = await getBuild(currentId.value) } catch (e) { clearInterval(timer) }
  }, 3000)
}

async function cancel(row: any) {
  await cancelBuild(row.id)
  ElMessage.success('已发送取消请求')
  load()
}

async function rebuild(row: any) {
  await startBuild(row.project?.id || (row.project && row.project.id) || row.projectId, row.branch, row.targets)
  ElMessage.success('已重新提交构建')
  load()
}

onMounted(async () => {
  projects.value = await listProjects()
  await load()
  // 支持从项目详情跳转直接打开日志
  if (route.query.open) {
    const row = rows.value.find(r => r.id === route.query.open) || await getBuild(route.query.open as string)
    if (row) openLog(row)
  }
})
onUnmounted(() => { clearInterval(timer); clearInterval(autoTimer) })
</script>
