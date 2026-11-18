<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>项目管理</h2>
        <p class="desc">注册 Git 仓库，配置构建目标与产物形态</p>
      </div>
      <div class="actions">
        <el-button type="primary" :icon="Plus" @click="openEdit(null)">新建项目</el-button>
      </div>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column label="项目" min-width="180">
        <template #="{ row }">
          <el-link type="primary" @click="$router.push(`/projects/${row.id}`)">{{ row.name }}</el-link>
          <div class="muted mono">{{ row.code }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #="{ row }"><el-tag size="small">{{ typeLabel(row.type) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="stack" label="技术栈" width="120" />
      <el-table-column prop="repoUrl" label="仓库" min-width="240" show-overflow-tooltip />
      <el-table-column prop="defaultBranch" label="默认分支" width="100" />
      <el-table-column label="操作" width="210">
        <template #="{ row }">
          <el-button size="small" @click="$router.push(`/projects/${row.id}`)">详情</el-button>
          <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="80" description="还没有项目，从注册一个 Git 仓库开始">
          <el-button type="primary" @click="openEdit(null)">创建第一个项目</el-button>
        </el-empty>
      </template>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑项目' : '新建项目'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="编码"><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 order-service" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="前端" value="FRONTEND" />
                <el-option label="后端" value="BACKEND" />
                <el-option label="前后端(FULL)" value="FULL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="默认分支"><el-input v-model="form.defaultBranch" placeholder="main" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">代码仓库</el-divider>
        <el-form-item label="Git 地址">
          <el-input v-model="form.repoUrl" placeholder="https://github.com/you/repo.git" />
          <div class="muted">支持 GitHub / GitLab / Gitee 的 HTTPS 或 SSH 地址，也支持构建机本地路径（如 /opt/repos/demo）</div>
        </el-form-item>
        <el-form-item label="拉取凭据">
          <el-select v-model="form.gitCredentialId" clearable placeholder="公开仓库无需选择" style="width: 100%">
            <el-option v-for="c in creds" :key="c.id" :label="`${c.name}（${credKindLabel(c.kind)}）`" :value="c.id" />
          </el-select>
          <div class="muted">私有仓库请先在「凭据管理」创建：HTTPS + Token 用「文本」（存 token）；SSH 地址用「SSH 私钥」</div>
        </el-form-item>
        <el-divider content-position="left">构建配置（可选）</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="构建命令"><el-input v-model="buildCommand" placeholder="默认按技术栈自动" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产物目录"><el-input v-model="outputDir" placeholder="前端默认 dist" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="Dockerfile"><el-input v-model="dockerfilePath" placeholder="仓库内路径，空=自动" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="镜像名"><el-input v-model="imageName" placeholder="默认用项目编码" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="容器端口"><el-input v-model="containerPort" placeholder="8080" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="端口映射"><el-input v-model="ports" placeholder="8080:8080, 9090:9090" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="Chart 路径"><el-input v-model="chartPath" placeholder="仓库内 chart 目录，空=使用内置模板" /></el-form-item>
        <el-form-item label="Ingress 域名"><el-input v-model="ingressHost" placeholder="内置 chart 专用，如 app.example.com；空=不创建 Ingress" /></el-form-item>
        <el-form-item label="最大副本数"><el-input v-model="maxReplicas" style="width: 120px" placeholder="大于副本数即启用 HPA" /></el-form-item>
        <el-form-item label="定时构建"><el-input v-model="cron" placeholder="Cron 表达式，如 0 0 3 * * *（每天 3 点；空=不定时）" /></el-form-item>
        <el-form-item label="Webhook 分支"><el-input v-model="webhookBranches" placeholder="逗号分隔，如 main,release/*；空=仅默认分支" /></el-form-item>
        <el-form-item label="Webhook 目标"><el-input v-model="webhookTargets" placeholder="逗号分隔，如 IMAGE,CHART；空=按项目类型默认" /></el-form-item>
        <el-divider content-position="left">部署健康检查（可选）</el-divider>
        <el-form-item label="检查 URL"><el-input v-model="healthUrl" placeholder="http://host:port/actuator/health" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProjects, createProject, updateProject, deleteProject, listCredentials } from '../api/api'

const list = ref<any[]>([])
const creds = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = ref<any>({})

const credKindLabel = (k: string) => ({ SSH_KEY: 'SSH私钥', PASSWORD: '密码', KUBECONFIG: 'kubeconfig', TEXT: '文本/Token' }[k] || k)

const buildCommand = ref(''); const outputDir = ref(''); const dockerfilePath = ref('')
const imageName = ref(''); const containerPort = ref(''); const ports = ref(''); const chartPath = ref('')
const cron = ref(''); const webhookBranches = ref(''); const webhookTargets = ref('')
const ingressHost = ref(''); const maxReplicas = ref('')
const healthUrl = ref('')

const typeLabel = (t: string) => ({ FRONTEND: '前端', BACKEND: '后端', FULL: 'FULL' }[t] || t)

async function load() {
  loading.value = true
  try {
    list.value = await listProjects()
    creds.value = await listCredentials()
  } finally {
    loading.value = false
  }
}

function openEdit(row: any) {
  form.value = row ? { ...row } : { type: 'FRONTEND', defaultBranch: 'main' }
  const bc = row?.buildConfig || {}
  buildCommand.value = bc.buildCommand || ''
  outputDir.value = bc.outputDir || ''
  dockerfilePath.value = bc.dockerfilePath || ''
  imageName.value = bc.imageName || ''
  containerPort.value = bc.containerPort ?? ''
  ports.value = Array.isArray(bc.ports) ? bc.ports.join(', ') : (bc.ports || '')
  chartPath.value = bc.chartPath || ''
  cron.value = bc.cron || ''
  webhookBranches.value = Array.isArray(bc.webhookBranches) ? bc.webhookBranches.join(', ') : (bc.webhookBranches || '')
  webhookTargets.value = Array.isArray(bc.webhookTargets) ? bc.webhookTargets.join(', ') : (bc.webhookTargets || '')
  ingressHost.value = bc.ingressHost || ''
  maxReplicas.value = bc.maxReplicas ?? ''
  healthUrl.value = row?.healthCheck?.url || ''
  dialog.value = true
}

function composeForm() {
  const bc: any = {}
  if (buildCommand.value) bc.buildCommand = buildCommand.value
  if (outputDir.value) bc.outputDir = outputDir.value
  if (dockerfilePath.value) bc.dockerfilePath = dockerfilePath.value
  if (imageName.value) bc.imageName = imageName.value
  if (containerPort.value) bc.containerPort = Number(containerPort.value) || containerPort.value
  if (ports.value) bc.ports = ports.value.split(',').map((s: string) => s.trim()).filter(Boolean)
  if (chartPath.value) bc.chartPath = chartPath.value
  if (cron.value.trim()) bc.cron = cron.value.trim()
  if (ingressHost.value.trim()) bc.ingressHost = ingressHost.value.trim()
  if (maxReplicas.value) bc.maxReplicas = Number(maxReplicas.value) || maxReplicas.value
  const wb = webhookBranches.value.split(',').map((s: string) => s.trim()).filter(Boolean)
  if (wb.length) bc.webhookBranches = wb
  const wt = webhookTargets.value.split(',').map((s: string) => s.trim()).filter(Boolean)
  if (wt.length) bc.webhookTargets = wt
  const body: any = { ...form.value, buildConfig: bc }
  // 清空健康检查 URL 时传空对象（后端对 null 字段不更新），HealthChecker 对空 URL 自动跳过
  body.healthCheck = { url: healthUrl.value.trim() }
  return body
}

async function save() {
  try {
    if (form.value.id) await updateProject(form.value.id, composeForm())
    else await createProject(composeForm())
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {}
}

async function remove(row: any) {
  const ok = await ElMessageBox.confirm(
    `确认删除项目「${row.name}」？其构建/部署历史与制品文件将一并删除，不可恢复。`, '删除确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  await deleteProject(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
