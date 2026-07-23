---
name: cgc-pms-runtime-refresh
description: 用于 cgc-pms 本地运行态刷新与验真：处理 Docker、backend、frontend、Vite 代理、dev-login、旧 backend 代理漂移和真实 URL 可达性。仅在用户要求重建、刷新、浏览器验收或排查本地运行态时使用。
---

# cgc-pms 本地运行态刷新

根规则由 Codex 自动加载，本 Skill 只保存运行态领域步骤。

1. 先把环境前置与业务失败分开；分类名称及处理原则引用 `../cgc-pms-ci-gate-triage/SKILL.md`，不在此复制。
2. 按目标页面选择入口：
   - 后端健康：`http://localhost:8080/api/actuator/health`
   - Legacy 前端：`http://localhost:5173/`
   - Clean-room V2：`http://localhost:5174/v2/`
   - dev-login：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`
3. 首次浏览器验收只初始化一次能力和页面状态；不猜 API/参数，同一参数错误不得原样重试。
4. 服务刷新后读取 `scripts/codex-autopilot/codex-autopilot.config.json` 的 `runtimeRefresh.waitSeconds`，稳定等待后再检查 health、端口、最终路由和关键接口。
5. 前端回到 `/login` 且日志显示 `/api/*` 指向旧 backend 容器 IP 时，先刷新对应前端 dev server，再排查路由守卫或业务逻辑。
6. 并行或批量验收使用唯一输出目录，不共享截图、Playwright 报告、测试结果或缓存。
7. 回报实际 URL、最终落点、关键端口/日志/HTTP 证据、失败分类和复验结果；不能只报命令退出码。

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
