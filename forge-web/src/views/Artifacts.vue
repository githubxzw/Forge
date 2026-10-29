<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>制品库</h2>
        <p class="desc">构建产物的统一存放与分发</p>
      </div>
      <div class="actions">
        <el-button v-if="selection.length" type="danger" plain :icon="Delete" @click="batchRemove">
          删除所选 ({{ selection.length }})
        </el-button>
      </div>
    </div>
    <div class="toolbar">
      <el-select v-model="projectId" placeholder="全部项目" clearable style="width: 200px" @change="() => { page = 0; load() }">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="type" placeholder="全部类型" clearable style="width: 170px" @change="() => { page = 0; load() }">
        <el-option label="前端包 TAR" value="TAR" />
        <el-option label="后端包 JAR" value="JAR" />
        <el-option label="Docker 镜像" value="IMAGE" />
        <el-option label="K8s Chart" value="CHART" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" @selection-change="rows => (selection = rows)">
      <el-table-column type="selection" width="40" />
      <el-table-column label="项目" width="120"><template #="{ row }">{{ row.project?.name }}</template></el-table-column>
      <el-table-column label="类型" width="86"><template #="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
      <el-table-column prop="version" label="版本" min-width="170" show-overflow-tooltip />
      <el-table-column label="位置/镜像" min-width="180" show-overflow-tooltip><template #="{ row }"><span class="mono muted">{{ row.ref }}</span></template></el-table-column>
      <el-table-column label="大小" width="88"><template #="{ row }" class="num">{{ fmtSize(row.size) }}</template></el-table-column>
      <el-table-column label="校验和" width="110">
        <template #="{ row }"><span class="mono muted" :title="row.checksum">{{ row.checksum ? row.checksum.slice(0, 8) + '…' : '-' }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="145"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
      <el-table-column label="操作" width="190">
        <template #="{ row }">
          <el-button size="small" type="primary" text @click="deploy(row)">部署</el-button>
          <el-button v-if="row.type === 'IMAGE'" size="small" text @click="push(row)">推送</el-button>
          <el-button v-if="row.type !== 'IMAGE'" size="small" text @click="download(row)">下载</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="90" description="还没有制品，去项目详情发起一次构建吧" />
      </template>
    </el-table>
    <div style="margin-top: 12px; text-align: right">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="50"
                     :current-page="page + 1" @current-change="pg => { page = pg - 1; load() }" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { listArtifacts, listProjects, deleteArtifact, batchDeleteArtifacts, pushArtifact, artifactDownloadUrl } from '../api/api'

const router = useRouter()
const projects = ref<any[]>([])
const projectId = ref<string | null>(null)
const type = ref<string | null>(null)
const rows = ref<any[]>([])
const selection = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const total = ref(0)

const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')
const fmtSize = (n?: number | null) => {
  if (n == null) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1048576).toFixed(2) + ' MB'
}

async function load() {
  loading.value = true
  try {
    const p = await listArtifacts(projectId.value, type.value, page.value, 50)
    rows.value = p.content || []
    total.value = p.totalElements || 0
  } finally {
    loading.value = false
  }
}

function deploy(row: any) {
  router.push({ path: '/deployments', query: { artifactId: row.id, projectId: row.project?.id } })
}

async function push(row: any) {
  const ok = await ElMessageBox.confirm(`推送镜像 ${row.ref} 到 registry？`, '推送确认')
    .then(() => true).catch(() => false)
  if (!ok) return
  await pushArtifact(row.id)
  ElMessage.success('推送完成')
}

function download(row: any) {
  const token = localStorage.getItem('forge_token') || ''
  fetch(artifactDownloadUrl(row.id), { headers: { Authorization: `Bearer ${token}` } })
    .then(async r => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      return r.blob()
    })
    .then(blob => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.ref?.split('/').pop() || 'artifact'
      a.click()
      URL.revokeObjectURL(url)
    })
    .catch(() => ElMessage.error('制品下载失败（文件可能已被清理）'))
}

async function remove(row: any) {
  const ok = await ElMessageBox.confirm(`确认删除制品 ${row.version}？`, '删除确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  await deleteArtifact(row.id)
  load()
}

async function batchRemove() {
  const ids = selection.value.map(r => r.id)
  const ok = await ElMessageBox.confirm(`确认删除所选 ${ids.length} 个制品？被部署引用的将自动跳过。`, '批量删除', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  const r = await batchDeleteArtifacts(ids)
  ElMessage.success(`已删除 ${r.deleted} 个${r.skipped ? `，${r.skipped} 个因被部署引用跳过` : ''}`)
  load()
}

onMounted(async () => { projects.value = await listProjects(); load() })
</script>
