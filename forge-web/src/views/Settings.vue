<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <template #header>构建与仓库设置</template>
          <el-form label-width="140px">
            <el-form-item label="镜像仓库"><el-input v-model="s.registry" placeholder="registry.example.com（空=本地镜像名）" /></el-form-item>
            <el-form-item label="仓库用户名"><el-input v-model="s.registryUsername" /></el-form-item>
            <el-form-item label="仓库密码">
              <el-input v-model="s.registryPassword" type="password" show-password placeholder="已配置则显示掩码，输入新值即更新" />
            </el-form-item>
            <el-form-item label="npm 镜像源"><el-input v-model="s.npmRegistry" placeholder="https://registry.npmjs.org" /></el-form-item>
            <el-form-item label="Maven 镜像"><el-input v-model="s.mavenMirror" placeholder="https://maven.aliyun.com/repository/public" /></el-form-item>
            <el-form-item label="构建后自动推镜像">
              <el-switch v-model="s.dockerAutoPush" active-value="true" inactive-value="false" />
            </el-form-item>
            <el-form-item label="后端基础镜像"><el-input v-model="s.dockerBaseJre" /></el-form-item>
            <el-form-item label="前端基础镜像"><el-input v-model="s.dockerBaseNginx" /></el-form-item>
            <el-form-item label="保留 release 数"><el-input v-model="s.keepReleases" style="width: 120px" /></el-form-item>
            <el-form-item>
              <el-button type="primary" @click="save">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header>修改密码</template>
          <el-form label-width="140px">
            <el-form-item label="原密码"><el-input v-model="oldPwd" type="password" show-password /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="newPwd" type="password" show-password /></el-form-item>
            <el-form-item><el-button @click="changePwd">修改</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header>构建机工具链探测</template>
          <div class="muted" style="margin-bottom: 10px">数据目录：{{ info.dataDir }}</div>
          <el-table :data="toolRows" size="small">
            <el-table-column prop="name" label="工具" width="110" />
            <el-table-column label="可用" width="90">
              <template #="{ row }">
                <el-tag size="small" :type="row.available ? 'success' : 'danger'">{{ row.available ? '可用' : '缺失' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" show-overflow-tooltip />
          </el-table>
          <div class="muted" style="margin-top: 10px">
            说明：git 用于代码检出；docker 用于镜像构建；helm/kubectl 用于 K8s 部署；node/npm/pnpm/mvn/gradle/tar 用于构建。
            SSH 部署只依赖网络连通性，无需上述工具。
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateSettings, systemInfo, changePassword } from '../api/api'

const s = ref<Record<string, string>>({})
const info = ref<any>({})
const oldPwd = ref('')
const newPwd = ref('')

const toolRows = computed(() => {
  const tools = info.value.tools || {}
  return Object.entries(tools).map(([name, t]: [string, any]) => ({ name, available: t?.available, version: t?.version || '' }))
})

async function load() {
  s.value = await getSettings()
  info.value = await systemInfo()
}

async function save() {
  await updateSettings(s.value)
  ElMessage.success('已保存')
  load()
}

async function changePwd() {
  await changePassword(oldPwd.value, newPwd.value)
  ElMessage.success('密码已修改')
  oldPwd.value = newPwd.value = ''
}

onMounted(load)
</script>
