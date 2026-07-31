# 第58条主线：新版前端正式切换与 Legacy 归档

**Goal:** 将 `frontend-admin-v2` 切换为仓库与本地开发环境的唯一正式前端，由其接管根路径、`5173` 端口、`frontend` Compose 服务、CI 制品与浏览器门禁；将 Legacy `frontend-admin` 跟踪源码归档并退出运行、构建与发布链。 **Architecture:** 复用已通过 M8 验收的 V2 router、共享契约、Docker 根路径构建和回滚演练链；保留 `frontend-admin-v2` 目录名以避免无价值的全仓路径搬迁，不引入微前端、第三套入口、新依赖或平行业务实现。

> 日期：2026-07-31
> 分支：`codex/v1.6-start`
> 计划基线：`0ab28a16411a4c0efd00ff7b56e1cd7a92bc8cd8`
> 状态：`COMPLETED / REPOSITORY_AND_LOCAL_RUNTIME_PASSED / PRODUCTION_NOT_EXECUTED`
> 授权：仓库内 Legacy 归档、新版正式入口切换、本地启动与必要验证
> 未授权：commit、push、PR、Tag、Release、目标环境变更、生产发布

## 范围

- `frontend-admin-v2/**`：转为正式前端，默认根路径与 `5173`，保留共享契约、权限、路由、单测和 E2E。
- `frontend-admin/**`：仅以 HEAD Git 跟踪清单选取应用源码，工作树归档到 `archive/v1.6/frontend-admin-legacy/`；不读取、搬移或清理被禁止的 `.omc/` 及本机忽略产物。
- Compose、前端 Dockerfile/Nginx、启动/重建脚本、CI 与静态契约改为新版唯一入口。
- README、标准、Backlog、项目地图、计划索引和正式验收报告同步现状。

## 非目标

- 不修改后端业务口径、数据库、权限码、金额、库存、审批或会计逻辑。
- 不连接生产、不生成或推送发布制品、不修改远程分支保护。
- 不删除 Git 历史，不清理 Legacy 本机缓存、`node_modules`、报告或禁止目录。

## 任务与阶段门

| 阶段 | 任务 | 通过条件 |
| --- | --- | --- |
| `ISSUE-058-001` | 冻结退役边界 | 影响面、归档目标、入口、回滚和禁止范围明确 |
| `ISSUE-058-002` | 新版接管正式入口 | `/`、`5173`、`frontend` 服务、正式构建制品和 E2E 全部指向新版 |
| `ISSUE-058-003` | Legacy 归档与静态解耦 | Legacy 不再被运行、构建或发布链读取；历史源码可读回滚 |
| `ISSUE-058-004` | 验证与正式收口 | 静态契约、新版单测/类型/Lint/构建/E2E、Docker 根路径与真实 URL 通过，治理载体一致 |

## 验收标准

- `frontend-admin-v2` 默认 `base=/`、开发端口 `5173`，Compose 仅有一个正式前端服务。
- Nginx 支持根路径 SPA、深链刷新、API/SSE 代理、健康检查和现有安全头。
- CI 的 lint、type-check、test、build、dependency audit、supply-chain artifact 和 E2E 全部以新版为对象，保留现有 job id 避免未授权的远程保护规则变更。
- `frontend-admin` 不再是 Git 跟踪运行目录；归档源码不参与正式构建和发布。
- 浏览器实际到达 `http://localhost:5173/`，登录、代表路由、静态资源和 API 无 Legacy 回落。

## 风险与回滚

| 风险 | 控制 | 回滚 |
| --- | --- | --- |
| 根路径与路由 base 不一致 | 静态契约 + 深链 E2E + Docker 真实 URL | 恢复 `/v2/` 和独立 `5174` 服务 |
| 新 Nginx 丢失生产安全/代理能力 | 复用 Legacy 已有 TLS、安全头、API/SSE 模板 | 回退正式镜像指针，归档源码仍在 |
| CI 必需检查名漂移 | 保留现有 job id，仅替换实际工作目录与命令 | 恢复 workflow 路径映射 |
| 本机忽略目录或用户数据被搬移 | 只搬迁 `git ls-files frontend-admin/**` 跟踪文件 | 按 Git 跟踪路径反向搬迁 |
| 生产证据被误判 | 报告固定区分仓库/本地切换与目标环境发布 | 生产继续 fail-close |

## 收口

- 输出 `docs/quality/第58条主线-新版前端正式切换与Legacy归档验收报告.md`。
- 回写计划索引、Current Focus、Done Issues、项目地图和必要的开发/运维规范。
- 统计新增后续项、关闭后续项、净变化与无载体悬空项。

## 执行结果

- `ISSUE-058-001～004` 全部通过；新版已接管根路径、`5173`、Compose `frontend`、CI 制品与 E2E。
- Legacy 应用源码 522 个文件归档到 `archive/v1.6/frontend-admin-legacy/`；受保护 `.omc/` 与本机忽略产物保留原位且不进入正式链。
- 新版 432 项单测、91 项迁移门 E2E、Docker 三阶段回滚演练、真实浏览器登录与业务路由均通过。
- 生产与 Git 交付未执行；AutoPilot 控制面指纹变化由单 Issue 金丝雀门 fail-close。
- 归档文件当前未 stage；最终 Git 交付必须使用 `git add -A` 等等价方式完整纳入删除与归档新增，本轮未获授权执行。
- 新增后续项 0、关闭后续项 0、后续项净变化 0；无无载体遗留项。
