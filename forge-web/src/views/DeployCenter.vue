<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>部署中心</h2>
        <p class="desc">选项目 → 选环境 → 选制品，一键部署与回滚</p>
      </div>
      <div class="actions">
        <el-button :icon="Operation" @click="openBatch">批量部署</el-button>
      </div>
    </div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>🚀 一键部署</template>
      <el-form inline>
        <el-form-item label="项目">
          <el-select v-model="dProject" style="width: 200px" placeholder="选择项目" @change="onProjectChange">
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
      <el-select v-model="projectId" placeholder="全部项目" clearable style="width: 200px" @change="() => { page = 0; load() }">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column label="项目" width="110"><template #="{ row }">{{ row.project?.name }}</template></el-table-column>
      <el-table-column label="环境" width="128">
        <template #="{ row }">{{ row.environment?.name }}<div class="muted">{{ row.environment?.kind }}</div></template>
      </el-table-column>
      <el-table-column prop="artifact.version" label="制品版本" min-width="165" show-overflow-tooltip />
      <el-table-column label="状态" width="84"><template #="{ row }"><status-tag :status="row.status" /></template></el-table-column>
      <el-table-column label="回滚自" width="76">
        <template #="{ row }"><el-tag v-if="row.rollbackOf" size="small" type="warning">回滚</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="80" />
      <el-table-column label="结果" min-width="150" show-overflow-tooltip>
        <template #="{ row }"><span class="mono muted">{{ resultSummary(row) }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="142"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
      <el-table-column label="操作" width="220">
        <template #="{ row }">
          <el-button size="small" text type="primary" @click="openLog(row)">日志</el-button>
          <el-button v-if="row.status === 'SUCCESS'" size="small" text type="warning" @click="rollback(row)">回滚</el-button>
          <el-button v-if="row.status === 'RUNNING' || row.status === 'PENDING'" size="small" text type="danger" @click="cancel(row)">取消</el-button>
          <template v-if="row.status === 'PENDING_APPROVAL'">
            <el-button v-if="auth.isAdmin" size="small" text type="success" @click="approve(row)">审批</el-button>
            <el-button v-if="auth.isAdmin" size="small" text type="danger" @click="reject(row)">拒绝</el-button>
            <el-button v-if="!auth.isAdmin" size="small" text type="danger" @click="cancel(row)">撤回</el-button>
          </template>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="90" description="还没有部署记录，用上方向导完成第一次部署" />
      </template>
    </el-table>
    <div style="margin-top: 12px; text-align: right">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page + 1" @current-change="pg => { page = pg - 1; load() }" />
    </div>

    <el-drawer v-model="drawer" title="部署日志" size="55%" destroy-on-close append-to-body>
      <log-view v-if="currentId" kind="deploy" :id="currentId" :log-url="deployLogUrl(currentId)" />
    </el-drawer>

    <el-dialog v-model="batchOpen" title="批量部署" width="720px">
      <el-form inline>
        <el-form-item label="目标环境">
          <el-select v-model="batchEnv" style="width: 260px" placeholder="选择环境">
            <el-option v-for="e in environments" :key="e.id" :label="`${e.name} (${e.kind})`" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="batchRows" @selection-change="rows => (batchSelection = rows)" max-height="360" size="small">
        <el-table-column type="selection" width="42" />
        <el-table-column label="项目" min-width="150">
          <template #="{ row }">{{ row.project.name }}<div class="muted mono">{{ row.project.code }}</div></template>
        </el-table-column>
        <el-table-column label="部署制品" min-width="260">
          <template #="{ row }">
            <el-select v-model="row.artifactId" size="small" style="width: 100%" placeholder="该环境下暂无可用制品">
              <el-option v-for="a in row.artifacts" :key="a.id" :label="`${a.type} · ${a.version}`" :value="a.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <div class="muted" style="margin-top: 10px">
        按勾选顺序依次部署到所选环境；某个项目失败会立即中止后续部署。各项目默认选中其最新可用制品。
      </div>
      <template #footer>
        <el-button @click="batchOpen = false">取消</el-button>
        <el-button type="primary" :loading="batchRunning" @click="doBatch">开始批量部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  listProjects, listEnvironments, listArtifacts, listDeployments,
  startDeploy, batchDeploy, rollbackDeploy, rollbackPreview, cancelDeploy, deployLogUrl,
  approveDeploy, rejectDeploy
} from '../api/api'
import StatusTag from '../components/StatusTag.vue'
import LogView from '../components/LogView.vue'
import { Operation } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
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

/** 每个项目的制品缓存：全局列表只取前 100 条，老项目的旧制品必须按项目拉取才能出现在下拉里 */
const projectArtifacts = ref<Record<string, any[]>>({})
async function loadProjectArtifacts(pid: string) {
  if (!pid || projectArtifacts.value[pid]) return
  try {
    const p = await listArtifacts(pid, null, 0, 50)
    projectArtifacts.value[pid] = p.content || []
  } catch (e) { /* 下拉回退到全局列表 */ }
}

const deployableArtifacts = computed(() => {
  const env = environments.value.find(e => e.id === dEnv.value)
  const allowed: Record<string, string[]> = { SSH: ['TAR', 'JAR'], DOCKER: ['IMAGE'], K8S: ['CHART'] }
  const kinds = env ? allowed[env.kind] : ['TAR', 'JAR', 'IMAGE', 'CHART']
  const pool = projectArtifacts.value[dProject.value] ?? artifacts.value
  return pool.filter(a => a.project?.id === dProject.value && kinds.includes(a.type))
})

function onProjectChange() {
  dArtifact.value = ''
  loadProjectArtifacts(dProject.value)
}

const resultSummary = (row: any) => {
  const r = row.result || {}
  const parts: string[] = []
  if (r.revision) parts.push('rev ' + r.revision)
  if (r.healthy === true) parts.push('健康✓')
  if (r.skipped) parts.push('未配置健康检查')
  if (row.message) parts.push(row.message)
  return parts.join(' · ') || '-'
}

/** silent=true 供轮询使用：不触动表格 loading，避免部署期间每 5 秒闪烁遮罩 */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const p = await listDeployments(projectId.value, null, page.value, 20)
    rows.value = p.content || []
    total.value = p.totalElements || 0
  } finally {
    if (!silent) loading.value = false
  }
}

async function doDeploy() {
  deploying.value = true
  try {
    const dep = await startDeploy(dProject.value, dEnv.value, dArtifact.value)
    if (dep.status === 'PENDING_APPROVAL') {
      ElMessage.success('部署申请已提交，等待管理员审批')
    } else {
      ElMessage.success('部署任务已提交')
    }
    page.value = 0
    await load()
    if (dep.status !== 'PENDING_APPROVAL') openLog(dep)
  } catch (e) {}
  deploying.value = false
}

async function rollback(row: any) {
  const preview = await rollbackPreview(row.id)
  if (preview.none) {
    ElMessage.warning('该部署之前没有可回滚的成功版本')
    return
  }
  const ok = await ElMessageBox.confirm(
    `将把「${row.project?.name}」在「${row.environment?.name}」回滚到版本 ${preview.version}（${fmt(preview.deployedAt)} 由 ${preview.operator || '-'} 部署），确认？`,
    '回滚确认', { type: 'warning' }).then(() => true).catch(() => false)
  if (!ok) return
  const dep = await rollbackDeploy(row.id)
  ElMessage.success(row.status === 'PENDING_APPROVAL' ? '回滚申请已提交，等待审批' : '回滚任务已提交')
  await load()
  if (dep.status !== 'PENDING_APPROVAL') openLog(dep)
}

async function approve(row: any) {
  const ok = await ElMessageBox.confirm(
    `批准「${row.project?.name}」部署版本 ${row.artifact?.version} 到「${row.environment?.name}」？`,
    '审批确认', { type: 'warning' }).then(() => true).catch(() => false)
  if (!ok) return
  await approveDeploy(row.id)
  ElMessage.success('已批准，部署进入执行队列')
  await load()
}

async function reject(row: any) {
  const { value } = await ElMessageBox.prompt('拒绝原因（可选）', '拒绝部署', {
    inputPlaceholder: '如：版本不对，等下个构建', type: 'error'
  }).catch(() => ({ value: null as string | null }))
  if (value === null) return
  await rejectDeploy(row.id, value)
  ElMessage.success('已拒绝')
  await load()
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
    try {
      await load(true)
      const row = rows.value.find(r => r.id === id)
      if (!row || row.status !== 'RUNNING') clearInterval(timer)
    } catch (e) {
      clearInterval(timer) // 后端不可达时停止轮询，避免错误风暴
    }
  }, 5000)
}

// 日志抽屉关闭后停止轮询
watch(drawer, v => { if (!v) clearInterval(timer) })

// ---------- 批量部署 ----------
const batchOpen = ref(false)
const batchEnv = ref('')
const batchRows = ref<any[]>([])
const batchSelection = ref<any[]>([])
const batchRunning = ref(false)

function allowedFor(kind: string | undefined) {
  const allowed: Record<string, string[]> = { SSH: ['TAR', 'JAR'], DOCKER: ['IMAGE'], K8S: ['CHART'] }
  return kind ? allowed[kind] : ['TAR', 'JAR', 'IMAGE', 'CHART']
}

function openBatch() {
  batchEnv.value = dEnv.value || ''
  batchSelection.value = []
  batchOpen.value = true
  refreshBatchRows()
  // 并发补齐各项目的制品列表（全局快照只有前 100 条，老项目旧制品会缺失）
  Promise.all(projects.value.map(p => loadProjectArtifacts(p.id))).then(refreshBatchRows)
}

/** 按当前目标环境重算各项目的可用制品与默认选中 */
function refreshBatchRows() {
  const kind = environments.value.find(e => e.id === batchEnv.value)?.kind
  batchRows.value = projects.value.map(p => {
    const pool = projectArtifacts.value[p.id] ?? artifacts.value
    const arts = pool.filter(a => a.project?.id === p.id && allowedFor(kind).includes(a.type))
    return { project: p, artifacts: arts, artifactId: arts[0]?.id || '' }
  })
}

// 对话框内切换目标环境后重算制品候选，否则 SSH 制品会被提交到 K8S 环境
watch(batchEnv, () => { if (batchOpen.value) refreshBatchRows() })

async function doBatch() {
  if (!batchEnv.value) { ElMessage.warning('请选择目标环境'); return }
  const items = batchSelection.value
    .filter(r => r.artifactId)
    .map(r => ({ projectId: r.project.id, artifactId: r.artifactId }))
  if (!items.length) { ElMessage.warning('请勾选项目并选择制品'); return }
  batchRunning.value = true
  try {
    await batchDeploy(batchEnv.value, items)
    ElMessage.success(`已提交 ${items.length} 个部署，按顺序执行`)
    batchOpen.value = false
    page.value = 0
    await load()
  } catch (e) {}
  batchRunning.value = false
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
    if (dProject.value) loadProjectArtifacts(dProject.value)
  }
})
onUnmounted(() => clearInterval(timer))
</script>
