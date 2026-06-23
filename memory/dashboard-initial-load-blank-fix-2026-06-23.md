---
name: dashboard-initial-load-blank-fix-2026-06-23
description: 修复首页驾驶舱首屏只加载项目列表、不加载默认项目数据导致页面空白的问题
metadata:
  type: feedback
  tags:
    - frontend
    - dashboard
    - vue
    - initial-load
    - vite
---

# 首页驾驶舱首屏空白修复

## 现象

访问 `/dashboard` 后只显示项目下拉和角色页签，项目总区域没有 KPI、图表或表格数据。页面没有明显报错，用户看到的是大面积空白。

## 根因

`useDashboardData()` 的 `onMounted` 只调用 `fetchProjects()`，没有触发首屏 `fetchViewData()`。默认角色为 `pm`，但 `selectedProjectId` 初始为空；在用户未切换页签或手动选择项目前，`watch([activeRole, selectedProjectId])` 不会触发，因此首屏数据不会加载。

调试时还遇到 Vite transform cache 未立即刷新：容器内源码已更新，但浏览器实际请求的 `/src/pages/dashboard/composables/useDashboardData.ts` 仍是旧的 `onMounted(() => { fetchProjects(); })`。强制重建前端容器并等待 2 分钟后恢复。

## 修复

- `onMounted` 改为异步初始化：
  - 先 `await fetchProjects()`。
  - 如果当前角色需要项目且尚未选择项目，自动选择项目列表第一项。
  - 随后 `await fetchViewData()`，确保首屏立即加载数据。
- 补充 dashboard 数据加载测试，锁定首屏必须加载项目并初始化驾驶舱数据。

## 验证

- `pnpm test:unit -- src/pages/dashboard/__tests__/DashboardDataLoading.test.ts` 通过，18 tests。
- `pnpm type-check` 通过。
- `python scripts\rebuild.py frontend` 后发现 Vite 仍返回旧模块，执行 `docker compose -f docker-compose.dev.yml up -d --force-recreate frontend` 并等待 2 分钟。
- `curl http://127.0.0.1:5173/src/pages/dashboard/composables/useDashboardData.ts` 确认加载到新的 `onMounted(async () => ...)`。
- 内置浏览器刷新 `/dashboard` 后 DOM 显示默认选中 `auto-project-001`，并渲染 `待办任务`、`项目经营概览`、表格等面板内容。

## 注意

Docker 重启后至少等待 2 分钟再做浏览器判断。Vite 截图 API 可能超时，必要时优先使用 DOM 状态、Vite 模块内容和后端日志验证。
