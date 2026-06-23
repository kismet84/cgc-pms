---
name: lg-design-token-alignment-2026-06-24
description: 将现有 lg-* 布局体系保留为结构层，并把视觉 Token 收敛到项目列表页企业级 UI 基准
metadata:
  type: feedback
  tags:
    - frontend
    - ui-design
    - design-system
    - css
    - lg-system
---

# lg-* 设计系统视觉基准收敛

## 背景

项目已有 `lg-*` 列表/台账页面共享类，覆盖合同、成本、付款、采购、库存、结算、系统设置等多个页面。用户确认采用 `docs/00-UI-Design-Baselines-and-Code-Specifications.md` 中的“项目列表页”视觉风格作为系统级设计基准，同时保留 `lg-*` 的布局和组件类名体系。

## 根因

原 `lg-*` 视觉来自 `ContractLedgerPage` 台账页，主色、圆角、阴影、表头色与新 UI 基准不一致：

- 主色为 `#1668dc`，而新基准为 `#1677FF`。
- 圆角以 `8px` 为主，而新基准为 `4px`。
- 阴影偏重，台账感较强；新基准要求轻阴影。
- vxe 表头使用 `#ebede9`，与企业级中性色规范不一致。

## 修复

- 在 `frontend-admin/src/assets/styles/global.css` 中将全局 Token 切换到新 UI 基准：主色、状态色、背景色、文本色、边框、圆角、阴影统一更新。
- 保留 `lg-page`、`lg-search-bar`、`lg-grid`、`lg-kpi-strip`、`lg-table-wrap`、`lg-panel` 等结构类名，避免重写页面模板。
- 收敛 `lg-*` 视觉细节：搜索框高度改为更通用的 `40px`，KPI 数字改为 `24px/600`，表格表头改用 `var(--surface-subtle)`，表格 hover 改用 `var(--bg)`。

## 验证

- `frontend-admin` 下执行 `pnpm build` 通过，包含 `vue-tsc --noEmit` 与 `vite build`。
- LSP CSS 诊断被本机缺少 `biome` 阻断，不能作为本次判断依据；以项目实际构建链为准。

## 注意

后续若继续统一页面，不要删除 `lg-*` 类名体系。优先清理页面局部硬编码颜色、局部 `.lg-breadcrumb`、内联 `style`，让它们继承全局 Token。
