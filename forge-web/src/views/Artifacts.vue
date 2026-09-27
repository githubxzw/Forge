<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="projectId" placeholder="全部项目" clearable style="width: 220px" @change="load">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="type" placeholder="全部类型" clearable style="width: 180px" @change="load">
        <el-option label="前端包 TAR" value="TAR" />
        <el-option label="后端包 JAR" value="JAR" />
        <el-option label="Docker 镜像" value="IMAGE" />
        <el-option label="K8s Chart" value="CHART" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column label="项目" width="140"><template #="{ row }">{{ row.project?.name }}</template></el-table-column>
      <el-table-column label="类型" width="110"><template #="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
      <el-table-column prop="version" label="版本" min-width="190" show-overflow-tooltip />
      <el-table-column label="位置/镜像" min-width="220"><template #="{ row }"><span class="mono muted">{{ row.ref }}</span></template></el-table-column>
      <el-table-column label="大小" width="100">
        <template #="{ row }">{{ row.size ? (row.size / 1024 / 1024).toFixed(2) + ' MB' : '-' }}</template>
      </el-table-column>
      <el-table-column label="校验和" width="130">
        <template #="{ row }"><span class="mono muted" :title="row.checksum">{{ row.checksum ? row.checksum.slice(0, 10) + '…' : '-' }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="160"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #="{ row }">
          <el-button size="small" type="primary" text @click="deploy(row)">部署</el-button>
          <el-button v-if="row.type === 'IMAGE'" size="small" text @click="push(row)">推送</el-button>
          <el-button v-if="row.type !== 'IMAGE'" size="small" text @click="download(row)">下载</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
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
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { listArtifacts, listProjects, deleteArtifact, pushArtifact, artifactDownloadUrl } from '../api/api'

const router = useRouter()
const projects = ref<any[]>([])
const projectId = ref<string | null>(null)
const type = ref<string | null>(null)
const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(0)
const total = ref(0)

const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')

async function load() {
  loading.value = true
  const p = await listArtifacts(projectId.value, type.value, page.value, 50)
  rows.value = p.content || []
  total.value = p.totalElements || 0
  loading.value = false
}

function deploy(row: any) {
  router.push({ path: '/deployments', query: { artifactId: row.id, projectId: row.project?.id } })
}

async function push(row: any) {
  await ElMessageBox.confirm(`推送镜像 ${row.ref} 到 registry？`, '推送确认')
  await pushArtifact(row.id)
  ElMessage.success('推送完成')
}

function download(row: any) {
  const token = localStorage.getItem('forge_token') || ''
  // a 标签无法带 header，走 fetch 下载
  fetch(artifactDownloadUrl(row.id), { headers: { Authorization: `Bearer ${token}` } })
    .then(r => r.blob()).then(blob => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.ref.split('/').pop() || 'artifact'
      a.click()
      URL.revokeObjectURL(url)
    })
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除制品 ${row.version}？`, '删除确认', { type: 'warning' })
  await deleteArtifact(row.id)
  load()
}

onMounted(async () => { projects.value = await listProjects(); load() })
</script>
