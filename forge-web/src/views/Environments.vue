<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>环境管理</h2>
        <p class="desc">云服务器（SSH）/ Docker 主机 / K8s 集群的统一接入</p>
      </div>
      <div class="actions">
        <el-button type="primary" :icon="Plus" @click="openEdit(null)">新建环境</el-button>
      </div>
    </div>
    <el-table :data="list">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column label="类型" width="100"><template #="{ row }"><el-tag size="small" :type="{ SSH: '', DOCKER: 'warning', K8S: 'success' }[row.kind]">{{ row.kind }}</el-tag></template></el-table-column>
      <el-table-column label="连接信息" min-width="260">
        <template #="{ row }"><span class="mono muted">{{ connSummary(row) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #="{ row }">
          <el-button size="small" type="success" plain :loading="testing === row.id" @click="test(row)">测试</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑环境' : '新建环境'" width="620px">
      <el-form :model="form" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.kind" style="width: 100%" :disabled="!!form.id">
                <el-option label="云服务器（SSH）" value="SSH" />
                <el-option label="Docker 主机" value="DOCKER" />
                <el-option label="K8s 集群" value="K8S" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.kind !== 'K8S'">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="主机"><el-input v-model="host" :placeholder="form.kind === 'DOCKER' ? '留空=本机 docker' : ''" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="端口"><el-input v-model="port" placeholder="22" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="用户名"><el-input v-model="username" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="SSH 凭据">
                <el-select v-model="credentialId" clearable style="width: 100%">
                  <el-option v-for="c in creds" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-if="form.kind === 'SSH'">
          <el-form-item label="部署目录"><el-input v-model="deployDir" placeholder="/opt/apps" /></el-form-item>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="保留版本数"><el-input v-model="keepReleases" placeholder="5" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="systemd 服务"><el-input v-model="systemdService" placeholder="可选，如 myapp.service" /></el-form-item></el-col>
          </el-row>
        </template>

        <template v-if="form.kind === 'DOCKER'">
          <el-form-item label="compose 目录"><el-input v-model="composeDir" placeholder="/opt/forge" /></el-form-item>
        </template>

        <template v-if="form.kind === 'K8S'">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="kubeconfig">
                <el-select v-model="kubeconfigCredentialId" clearable style="width: 100%">
                  <el-option v-for="c in creds.filter(c => c.kind === 'KUBECONFIG')" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="namespace"><el-input v-model="namespace" placeholder="default" /></el-form-item></el-col>
          </el-row>
        </template>

        <el-form-item label="环境变量">
          <el-input v-model="varsText" type="textarea" :rows="3" placeholder="KEY=VALUE 每行一条&#10;java.opts=-Xms512m&#10;helm.replicaCount=2" />
          <div class="muted">java.opts/server.port 用于 SSH 部署；helm.* 作为 --set 注入 K8s；其余注入容器环境</div>
        </el-form-item>
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
import { listEnvironments, createEnvironment, updateEnvironment, deleteEnvironment, testEnvironment, listCredentials } from '../api/api'

const list = ref<any[]>([])
const creds = ref<any[]>([])
const dialog = ref(false)
const form = ref<any>({})
const testing = ref('')

const host = ref(''); const port = ref(''); const username = ref(''); const credentialId = ref('')
const deployDir = ref(''); const keepReleases = ref(''); const systemdService = ref('')
const composeDir = ref(''); const kubeconfigCredentialId = ref(''); const namespace = ref('')
const varsText = ref('')

const connSummary = (row: any) => {
  const c = row.connConfig || {}
  if (row.kind === 'K8S') return `ns=${c.namespace || 'default'}`
  return [c.host || '(local)', c.port, c.username, c.deployDir || c.composeDir].filter(Boolean).join(' · ')
}

async function load() {
  list.value = await listEnvironments()
  creds.value = await listCredentials()
}

function openEdit(row: any) {
  form.value = row ? { ...row } : { kind: 'SSH', vars: {} }
  const c = row?.connConfig || {}
  host.value = c.host || ''; port.value = c.port ?? ''; username.value = c.username || ''
  credentialId.value = c.credentialId || ''; deployDir.value = c.deployDir || ''
  keepReleases.value = c.keepReleases ?? ''; systemdService.value = c.systemdService || ''
  composeDir.value = c.composeDir || ''; kubeconfigCredentialId.value = c.kubeconfigCredentialId || ''
  namespace.value = c.namespace || ''
  const vars = row?.vars || {}
  varsText.value = Object.entries(vars).map(([k, v]) => `${k}=${v}`).join('\n')
  dialog.value = true
}

function composeForm() {
  const c: any = {}
  if (form.value.kind !== 'K8S') {
    if (host.value) c.host = host.value
    if (port.value) c.port = Number(port.value) || port.value
    if (username.value) c.username = username.value
    if (credentialId.value) c.credentialId = credentialId.value
  }
  if (form.value.kind === 'SSH') {
    c.deployDir = deployDir.value
    if (keepReleases.value) c.keepReleases = Number(keepReleases.value) || keepReleases.value
    if (systemdService.value) c.systemdService = systemdService.value
  }
  if (form.value.kind === 'DOCKER') c.composeDir = composeDir.value
  if (form.value.kind === 'K8S') {
    c.kubeconfigCredentialId = kubeconfigCredentialId.value
    c.namespace = namespace.value || 'default'
  }
  const vars: any = {}
  varsText.value.split('\n').filter(l => l.includes('=')).forEach(l => {
    const i = l.indexOf('=')
    vars[l.slice(0, i).trim()] = l.slice(i + 1).trim()
  })
  return { ...form.value, connConfig: c, vars }
}

async function save() {
  try {
    if (form.value.id) await updateEnvironment(form.value.id, composeForm())
    else await createEnvironment(composeForm())
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {}
}

async function test(row: any) {
  testing.value = row.id
  try {
    const r = await testEnvironment(row.id)
    if (r.ok) ElMessage.success('连通正常: ' + (r.detail || '').split('\n')[0])
    else ElMessage.error('连接失败: ' + (r.detail || ''))
  } catch (e) {}
  testing.value = ''
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除环境「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteEnvironment(row.id)
  load()
}

onMounted(load)
</script>
