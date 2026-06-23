# 全系统 UI 重构计划

> 制定依据：`docs/prompt/dev-plan.md` 与 `docs/00-UI-Design-Baselines-and-Code-Specifications.md`。
>
> 重构原则：选用新文档视觉风格作为系统级设计基准，保留 `lg-*` 作为布局和组件类名体系。

## 一、任务拆解

### 模块 A：设计基准与 Token 体系统一

#### 任务 A1：固化系统级 UI Token 基准

说明：以 `docs/00-UI-Design-Baselines-and-Code-Specifications.md` 为唯一视觉基准，统一颜色、字体、圆角、阴影、间距、表格、按钮、输入框等基础 Token。保留现有 `lg-*` 类名体系作为结构层。

- 输入：UI 基准文档、`frontend-admin/src/assets/styles/global.css`、当前 `lg-*` 类名体系
- 输出：全局 CSS Token 稳定版本，`lg-*` 结构类继续可用，页面视觉统一继承 Token
- 涉及文件建议：
  - 🔨修改 `frontend-admin/src/assets/styles/global.css`
  - 🔨修改 `docs/05-前端开发规范.md`
  - ✨新增 `"docs/superpowers/plans/2026-06-24-ui-refactor-plan.md`
- 复杂度：P0
- 验收标准：
  - Given 当前项目存在多套颜色、圆角、阴影风格；When 打开 `global.css`；Then 所有基础视觉变量必须以新 UI 基准为准。
  - Given 任意使用 `lg-*` 的页面；When 页面渲染；Then 页面结构不变，但按钮、卡片、表格、KPI、搜索栏视觉自动继承新 Token。
  - Given 开发者新增页面；When 查阅前端规范；Then 能明确知道应使用共享结构类，而不是新增独立风格。

#### 任务 A2：建立 UI 重构检查清单

说明：建立页面迁移清单，明确哪些页面已经对齐、哪些页面仍有局部硬编码、哪些页面需要视觉复查。

- 输入：`frontend-admin/src/pages/**`、`global.css`、当前使用 `lg-*` 的页面/组件
- 输出：页面级迁移状态表、模块重构优先级、待清理硬编码列表
- 涉及文件建议：
  - ✨新增 `docs/quality/ui-refactor-checklist.md`
  - ✨新增 `frontend-admin/src/assets/styles/README.md`
- 复杂度：P0
- 验收标准：
  - Given 全系统包含多个业务模块；When 打开检查清单；Then 能看到每个模块的迁移状态。
  - Given 页面存在硬编码颜色或尺寸；When 执行检查；Then 能定位到文件路径、样式片段、建议替换 Token。
  - Given 后续继续重构；When 开发者查看清单；Then 能按优先级推进，不重复评估已完成页面。

### 模块 B：全局布局与导航统一

#### 任务 B1：统一应用壳层视觉

说明：将主布局、顶部栏、侧边栏、菜单激活态统一到新 UI 基准，尤其侧边栏父级和子级选中态要与项目列表页一致。

- 输入：`frontend-admin/src/layouts/**`、`frontend-admin/src/router`、新 UI 基准文档
- 输出：统一侧边栏背景、Logo 区、菜单 hover、菜单 selected、collapsed 状态
- 涉及文件建议：
  - 🔨修改 `frontend-admin/src/layouts/BasicLayout.vue`
  - 🔨修改 `frontend-admin/src/layouts/components/**`
  - 🔨修改 `frontend-admin/src/assets/styles/global.css`
- 复杂度：P0
- 验收标准：
  - Given 用户访问任意模块；When 当前路由处于某个菜单节点下；Then 父级菜单和子级菜单选中态必须一致使用新主色体系。
  - Given 用户折叠侧边栏；When 鼠标 hover 图标菜单；Then hover、selected、tooltip 不应出现旧蓝色或旧灰色。
  - Given 页面内容较长；When 滚动页面；Then 顶栏、侧边栏、内容区之间没有视觉错位或白边断层。

#### 任务 B2：统一页面外层结构

说明：所有业务页统一使用 `lg-page app-page` 或兼容包装结构，避免页面边距、背景、最小高度不一致。

- 输入：`frontend-admin/src/pages/**`、`global.css`
- 输出：页面背景、内容区 padding、头部间距统一，局部页面不再自定义大面积背景和边距
- 涉及文件建议：🔨修改 `frontend-admin/src/pages/**/*.vue`
- 复杂度：P1
- 验收标准：
  - Given 任意业务列表页；When 页面加载完成；Then 页面背景应为统一浅灰，主体卡片为白色，顶部间距一致。
  - Given 页面没有数据；When 显示空状态；Then 空状态区域不应因页面自定义 padding 出现偏移。
  - Given 浏览器宽度小于 1280px；When 页面响应式折叠；Then 主区域与右侧分析栏应按 `lg-grid` 规则稳定堆叠。

### 模块 C：列表页 / 台账页统一

#### 任务 C1：统一搜索栏与筛选区

说明：所有列表页统一搜索栏结构、输入框高度、按钮大小、占位文案、查询/重置交互。

- 输入：当前各模块搜索栏实现、`lg-search-bar`、Ant Design Vue 表单组件
- 输出：通用搜索栏视觉一致，查询/重置按钮顺序一致，占位文案简洁中文化
- 涉及文件建议：🔨修改 `frontend-admin/src/pages/**/*.vue`、`frontend-admin/src/assets/styles/global.css`
- 复杂度：P1
- 验收标准：
  - Given 用户进入任意列表页；When 查看搜索栏；Then 输入框、查询按钮、重置按钮高度和间距一致。
  - Given 用户输入关键词后点击查询；When API 返回数据；Then 表格刷新，分页回到第一页。
  - Given 用户点击重置；When 当前存在关键词、状态、项目筛选；Then 筛选条件清空，数据恢复默认列表。
  - Given 某模块存在高级筛选；When 展开/收起筛选项；Then 不应破坏基础搜索栏布局。

#### 任务 C2：统一工具栏与操作按钮

说明：统一“新建、刷新、列设置、导出、批量操作”等工具栏按钮位置、文案、图标、尺寸和权限态。

- 输入：`lg-toolbar`、各业务模块操作栏、权限控制逻辑
- 输出：左侧主操作，右侧筛选/辅助操作；主要按钮统一蓝色；默认按钮统一白底灰边
- 涉及文件建议：🔨修改项目、合同、付款、采购、变更、系统用户、系统角色等列表页
- 复杂度：P1
- 验收标准：
  - Given 用户有新增权限；When 打开列表页；Then “新建/新增”按钮位于工具栏左侧第一位，样式为主按钮。
  - Given 用户无新增权限；When 打开列表页；Then 主按钮不显示或禁用，工具栏布局不塌陷。
  - Given 用户点击刷新；When 请求完成；Then 表格数据刷新，按钮不出现重复 loading 或错位。
  - Given 页面支持列设置；When 打开列设置下拉；Then 菜单宽度、复选项间距、hover 态统一。

#### 任务 C3：统一表格视觉与操作列

说明：所有 `vxe-grid` / Ant Table 页面统一表头背景、字体、行高、hover、边框、金额数字、操作链接。

- 输入：`lg-table-wrap`、`vxe-grid`、各页面 columns 配置
- 输出：表格统一视觉、操作列统一为文字链接、删除/危险操作统一红色、金额列使用 tabular number
- 涉及文件建议：🔨修改 `frontend-admin/src/assets/styles/global.css`、`frontend-admin/src/pages/**/*.vue`
- 复杂度：P0
- 验收标准：
  - Given 任意业务表格；When 页面渲染；Then 表头背景、字体大小、字体颜色、边框颜色与 `lg-table-wrap` 一致。
  - Given 用户 hover 表格行；When 鼠标经过；Then 行背景变为统一 hover 色，不出现模块自定义颜色。
  - Given 表格包含金额列；When 金额显示；Then 小数对齐稳定，使用 `lg-money` 或等价数字样式。
  - Given 操作列包含查看、编辑、删除；When 用户查看操作列；Then 普通操作为蓝色文字，删除为红色文字，无下划线。
  - Given 表格为空；When API 返回空数组；Then 空状态文案为中文简洁表达。

### 模块 D：KPI 与分析面板统一

#### 任务 D1：统一 KPI 统计卡片

说明：统一各模块顶部统计卡片布局、数字字号、单位、图标、风险态、移动端降级。

- 输入：`lg-kpi-strip`、`lg-kpi-card`、各模块统计数据
- 输出：4/5 列弹性网格、数字 `24px`、单位小字、风险卡片统一浅红背景
- 涉及文件建议：🔨修改合同、发票、收货、库存、成本、付款、分包等 KPI 组件或页面
- 复杂度：P1
- 验收标准：
  - Given 页面包含 5 个统计指标；When 桌面宽度打开页面；Then KPI 应为 5 列等宽布局。
  - Given 页面只包含 2~4 个统计指标；When 桌面宽度打开页面；Then 卡片尺寸、字号、阴影必须一致。
  - Given KPI 数值为空或接口返回 null；When 页面渲染；Then 显示 `0` 或 `--`，不能出现 `NaN`、`undefined`。
  - Given 浏览器宽度小于 1100px；When 页面响应式变化；Then KPI 自动折叠为 2 列或 1 列，不横向溢出。

#### 任务 D2：统一右侧分析栏

说明：统一类型分布、状态分布、风险预警、临期提醒等右侧分析卡片的视觉和数据空态。

- 输入：`lg-analysis-rail`、`lg-panel`、`lg-type-list`、`lg-warning-list`
- 输出：分析栏宽度统一、色条列表统一、预警列表统一、空状态统一
- 涉及文件建议：🔨修改合同、发票、库存、付款、成本、结算、采购等分析面板
- 复杂度：P1
- 验收标准：
  - Given 页面有右侧分析栏；When 桌面端打开；Then 右侧栏宽度为统一结构，不随页面局部样式漂移。
  - Given 分布数据为空；When 页面渲染；Then 显示统一中文空状态，如“暂无统计数据”。
  - Given 预警列表存在风险项；When 页面渲染；Then 风险项使用浅红背景、红色强调数字。
  - Given 页面宽度小于 1280px；When 响应式折叠；Then 分析栏从右侧栏转换为下方卡片区，布局不溢出。

### 模块 E：表单、弹窗、详情页统一

#### 任务 E1：统一新增/编辑弹窗

说明：统一 Ant Design Modal 的宽度、标题、表单栅格、按钮文案、确认/取消顺序、校验提示。

- 输入：各模块新增/编辑 Modal、Ant Design Vue Form
- 输出：统一弹窗视觉、表单项间距、校验文案
- 涉及文件建议：🔨修改系统用户、角色、字典、物料、组织、合作方等表单页面
- 复杂度：P1
- 验收标准：
  - Given 用户点击新增；When 弹窗打开；Then 标题、宽度、表单间距、按钮位置统一。
  - Given 必填项为空；When 用户点击确认；Then 校验提示为中文业务表达，不出现后端字段名或英文混排。
  - Given 保存接口失败；When 后端返回错误；Then 页面显示中性中文错误提示，并保持用户已输入内容。
  - Given 用户点击取消；When 表单已有编辑内容；Then 弹窗关闭或按已有确认规则处理，不残留 loading 状态。

#### 任务 E2：统一详情页与 Drawer

说明：统一详情页、抽屉、审批流详情、业务单据详情的信息密度、标题层级、分组卡片和操作按钮。

- 输入：合同详情、项目详情、库存详情、审批详情等
- 输出：详情页标题区统一、信息分组统一、操作区统一、状态标签统一
- 涉及文件建议：🔨修改项目、合同、审批、库存、结算等详情页
- 复杂度：P2
- 验收标准：
  - Given 用户进入详情页；When 页面加载完成；Then 标题、状态、主操作按钮位于统一区域。
  - Given 详情数据加载中；When 请求未完成；Then 显示统一 loading，不出现空白闪烁。
  - Given 详情数据不存在或无权限；When API 返回异常；Then 显示统一错误/无权限状态，并提供返回入口。
  - Given 页面包含审批状态；When 状态变化；Then 标签颜色和文案符合系统状态色规范。

### 模块 F：文案、空状态、错误提示统一

#### 任务 F1：统一中文文案语气

说明：清理“接口请求失败”“No Data”“submit success”等非业务化或英文混排文案，统一为简洁、中性中文表达。

- 输入：`frontend-admin/src/**`、API 错误处理逻辑
- 输出：页面提示文案统一、空状态文案统一、弹窗按钮统一
- 涉及文件建议：
  - 🔨修改 `frontend-admin/src/api/request.ts`
  - 🔨修改 `frontend-admin/src/components/**`
  - 🔨修改 `frontend-admin/src/pages/**/*.vue`
  - ✨新增 `frontend-admin/src/constants/uiText.ts`
- 复杂度：P1
- 验收标准：
  - Given API 请求失败；When 前端捕获错误；Then 用户看到简洁中文提示，不直接暴露技术错误。
  - Given 列表为空；When 页面渲染；Then 显示统一文案，如“暂无数据”。
  - Given 弹窗二次确认；When 用户执行删除、撤回、驳回等操作；Then 按钮文案统一为“确认 / 取消”或业务化中文。
  - Given 页面存在英文状态值；When 渲染给用户；Then 必须映射为中文业务文案。

### 模块 G：组件抽象与复用

#### 任务 G1：抽象通用列表页组件

说明：在不破坏现有页面的前提下，将高频结构抽象为可复用组件，减少页面重复样式和重复模板。

- 输入：`lg-*` 类名体系、各列表页重复结构
- 输出：通用页面头、搜索栏、工具栏、KPI 卡片、空状态组件
- 涉及文件建议：
  - ✨新增 `frontend-admin/src/components/list-page/LgPageHeader.vue`
  - ✨新增 `frontend-admin/src/components/list-page/LgSearchBar.vue`
  - ✨新增 `frontend-admin/src/components/list-page/LgToolbar.vue`
  - ✨新增 `frontend-admin/src/components/list-page/LgKpiCard.vue`
  - ✨新增 `frontend-admin/src/components/list-page/LgEmptyState.vue`
  - ✨新增 `frontend-admin/src/components/list-page/index.ts`
- 复杂度：P2
- 验收标准：
  - Given 新增一个列表页；When 使用通用组件搭建；Then 不需要手写重复搜索栏、工具栏、KPI 基础结构。
  - Given 旧页面暂未迁移组件；When 全局 Token 更新；Then 旧页面仍能通过 `lg-*` 类继承视觉变化。
  - Given 通用组件 props 缺省；When 页面传入最少配置；Then 组件仍能显示合理默认状态。
  - Given 通用组件接收 slot；When 页面需要业务扩展；Then 可以插入自定义筛选项、按钮或 KPI 内容。

### 模块 H：视觉回归与质量门禁

#### 任务 H1：建立 UI 重构验证脚本

说明：建立基础自动化检查，至少覆盖构建、类型检查、关键页面冒烟截图或 DOM 验证。

- 输入：Playwright E2E、Vitest、Vite build
- 输出：UI 重构专用验证命令、关键页面冒烟用例、构建通过作为基础门禁
- 涉及文件建议：
  - 🔨修改 `frontend-admin/e2e/**`
  - ✨新增 `frontend-admin/e2e/ui-refactor-smoke.spec.ts`
  - 🔨修改 `frontend-admin/package.json`
  - 🔨修改 `docs/09-测试规范.md`
- 复杂度：P0
- 验收标准：
  - Given 开发者完成一批 UI 重构；When 执行 `pnpm build`；Then TypeScript 和 Vite 构建必须通过。
  - Given 执行 UI 冒烟测试；When Playwright 打开核心页面；Then 页面无空白、无控制台关键错误、主要区域可见。
  - Given 某页面因接口 mock 或权限问题无法加载；When 测试失败；Then 错误信息应能定位到具体路由和缺失状态。

## 二、改动文件清单与计划

- 🔨修改 `frontend-admin/src/assets/styles/global.css`：继续完善 Token 映射，清理剩余硬编码颜色、阴影、圆角、表格 hover、Ant Design 局部覆盖。保留 `lg-*` 类名结构，避免页面大规模模板重写。
- ✨新增 `"docs/superpowers/plans/2026-06-24-ui-refactor-plan.md`：记录 UI 重构原则：新文档视觉为基准，`lg-*` 为结构层，禁止新增局部视觉体系。
- ✨新增 `docs/quality/ui-refactor-checklist.md`：记录所有页面迁移状态、待清理硬编码、视觉复查优先级、责任模块和验收结果。
- 🔨修改 `docs/05-前端开发规范.md`：将 UI Token、`lg-*` 页面结构、列表页规范、表格规范、KPI 规范写入正式前端开发规范。
- 🔨修改 `docs/09-测试规范.md`：增加 UI 重构后的构建、冒烟测试、视觉回归建议。
- 🔨修改 `frontend-admin/src/layouts/BasicLayout.vue`：统一主布局背景、内容区间距、顶部栏和侧边栏视觉。
- 🔨修改 `frontend-admin/src/layouts/components/**`：统一菜单激活态、Logo 区、折叠菜单、用户菜单、面包屑等视觉细节。
- 🔨修改 `frontend-admin/src/pages/project/index.vue`：作为标杆页面，确保其与新 UI 基准完全一致。
- 🔨修改 `frontend-admin/src/pages/contract/ContractLedgerPage.vue`：保留台账结构，收敛表格、KPI、分析栏、操作栏视觉。
- 🔨修改 `frontend-admin/src/pages/contract/components/ContractKpiStrip.vue`：对齐 KPI 卡片字号、单位、间距、风险态。
- 🔨修改 `frontend-admin/src/pages/contract/components/ContractAnalysisPanel.vue`：对齐右侧分析栏、分布条、预警列表。
- 🔨修改 `frontend-admin/src/pages/cost/ledger.vue`：统一成本台账搜索栏、KPI、表格、分析栏和预警样式。
- 🔨修改 `frontend-admin/src/pages/cost/summary.vue`：统一成本汇总页的 KPI、图表卡片、异常明细和分析面板。
- 🔨修改 `frontend-admin/src/pages/payment/index.vue`：统一付款列表页 KPI、表格、右侧资金风险分析和临期提醒。
- 🔨修改 `frontend-admin/src/pages/purchase/order.vue`：统一采购订单列表、状态分布、工具栏和筛选控件。
- 🔨修改 `frontend-admin/src/pages/inventory/**/*.vue`：统一库存、仓库、出入库、采购申请等页面的 KPI、表格、移动卡片与分析栏。
- 🔨修改 `frontend-admin/src/pages/subcontract/**/*.vue`：统一分包任务、分包计量页面的搜索栏、KPI 和表格样式。
- 🔨修改 `frontend-admin/src/pages/settlement/index.vue`：统一结算列表、状态分布、未付提醒和表格操作列。
- 🔨修改 `frontend-admin/src/pages/invoice/**/*.vue`：统一发票列表、核验面板、异常提醒、KPI 组件。
- 🔨修改 `frontend-admin/src/pages/approval/**/*.vue`：统一审批中心列表、状态标签、待办/已办/抄送页结构和文案。
- 🔨修改 `frontend-admin/src/pages/system/**/*.vue`：统一用户、角色、字典、系统设置类页面的列表、弹窗、表格和操作按钮。
- 🔨修改 `frontend-admin/src/pages/org/**/*.vue`：统一组织架构、公司、部门、岗位面板的卡片、树、表单与按钮。
- 🔨修改 `frontend-admin/src/pages/material/dictionary.vue`：统一物料字典列表、搜索、弹窗、表格和状态标签。
- 🔨修改 `frontend-admin/src/pages/partner/index.vue`：统一合作方列表和弹窗表单。
- ✨新增 `frontend-admin/src/components/list-page/LgPageHeader.vue`：封装页面头部、面包屑、标题、右侧操作区。
- ✨新增 `frontend-admin/src/components/list-page/LgSearchBar.vue`：封装通用搜索栏，支持关键词、扩展筛选 slot、查询、重置。
- ✨新增 `frontend-admin/src/components/list-page/LgToolbar.vue`：封装列表工具栏，规范左主操作和右辅助操作。
- ✨新增 `frontend-admin/src/components/list-page/LgKpiCard.vue`：封装 KPI 卡片，支持 label、value、unit、trend、warn、bar。
- ✨新增 `frontend-admin/src/components/list-page/LgEmptyState.vue`：封装统一空状态，替换各页面局部空状态。
- ✨新增 `frontend-admin/src/components/list-page/index.ts`：统一导出列表页共享组件。
- ✨新增 `frontend-admin/src/constants/uiText.ts`：收敛通用中文文案。
- 🔨修改 `frontend-admin/src/api/request.ts`：统一 API 错误提示的中文化、去技术化表达。
- ✨新增 `frontend-admin/e2e/ui-refactor-smoke.spec.ts`：覆盖项目列表、合同台账、成本台账、付款列表、库存列表、审批中心、系统用户等核心页面。
- 🔨修改 `frontend-admin/package.json`：增加 UI 冒烟测试脚本，如 `test:e2e:ui` 或 `smoke:ui`。

## 三、数据流变化

本次是 UI 重构，原则上不改变后端 API、数据库结构、接口字段和业务流程。主要变化集中在前端展示层、状态映射和用户交互反馈。

### 1. 列表页查询流程

```text
用户输入筛选条件
→ 点击查询
→ 前端更新查询参数
→ 调用 GET /api/xxx 或 POST /api/xxx/page
→ 后端返回分页数据
→ 前端更新 tableData / total
→ 表格、分页、KPI、分析栏重新渲染
```

变化点：查询栏视觉统一为 `lg-search-bar`；查询/重置按钮位置统一；空结果统一显示“暂无数据”；错误提示统一通过全局中文文案处理。

### 2. KPI 数据展示流程

```text
页面加载
→ 请求列表数据或统计接口
→ 前端计算/接收 KPI 指标
→ 传入 lg-kpi-card / 模块 KPI 组件
→ 按统一 Token 渲染数字、单位、风险态
```

变化点：KPI 数据格式不变；显示层统一处理 null、undefined、NaN；单位如“个”“万元”“%”统一使用小字号；风险态统一使用 `--error` / `--error-soft`。

### 3. 表格操作流程

```text
用户点击查看/编辑/删除/提交审批
→ 前端执行路由跳转、打开弹窗或调用 API
→ 接口成功后刷新列表
→ 接口失败后显示统一中文错误提示
```

变化点：操作列视觉统一为 `lg-link` / `lg-del`；删除等危险操作统一红色；二次确认弹窗文案统一。

### 4. 右侧分析栏数据流

```text
页面列表数据或统计接口返回
→ 前端计算状态分布、类型分布、风险项
→ 渲染 lg-type-list / lg-warning-list
```

变化点：数据来源不变；分布条、百分比、数量展示统一；空分析数据统一显示“暂无统计数据”。

### 5. 状态管理变化

原则上不新增复杂状态管理。仅建议新增轻量前端常量：`frontend-admin/src/constants/uiText.ts`，用于集中维护通用成功提示、失败提示、空状态文案、弹窗按钮文案、删除确认文案。Pinia 业务 store 不应因 UI 重构发生结构性变化。

## 四、影响范围与回归测试建议

- 全局样式：影响所有页面背景、卡片、表格、按钮、输入框、滚动条、阴影、圆角。回归建议：打开至少 10 个不同模块页面，检查是否存在背景断层、圆角异常、局部旧颜色残留。
- 主布局和侧边栏：影响登录后所有业务页面、菜单激活态、折叠态、顶部栏、内容区。回归建议：逐个点击一级菜单和二级菜单，确认父子级选中态一致。
- 项目管理模块：影响项目列表、项目详情、项目成员、项目选择器。回归建议：查询项目、新建项目、编辑项目、查看详情。
- 合同管理模块：影响合同台账、合同详情、合同表单、合同 KPI、分析栏。回归建议：打开合同台账，检查 KPI、表格、右侧分析栏，进入详情页确认信息分组和操作按钮正常。
- 成本管理模块：影响成本台账、成本汇总、目标成本、成本科目。回归建议：执行成本筛选，查看 KPI、超预算预警和成本构成分析。
- 付款与发票模块：影响付款列表、付款申请、发票列表、发票核验面板。回归建议：查询付款申请，查看临期付款提醒和发票核验状态分布。
- 采购与库存模块：影响采购订单、采购申请、仓库、库存台账、出入库流水。回归建议：在库存页面执行项目筛选，查看出入库流水，检查移动端卡片列表。
- 分包与结算模块：影响分包任务、分包计量、结算列表、结算状态分析。回归建议：查询分包任务和结算单，确认 KPI、表格、操作列和状态标签统一。
- 审批中心：影响我的待办、我的已办、抄送我的、流程管理、状态标签。回归建议：打开审批待办列表，执行查看/审批入口检查。
- 系统管理与基础资料：影响用户、角色、字典、组织架构、合作方、物料字典。回归建议：新增/编辑/删除基础资料，确认弹窗表单、校验提示、表格操作列一致。
- E2E / 单元测试：Playwright 页面定位可能因结构组件化而变化，少量测试快照或文本断言可能受中文文案统一影响。回归建议：执行 `pnpm build`、`pnpm test:unit` 和 UI 冒烟 Playwright 测试。

## 五、冒烟测试方案

1. 登录后进入项目列表页：打开 `/project`，预期页面背景、搜索栏、统计卡片、表格、分页均符合新 UI 基准，无旧色块残留。
2. 合同台账核心布局检查：打开合同台账页，查看 KPI、表格、右侧分析栏，预期 `lg-grid` 双栏布局正常，KPI 为统一卡片，表格表头为浅灰。
3. 成本台账筛选与重置：输入关键词或选择项目后点击查询，再点击重置，预期数据刷新、分页回到第一页，搜索栏按钮和输入框视觉统一。
4. 付款列表风险提醒检查：打开付款模块，查看资金风险和临期付款区域，预期风险项使用统一浅红背景和红色强调。
5. 库存台账移动端响应式检查：将浏览器宽度缩小到 520px 以下，打开库存页面，预期 KPI 折叠为单列，表格或卡片列表不横向溢出。
6. 系统用户新增弹窗检查：进入系统用户页，点击新增用户，预期弹窗宽度、标题、表单间距、确认/取消按钮统一。
7. 审批中心空状态检查：打开没有数据的审批列表或使用筛选得到空结果，预期显示统一中文空状态，不出现英文、技术字段或布局塌陷。
8. 删除确认与错误提示检查：在任意基础资料列表点击删除并取消，再模拟接口失败，预期确认弹窗文案统一，失败提示为中性中文，页面不丢失当前筛选状态。
9. 侧边栏菜单激活态检查：依次进入项目、合同、成本、库存、系统管理下的二级页面，预期父级和子级菜单选中态统一使用新主色，折叠后仍能正确高亮。
10. 构建与基础质量门禁：执行 `cd frontend-admin && pnpm build`，预期 `vue-tsc --noEmit` 与 `vite build` 均通过。
