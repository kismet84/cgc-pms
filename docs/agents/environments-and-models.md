# 适用环境 & 模型配置

> 最后更新：2026-06-17
> 本文档汇总 CGC-PMS 全部运行时环境定义，以及 OhMyOpenCode 层 Agent / Category 模型配置。

---

## 一、运行时环境（5 套）

### 1.1 环境总览

| # | 环境 | Spring Profile | 数据库 | 缓存 | 文件存储 | 用途 |
|---|------|---------------|--------|------|----------|------|
| 1 | **local** | `local` | H2 内存库 | ❌ 禁用 | ❌ 禁用 | 零依赖离线开发 |
| 2 | **dev** | `dev` | MySQL :3307 | Redis :6379 | MinIO :9000 | 日常开发（Docker 基础设施） |
| 3 | **test** | `test` | MySQL cgc_pms_test | Redis DB:1 | MinIO test bucket | 自动化测试 |
| 4 | **prod** | `prod` | MySQL (SSL) | Redis (认证) | MinIO | 生产部署 |
| 5 | **CI** | （覆盖） | MySQL 8.0 svc | Redis 7 svc | — | GitHub Actions |

### 1.2 local — 零依赖离线开发

| 属性 | 值 |
|------|-----|
| 数据库 | H2 内存库 `jdbc:h2:mem:cgcpms_test;MODE=MySQL` |
| Redis | 禁用（`RedisAutoConfiguration` excluded） |
| MinIO | 禁用（`minio.enabled: false`） |
| Flyway | 启用，使用 `classpath:db/migration-h2` |
| JWT 过期 | 24h（access）/ 7d（refresh） |
| 启动命令 | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |

### 1.3 dev — 日常开发

| 属性 | 值 |
|------|-----|
| 数据库 | MySQL 8.0 `localhost:3307`（Docker 容器） |
| Redis | `localhost:6379`（密码认证） |
| MinIO | `http://localhost:9000`（bucket: `cgc-pms`） |
| Flyway | 启用，标准 `db/migration`，校验开启 |
| JWT 过期 | 15min（access）/ 7d（refresh） |
| CORS | `http://localhost:5173` |
| 启动方式 | ① `scripts\start-dev.bat`（一键） ② `./mvnw -Dspring-boot.run.profiles=dev` ③ `docker compose -f docker-compose.dev.yml up -d`（全 Docker） |
| 特色 | docker-compose.dev.yml 源码挂载 + Vite HMR 热更新 |

### 1.4 test — 自动化测试

| 属性 | 值 |
|------|-----|
| 数据库 | MySQL `localhost:3306`，专用库 `cgc_pms_test` |
| Redis | `localhost:6379`，database 1 |
| MinIO | `http://localhost:9000`，bucket `cgc-pms-test` |
| Flyway | 启用，`clean-disabled: true` |
| 启动命令 | `./mvnw test` |

> 注意：`src/test/resources/` 下另有 `application.yml`（Redis/MinIO 禁用）、`application-test.yml`（MySQL 主库）、`application-local.yml`（H2 内存库），用于单元测试上下文。

### 1.5 prod — 生产部署

| 属性 | 值 |
|------|-----|
| 数据库 | MySQL 8.0，SSL 强制（`useSSL=true, requireSSL=true`） |
| Redis | 环境变量配置 host/port/password |
| MinIO | 环境变量配置 endpoint/key/secret/bucket |
| Flyway | 启用，`clean-disabled: true`（安全保护） |
| Swagger | 禁用 |
| Actuator | Health + Info 端点 |
| JWT | **必须**在 `.env` 设置，无硬编码 fallback |
| Cookie | `secure: true`（仅 HTTPS） |
| 日志 | root WARN，app INFO |
| 部署 | `docker compose -f docker-compose.prod.yml up -d` |

---

## 二、Docker 部署架构

### 2.1 三个 Compose 文件

| 文件 | 用途 | 包含服务 |
|------|------|----------|
| `deploy/docker-compose.yml` | 基础设施（开发/测试共用） | MySQL, Redis, MinIO |
| `deploy/docker-compose.dev.yml` | 全栈开发（源码挂载 + HMR） | MySQL + Redis + MinIO + Backend(源码) + Frontend(Vite HMR) |
| `deploy/docker-compose.prod.yml` | 生产部署 | MySQL + Redis + MinIO + Backend + Frontend(Nginx SSL) |

### 2.2 生产服务规格

| 服务 | 镜像 | 容器名 | 内存限制 | 端口 |
|------|------|--------|----------|------|
| MySQL 8.0 | `mysql:8.0` | `cgc-pms-mysql` | 512M | 内网 |
| Redis 7 | `redis:7-alpine` | `cgc-pms-redis` | 256M | 内网 |
| MinIO | `minio/minio` | `cgc-pms-minio` | 512M | 内网 |
| Backend | 自构建 JRE 21 | `cgc-pms-backend` | 1G | 8080 |
| Frontend | 自构建 Nginx | `cgc-pms-frontend` | 128M | 80 / 443 |

### 2.3 环境变量（`deploy/.env.example`）

```
MYSQL_ROOT_PASSWORD / MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD
REDIS_PASSWORD
JWT_SECRET            # 至少 32 字符，生产必须设置
CORS_ALLOWED_ORIGINS  # 生产设为真实域名
MINIO_ROOT_USER / MINIO_ROOT_PASSWORD
```

---

## 三、CI/CD 流水线

文件：`.github/workflows/ci.yml`

```
push main/develop →
  ├─ backend-test    (Java 21 + MySQL 8.0 + Redis 7, ubuntu-latest)
  ├─ frontend-build  (Node 20 + pnpm, vue-tsc + vite build)
  ├─ flyway-check    (依赖 backend-test, 启动 Spring Boot 验证 Flyway 迁移)
  ├─ docker-build    (依赖 backend-test + frontend-build, Docker Buildx 构建镜像)
  └─ deploy          (仅 workflow_dispatch: registry push + SSH 部署 + health check)
```

触发条件：
- `push` 到 `main` 或 `develop` 分支
- PR 到 `main`
- 手动触发 `workflow_dispatch`（含 deploy）

---

## 四、OhMyOpenCode 模型配置

配置文件：`C:\Users\L1597\.config\opencode\`

### 4.1 Provider

文件：`opencode.json`

| Provider | 端点 | 协议 | 模型 |
|----------|------|------|------|
| **n1n** | `https://llm-api.net/v1` | OpenAI 兼容 | `claude-opus-4-8` |
| **jojocode** | `https://max.jojocode.com/v1` | OpenAI 兼容 | `GPT-5.5`, `gpt-5.5`, `gpt-5.4` |
| **opencode**（内置） | — | — | `gpt-5.5`, `gemini-3.1-pro`, `glm-5`, `claude-opus-4-7`, `claude-sonnet-4-6`, `gpt-5.4-mini`, `gpt-5-nano`, `kimi-k2.5` |
| **deepseek**（内置） | — | — | `deepseek-v4-pro`, `deepseek-v4-flash`, `deepseek-chat` |

### 4.2 Subagent → 模型映射

文件：`oh-my-openagent.json`

| Agent | 主模型 | Fallback 链 |
|-------|--------|-------------|
| **sisyphus**（主 Agent） | `deepseek/deepseek-v4-pro` | chat → kimi-k2.5 → gpt-5.5(medium) |
| **hephaestus** | `deepseek/deepseek-v4-pro` | chat |
| **oracle** | `deepseek/deepseek-v4-pro` | chat |
| **prometheus** | `jojocode/gpt-5.5` (max) | opencode/gpt-5.5(high) → gemini-3.1-pro |
| **explore** | `deepseek/deepseek-v4-flash` | v4-pro |
| **librarian** | `deepseek/deepseek-v4-flash` | v4-pro |
| **metis** | `jojocode/gpt-5.5` | chat |
| **momus** | `jojocode/gpt-5.5` | chat |
| **atlas** | `deepseek/deepseek-v4-pro` | chat |
| **multimodal-looker** | `deepseek/deepseek-v4-pro` | gpt-5-nano |
| **sisyphus-junior** | `deepseek/deepseek-v4-pro` | chat → claude-sonnet-4-6 |

### 4.3 Category → 模型映射

| Category | 主模型 | Fallback 链 | 适用场景 |
|----------|--------|-------------|----------|
| **visual-engineering** | `jojocode/gpt-5.5` | glm-5 → claude-opus-4-7(max) | 前端/UI/样式/动画 |
| **ultrabrain** | `jojocode/gpt-5.5` | gpt-5.5(xhigh) → gemini-3.1-pro(high) | 硬逻辑/算法/架构 |
| **deep** | `deepseek/deepseek-v4-pro` | gpt-5.5(medium) → gemini-3.1-pro(high) | 端到端复杂实现 |
| **artistry** | `deepseek/deepseek-v4-pro` | gpt-5.5(max) → gpt-5.5 | 创造性/非传统解法 |
| **quick** | `deepseek/deepseek-v4-pro` | chat → gpt-5.4-mini | 单文件/琐碎修改 |
| **unspecified-low** | `deepseek/deepseek-v4-pro` | chat → claude-sonnet-4-6 | 通用低难度 |
| **unspecified-high** | `deepseek/deepseek-v4-pro` | chat → claude-sonnet-4-6 | 通用高难度 |
| **writing** | `deepseek/deepseek-v4-pro` | chat → claude-sonnet-4-6 | 文档/技术写作 |

### 4.4 模型分层策略

```
⚡ 轻量高频 (flash)  → explore, librarian                  → 搜索/查文档
🏗️ 主力执行 (v4-pro) → sisyphus, oracle, junior,             → 12 个 Agent/Category
                       deep, artistry, quick,
                       unspecified, writing, atlas,
                       hephaestus
🧠 顶级推理 (gpt-5.5) → prometheus(max), metis, momus,       → 计划/评审/UI/超难逻辑
                       visual-engineering, ultrabrain
```

### 4.5 Variant 参数说明

`variant` 控制推理强度/质量等级，取值：`max` > `xhigh` > `high` > `medium` > `low`

| Variant | 语义 | 使用位置 |
|---------|------|----------|
| `max` | 最强能力 | prometheus(j), artistry fallback, visual-engineering fallback |
| `xhigh` | 极致推理 | ultrabrain fallback |
| `high` | 强推理 | deep fallback, ultrabrain fallback |
| `medium` | 中等推理 | sisyphus fallback, deep fallback |

---

## 五、模型家族适用场景速查

| 家族 | 代表模型 | 最适合 | 注意事项 |
|------|---------|--------|----------|
| **DeepSeek** | v4-pro, v4-flash | 主力全场景覆盖 | — |
| **GPT-5.5** | jojocode/gpt-5.5, opencode/gpt-5.5 | 深度推理、架构设计、复杂编码 | variant 越高推理越深 |
| **Claude** | claude-opus-4-7, claude-sonnet-4-6 | 指令遵循、结构化输出 | Opus 系列是 Sisyphus 编排首选 |
| **Gemini** | gemini-3.1-pro | 视觉/前端 UI 任务 | — |
| **GLM** | glm-5 | Claude 替代，覆盖面广 | — |
| **Kimi** | kimi-k2.5 | 散文/文档写作 | Claude 替代品 |

---

## 六、关联文件索引

| 类别 | 文件路径 |
|------|----------|
| Agent 协作规则 | `AGENTS.md` |
| 多 Agent 工作流 | `docs/agents/multi-agent-workflow.md` |
| 消息协议 | `docs/agents/message-protocol.md` |
| 主 Agent 提示词 | `docs/agents/prompts/main-agent.md` |
| 故障排查记忆 | `docs/agents/project-troubleshooting-memory.md` |
| OpenCode 配置 | `C:\Users\L1597\.config\opencode\opencode.json` |
| OmO Agent 配置 | `C:\Users\L1597\.config\opencode\oh-my-openagent.json` |
| 应用配置 | `backend/src/main/resources/application*.yml` |
| Docker Compose | `deploy/docker-compose*.yml` |
| 环境变量模板 | `deploy/.env.example` |
| CI/CD | `.github/workflows/ci.yml` |
