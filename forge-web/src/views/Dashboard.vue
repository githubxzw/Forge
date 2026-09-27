<template>
  <div class="page">
    <el-row :gutter="14">
      <el-col :span="6"><el-card class="stat-card"><div class="num">{{ s.projects ?? '-' }}</div><div class="label">项目</div></el-card></el-col>
      <el-col :span="6"><el-card class="stat-card"><div class="num">{{ s.builds ?? '-' }}</div><div class="label">构建任务</div></el-card></el-col>
      <el-col :span="6"><el-card class="stat-card"><div class="num">{{ s.artifacts ?? '-' }}</div><div class="label">制品</div></el-card></el-col>
      <el-col :span="6"><el-card class="stat-card"><div class="num">{{ s.deployments ?? '-' }}</div><div class="label">部署记录</div></el-card></el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card>
          <template #header>最近构建</template>
          <el-table :data="s.recentBuilds || []" size="small">
            <el-table-column label="项目" #="{ row }">{{ row.project?.name }}</el-table-column>
            <el-table-column prop="branch" label="分支" width="110" />
            <el-table-column prop="version" label="版本" show-overflow-tooltip />
            <el-table-column label="状态" width="90" #="{ row }"><status-tag :status="row.status" /></el-table-column>
            <el-table-column label="时间" width="160" #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>最近部署</template>
          <el-table :data="s.recentDeployments || []" size="small">
            <el-table-column label="项目" #="{ row }">{{ row.project?.name }}</el-table-column>
            <el-table-column label="环境" width="90" #="{ row }">{{ row.environment?.name }}</el-table-column>
            <el-table-column prop="artifact.version" label="制品版本" show-overflow-tooltip />
            <el-table-column label="状态" width="90" #="{ row }"><status-tag :status="row.status" /></el-table-column>
            <el-table-column label="时间" width="160" #="{ row }"><span class="muted">{{ fmt(row.createdAt) }}</span></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { stats } from '../api/api'
import dayjs from 'dayjs'
import StatusTag from '../components/StatusTag.vue'

const s = ref<any>({})
onMounted(async () => { s.value = await stats() })
const fmt = (t: string) => (t ? dayjs(t).format('MM-DD HH:mm:ss') : '-')
</script>
