---
name: cgc-pms-runtime-refresh
description: 用于 cgc-pms 本地运行态刷新与验真：处理 Docker、backend、frontend、Vite 代理、dev-login、旧 backend 代理漂移和真实 URL 可达性。仅在用户要求重建、刷新、浏览器验收或排查本地运行态时使用。
---

# cgc-pms 本地运行态刷新

根规则由 Codex 自动加载，本 Skill 只保存运行态领域步骤。

1. 先把环境前置与业务失败分开；分类名称及处理原则引用 `../cgc-pms-ci-gate-triage/SKILL.md`，不在此复制。
2. 按目标页面选择入口：
   - 后端健康：`http://localhost:8080/api/actuator/health`
   - 正式前端：`http://localhost:5173/`
   - dev-login：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`
3. 首次浏览器验收只初始化一次能力和页面状态；不猜 API/参数，同一参数错误不得原样重试。
4. 服务刷新后读取 `scripts/codex-autopilot/codex-autopilot.config.json` 的 `runtimeRefresh.waitSeconds` 作为最大就绪超时；轮询 health、端口、最终路由和关键接口，就绪即提前返回，不做固定等待。

## G4 运行态与浏览器门禁

G4 只有同时满足以下条件才通过：

- 实际 backend 数据源、租户、演示数据和功能开关与 G0 基线一致；
- health、目标端口、最终路由和关键接口均成功；
- 用户指定内置浏览器时，必须使用内置浏览器完成验收；
- 每个验收阶段证明新 DOM 标识存在、旧 DOM 标识消失、控制台无相关错误；
- 浏览器连接、旧容器、旧前端或测试数据未就绪时，分类为 `environment_prerequisite`，不得判定业务通过。

G4 未通过时，只允许修复运行前置并在配置最大超时内等待就绪后复验；不得用普通 reload、mock 页面、局部 API 或自动化单测替代真实浏览器证据。

5. 前端回到 `/login` 且日志显示 `/api/*` 指向旧 backend 容器 IP 时，先刷新对应前端 dev server，再排查路由守卫或业务逻辑。
6. 源码和前端验证已包含新文案/控件，但浏览器 DOM 仍出现变更前内容时，判为 `environment_prerequisite` 的旧前端运行态，不判业务整改失败。先确认实际前端进程及源码挂载，再只刷新承载 Vite 的前端服务；不得顺带重建 backend、重启数据库或重复修改业务代码。
7. 刷新后在 `runtimeRefresh.waitSeconds` 最大超时内轮询就绪，就绪即重新进入目标 URL。复验必须同时证明：新 DOM 标识存在、旧 DOM 标识消失、控制台无相关错误；只看到页面可访问或只执行普通 reload 不算完成。前端服务不存在、源码未挂载或最小刷新失败时，才使用配置中的完整 `runtimeRefresh.command`。
8. 并行或批量验收使用唯一输出目录，不共享截图、Playwright 报告、测试结果或缓存。
9. 回报实际 URL、最终落点、关键端口/日志/HTTP 证据、失败分类和复验结果；不能只报命令退出码。

## 最小回报

```text
刷新范围=
访问 URL=
实际落点=
关键证据=
失败分类=
输出目录=
下一步=
```
