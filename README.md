# 🔨 Forge — 统一项目构建与部署管理平台

个人/小团队使用的统一项目管理工具：**项目托管 → 构建（前端包 / 后端包 / Docker 镜像 / K8s Chart）→ 制品库 → 一键部署 → 回滚** 全链路。

```
┌────────────────────────────────────────────────────────┐
│              forge-web（Vue 3 + Element Plus）          │
│   项目 │ 构建 │ 制品库 │ 环境 │ 凭据 │ 部署中心 │ 设置     │
└──────────────────────┬─────────────────────────────────┘
                       │ REST + WebSocket（实时日志）
┌──────────────────────▼─────────────────────────────────┐
│              forge-server（Spring Boot 3）               │
│  Builder SPI: Frontend / Backend / Docker / Helm        │
│  Deployer SPI: SSH(releases+软链) / Docker(compose)      │
│                / K8S(helm upgrade/rollback)             │
│  SQLite + AES-GCM 加密凭据 + 本地制品库                    │
└─────────────────────────────────────────────────────────┘
```

## 功能总览

| 模块 | 能力 |
|---|---|
| 项目管理 | Git 仓库注册、技术栈、构建配置（命令/产物目录/Dockerfile/Chart/端口）、健康检查配置 |
| 构建 | ① 前端包 `npm/pnpm/yarn → dist.tar.gz` ② 后端包 `Maven/Gradle → jar` ③ Docker 镜像（项目 Dockerfile 优先，否则内置模板）④ K8s Chart（自带 chart 优先，否则内置模板 + helm package）|
| 构建编排 | 版本号自动生成 `分支-时间戳-commit7`、实时日志（WS 推送+历史重放）、取消（杀进程树）、并行度可配 |
| 制品库 | sha256 校验、下载、镜像 docker push、按项目/类型筛选 |
| 环境 | SSH 裸机 / Docker 主机（本机或 SSH 远端）/ K8s 集群，连通性一键测试 |
| 部署 | 一键部署（项目→环境→制品向导）、部署后健康检查（HTTP 轮询）、部署历史 |
| 回滚 | 一键回滚到该部署之前的最近成功版本；K8s 走 `helm rollback`，SSH 走软链切换 |
| 安全 | 凭据 AES-256-GCM 加密落库（密钥 `FORGE_SECRET_KEY` 或数据目录自动生成）、JWT 认证、命令参数数组防注入、审计（部署记录操作人）|

## 快速开始

### 0. 依赖

- 运行 Forge：JDK 17+
- 构建机工具链（按需）：`git`（必需）、`node/npm`（前端包）、`mvn`/`gradle`（后端包）、`docker`（镜像）、`helm` + `kubectl`（K8s 部署）。工具缺失只影响对应功能，Forge 启动时会在「系统设置」页探测并展示。
- 目标机：SSH 部署只需账号密码或私钥；无其他要求。

### 1. 打包

```bash
./build-all.sh          # 前端 dist 拷入后端 → mvn package
# 产物：forge-server/target/forge-server.jar（内嵌全部页面，单 jar 交付）
```

### 2. 启动

```bash
java -jar forge-server/target/forge-server.jar
# 默认 http://localhost:8080 ，账号 admin / admin123
```

常用环境变量：

| 变量 | 说明 | 默认 |
|---|---|---|
| `FORGE_DATA_DIR` | 数据目录（SQLite/制品/日志/密钥）| `~/.forge` |
| `FORGE_ADMIN_USERNAME` / `FORGE_ADMIN_PASSWORD` | 初始管理员 | admin / admin123 |
| `FORGE_SECRET_KEY` | 凭据加密密钥（32 字节 Base64），不设则自动生成存于数据目录 | 自动 |
| `FORGE_BUILD_PARALLELISM` | 构建并行数 | 2 |
| `--server.port` | HTTP 端口 | 8080 |

### 3. 第一次使用

1. 登录 →「凭据管理」录入 SSH 私钥或密码；
2. 「环境管理」新建环境（SSH：填主机/用户/凭据/部署目录）→ 点「测试」验证连通；
3. 「项目管理」新建项目（填 Git 仓库地址）；
4. 项目详情 →「发起构建」勾选目标 → 实时看日志；
5. 「部署中心」选项目/环境/制品 → 部署 → 自动健康检查；出问题点「回滚」。

前端开发模式（可选）：

```bash
cd forge-web && npm install && npm run dev   # vite 代理 /api、/ws 到 8080
```

## 部署 Forge 到云服务器（生产建议）

```bash
# 1. 上传 jar 与部署样例
scp forge-server/target/forge-server.jar deploy/forge.service deploy/nginx-forge.conf deploy/backup.sh user@server:/tmp/

# 2. 服务器上安装（假设专用用户 forge，目录 /opt/forge）
sudo useradd -r -m -d /opt/forge forge || true
sudo mkdir -p /opt/forge && sudo mv /tmp/forge-server.jar /opt/forge/
sudo chown -R forge:forge /opt/forge
sudo cp /tmp/forge.service /etc/systemd/system/   # 按需修改 FORGE_ADMIN_PASSWORD
sudo systemctl daemon-reload && sudo systemctl enable --now forge

# 3. HTTPS 反向代理（日志实时推送依赖 WebSocket 配置）
sudo cp /tmp/nginx-forge.conf /etc/nginx/conf.d/   # 修改 server_name 与证书路径
sudo nginx -t && sudo nginx -s reload

# 4. 每日备份（SQLite + 制品 + 加密密钥）
sudo cp /tmp/backup.sh /opt/forge/ && sudo chmod +x /opt/forge/backup.sh
(crontab -l 2>/dev/null; echo "30 2 * * * /opt/forge/backup.sh") | crontab -
```

安全清单：首次登录后立即改密码；`FORGE_ALLOWED_ORIGINS` 设为你的域名；防火墙只开放 80/443；
数据目录 `/opt/forge/data` 包含加密密钥与数据库，务必用 `deploy/backup.sh` 定期备份。

## 部署形态说明

- **SSH + JAR**：上传 jar 到 `{deployDir}/releases/{version}/`，生成 `start.sh`（可用环境变量 `java.opts`、`server.port`），软链 `current` 切换后重启（优先 systemd 服务，否则 `pkill + nohup`）。旧 release 自动清理（保留 `keepReleases` 个）。
- **SSH + 前端包**：解压到 release 目录并软链，服务器 nginx root 指向 `{deployDir}/current` 即可。
- **Docker 主机**：渲染 `docker-compose.yml`（镜像 tag、端口 `buildConfig.ports`、环境变量）上传后 `docker compose up -d`；`host` 留空则在本机执行。
- **K8s**：内置通用 Chart（Deployment+Service）或项目自带 chart；镜像信息自动注入（`--set image.*`），`helm upgrade --install --wait`，回滚 `helm rollback`。环境变量中 `helm.*` 前缀项作为 `--set` 注入。

## 项目结构

```
Forge/
├── forge-server/                  # Spring Boot 3 后端
│   └── src/main/java/com/forge/
│       ├── auth/                  # JWT 登录/改密
│       ├── build/                 # Builder SPI + 4 实现 + 构建编排 + 制品服务
│       ├── deploy/                # Deployer SPI + 3 实现 + 健康检查 + 部署编排
│       ├── domain/                # 实体 + Repository（JPA/SQLite）
│       ├── credential/            # AES-GCM 加密凭据
│       ├── infra/                 # 进程执行 / git / SSH(MINA sshd) / 日志流(WS)
│       ├── project/               # 项目 + 环境
│       ├── setting/               # 系统设置（registry/镜像源/保留策略）
│       └── system/                # 工具链探测 / 统计
│   └── src/main/resources/templates/   # 内置 Dockerfile / Helm Chart 模板
├── forge-web/                     # Vue 3 + TS + Element Plus 控制台
├── build-all.sh                   # 一键打包
└── docs/开发方案.md
```

## v1 已知边界（预留扩展点）

- 构建机工具链需自备（Forge 只探测不管理版本）；后续可做工具链容器化。
- FULL 类型项目若无自带 Dockerfile，仅按 jar/dist 产出单镜像；前后端双镜像需拆两个项目或提供 Dockerfile/chart。
- 镜像仓库凭据在「系统设置」中配置（单组）；多 registry 待扩展。
- Webhook 自动构建、构建缓存、消息通知排在下一阶段（`BuildTrigger.WEBHOOK` 枚举已预留）。
