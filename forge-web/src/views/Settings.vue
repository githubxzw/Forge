<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>系统设置</h2>
        <p class="desc">镜像仓库、构建镜像源与工具链状态</p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">
          {{ dirty ? '保存修改' : '无待保存修改' }}
        </el-button>
      </div>
    </div>
    <el-row v-if="auth.isAdmin" :gutter="16">
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
            <el-form-item label="日志保留天数"><el-input v-model="s.keepLogDays" style="width: 120px" placeholder="0=永久保留" /></el-form-item>
            <el-form-item label="Git 浅克隆">
              <el-switch v-model="s.gitShallow" active-value="true" inactive-value="false" />
              <div class="muted">clone --depth 1 加速检出；构建依赖 git 历史时关闭</div>
            </el-form-item>
            <el-form-item label="镜像 GC">
              <el-switch v-model="s.dockerImageGc" active-value="true" inactive-value="false" />
              <div class="muted">每日 03:40 清理未被制品引用的本地镜像与悬挂层</div>
            </el-form-item>
            <el-divider content-position="left">结果通知</el-divider>
            <el-form-item label="通知渠道">
              <el-select v-model="s.notifyType" style="width: 200px">
                <el-option label="不通知" value="none" />
                <el-option label="钉钉机器人" value="dingtalk" />
                <el-option label="企业微信机器人" value="wecom" />
                <el-option label="飞书机器人" value="feishu" />
                <el-option label="Slack 机器人" value="slack" />
                <el-option label="邮件" value="email" />
                <el-option label="通用 JSON" value="raw" />
              </el-select>
              <div class="muted">构建/部署失败或成功后推送；发送失败不影响任务本身</div>
            </el-form-item>
            <el-form-item label="构建也通知">
              <el-switch v-model="s.notifyBuild" active-value="true" inactive-value="false" />
              <span class="muted" style="margin-left: 8px">关闭则只通知部署结果</span>
            </el-form-item>
            <el-form-item label="健康监控间隔">
              <el-input v-model="s.monitorIntervalMin" style="width: 120px" placeholder="分钟" />
              <div class="muted">按项目健康检查配置持续探测已部署服务，异常/恢复时推送通知；0=关闭</div>
            </el-form-item>
            <template v-if="s.notifyType !== 'email'">
              <el-form-item label="Webhook 地址">
                <el-input v-model="s.notifyWebhook" placeholder="https://oapi.dingtalk.com/robot/send?access_token=... 或 Slack hooks.slack.com/services/..." />
              </el-form-item>
              <el-form-item>
                <el-button :loading="testing" :disabled="s.notifyType === 'none' || !s.notifyWebhook" @click="sendTest">
                  发送测试通知
                </el-button>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="SMTP 主机"><el-input v-model="s.notifyEmailHost" placeholder="smtp.example.com" style="width: 260px" /></el-form-item>
              <el-form-item label="SMTP 端口"><el-input v-model="s.notifyEmailPort" style="width: 120px" placeholder="465" /></el-form-item>
              <el-form-item label="加密方式">
                <el-select v-model="s.notifyEmailSsl" style="width: 160px">
                  <el-option label="SSL (465)" value="true" />
                  <el-option label="STARTTLS (587)" value="false" />
                </el-select>
              </el-form-item>
              <el-form-item label="认证用户"><el-input v-model="s.notifyEmailUser" style="width: 260px" /></el-form-item>
              <el-form-item label="认证密码">
                <el-input v-model="s.notifyEmailPass" type="password" show-password placeholder="已配置则显示掩码，输入新值即更新" />
              </el-form-item>
              <el-form-item label="发件人"><el-input v-model="s.notifyEmailFrom" placeholder="forge@example.com" style="width: 260px" /></el-form-item>
              <el-form-item label="收件人"><el-input v-model="s.notifyEmailTo" placeholder="a@example.com, b@example.com" /></el-form-item>
              <el-form-item>
                <el-button :loading="testing" :disabled="!s.notifyEmailHost || !s.notifyEmailTo" @click="sendTest">
                  发送测试邮件
                </el-button>
              </el-form-item>
            </template>
          </el-form>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header>用户管理</template>
          <div class="muted" style="margin-bottom: 10px">
            管理员：全部权限；操作员：构建/部署/回滚日常操作；观察者：只读。审批由管理员完成。
          </div>
          <el-button size="small" type="primary" @click="userDialog = true">新建用户</el-button>
          <el-table :data="users" size="small" style="margin-top: 10px">
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column label="角色" width="140">
              <template #="{ row }">
                <el-select :model-value="row.role" size="small" style="width: 110px" @update:model-value="(v: string) => changeRole(row, v)">
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="操作员" value="OPERATOR" />
                  <el-option label="观察者" value="VIEWER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #="{ row }"><span class="muted">{{ fmtTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column width="190">
              <template #="{ row }">
                <el-button link size="small" @click="resetPassword(row)">重置密码</el-button>
                <el-button link type="danger" size="small" :disabled="row.username === auth.username" @click="removeUser(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
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

        <el-card style="margin-top: 16px">
          <template #header>API Token</template>
          <div class="muted" style="margin-bottom: 10px">
            脚本/CI 调用 REST API 的长期凭证：请求头 <code>Authorization: Token &lt;令牌&gt;</code>。
            明文仅创建时显示一次，吊销立即生效。用法见 docs/api.md。
          </div>
          <el-button size="small" type="primary" @click="newTokenDialog = true">新建令牌</el-button>
          <el-table :data="tokens" size="small" style="margin-top: 10px" empty-text="暂无令牌">
            <el-table-column prop="name" label="名称" width="130" />
            <el-table-column prop="prefix" label="前缀" width="150" />
            <el-table-column label="最近使用" width="160">
              <template #="{ row }"><span class="muted">{{ row.lastUsedAt ? fmtTime(row.lastUsedAt) : '从未' }}</span></template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #="{ row }"><span class="muted">{{ fmtTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column width="80">
              <template #="{ row }"><el-button link type="danger" @click="revokeToken(row)">吊销</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 修改密码是自助操作，所有角色可用（非管理员只看到这张卡） -->
    <el-card v-if="!auth.isAdmin">
      <template #header>修改密码</template>
      <el-form label-width="140px">
        <el-form-item label="原密码"><el-input v-model="oldPwd" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="newPwd" type="password" show-password /></el-form-item>
        <el-form-item><el-button @click="changePwd">修改</el-button></el-form-item>
      </el-form>
      <div class="muted">系统设置、用户与令牌管理需要管理员权限，如需调整请联系管理员。</div>
    </el-card>

    <el-dialog v-model="userDialog" title="新建用户" width="480px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="newUserName" placeholder="如 zhangsan" @keyup.enter="doCreateUser" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="newUserPwd" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="newUserRole" style="width: 160px">
            <el-option label="操作员" value="OPERATOR" />
            <el-option label="观察者" value="VIEWER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingUser" @click="doCreateUser">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="newTokenDialog" title="新建 API Token" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="newTokenName" placeholder="如 ci-script" @keyup.enter="doCreateToken" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newTokenDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreateToken">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tokenDialog" title="令牌已创建（仅显示一次）" width="580px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="请立即复制保存，关闭后无法再次查看" style="margin-bottom: 10px" />
      <el-input :model-value="createdToken" readonly type="textarea" :rows="2" />
      <template #footer>
        <el-button @click="copyToken">复制</el-button>
        <el-button type="primary" @click="tokenDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSettings, updateSettings, systemInfo, changePassword, notifyTest, listTokens, createToken, deleteToken, listUsers, createUser, updateUser, deleteUser } from '../api/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const s = ref<Record<string, string>>({})
const info = ref<any>({})
const oldPwd = ref('')
const newPwd = ref('')
const testing = ref(false)
const saving = ref(false)
// 与最近一次加载/保存的快照比对，驱动顶部保存按钮状态
const savedSnapshot = ref('')

// ---------- API Token ----------
const tokens = ref<any[]>([])
const newTokenDialog = ref(false)
const newTokenName = ref('')
const creating = ref(false)
const tokenDialog = ref(false)
const createdToken = ref('')
const fmtTime = (v: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')

async function loadTokens() {
  tokens.value = await listTokens()
}

async function doCreateToken() {
  if (!newTokenName.value.trim()) { ElMessage.warning('请填写令牌名称'); return }
  creating.value = true
  try {
    const created = await createToken(newTokenName.value.trim())
    createdToken.value = created.prefix // 创建接口在 prefix 字段一次性带回完整明文
    newTokenDialog.value = false
    tokenDialog.value = true
    newTokenName.value = ''
    loadTokens()
  } catch (e) {}
  creating.value = false
}

async function revokeToken(row: any) {
  const ok = await ElMessageBox.confirm(`确认吊销令牌「${row.name}」？使用它的脚本将立即失效。`, '吊销确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  await deleteToken(row.id)
  ElMessage.success('已吊销')
  loadTokens()
}

async function copyToken() {
  await navigator.clipboard.writeText(createdToken.value)
  ElMessage.success('已复制到剪贴板')
}

// ---------- 用户管理（仅 ADMIN 可见本卡片；接口侧同样受 ADMIN 限制） ----------
const users = ref<any[]>([])
const userDialog = ref(false)
const newUserName = ref('')
const newUserPwd = ref('')
const newUserRole = ref('OPERATOR')
const creatingUser = ref(false)

async function loadUsers() {
  if (!auth.isAdmin) return
  users.value = await listUsers()
}

async function doCreateUser() {
  if (!newUserName.value.trim() || !newUserPwd.value) { ElMessage.warning('请填写用户名与密码'); return }
  creatingUser.value = true
  try {
    await createUser({ username: newUserName.value.trim(), password: newUserPwd.value, role: newUserRole.value })
    ElMessage.success('用户已创建')
    userDialog.value = false
    newUserName.value = ''; newUserPwd.value = ''
    loadUsers()
  } catch (e) {}
  creatingUser.value = false
}

async function changeRole(row: any, role: string) {
  try {
    await updateUser(row.id, { role })
    ElMessage.success(`已将 ${row.username} 调整为${role === 'ADMIN' ? '管理员' : role === 'OPERATOR' ? '操作员' : '观察者'}`)
    loadUsers()
  } catch (e) { loadUsers() }
}

async function resetPassword(row: any) {
  const { value } = await ElMessageBox.prompt(`为 ${row.username} 设置新密码（至少 6 位）`, '重置密码', {
    inputType: 'password', inputPattern: /^.{6,}$/, inputErrorMessage: '密码长度至少 6 位'
  }).catch(() => ({ value: null as string | null }))
  if (value === null) return
  await updateUser(row.id, { newPassword: value })
  ElMessage.success('密码已重置，该用户的登录态已全部失效')
}

async function removeUser(row: any) {
  const ok = await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  await deleteUser(row.id)
  ElMessage.success('已删除')
  loadUsers()
}

async function sendTest() {
  testing.value = true
  try {
    await notifyTest()
    ElMessage.success('测试通知已发送，请到群里查看')
  } catch (e) {}
  testing.value = false
}

const toolRows = computed(() => {
  const tools = info.value.tools || {}
  return Object.entries(tools).map(([name, t]: [string, any]) => ({ name, available: t?.available, version: t?.version || '' }))
})

const dirty = computed(() => JSON.stringify(s.value) !== savedSnapshot.value)

async function load() {
  if (!auth.isAdmin) return // 非管理员只保留改密码卡片，管理接口会 403
  s.value = await getSettings()
  savedSnapshot.value = JSON.stringify(s.value)
  info.value = await systemInfo()
  loadTokens()
  loadUsers()
}

async function save() {
  saving.value = true
  try {
    await updateSettings(s.value)
    savedSnapshot.value = JSON.stringify(s.value)
    ElMessage.success('已保存')
    load()
  } catch (e) { /* 错误已弹 */ }
  saving.value = false
}

async function changePwd() {
  await changePassword(oldPwd.value, newPwd.value)
  ElMessage.success('密码已修改')
  oldPwd.value = newPwd.value = ''
}

onMounted(load)
</script>
