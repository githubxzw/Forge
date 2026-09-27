<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>凭据管理</h2>
        <p class="desc">AES-256-GCM 加密存储，保存后不可查看原文</p>
      </div>
      <div class="actions">
        <el-button type="primary" :icon="Plus" @click="openEdit(null)">新建凭据</el-button>
      </div>
    </div>
    <el-table :data="list">
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column label="类型" width="140">
        <template #="{ row }"><el-tag size="small">{{ kindLabel(row.kind) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #="{ row }"><span class="muted">{{ row.createdAt?.replace('T', ' ').slice(0, 19) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑凭据（留空保留原值）' : '新建凭据'" width="560px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.kind" style="width: 100%">
                <el-option label="SSH 私钥" value="SSH_KEY" />
                <el-option label="密码" value="PASSWORD" />
                <el-option label="kubeconfig" value="KUBECONFIG" />
                <el-option label="文本" value="TEXT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.kind === 'SSH_KEY'" label="私钥">
          <el-input v-model="privateKey" type="textarea" :rows="8" placeholder="-----BEGIN OPENSSH PRIVATE KEY-----" />
        </el-form-item>
        <el-form-item v-if="form.kind === 'SSH_KEY'" label="私钥口令">
          <el-input v-model="passphrase" type="password" show-password placeholder="无则留空" />
        </el-form-item>
        <el-form-item v-if="form.kind === 'PASSWORD'" label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <el-form-item v-if="form.kind === 'KUBECONFIG'" label="kubeconfig">
          <el-input v-model="kubeconfig" type="textarea" :rows="10" placeholder="粘贴 kubeconfig YAML" />
        </el-form-item>
        <el-form-item v-if="form.kind === 'TEXT'" label="内容">
          <el-input v-model="text" type="textarea" :rows="3" />
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
import { listCredentials, createCredential, updateCredential, deleteCredential } from '../api/api'

const list = ref<any[]>([])
const dialog = ref(false)
const form = ref<any>({})
const privateKey = ref(''); const passphrase = ref(''); const password = ref(''); const kubeconfig = ref(''); const text = ref('')

const kindLabel = (k: string) => ({ SSH_KEY: 'SSH 私钥', PASSWORD: '密码', KUBECONFIG: 'kubeconfig', TEXT: '文本' }[k] || k)

async function load() { list.value = await listCredentials() }

function openEdit(row: any) {
  form.value = row ? { ...row } : { kind: 'SSH_KEY' }
  privateKey.value = ''; passphrase.value = ''; password.value = ''; kubeconfig.value = ''; text.value = ''
  dialog.value = true
}

function composeData(): any {
  if (form.value.kind === 'SSH_KEY') {
    return privateKey.value ? { privateKey: privateKey.value, passphrase: passphrase.value } : {}
  }
  if (form.value.kind === 'PASSWORD') return password.value ? { password: password.value } : {}
  if (form.value.kind === 'KUBECONFIG') return kubeconfig.value ? { kubeconfig: kubeconfig.value } : {}
  return text.value ? { value: text.value } : {}
}

async function save() {
  try {
    if (form.value.id) await updateCredential(form.value.id, { name: form.value.name, kind: form.value.kind, data: composeData() })
    else await createCredential({ name: form.value.name, kind: form.value.kind, data: composeData() })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {}
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除凭据「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteCredential(row.id)
  load()
}

onMounted(load)
</script>
