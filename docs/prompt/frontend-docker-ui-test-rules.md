# 前端 Docker 与 UI 测试规则

适用范围：`frontend-admin` 页面重构、UI 对齐、交互修复、Codex 内置浏览器验收和 Playwright 回归。

## 基准环境

- 前端验收以 Docker 中运行的前端服务为准。
- 默认访问地址：`http://localhost:5173/`。
- 不以本地构建产物、旧浏览器标签页或未重启的 Vite 运行态作为最终验收依据。

## 默认视口

- Codex 内置浏览器桌面端默认验收口径：Docker 前端 + Codex 内置浏览器，缩放比例 `67%`，实测 viewport 约 `1714x964`。
- 严格 `1920x1080` 仅用于外部 Playwright/Chrome 验收，或用户明确要求严格桌面尺寸的场景；不作为 Codex 内置浏览器默认验收尺寸。
- 若浏览器缩放、窗口大小或宿主窗口状态发生变化，先读取并记录 `window.innerWidth` / `window.innerHeight`，再按实际尺寸验收。

## 前端改动后的服务刷新

前端代码改动后，必须重启 Docker 前端服务：

```powershell
docker compose -f deploy/docker-compose.dev.yml restart frontend
```

重启后统一等待 `180秒` 再进行 UI 验收。即使日志很快显示 Vite ready，也要给依赖安装、热更新、页面缓存和浏览器状态留出稳定时间。

## Ready 检查

验收前查看前端服务日志：

```powershell
docker compose -f deploy/docker-compose.dev.yml logs --tail=80 frontend
```

确认出现类似输出：

```text
VITE v6.x.x ready
Local: http://localhost:5173/
```

## UI 验收规则

- UI 视觉验收默认使用 Codex 内置浏览器，运行态以 Docker 前端 `http://localhost:5173/` 为准。
- Playwright 用于 E2E、自动化回归、DOM 批量断言或用户明确要求的场景。
- 需要严格对齐尺寸时，先记录当前 `window.innerWidth` / `window.innerHeight`；仅在外部 Playwright/Chrome 或用户明确要求时使用严格 `1920x1080`。
- 默认登录账号：`admin / admin123`。
- 避免滥用 `networkidle`，部分页面存在持续请求，可能导致等待超时。
- 推荐使用：
  - `waitUntil: 'domcontentloaded'`
  - 再等待关键元素可见，例如页面标题、按钮、表格、弹窗、抽屉、面板等。

## 推荐验收流程

```text
修改前端代码
→ 运行相关测试或 pnpm build
→ 重启 Docker frontend
→ 等待 `180秒`
→ 查看日志确认 Vite ready
→ Codex 内置浏览器打开目标页面
→ 打开目标页面
→ 验证视觉、DOM 尺寸和关键交互
```

## 常见误判排查

如果页面没有变化，先检查：

- Docker 前端服务是否已经重启。
- 是否等待了 `180秒`。
- Codex 内置浏览器是否访问 `http://localhost:5173/`。
- 是否命中隐藏文本、旧 DOM 或多个同名元素。
- Playwright strict mode 是否因为多个匹配元素而点击失败。
- 当前页面是否仍由缓存、旧标签页或未刷新状态展示。
