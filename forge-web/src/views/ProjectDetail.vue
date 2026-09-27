<template>
  <div class="page" v-loading="loading">
    <div class="toolbar">
      <el-button :icon="Back" @click="$router.push('/projects')">返回</el-button>
      <span style="font-size: 16px; font-weight: 600">{{ p.name }}</span>
      <el-tag size="small">{{ p.code }}</el-tag>
      <el-tag size="small" type="info">{{ p.type }}</el-tag>
      <span class="muted mono">{{ p.repoUrl }} @ {{ p.defaultBranch }}</span>
      <span style="flex: 1"></span>
      <el-button type="primary" :icon="VideoPlay" @click="buildDialog = true">发起构建</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="构建任务" name="builds">
        <el-table :data="builds" size="small">
          <el-table-column prop="branch" label="分支" width="110" />
          <el-table-column prop="version" label="版本" min-width="200" show-overflow-tooltip />
          <el-table-column label="目标" min-width="180">
            <template #="{ row }"><el-tag v-for="t in row.targets" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="90"><template #="{ row }"><status-tag :status="row.status" /></template></el-table-column>
          <el-table-column label="耗时" width="90"><template #="{ row }">{{ duration(row) }}</template></el-table-column>
          <el-table-column label="时间" width="160"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #="{ row }">
              <el-button size="small" text type="primary" @click="$router.push({ path: '/builds', query: { open: row.id } })">日志</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="制品" name="artifacts">
        <el-table :data="artifacts" size="small">
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="version" label="版本" min-width="200" show-overflow-tooltip />
          <el-table-column prop="ref" label="位置/镜像" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="100"><template #="{ row }">{{ row.size ? (row.size / 1024 / 1024).toFixed(2) + ' MB' : '-' }}</template></el-table-column>
          <el-table-column label="时间" width="160"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="部署历史" name="deploys">
        <el-table :data="deployments" size="small">
          <el-table-column label="环境" width="120"><template #="{ row }">{{ row.environment?.name }} ({{ row.environment?.kind }})</template></el-table-column>
          <el-table-column prop="artifact.version" label="制品版本" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="90"><template #="{ row }"><status-tag :status="row.status" /></template></el-table-column>
          <el-table-column prop="operator" label="操作人" width="100" />
          <el-table-column label="时间" width="160"><template #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="buildDialog" title="发起构建" width="480px">
      <el-form label-width="90px">
        <el-form-item label="分支"><el-input v-model="branch" /></el-form-item>
        <el-form-item label="构建目标">
          <el-checkbox-group v-model="targets">
            <el-checkbox value="FRONTEND_TAR" v-if="p.type !== 'BACKEND'">前端包</el-checkbox>
            <el-checkbox value="BACKEND_JAR" v-if="p.type !== 'FRONTEND'">后端包</el-checkbox>
            <el-checkbox value="IMAGE">Docker 镜像</el-checkbox>
            <el-checkbox value="CHART">K8s Chart</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buildDialog = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="doBuild">开始构建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Back, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getProject, listBuilds, listArtifacts, listDeployments, startBuild } from '../api/api'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const id = route.params.id as string
const p = ref<any>({})
const builds = ref<any[]>([])
const artifacts = ref<any[]>([])
const deployments = ref<any[]>([])
const loading = ref(false)
const tab = ref('builds')
const buildDialog = ref(false)
const branch = ref('')
const targets = ref<string[]>([])
const starting = ref(false)

const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')
const duration = (row: any) => {
  if (!row.startedAt || !row.finishedAt) return '-'
  return Math.max(1, Math.round((new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime()) / 1000)) + 's'
}

async function load() {
  loading.value = true
  p.value = await getProject(id)
  branch.value = p.value.defaultBranch || 'main'
  targets.value = p.value.type === 'FRONTEND' ? ['FRONTEND_TAR'] : p.value.type === 'BACKEND' ? ['BACKEND_JAR'] : ['FRONTEND_TAR', 'BACKEND_JAR']
  builds.value = (await listBuilds(id, 0, 20)).content || []
  artifacts.value = (await listArtifacts(id, null, 0, 50)).content || []
  deployments.value = (await listDeployments(id, null, 0, 20)).content || []
  loading.value = false
}

async function doBuild() {
  if (!targets.value.length) { ElMessage.warning('请选择构建目标'); return }
  starting.value = true
  try {
    await startBuild(id, branch.value, targets.value)
    ElMessage.success('构建任务已提交')
    buildDialog.value = false
    load()
  } catch (e) {}
  starting.value = false
}

onMounted(load)
</script>
