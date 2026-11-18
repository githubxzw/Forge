# Forge REST API 使用指南

Forge 的全部管理能力都通过 `/api/**` REST 接口提供（控制台本身也调用同一组接口），可用于脚本、CI 流水线或二次集成。

## 认证

| 方式 | 请求头 | 适用场景 |
|---|---|---|
| API Token | `Authorization: Token forge_xxx` 或 `X-API-Token: forge_xxx` | 脚本 / CI / 长期集成（推荐） |
| JWT | `Authorization: Bearer <jwt>` | 网页登录态（24h 过期） |

API Token 在 **系统设置 → API Token** 中创建：明文仅创建时显示一次，库内只存 sha256，吊销立即生效。请求体/响应体均为 JSON，统一包裹为 `{"code": 200, "data": ...}`，错误时 `code` 为 4xx/5xx、`message` 为中文错误描述。

> Webhook（`/api/webhook/{code}`）与登录本身不需要 Token，走各自的 secret 校验。

```bash
TOKEN="forge_xxxxxxxxxxxxxxxx"
BASE="http://forge.example.com:9090"
curl -s -H "Authorization: Token $TOKEN" "$BASE/api/projects"
```

## 核心端点速查

### 项目 / 环境 / 凭据

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/projects` | 项目列表 |
| POST | `/api/projects` | 新建项目（repoUrl/type/buildConfig 等） |
| PUT | `/api/projects/{id}` | 更新项目 |
| PUT | `/api/projects/{id}/webhook-secret` | 重新生成 webhook secret |
| GET | `/api/environments` | 环境列表 |
| POST | `/api/environments/{id}/test` | 连通性测试 |
| GET | `/api/credentials` | 凭据列表（仅元数据） |

### 构建与制品

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/builds` | 发起构建：`{"projectId", "branch", "targets": ["BACKEND_JAR","IMAGE","CHART","FRONTEND_TAR"]}` |
| GET | `/api/builds?projectId=&page=&size=` | 构建历史 |
| GET | `/api/builds/{id}` | 构建详情（状态/版本/commit） |
| POST | `/api/builds/{id}/cancel` | 取消构建 |
| GET | `/api/artifacts?projectId=&type=` | 制品列表（含 sha256） |
| GET | `/api/artifacts/{id}/download` | 下载制品（自动校验 sha256） |
| POST | `/api/artifacts/{id}/push` | 镜像推送到 registry |

### 部署与回滚

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/deployments` | 部署：`{"projectId", "environmentId", "artifactId"}` |
| POST | `/api/deployments/batch` | 批量顺序部署：`{"environmentId", "items": [...]}` |
| GET | `/api/deployments/{id}/rollback-preview` | 回滚预览 |
| POST | `/api/deployments/{id}/rollback` | 回滚到上一次成功版本 |
| POST | `/api/deployments/{id}/cancel` | 取消部署（断开 SSH / 杀进程树） |
| GET | `/api/deployments?projectId=&environmentId=` | 部署历史 |

## 典型脚本：构建 → 等待 → 部署 → 回滚

```bash
#!/usr/bin/env bash
set -euo pipefail
TOKEN="forge_xxx"; BASE="http://localhost:9090"
AUTH="Authorization: Token $TOKEN"

# 1. 发起构建并拿到 buildId
build=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"projectId":"<项目ID>","branch":"main","targets":["BACKEND_JAR"]}' "$BASE/api/builds" | jq -r .data.id)

# 2. 轮询直到终态（SUCCESS / FAILED / CANCELLED）
while :; do
  status=$(curl -s -H "$AUTH" "$BASE/api/builds/$build" | jq -r .data.status)
  [ "$status" != "PENDING" ] && [ "$status" != "RUNNING" ] && break
  sleep 5
done
[ "$status" = "SUCCESS" ] || { echo "构建失败: $status"; exit 1; }

# 3. 取最新制品并部署
artifact=$(curl -s -H "$AUTH" "$BASE/api/artifacts?projectId=<项目ID>&type=JAR&size=1" | jq -r '.data.content[0].id')
deploy=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"<项目ID>\",\"environmentId\":\"<环境ID>\",\"artifactId\":\"$artifact\"}" \
  "$BASE/api/deployments" | jq -r .data.id)

# 4. 出问题回滚
# curl -s -X POST -H "$AUTH" "$BASE/api/deployments/$deploy/rollback"
```

> 等待终态也可以直接监听 WebSocket `/ws/logs/build/{id}`（需先 `GET /api/auth/ticket` 取一次性 ticket 拼在 URL 上），日志流以 `__FORGE_LOG_END__` 结束。

## Token 管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tokens` | 列表（name/prefix/lastUsedAt，无明文） |
| POST | `/api/tokens` | 创建：`{"name":"ci-script"}` → 响应 `prefix` 字段一次性带回明文 |
| DELETE | `/api/tokens/{id}` | 吊销 |

## 安全建议

- Token 等同管理员权限（当前无角色区分），只放在 CI 的密钥变量里，不要提交进仓库；
- 公网部署务必配置 `FORGE_ALLOWED_ORIGINS` 为具体域名（REST 与 WebSocket 共用）；
- 令牌泄露时第一时间在设置页吊销。
