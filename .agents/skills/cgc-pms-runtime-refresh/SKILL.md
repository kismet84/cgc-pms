---
name: cgc-pms-runtime-refresh
description: 用于 cgc-pms 本地运行态刷新与验真：处理 Docker、backend、frontend、Vite 代理、5173 dev-login、旧 172.19.x.x:8080 代理漂移和真实 URL 可达性。当用户要求重建前后端、刷新本地运行态、排查页面跳登录或确认 localhost 访问是否真实可用时使用。
---

# cgc-pms 本地运行态刷新

通用协议=`docs/standards/codex-task-execution-policy.md`

1. 先读仓库根 `AGENTS.override.md`、`AGENTS.md` 与通用协议，运行态问题先和代码问题分开判断。
2. 每个线程首次做浏览器验收时只初始化一次工具能力和页面状态；不猜测 API/参数，同一参数错误不得原样重试。
3. 默认健康与前端验收入口：
   - `http://localhost:8080/api/actuator/health`
   - `http://localhost:5173/`
   - 跳登录验收入口：`http://localhost:5173/api/auth/dev-login?redirect=/dashboard`
4. 运行态刷新后，后端和前端统一按当前项目规则或动态配置的稳定等待时间再验真；不得在等待窗口内把未就绪定性为业务失败。
5. 若 `http://localhost:5173` 回到 `/login`，且前端日志出现 `/api/*` 指向旧 `172.19.x.x:8080` 的 `ECONNREFUSED` 或同类代理错误，优先判定为前端 dev server 持有旧 backend 容器 IP；先刷新前端运行态，再决定是否继续排查路由守卫或后端逻辑。
6. 并行或批量验收必须使用唯一输出目录，不共享截图名、Playwright 报告、测试结果或缓存目录。
7. 需要回报真实可达性时，至少说明：
   - 实际访问 URL
   - 最终落点页面或路由
   - 关键日志或端口证据
   - 当前问题属于环境前置还是业务质量
8. 不要只报命令退出码；要给真实 URL 状态与浏览器或 HTTP 结果。

## 最小回报骨架

```text
刷新范围=
访问 URL=
实际落点=
关键证据=
问题分类=环境前置类 / 真实质量安全类 / 需要确认
输出目录=
下一步=
```
