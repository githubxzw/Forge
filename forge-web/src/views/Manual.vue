<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>使用手册</h2>
        <p class="desc">从零到一：把你的项目通过 Forge 构建并部署到服务器</p>
      </div>
    </div>
    <el-anchor :offset="70" class="manual-anchor" direction="vertical">
      <el-anchor-link href="#s1" title="① 快速上手" />
      <el-anchor-link href="#s2" title="② 凭据管理" />
      <el-anchor-link href="#s3" title="③ 环境管理" />
      <el-anchor-link href="#s4" title="④ 项目管理" />
      <el-anchor-link href="#s5" title="⑤ 构建与制品" />
      <el-anchor-link href="#s6" title="⑥ 部署与回滚" />
      <el-anchor-link href="#s7" title="⑦ 常见问题" />
    </el-anchor>

    <el-card class="section" id="s1" shadow="never">
      <h3><span class="idx">1</span>快速上手 · 五步走</h3>
      <el-steps :active="5" align-center style="margin-top: 18px">
        <el-step title="建凭据" description="SSH 私钥 / Token" />
        <el-step title="接环境" description="云服务器 / Docker / K8s" />
        <el-step title="注册项目" description="填 Git 地址" />
        <el-step title="发起构建" description="前端包/后端包/镜像/Chart" />
        <el-step title="一键部署" description="失败可一键回滚" />
      </el-steps>
      <p style="margin-top: 18px">
        Forge 管理的是一条完整流水线：<b>代码 → 制品 → 运行环境</b>。
        每次构建都会生成唯一版本号（<span class="mono">分支-时间戳-commit</span>），
        每次部署都有留档，任何时候都可以一键回滚到上一个成功版本。
      </p>
    </el-card>

    <el-card class="section" id="s2" shadow="never">
      <h3><span class="idx">2</span>凭据管理 —— 一切连接的钥匙</h3>
      <p>所有敏感信息（服务器密码、私钥、kubeconfig）都集中在这里，<b>AES-256-GCM 加密落库</b>，保存后不可再查看原文。</p>
      <el-table :data="credTypes" size="small" style="margin-top: 8px">
        <el-table-column prop="kind" label="类型" width="140" />
        <el-table-column prop="usage" label="用途" min-width="260" />
        <el-table-column prop="fields" label="需要填写" min-width="200" />
      </el-table>
      <p style="margin-top: 12px">
        典型场景：<b>云服务器登录</b> 用 SSH 私钥（推荐）或密码；<b>GitHub 私有仓库</b> 用文本类型存
        Personal Access Token；<b>K8s 集群</b> 用 kubeconfig。
      </p>
    </el-card>

    <el-card class="section" id="s3" shadow="never">
      <h3><span class="idx">3</span>环境管理 —— 告诉 Forge 部署到哪里</h3>
      <el-table :data="envTypes" size="small" style="margin-top: 8px">
        <el-table-column prop="kind" label="环境类型" width="170" />
        <el-table-column prop="what" label="是什么" min-width="220" />
        <el-table-column prop="artifacts" label="可部署制品" min-width="170" />
        <el-table-column prop="how" label="部署方式" min-width="230" />
      </el-table>
      <p style="margin-top: 12px">
        <b>云服务器（SSH）</b> 是最常用的类型：填好 IP、端口、用户名和凭据，点「测试」验证连通。
        后端包会部署到 <span class="mono">{deployDir}/releases/{版本}/</span> 并用 <span class="mono">current</span> 软链原子切换；
        前端静态包解压到同目录，把服务器 nginx 的 root 指向 <span class="mono">{deployDir}/current</span> 即可自动生效。
      </p>
      <div class="code"># 云服务器后端部署后的目录结构
/opt/apps/myapp/
├── releases/
│   ├── main-202609272159-95af60e/
│   │   ├── app.jar
│   │   └── start.sh
│   └── main-202609272150-1b2c3d4/
├── current -&gt; releases/main-202609272159-95af60e   # 软链指向当前版本
└── logs/</div>
    </el-card>

    <el-card class="section" id="s4" shadow="never">
      <h3><span class="idx">4</span>项目管理 —— 注册你的 Git 仓库</h3>
      <p>
        在「项目管理」→「新建项目」中填写：<b>项目编码</b>（唯一，用作镜像名/目录名，建议小写加中划线）、
        <b>Git 地址</b>（GitHub / GitLab / Gitee 的 HTTPS 或 SSH 地址，也支持构建机本地路径）、
        <b>默认分支</b> 与 <b>类型</b>（前端 / 后端 / 前后端）。
      </p>
      <p><b>私有仓库</b>：先在「凭据管理」创建凭据，再在项目的「拉取凭据」中选择 ——</p>
      <ul>
        <li>HTTPS + Token（GitHub 推荐：Settings → Developer settings → Personal access tokens，勾选 repo 权限）→ 凭据类型选「文本」，内容填 token；</li>
        <li>SSH 地址（<span class="mono">git@github.com:you/repo.git</span>）→ 凭据类型选「SSH 私钥」，粘贴私钥内容。</li>
      </ul>
      <p>构建配置均为可选，留空即自动识别：</p>
      <el-table :data="buildFields" size="small" style="margin-top: 8px">
        <el-table-column prop="field" label="配置项" width="130" />
        <el-table-column prop="desc" label="说明" min-width="330" />
        <el-table-column prop="default" label="默认行为" min-width="220" />
      </el-table>
    </el-card>

    <el-card class="section" id="s5" shadow="never">
      <h3><span class="idx">5</span>构建与制品 —— 四类产物一次搞定</h3>
      <p>项目详情页点「发起构建」，勾选本次需要的目标：</p>
      <el-table :data="targets" size="small" style="margin-top: 8px">
        <el-table-column prop="target" label="构建目标" width="150" />
        <el-table-column prop="flow" label="执行流程" min-width="330" />
        <el-table-column prop="output" label="产物" min-width="200" />
      </el-table>
      <p style="margin-top: 12px">
        构建过程实时输出日志（WebSocket 推送），可随时取消（会终止整棵进程树）。
        成功后制品进入「制品库」，带 sha256 校验和；镜像制品可一键 <span class="mono">docker push</span> 到系统设置里配置的仓库。
      </p>
    </el-card>

    <el-card class="section" id="s6" shadow="never">
      <h3><span class="idx">6</span>部署与回滚 —— 一键上线，一键后悔</h3>
      <p>
        「部署中心」选择 项目 → 环境 → 制品，点「部署」。环境类型决定可选的制品（SSH 只能选前端/后端包，
        DOCKER 选镜像，K8S 选 Chart）。部署完成后自动执行健康检查（在项目配置里填健康检查 URL）。
      </p>
      <ul>
        <li><b>回滚</b>：任何成功部署的记录都可以一键回滚 —— 回到该部署之前最近一次成功版本；K8s 走 <span class="mono">helm rollback</span>，云服务器切换软链。</li>
        <li><b>批量部署</b>：部署中心的「批量部署」可勾选多个项目按顺序发布到同一环境，前序失败立即中止，适合一次更新一组服务。</li>
        <li><b>审计</b>：每条部署记录了操作人、时间、结果与完整日志，出问题可追溯。</li>
        <li><b>通知</b>：系统设置里配置钉钉/企业微信/飞书机器人后，每次部署结束都会推送结果。</li>
        <li><b>保留策略</b>：云服务器默认保留最近 5 个 release（环境配置里可改），制品永久保留可手动删除；构建日志按系统设置的天数自动清理。</li>
      </ul>
    </el-card>

    <el-card class="section" id="s7" shadow="never">
      <h3><span class="idx">7</span>常见问题</h3>
      <p><b>Q：构建时报 git/docker/mvn 找不到？</b><br />
        Forge 在「系统设置」页探测构建机工具链。缺什么装什么：构建机需要 git（必需）+ 对应构建工具；docker/helm 只在构建镜像或部署 K8s 时需要。</p>
      <p><b>Q：私有仓库拉取失败？</b><br />
        检查三点：凭据类型与地址协议是否匹配（token 配 HTTPS 地址、私钥配 SSH 地址）；GitHub Token 是否勾了 repo 权限；SSH 私钥是否完整（包含 BEGIN/END 行）。</p>
      <p><b>Q：怎么做到 push 代码自动构建？</b><br />
        项目详情页有「Webhook 自动构建」卡片：复制地址和密钥，到 GitHub / Gitee / GitLab 仓库的 Settings → Webhooks 添加，
        Content-Type 选 application/json、Secret 填密钥即可。支持 GitHub 签名校验与 GitLab Token；同分支有进行中的构建会自动跳过。</p>
      <p><b>Q：SSH 部署后端包后服务没起来？</b><br />
        看部署日志确认启动命令执行成功；若服务器用 systemd 托管，在环境配置里填 systemdService 名称。
        最常见原因是 SSH 非交互登录找不到 java —— 在环境配置里填「Java 路径」（服务器上 JAVA_HOME）。应用自身日志在 <span class="mono">{deployDir}/logs/{版本}.log</span>。</p>
      <p><b>Q：健康检查一直失败？</b><br />
        确认项目配置的健康检查 URL 从 Forge 所在网络可达；云服务器场景下 host 自动取环境配置的 IP，也可以直接填完整 URL。</p>
      <p><b>Q：忘记管理员密码？</b><br />
        删除数据目录中的 <span class="mono">forge.db</span> 会重置一切（慎用）；或用环境变量 <span class="mono">FORGE_ADMIN_PASSWORD</span> 重新初始化。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
const credTypes = [
  { kind: 'SSH 私钥', usage: '云服务器 SSH 登录（推荐）、SSH 协议的 Git 仓库', fields: '私钥内容、可选口令' },
  { kind: '密码', usage: '云服务器 SSH 密码登录、HTTPS Git（账号+密码）', fields: '用户名（可选）、密码' },
  { kind: '文本 / Token', usage: 'GitHub / GitLab Personal Access Token 拉私有仓库', fields: 'token 内容' },
  { kind: 'kubeconfig', usage: 'K8s 集群部署', fields: 'kubeconfig YAML 全文' }
]
const envTypes = [
  { kind: '云服务器（SSH）', what: '任何能 SSH 登录的主机（阿里云/腾讯云/自建机房）', artifacts: '前端包 TAR、后端包 JAR', how: 'releases 目录 + current 软链，自动清理旧版本' },
  { kind: 'Docker 主机', what: '装有 docker 的机器（本机或 SSH 远端）', artifacts: 'Docker 镜像 IMAGE', how: '渲染 docker-compose.yml 后 up -d' },
  { kind: 'K8s 集群', what: '任何 kubeconfig 可连接的集群', artifacts: 'Helm Chart', how: 'helm upgrade --install --wait' }
]
const buildFields = [
  { field: '构建命令', desc: '覆盖默认构建命令，如 npm run build:prod', default: '前端 npm run build；后端 mvn package' },
  { field: '产物目录', desc: '前端构建产物所在目录', default: '自动识别 dist / build' },
  { field: 'Dockerfile', desc: '仓库内 Dockerfile 相对路径', default: '根目录 Dockerfile，或按产物套内置模板' },
  { field: '镜像名', desc: '覆盖默认镜像名', default: '系统设置 registry + 项目编码' },
  { field: '容器端口 / 端口映射', desc: '容器端口与宿主机映射（如 8080:8080）', default: '按项目类型 80 / 8080' },
  { field: 'Chart 路径', desc: '仓库内 chart 目录', default: '使用 Forge 内置通用 Chart' },
  { field: '健康检查 URL', desc: '部署后自动轮询的地址', default: '跳过健康检查' }
]
const targets = [
  { target: '前端包 TAR', flow: '识别包管理器 → install → build → 打包 tar.gz', output: 'dist.tar.gz + sha256' },
  { target: '后端包 JAR', flow: 'Maven/Gradle（支持 wrapper 与镜像加速）→ package', output: '可运行 jar' },
  { target: 'Docker 镜像', flow: '项目 Dockerfile 或内置模板 → docker build（可自动 push）', output: '镜像 name:tag' },
  { target: 'K8s Chart', flow: '自带 chart 或内置模板 → helm package', output: 'chart tgz' }
]
</script>
