# 第58条主线：新版前端正式切换与 Legacy 归档验收报告

> 日期：2026-07-31
> 分支：`codex/v1.6-start`
> 基线：`0ab28a16411a4c0efd00ff7b56e1cd7a92bc8cd8`
> 裁决：`通过（仓库与本地运行态）/ 生产未执行且仍阻塞`

## 结论

`ISSUE-058-001～004` 全部通过。`frontend-admin-v2` 已成为仓库与本地唯一正式前端，接管根路径、`5173`、Compose `frontend`、CI 制品及浏览器门禁；Legacy 应用源码 522 个文件已在工作树归档到 `archive/v1.6/frontend-admin-legacy/`，不再进入正式运行、构建或发布链。

生产没有发布，也没有取得目标环境证据；`REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION` 继续阻塞生产。本报告不把本地通过解释为生产通过。

## 实施事实

- 新版默认 `base=/`、开发端口 `5173`；`/v2/*` 仅作兼容跳转并去除旧前缀。
- Compose 开发环境仅保留一个正式 `frontend` 服务；旧 `frontend-v2` 重复容器已删除。
- CI 的 lint、类型、单测、构建、依赖审计、制品和 E2E 全部读取新版；保留既有 job id 兼容远程保护规则。
- 新版 Nginx 提供 SPA 深链、API/SSE 代理、健康检查、TLS 模板和安全头。
- Legacy 应用可从归档独立构建回滚镜像；受保护 `.omc/` 与本机忽略缓存未读取、未清理、未进入归档制品。

## 验证证据

| 验证 | 结果 |
| --- | --- |
| route ledger | 87 routes、73 views、65 unique；通过 |
| Clean-room boundary | 新版 206 文件 + 契约 16 文件；通过 |
| Design System | 3 文件、81 项；通过 |
| 新版 unit | 55 文件、432 项；通过 |
| lint | 0 error；14 个既有 Prettier warning，不阻塞且未扩展范围 |
| contracts/type/build/bundle | 全部通过；268 modules、57 JS assets |
| migration gate E2E | 91 passed、1 环境门控 skipped、0 failed |
| workflow contract | 13 jobs、9 uploads、3 downloads；通过 |
| 后端静态治理 | `DatabaseGovernanceStaticTest` 6/6；通过 |
| AutoPilot | ready routing、runner compatibility、fingerprint self-test；通过 |
| Compose | dev、prod、M8 静态配置；通过 |
| Docker 回滚 | `V2_ROOT→LEGACY_ROLLBACK→V2_RESTORE`；API、SSE、静态资源、深链和两次浏览器门均通过 |
| 本地运行态 | `cgc-pms-frontend-dev` healthy；`/`、`/health`、代理后端 health 均 200 |
| 应用内浏览器 | `/v2/dashboard` 跳转 `/login?redirect=/dashboard`；本地登录进入 `/dashboard`；`/project/list` 真实数据可见 |
| 差异检查 | `git diff --check` 通过 |

首次 migration E2E 失败 37 项，唯一原因是测试仍匹配 `/v2` 旧入口，分类为 `quality_or_security / DELIVERY_GATE_OMISSION`；统一改为根路径后 91/91 通过。路由单测首次 1 项旧前缀断言失败，同根因修正后 432/432 通过。

## 回滚与剩余门禁

- 回滚：使用 `archive/v1.6/frontend-admin-legacy/` 独立构建旧镜像并切换边缘上游；本轮已实测恢复新版。
- AutoPilot：当前控制面指纹 `51cff640086c50dd13cab5f0f9369c1ad51acb41cfb7cb7ced4141d17dfeac29` 与上次金丝雀不同；进入 N>1 或无界执行前必须明确发送 `启动迭代-1`。现有门禁会 fail-close，不构成悬空事项。
- 未授权且未执行：commit、push、PR、Tag、GitHub Release、目标环境变更、生产发布。
- 未授权且未执行 stage：522 个 Legacy 删除与 522 个归档新增尚未进入 Git index；交付时必须完整纳入两侧，禁止只提交删除。

## 零悬空收口

- 本轮修复并复验：正式入口、CI/Compose、路由兼容、Docker 回滚、控制面路径。
- 超出范围并由既有载体承接：三项生产 `RELEASE_GATE`。
- 证据不足或无明确价值而关闭：14 个既有格式 warning 不影响本轮目标，不扩展为格式化改造。
- 新增后续项 0、关闭后续项 0、后续项净变化 0；无无载体遗留项。
