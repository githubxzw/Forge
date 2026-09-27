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
      <el-table-column label="操作" width="160" fixed="right">
        <template #="{ row }">
          <el-button size="small" @click="$router.push(`/projects/${row.id}`)">详情</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
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
        <el-form-item label="Git 仓库"><el-input v-model="form.repoUrl" placeholder="https://github.com/you/repo.git" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="技术栈"><el-input v-model="form.stack" placeholder="vue/npm 或 maven" /></el-form-item></el-col>
        </el-row>
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
import { listProjects, createProject, updateProject, deleteProject } from '../api/api'

const list = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = ref<any>({})

const buildCommand = ref(''); const outputDir = ref(''); const dockerfilePath = ref('')
const imageName = ref(''); const containerPort = ref(''); const ports = ref(''); const chartPath = ref('')
const healthUrl = ref('')

const typeLabel = (t: string) => ({ FRONTEND: '前端', BACKEND: '后端', FULL: 'FULL' }[t] || t)

async function load() {
  loading.value = true
  list.value = await listProjects()
  loading.value = false
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
  const body: any = { ...form.value, buildConfig: bc }
  if (healthUrl.value) body.healthCheck = { url: healthUrl.value }
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
  await ElMessageBox.confirm(`确认删除项目「${row.name}」？构建/部署历史将一并失效。`, '删除确认', { type: 'warning' })
  await deleteProject(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
