import { http, unwrap } from './http'

// ---------- 认证 ----------
export const login = (username: string, password: string) =>
  unwrap<{ token: string; username: string; role: string }>(http.post('/auth/login', { username, password }))
export const changePassword = (oldPassword: string, newPassword: string) =>
  unwrap(http.post('/auth/password', { oldPassword, newPassword }))
/** 一次性短效 ticket：WS 握手与日志下载等无法带 Authorization 头的通道使用 */
export const fetchTicket = () => unwrap<string>(http.get('/auth/ticket'))

// ---------- API Token（脚本调用 REST API 的长期凭证） ----------
export const listTokens = () => unwrap<any[]>(http.get('/tokens'))
export const createToken = (name: string) => unwrap<any>(http.post('/tokens', { name }))
export const deleteToken = (id: string) => unwrap(http.delete(`/tokens/${id}`))

// ---------- 用户管理（仅 ADMIN） ----------
export const listUsers = () => unwrap<any[]>(http.get('/users'))
export const createUser = (data: { username: string; password: string; role: string }) =>
  unwrap(http.post('/users', data))
export const updateUser = (id: string, data: { role?: string; newPassword?: string }) =>
  unwrap(http.put(`/users/${id}`, data))
export const deleteUser = (id: string) => unwrap(http.delete(`/users/${id}`))

// ---------- 部署审批（仅 ADMIN） ----------
export const approveDeploy = (id: string) => unwrap<any>(http.post(`/deployments/${id}/approve`))
export const rejectDeploy = (id: string, reason: string) =>
  unwrap<any>(http.post(`/deployments/${id}/reject`, { reason }))

// ---------- 持续监控 ----------
export const monitorStatus = () => unwrap<any[]>(http.get('/monitor/status'))

// ---------- 项目 ----------
export const listProjects = () => unwrap<any[]>(http.get('/projects'))
export const getProject = (id: string) => unwrap(http.get(`/projects/${id}`))
export const createProject = (data: any) => unwrap(http.post('/projects', data))
export const updateProject = (id: string, data: any) => unwrap(http.put(`/projects/${id}`, data))
export const deleteProject = (id: string) => unwrap(http.delete(`/projects/${id}`))
export const regenerateWebhookSecret = (id: string) => unwrap<string>(http.put(`/projects/${id}/webhook-secret`))

// ---------- 环境 ----------
export const listEnvironments = () => unwrap<any[]>(http.get('/environments'))
export const createEnvironment = (data: any) => unwrap(http.post('/environments', data))
export const updateEnvironment = (id: string, data: any) => unwrap(http.put(`/environments/${id}`, data))
export const deleteEnvironment = (id: string) => unwrap(http.delete(`/environments/${id}`))
export const testEnvironment = (id: string) => unwrap(http.post(`/environments/${id}/test`))

// ---------- 凭据 ----------
export const listCredentials = () => unwrap<any[]>(http.get('/credentials'))
export const createCredential = (data: any) => unwrap(http.post('/credentials', data))
export const updateCredential = (id: string, data: any) => unwrap(http.put(`/credentials/${id}`, data))
export const deleteCredential = (id: string) => unwrap(http.delete(`/credentials/${id}`))

// ---------- 构建 ----------
export const startBuild = (projectId: string, branch: string, targets: string[]) =>
  unwrap<any>(http.post('/builds', { projectId, branch, targets }))
export const listBuilds = (projectId: string | null, page = 0, size = 20) =>
  unwrap<any>(http.get('/builds', { params: { projectId: projectId || undefined, page, size } }))
export const getBuild = (id: string) => unwrap<any>(http.get(`/builds/${id}`))
export const cancelBuild = (id: string) => unwrap(http.post(`/builds/${id}/cancel`))
export const buildLogUrl = (id: string) => `/api/builds/${id}/log`

// ---------- 制品 ----------
export const listArtifacts = (projectId: string | null, type: string | null, page = 0, size = 50) =>
  unwrap<any>(http.get('/artifacts', { params: { projectId: projectId || undefined, type: type || undefined, page, size } }))
export const deleteArtifact = (id: string) => unwrap(http.delete(`/artifacts/${id}`))
export const batchDeleteArtifacts = (ids: string[]) =>
  unwrap<{ deleted: number; skipped: number }>(http.post('/artifacts/batch-delete', { ids }))
export const pushArtifact = (id: string) => unwrap(http.post(`/artifacts/${id}/push`))
export const artifactDownloadUrl = (id: string) => `/api/artifacts/${id}/download`

// ---------- 部署 ----------
export const startDeploy = (projectId: string, envId: string, artifactId: string) =>
  unwrap<any>(http.post('/deployments', { projectId, envId, artifactId }))
export const batchDeploy = (envId: string, items: { projectId: string; artifactId: string }[]) =>
  unwrap<any[]>(http.post('/deployments/batch', { envId, items }))
export const rollbackDeploy = (id: string) => unwrap<any>(http.post(`/deployments/${id}/rollback`))
export const rollbackPreview = (id: string) =>
  unwrap<{ none: boolean; version?: string; deployedAt?: string; operator?: string }>(
    http.get(`/deployments/${id}/rollback-preview`))
export const cancelDeploy = (id: string) => unwrap(http.post(`/deployments/${id}/cancel`))
export const listDeployments = (projectId: string | null, envId: string | null, page = 0, size = 20) =>
  unwrap<any>(http.get('/deployments', { params: { projectId: projectId || undefined, envId: envId || undefined, page, size } }))
export const getDeployment = (id: string) => unwrap<any>(http.get(`/deployments/${id}`))
export const deployLogUrl = (id: string) => `/api/deployments/${id}/log`

// ---------- 设置/系统/统计 ----------
export const getSettings = () => unwrap<Record<string, string>>(http.get('/settings'))
export const updateSettings = (values: Record<string, string>) => unwrap(http.put('/settings', values))
export const notifyTest = () => unwrap(http.post('/settings/notify-test'))
export const systemInfo = () => unwrap<any>(http.get('/system/info'))
export const stats = () => unwrap<any>(http.get('/stats'))
