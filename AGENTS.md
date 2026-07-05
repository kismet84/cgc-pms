# AGENTS.md

若仓库根存在 `AGENTS.override.md`，必须先读取并遵守；本文件只提供项目级基础规则、项目背景、常用命令和长期约定；如与 `AGENTS.override.md` 冲突，以 `AGENTS.override.md` 为准。

本文件是 AI 编程助手在本仓库工作的高优先级入口说明。保持精简：这里只放必须马上知道的规则；详细流程放在 `docs/` 中，经验索引见 `memory/MEMORY.md`。

## 强制规则

- 所有回答必须使用中文。
- 修改代码后必须运行相关验证：后端至少跑相关测试/构建，前端至少跑类型检查/测试/构建中的相关项。
- 不要修改已经应用过的 Flyway 迁移脚本；数据库结构变化必须新增版本化 migration。
- 做外科手术式修改：只改必要文件，遵循现有命名、目录、异常处理和测试风格。
- 解决错误或工具陷阱后，优先在当前会话中沉淀可复用结论；只有在当前运行环境允许且用户明确要求时，才写入 `memory/` 并更新 `memory/MEMORY.md`。
- `AGENTS.md` 为仓库级协作规则入口；`CLAUDE.md` 若存在则属于本地 AI 协作配置，不影响系统运行。
- 不要发送无信息量 commentary；需要进度同步时，只保留与当前任务直接相关的最小更新。
- 任务执行完后及时清理node.js、自动化/Playwright 残留

## 自动经验记录

每次解决一个错误或问题后，优先整理成可复用结论；只有在当前运行环境允许且用户明确要求时，才保存到 `memory/`：

- 覆盖编译失败、测试失败、运行时异常、配置错误、Flyway 失败、工具调用陷阱等。
- 每个经验单独一个 `.md` 文件，包含 frontmatter：`name`、`description`、`metadata type/feedback`、`tags`。
- 保存后更新 `memory/MEMORY.md` 索引：一行链接 + 一句话描述。
- 如果当前运行环境不允许直接写 memory，则保留在会话或普通文档中，不强制落盘。

## 新会话启动模板（可复制粘贴）

每次新会话（尤其是新线程）建议先发送：

```text
请先读取并严格遵循 D:\projects-test\cgc-pms\AGENTS.override.md、D:\projects-test\cgc-pms\AGENTS.md 和本仓库可见的工具说明；先按 CODEGRAPH 要求在检索代码时优先使用 CodeGraph；
如任务涉及审计，按仓库现状直接输出基于代码证据的审计结论；如需归档正式审计报告，按 AGENTS.override.md 的审计与归档边界写入 docs/quality/。
```

## 项目与运行入口

- 项目目录：`backend/` 后端、`frontend-admin/` 前端、`deploy/` 部署、`docs/` 文档中心。
- 快速启动、本地访问地址、运行态刷新、常见验证：`docs/01-快速开始.md`
- 系统分层、模块域、数据与部署架构：`docs/02-系统架构.md`
- Docker、生产部署、回滚、备份、监控：`docs/10-部署运维手册.md`
- 前端本地验收默认入口：`http://localhost:5173`
- 前后端重启后的统一稳定等待时间按 `180秒` 执行；后端至少等待 `180秒` 后再做 health / Flyway / 接口验收，前端至少等待 `180秒` 并确认 Vite ready 后，再做 Playwright UI 验收。
- 若 Docker 连续多次不可用（如 `dockerDesktopLinuxEngine` 管道不存在、`docker ps` 无法连接、`5173/8080` 均拒绝连接），在需要运行态验收且用户允许运维动作时，先尝试重启 WSL2 与 Docker Desktop，再等待 `180秒` 后复查 `docker ps`、后端 health、前端入口；不得把 Docker 不可用伪装成业务失败或验收通过。

## 质量与避坑入口

- 高频陷阱索引：`memory/MEMORY.md`。
- 测试规范与测试数据隔离：`docs/09-测试规范.md`。
- 后端开发规范：`docs/04-后端开发规范.md`。
- 前端开发规范与 `lg-*` 设计系统：`docs/05-前端开发规范.md`、`frontend-admin/src/assets/styles/global.css`。
- 数据库与迁移规范：`docs/07-数据库与迁移规范.md`。
- UI 基线：`docs/00-UI-Design-Baselines-and-Code-Specifications.md`。

## 触发协议

### 代码审计 / 代码评审 / 安全审计 / 生产可用性审计

审计报告写入：

```text
docs/quality/code-audit-YYYY-MM-DD-<short-topic>.md
```

落盘动作遵循 `AGENTS.override.md` 的审计与归档边界，默认由审计/归档型子智能体执行，主线程只负责结论和验收。

### 飞书确认交互

遇到必须用户决策且需要飞书确认时，读取：

```text
docs/prompt/lark-confirmation-flow.md
```

## 文档入口

- 文档索引：`docs/README.md`
- 快速开始：`docs/01-快速开始.md`
- 系统架构：`docs/02-系统架构.md`
- 业务模块说明：`docs/03-业务模块说明.md`
- API 契约：`docs/06-API契约规范.md`
- 权限与审批：`docs/08-权限与审批流程.md`
- 安全规范：`docs/11-安全规范.md`
