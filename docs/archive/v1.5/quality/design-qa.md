# 库存台账、领用与退料设计 QA

> 归档说明（2026-08-09）：本文从仓库根目录迁入 V1.5 质量归档。下列本地绝对路径仅记录当时取证位置，不承诺当前仍存在。

- source visual truth path: conversation attachment `Browser Comment 1`
- source design brief: 用户选择的方案 3——库存台账默认全量列表与底部流水抽屉；领用与退料采用左侧申请列表、右侧完整链路及多行申请表单
- implementation screenshot paths:
  - `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\inventory-stock-drawer.png`
  - `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\requisition-editor.png`
- viewport: `1280 × 720 CSS px`
- source pixels: `1502 × 1114`，density unknown
- implementation pixels: `1280 × 720`，浏览器 DPR `1.25`，截图已归一为 CSS 尺寸
- state:
  - 库存：全部项目、全部仓库、全部物料，打开“演示项目主仓库 / 演示钢筋”流水抽屉
  - 领用：全部项目，打开“发起领料申请”，已添加两行物料

## Full-view comparison evidence

源截图用于核对 CGC-PMS 壳层、导航、字号、颜色、卡片和表格密度；页面专属结构以用户确认的方案 3 为准。实现保留既有蓝色主色、浅灰工作区、白色业务卡片、紧凑表格和权限驱动操作，不引入额外视觉体系。

- 库存台账默认显示 3 条服务端余额；独立“出入库”页签消失。
- 底部抽屉同时展示余额、在途供应、历史净领料和 4 条来源流水。
- 领用与退料在 1280 px 保持双栏主从布局，实测列宽为 `437.237px / 591.562px`。
- 领料申请表单支持两行物料；项目、合同、仓库、日期和提交操作均在右侧工作区。

## Focused region comparison evidence

- 库存抽屉：数量、价值、成本、安全库存、来源和业务编号可读；操作集中在抽屉内。
- 领用表单：必填标记、两行物料、移除、保存草稿和保存并提交审批均可见。
- 无图片、插画、Logo 替换或自定义 SVG；image quality / asset fidelity 不适用。

## Required fidelity surfaces

- fonts and typography: 沿用项目字体与字重 token；标题、指标、表头、辅助文案层级清晰，无溢出。
- spacing and layout rhythm: 库存表、抽屉、主从工作区和多行表单使用既有间距、圆角和边框 token；桌面、平板、手机无页面级横向溢出。
- colors and visual tokens: 沿用项目主色、表面色、边框色及状态色；未新增独立颜色体系。
- image quality and asset fidelity: 页面无业务图片目标，不适用。
- copy and content: “领用与退料”“发起领料申请”“全部库存余额”“库存明细与流水”与业务动作一致；数量、金额和状态均展示服务端事实。

## Findings and comparison history

1. `[P1 fixed]` 领退料链路请求使用了不存在的单数路径，页面出现“资源不存在”。
   - fix: 改为后端真实入口 `/procurement-traces/requisitions/{id}`。
   - post-fix evidence: 浏览器无告警，申请明细和完整链路正常显示。
2. `[P2 fixed]` 1280 px 时主从工作台被过早折为单列。
   - fix: 缩小左栏最小宽度，将单列断点调整到 72rem。
   - post-fix evidence: `requisition-editor.png` 显示左侧申请列表与右侧两行申请表单并排。
3. `[P2 fixed]` 领料明细横向滚动区缺少键盘入口。
   - fix: 增加 `role="region"`、可访问名称和 `tabindex="0"`。
   - post-fix evidence: 两页三档视口 Axe 严重/致命问题为 0。

## Primary interactions tested

- 库存全量默认加载、点击物料编码打开流水、阈值维护、调拨冲突、旧出入库地址跳转。
- 领料新增权限、两行物料表单、保存并提交、审批权限、出库、退料、冲销。
- 视口：`1440 × 900`、`1024 × 768`、`390 × 844`。
- browser console errors checked: `0`。

## Follow-up polish

- `[P3]` 部分服务端时间保留高精度 ISO 文本；可在全站时间展示规范统一时处理。

final result: passed

# 第55条主线 VCG-B01 批次7采购执行单据明细设计 QA

- source visual truth URL: `http://127.0.0.1:5174/v2/inventory/material-requisition`
- source state: `REQ-20260718-001`“领退料链路”弹窗打开，领料明细表格可见
- source screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch7\reference-requisition-detail.png`
- implementation URLs: `http://127.0.0.1:5174/v2/inventory/purchase-request`、`/v2/purchase/order`、`/v2/purchase/receipt`
- implementation screenshots: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch7\after-purchase-request.png`、`after-purchase-order.png`、`after-material-receipt.png`
- full-view comparison: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch7\compare-reference-vs-purchase-request.png`
- focused comparison: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch7\focused-reference-vs-purchase-request.png`
- viewport: `1698 × 1114 CSS px`
- source and implementation pixels: `1698 × 1113`；浏览器密度按 `1:1` 对齐，无缩放归一
- state: 平台管理员、本地演示数据、三个详情弹窗分别打开；未执行业务写入

## Full-view comparison evidence

领料参考与采购申请实现已置于同一 `3396 × 1113` 对照输入。实现保留 V3详情弹窗标题、事实卡、自适应高度和液态玻璃材质，将原单行紧凑列表改为带表头、行表头、条数徽标和滚动区域的结构化明细表。采购三态共享同一弹窗与表格骨架，仅列集合按业务事实变化。

## Focused region comparison evidence

`2000 × 190` 聚焦对照同时展示领料与采购申请明细区。物料、规格、单位、数量、单价/预计单价、金额/预计金额等字段逐列对齐；首列使用行表头，表头层级、分隔线、文字密度和条数提示一致。采购申请因服务端契约不提供规格，明确显示 `-`，未猜测或前端补算。

## Required fidelity surfaces

- fonts and typography: 复用 V3共享表头、正文、行表头和徽标字号/字重；长标题与数值无截断。
- spacing and layout rhythm: 明细进入共享 section 卡片；表头、数据行和条数徽标间距稳定，三页弹窗自适应高度且无大面积空白。
- colors and visual tokens: 背景、边框、表头底色、分隔线、圆角和阴影全部来自共享 V3 token；无页面私有材质。
- image quality and asset fidelity: 参考和实现均无独立图像资产；不适用。
- copy and content: 保留三个服务端明细契约的全部现有数量、单价、金额、收货和质量事实；未更改业务文案语义。

## Findings and comparison history

1. `[P1 fixed]` 采购三页原明细为“物料 + 分号串联数值”的紧凑列表，不能与领料明细逐列比较。
   - fix: 在共用 `PurchaseExecutionPage.vue` 中替换为 V3共享 section 与 table 结构，三路由一次生效。
2. `[P2 fixed]` 原格式没有规格、单位、计划日期、质量数量和使用部位的稳定列对齐。
   - fix: 按申请、订单、验收服务端契约分别展开列，不删除原已展示事实。
3. `[intentional constraint]` 材料验收有 `12` 列，字段多于领料参考。
   - adjudication: 保留质量与收货事实优先；共享表格滚动区负责窄视口降级，当前桌面视口无页面横向溢出。

## Browser and interaction checks

- 采购申请：点击 `PR-20260718-002`，表格展示 `1.0000 / 1000.0000 / 1000.00 / 2025-12-31`。
- 采购订单：点击 `PR-20260718-004`，表格展示规格、单位、数量、单价、金额和已收数量。
- 材料验收：点击 `MR-20260718-002`，表格展示实收、合格、不合格、单价、金额和使用部位。
- 三个弹窗均通过真实点击打开；关闭按钮语义保留。页面横向溢出 `0`，浏览器 console error/warn `0`。

## Automated verification

- 目标单测：`21/21`项通过。
- V2完整 unit：`48`个文件、`352/352`项通过。
- type-check、Clean-room `173 + 16`、route ledger `87 / 73 / 65`、生产构建和 bundle size：通过。
- ESLint：`0 error`；6个未触及 E2E 文件既存 warning。

final result: passed

---

# 第55条主线 VCG-B01 批次6浏览器批注设计 QA

- source visual truth: 用户在真实 `5174` 业务页提交的5个元素批注及“按钮样式没有统一”
- viewport: `1698 × 1114`
- implementation entry: `http://127.0.0.1:5174/v2/`
- comparison evidence:
  - `frontend-admin-v2/test-results/design-qa/v3-batch6-approval-actions.jpg`
  - `frontend-admin-v2/test-results/design-qa/v3-batch6-daily-detail-scrollbar.jpg`
  - `frontend-admin-v2/test-results/design-qa/v3-batch6-daily-create-fields.jpg`
  - `frontend-admin-v2/test-results/design-qa/v3-batch6-schedule-fields.jpg`
  - `frontend-admin-v2/test-results/design-qa/v3-batch6-bid-facts.jpg`

## Findings and fixes

1. `[P1 fixed]` 审批动作按钮与下一组详情卡视觉贴合。
   - fix: 共享动作区增加顶部分隔线与上下各 `12px` 留白；按钮底部至下一卡片实测 `12px`，无重叠。
2. `[P1 fixed]` 现场日报长详情显示垂直滑块。
   - fix: 共享正文和内部滚动区隐藏标准及 WebKit滑块，保持 `overflow:auto`；正文 `1140 > 775`，脚本滚动成功。
3. `[P1 fixed]` 投标成本详情使用裸 `dt/dd`，未进入共享事实行布局。
   - fix: 该页及同类 `6` 个商业页、`8` 个事实块全部迁移为 `div > dt + dd`；投标详情现为4行，每行 `62px`，标签列 `120px`，间距 `16px`。
4. `[P1 fixed]` 项目计划“说明”文本域仅占所在列 `25%`。
   - fix: 共享标准弹窗原生控件合同统一满宽，说明框实测 `100%`。
5. `[P1 fixed]` 现场日报日期、人数和文本域仅占所在列 `16%–29%`。
   - fix: 同一共享合同覆盖日期、数字和文本域，全部实测 `100%`；checkbox、radio和 file保持紧凑语义。
6. `[P1 fixed]` 弹窗内普通按钮与玻璃按钮尺寸、内边距和圆角不一致。
   - fix: 共享按钮合同统一为 `40px / 20px / 8px`；保留颜色和材质表达的操作语义。

## Visual comparison

五组对照均按相同视口、相同真实路由和相同打开状态左右合成。审批动作区不再贴住下一卡片；长日报右侧滑块消失但内容仍可滚动；日报和计划输入完整占列；投标事实从密集裸节点变为与其他详情一致的双栏事实行。未新增页面私有视觉、组件或依赖。

## Interactions and browser checks

- 审批详情直接路由打开，动作按钮和后续卡片边界可读。
- 日报详情正文可脚本滚动；关闭后可重新打开新建日报。
- 计划与投标详情均从真实列表操作打开。
- 各状态 DOM无 Vite错误覆盖层；未执行保存、审批、创建、删除或状态变更。

## Automated verification

- 目标契约：`68/68`。
- 完整 unit：`48`个文件、`352/352`。
- ESLint：`0 error`；6个未触及 E2E 文件既存 warning。
- Clean-room：`173`个 V2文件、`16`个 contracts文件。
- route ledger：`87`路由、`73`视图入口、`65`唯一视图。
- contracts type-check、生产构建、bundle size、`git diff --check`：通过。
- 运行态：`5174 HTTP 200`；`cgc-pms-frontend-v2-dev healthy`。

final result: passed

---

# 第55条主线 VCG-B01 液态玻璃弹窗单页试点设计 QA

- source visual truth path: `D:\用户\下载\liquid-glass-dialog-demo.zip` 内 `desktop.png`
- source SHA-256: `9625A3461572A67DAE9C132ACBF512B0CD2D9165B06EDDD7AB9346B397A4029B`
- implementation: `frontend-admin-v2/src/components/preview/DesignSystemPreview.vue`
- latest comparable implementation screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-v2-dialog-review-desktop-4173.png`
- viewport: `1672 × 941 CSS px`
- source pixels: `1672 × 941`
- implementation pixels: `1672 × 941`
- state: V2设计系统预览页，审批详情弹窗打开

## Full-view comparison evidence

源图与首轮实现已在同一比较输入中按相同像素尺寸核对。首轮实现已达到浅色玻璃表面、冷色模糊遮罩、40px圆角、详情双栏、费用表格和三按钮操作区；弹窗初始尺寸偏小的问题已定位为 `.v2-dialog-standard` 后置覆盖试点宽度。

## Focused region comparison evidence

- 弹窗框架：源图约 `1040 × 880`；首轮实现由 `736 × 672` 修正到 `1040 × 780`，最终代码目标已调整为 `1040 × 880`。
- 标题区：最终代码增加可访问标题后缀 slot，将“审批通过”状态徽标置于标题行。
- 正文区：首轮截图存在焦点蓝色外框和桌面正文滚动；最终代码已增加试点专用焦点覆盖并扩展高度。
- 最终代码变更后，本地URL被浏览器安全策略拒绝重载，因此没有取得绑定最终代码的浏览器截图。

## Required fidelity surfaces

- fonts and typography: 沿用V2字体和字号token；标题目标42px，正文保持现有紧凑业务密度。
- spacing and layout rhythm: 宽度、目标高度、圆角、标题/正文/页脚分区已按源图收敛；最终截图待补。
- colors and visual tokens: 表面、边框、遮罩、阴影和模糊均进入V2 token，无裸十六进制值。
- image quality and asset fidelity: 源图无独立业务图片资产；未生成图片、SVG或占位素材。
- copy and content: 审批编号、申请信息、费用明细、状态和金额与源图示例一致，仅用于视觉演示。

## Findings and comparison history

1. `[P2 fixed in code, visual recheck blocked]` 标准宽度后置覆盖导致详情弹窗过窄。
   - fix: 使用 `.v2-dialog__panel.v2-dialog-liquid-review` 提升试点选择器优先级，并设置详情宽度token。
2. `[P2 fixed in code, visual recheck blocked]` 首轮截图出现强蓝色焦点外框和正文滚动。
   - fix: 增加试点专用焦点覆盖；弹窗目标高度调整为源图的880px级别；状态徽标迁入标题后缀slot。
3. `[P2 fixed in code, visual recheck blocked]` 不支持 `backdrop-filter` 的环境仍会继承面板位移动画。
   - fix: 降级分支使用不透明表面，并仅对试点面板关闭过渡和位移。
4. `[scope boundary]` 最终浏览器重载被本地URL安全策略阻断。
   - impact: 本记录没有绑定最终代码的桌面/平板/移动端截图、控制台和交互证据，因此不得据此推广到共享默认样式或正式业务页。
   - adjudication: 用户于2026-07-26目视审核当前本地实例并明确回复“通过”；单页视觉方向审核门关闭，扩面仍需另行授权和完整复验。

## Static verification

- `pnpm exec vitest run tests/unit/design-system.test.ts`：50项通过。
- `pnpm check:boundary`：173个V2文件、16个contracts文件通过。
- 目标 ESLint、`pnpm type-check`、`pnpm build`：通过。

final result: passed

---

# 第55条主线 VCG-B01 双端共享弹窗推广设计 QA

- source visual truth path: `D:\用户\下载\liquid-glass-dialog-demo.zip` 内 `desktop.png`
- source SHA-256: `9625A3461572A67DAE9C132ACBF512B0CD2D9165B06EDDD7AB9346B397A4029B`
- final V2 screenshot: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\vcg-b01-v2-detail-final.png`
- final source comparison: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\vcg-b01-source-vs-implementation.png`
- final V2 confirm screenshot: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\vcg-b01-v2-confirm-final.png`
- final Legacy screenshot: `D:\projects-test\cgc-pms\frontend-admin\test-results\design-qa\vcg-b01-legacy-expense-final.png`
- browser viewport obtained: `1280 × 720 CSS px`

## Full-view comparison evidence

参考图已缩放至 `1280 × 720`，与绑定最终代码的 V2 审批详情截图合成同一比较输入。实现保留浅色玻璃表面、冷色模糊遮罩、亮边、多层阴影、标题/正文/页脚分区、审批信息和费用表格。详情最大宽度保持合同值 `65rem / 1040px`，没有随参考图作等比缩小。

## Focused region comparison evidence

- V2详情：`1040 × 668.8`，正文独立滚动，操作区固定；较短高度来自当前 `720px` 视口的上下安全间距。
- V2确认框：宽 `512px`，符合小型确认档；共享玻璃表面和危险确认语义生效。
- Legacy表单：内容宽 `800px`，`rgba(255,255,255,.82)` 表面、`24px` 圆角、`blur(24px) saturate(1.35)`，正文 `overflow:auto`。
- 两端共享入口生效；未发现页面级材质复制。

## Required fidelity surfaces

- fonts and typography: 沿用两端既有字体与字号 token；标题、字段、金额、状态和辅助文案可读。
- spacing and layout rhythm: 标题、滚动正文、固定操作区分层明确；V2详情、V2确认、Legacy表单三档尺寸可区分。
- colors and visual tokens: 表面、遮罩、边框、圆角、阴影和模糊均进入各端既有 token / CSS 变量。
- image quality and asset fidelity: 源图无独立业务图片资产；不适用。
- copy and content: V2使用模拟审批详情；Legacy使用真实费用申请页面，未执行业务写入。

## Findings and comparison history

1. `[P2 fixed]` V2详情共享规则只保留自然高度，未达到参考图的详情层级。
   - fix: 桌面详情高度绑定 `min(max-height, viewport - gutters)`，移动端恢复自动高度。
2. `[P2 fixed]` V2确认框被标准宽度覆盖。
   - fix: 共享层显式恢复 `32rem` 确认宽度。
3. `[P2 fixed]` Legacy长表单内容可能被 `overflow:hidden` 截断。
   - fix: 弹窗内容改为列式布局，正文 `min-height:0; overflow:auto`，标题和页脚固定。
4. `[environment prerequisite fixed]` Legacy `dev-login` 因本地 backend 热加载类路径漂移返回500并触发 Logback类缺失。
   - fix: 重启任务范围内本地 backend；健康检查恢复 `UP`，`dev-login` 返回 `302 /dashboard`。
5. `[P1 evidence gate open]` 未取得 `1440 / 1024 / 390`、axe、200%缩放、完整焦点链和能力降级实测。
   - impact: 共享入口代码已整改并通过自动门禁，但第55条主线正式扩面验收不可判通过。
   - owner/carrier: `VCG-B01-QA-01`，见视觉组件治理任务计划书第2.3节。

## Automated and browser verification

- Legacy：弹窗 token / CSS 契约 `7` 项通过；目标及完整 lint、type-check、生产构建通过。
- V2：设计系统 `50` 项通过；完整 lint、Clean-room边界、type-check、生产构建通过。
- 浏览器：V2详情、V2确认、Legacy费用申请表单在 `1280 × 720` 可见且可操作；控制台错误 `0`。
- 既存无障碍扫描报告 `25` 个未触及业务文件的图标按钮名称问题，分类为 `quality_or_security / existing baseline`，非本轮引入。

final result: blocked

---

# 第55条主线 VCG-B01 真实 V2 驾驶舱预警弹窗设计 QA

- source visual truth path: `D:\用户\下载\liquid-glass-dialog-demo.zip` 内 `desktop.png`
- source SHA-256: `9625A3461572A67DAE9C132ACBF512B0CD2D9165B06EDDD7AB9346B397A4029B`
- implementation URL: `http://127.0.0.1:5174/v2/dashboard`
- implementation screenshot: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\vcg-b01-dashboard-alert-final.png`
- comparison image: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\vcg-b01-dashboard-source-vs-implementation.png`
- viewport: `1280 × 720 CSS px`
- source pixels: `1672 × 941`，归一为 `1280 × 720`
- implementation pixels: `1280 × 720`
- state: 平台管理员，驾驶舱首条服务端高风险预警的处置弹窗打开；未执行业务写入

## Full-view comparison evidence

参考审批详情与真实驾驶舱预警处置已置于同一 `2560 × 768` 比较输入。两者保持同一浅色液态玻璃语言：冷色模糊遮罩、半透明表面、亮边、多层阴影、40px圆角、标题/说明/正文分区和圆形关闭按钮。真实预警内容较短且包含处置表单，按既有尺寸合同使用 `46rem / 736px` 标准操作档，不复制参考图的 `1040px` 详情档。

## Focused region comparison evidence

- 标题区：风险等级“高”进入标题后缀，层级对应参考图的状态徽标；关闭按钮和说明文案保持可访问名称。
- 事实区：预警信息、规则、状态、项目和触发时间形成双栏事实网格；服务端事实未改写。
- 操作区：目标状态、处理说明和确认处置保持原权限与接口语义；当前记录已读/已接单，因此条件式快捷操作未显示。
- 实测：`736 × 342.15`，表面 `rgba(245,249,255,.72)`，`border-radius:40px`，`backdrop-filter:blur(34px) saturate(1.78)`，正文 `overflow:auto`。

## Required fidelity surfaces

- fonts and typography: 沿用 V2 字体、字号和字重 token；标题、风险徽标、事实标签和值的层级与参考方向一致。
- spacing and layout rhythm: 采用标准操作档消除无价值空白；标题、事实和表单间距紧凑且无裁切。
- colors and visual tokens: 表面、边框、阴影、遮罩和风险色全部复用现有 V2 token，无新视觉值。
- image quality and asset fidelity: 源图和真实弹窗均无独立业务图片资产；不适用。
- copy and content: 保留真实预警文案、项目、规则、状态、触发时间与处置动作；未使用预览模拟审批数据。

## Findings and comparison history

1. `[P2 fixed]` `5174` 首次仍显示 Vite 重启前的旧弹窗样式。
   - fix: 重启真实 `cgc-pms-frontend-v2-dev`，按运行态配置等待 `25s`，确认共享玻璃 token 生效。
2. `[P2 fixed]` 真实短内容处置弹窗误用详情档，刷新后形成 `1040px` 宽、接近满高的大面积空白面板。
   - fix: 移除驾驶舱页面的 `v2-detail-dialog` 面板标记，复用 `V2Dialog` 默认标准操作档；风险徽标移入标题后缀。
3. `[intentional constraint]` 参考图是审批详情，真实弹窗是预警处置；宽度和高度不作等比复制。
   - adjudication: 两者复用同一材质、圆角、遮罩和分区语言，尺寸按内容复杂度分档，符合计划书第2.1节合同。

## Primary interactions tested

- 点击服务端预警行打开弹窗。
- Escape关闭，焦点恢复到原预警行。
- 原预警行按 Enter 重新打开。
- browser console errors checked: `0`。

## Automated verification

- `pnpm exec vitest run tests/unit/dashboard-page.test.ts tests/unit/design-system.test.ts`：`64`项通过。
- 目标 ESLint、Prettier、`pnpm type-check`、`pnpm build`：通过。

final result: passed

---

# 第55条主线 VCG-B01 V3弹窗唯一基线与 V2全量推广设计 QA

- source visual truth URL: `http://localhost:4173/v2/src/components/preview/index.html`
- source state: “审批详情”打开，含标题状态、说明、审批信息卡、费用表格卡和三按钮页脚
- original baseline screenshot: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\v3-approval-detail-baseline.png`
- adaptive implementation screenshot: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\v3-approval-detail-adaptive.png`
- same-state comparison: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\v3-baseline-vs-adaptive.png`
- real business montage: `D:\projects-test\cgc-pms\frontend-admin-v2\test-results\design-qa\v3-real-business-dialog-montage.png`
- real project entry: `http://127.0.0.1:5174/v2/`

## Full-view comparison evidence

原固定高度基线与共享 V3实现已在同一 `3272 × 1114` 对照输入中按相同 `1636 × 1114` 视口、相同内容和相同打开状态核对。标题、说明、状态徽标、信息卡、表格卡、页脚、按钮、材质、圆角和阴影保持一致；仅移除固定 `880px` 高度造成的无价值空白。

- 原面板：`1040 × 880`，正文 `661px`。
- 自适应面板：`1040 × 809.45`，正文 `590.4px`，`scrollHeight = clientHeight = 590`。
- 结论：短详情自然收缩；未通过隐藏、裁切或业务页补丁消除空白。

## Shared structure and full rollout

- 预览页删除全部 `preview-approval-*` 私有弹窗视觉，改用 `v2-detail-dialog__facts / __section / __section-heading / __table / __accent`。
- 共享层统一标题层级、信息卡、详情卡、表格、页脚、按钮、标准/详情/宽/底部抽屉尺寸、自适应高度和正文滚动。
- 全仓 `55` 个 `V2Dialog` 调用继续进入同一 `V2Dialog` 骨架；业务页只允许四个共享面板类。
- 清除现场日报、WBS、库存、供应商和领退料页面的私有面板宽度、位置、圆角、层级或无效类。
- 静态门禁阻止后续业务页新增私有 `panel-class` 或越界覆盖 `.v2-dialog__*`。

## Real browser evidence

`1280 × 720`、平台管理员、本地示例数据、未执行业务写入：

1. 驾驶舱预警处置：标准弹窗 `736 × 422.55`；信息卡与表单无空白、无裁切。
2. 审批详情：`1040 × 668.8`；正文 `scrollHeight 588 > clientHeight 539`，超限后独立滚动。
3. 现场日报长详情：正文 `1140 > 568`，5个共享详情卡连续排列；标题保持可见。
4. 现场日报宽编辑：共享宽弹窗 `1152px`，正文 `1488 > 484`。
5. 库存明细与流水：共享底部抽屉 `1248px`，距左右和底部各 `16px`；正文 `526 > 432`。
6. 四个真实页面 browser console error：`0`。

## Primary interactions tested

- 点击驾驶舱服务端预警行打开。
- Escape关闭；焦点恢复到原 `TR`。
- 原预警行 Enter重开；焦点进入 `role=dialog` 面板。
- 长内容超过视口后正文滚动，面板不继续增高。
- 关闭按钮、状态徽标、表格可访问名称和焦点入口保持共享语义。

## Automated verification

- V2完整 unit：`48`个文件、`352/352`项通过。
- Prettier：触及文件全部通过。
- ESLint：`0 error`；6个未触及 E2E 文件既存 warning。
- Clean-room：`173`个 V2文件、`16`个 contracts文件通过。
- route ledger：`87`路由、`73`视图入口、`65`唯一视图，通过。
- contracts type-check、V2生产构建、bundle size、`git diff --check`：通过。

## Scope adjudication

当前 Clean-room V2 V3共享结构全量推广与真实浏览器代表验收通过。原计划 `VCG-B01-QA-01` 的 Legacy + V2双端 `1440 / 1024 / 390`、axe、200%缩放和能力降级属于更高阶正式证据，继续保留唯一载体；不作为本次 V2 V3本地交付的新增悬空项。

final result: passed

---

# 第55条主线 VCG-B01 批次8弹窗操作按钮统一设计 QA

- source visual truth URL: `http://127.0.0.1:5174/v2/project/list`
- source state: “新建项目”弹窗打开，页脚为右对齐“取消 / 创建”
- source screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch8\source-project-footer.png`
- implementation URL: `http://127.0.0.1:5174/v2/approval/instances/520000000000009541?returnTab=todo`
- implementation screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch8\final-workflow-footer.png`
- same-input comparison: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch8\reference-vs-final-implementation.png`
- viewport: `1698 × 1114` CSS像素；源图与实现图均为 `1698 × 1113`

## Full-view comparison evidence

基线与真实审批详情已在同一张 `1698 × 551` 对照图中完整比较。两者操作按钮均位于独立底部页脚，右对齐、`8px`间距、`40px`高度；审批页危险、主要和次要操作保持业务语义色。正文“可用操作”条已删除，按钮与下方卡片重叠归零。

## Shared contract and rollout

- 全部 `55` 个 `V2Dialog` 调用纳入静态检查。
- 对话级取消、提交、审批、处置和单据状态操作进入共享 `#footer`。
- 表单提交按钮使用原生 `form`关联，保留 Enter提交与校验。
- 触及的15个业务页不再使用 `V2GlassButton`；未新增组件、CSS、依赖或页面私有弹窗视觉。

## Real browser evidence

- 审批详情：面板 `1040 × 732.15`，正文 `1040 × 513.1`，页脚 `1040 × 84.8`；重叠按像素舍入为 `0`。
- 审批页脚：`display:flex`、`justify-content:flex-end`、`gap:8px`、`position:sticky`；5个按钮均高 `40px`。
- 采购申请新建：取消为 secondary、保存为 primary；保存按钮 `type=submit` 且关联 `purchase-execution-editor-form`。
- 驾驶舱预警处置：处理说明保留正文，确认处置进入 `contentinfo`页脚；未执行处置。
- 代表页面 browser console error/warning：`0`。

## Automated verification

- V2完整 unit：`48`个文件、`352/352`项通过。
- `pnpm type-check`、`pnpm build`：通过。
- ESLint：`0 error`；6个未触及 E2E 文件既存 Prettier warning。
- Clean-room：`173`个 V2文件、`16`个 contracts文件通过。
- `git diff --check`：通过。

final result: passed

---

# 第55条主线 VCG-B01 批次9台账路由弹窗化设计 QA

- implementation entry: `http://127.0.0.1:5174/v2/project/list`
- project detail screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch9\project-detail.png`
- contract create screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch9\contract-create.png`
- viewport: `1280 × 720` CSS像素

## Shared structure and behavior

- 项目、合同、签证变更、结算台账保留原列表作为弹窗底层；详情、新建和编辑不再替换整页。
- 纯查看弹窗允许遮罩或 Escape 隐式退出；新建、编辑及含可编辑控件的详情禁止遮罩和 Escape 退出。
- 详情事实、分区、表格和页脚全部复用 V3共享类；未新增组件、CSS、依赖或页面私有弹窗视觉。
- 既有深链、接口、权限和状态动作保持不变；浏览器验收未执行业务写入。

## Real browser evidence

1. 项目详情：台账仍在背景，详情为 `role=dialog`；状态编辑已拆为独立编辑弹窗，不再混入纯查看正文。
2. 项目编辑：Escape 后 URL 和弹窗均不变；“取消”后返回 `/v2/project/list`。
3. 合同详情：台账仍在背景；已结算合同通过 Escape 关闭并返回台账。新建合同 Escape 后保持 `/v2/contract/create` 和打开状态；“取消”关闭。
4. 签证变更：新建弹窗 Escape 后保持 `?mode=create`；已生效详情通过 Escape 关闭并返回列表。
5. 结算台账：新建结算 Escape 后保持打开；已定案详情通过 Escape 关闭并返回列表。
6. 代表页面 browser console error/warning：`0`。

## Automated verification

- V2完整 unit：`48`个文件、`354/354`项通过。
- `pnpm type-check`、`pnpm build`：通过。
- ESLint：`0 error`；6个未触及 E2E 文件既存 Prettier warning。
- Clean-room：`173`个 V2文件、`16`个 contracts文件通过。

final result: passed

---

# 第55条主线 VCG-B01 批次9.1结算详情换行修复设计 QA

- source visual truth path: `C:\Users\SUMMAD~1\AppData\Local\Temp\codex-clipboard-a15914f3-8780-41c9-95d3-d8ac507776ec.png`
- implementation URL: `http://127.0.0.1:5174/v2/settlement/520000000000009504`
- implementation screenshot: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-settlement-dialog-fix\after.png`
- before/after comparison: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-settlement-dialog-fix\before-after.png`
- source pixels: `2122 × 1392`，对应用户 `1698 × 1114` CSS视口与 `1.25` 密度
- implementation pixels / viewport: `1280 × 720`，`devicePixelRatio=1.25`
- state: 平台管理员、结算单 `STL-20260720-001` 详情弹窗打开；未执行业务写入

## Full-view and focused comparison evidence

用户证据与实现截图已合并比较。修复前摘要卡内部仍为双栏事实网格，首个值列实测 `0px × 168px`，金额和中文逐字符堆叠；修复后外层三卡和视觉材质不变，内部事实改为单列行，首个值列为 `163.2px × 16.8px`。金额、项目、合同、状态和金额口径全部恢复正常横向阅读。两张证据的视口尺寸不同，因此只裁决跨视口稳定的事实行换行缺陷，不对面板绝对尺寸作像素一致性声明。

## Required fidelity surfaces

- fonts and typography: 字体、字号、字重和行高未变；仅消除异常逐字换行。
- spacing and layout rhythm: 外层三卡、卡间距、内边距和分隔线保持；单列事实行恢复稳定的标签和值节奏。
- colors and visual tokens: 无新增颜色、阴影、圆角或材质。
- image quality and asset fidelity: 页面无业务图片资产；不适用。
- copy and content: 服务端项目、合同、金额、状态和口径事实未改写。

## Findings and comparison history

1. `[P1 fixed]` 三卡内部双栏事实网格把值轨道压缩为 `0px`，导致全部事实值逐字换行。
   - fix: 结算摘要范围内将事实网格改为单列，并恢复单列倒数第二项分隔线。
   - post-fix: 值轨道 `163.2px`，代表中文一行显示；金额、状态与口径无逐字堆叠。

## Primary interactions tested

- 关闭详情弹窗后返回 `/v2/settlement/list`。
- 点击 `STL-20260720-001` 重新打开详情，修复布局保持。
- browser console error/warning：`0`。

## Automated verification

- 目标测试：`61/61`项通过。
- V2完整 unit：`48`个文件、`354/354`项通过。
- Prettier、目标 ESLint、type-check、生产构建、Clean-room `173 + 16`：通过。

final result: passed

---

# 第55条主线 VCG-B01 批次10台账信息密度与全部项目事实设计 QA

- implementation entry: `http://127.0.0.1:5174/v2/site/daily-log`
- viewport: `1698 × 1114`
- screenshots: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-batch10-gNO9Eg`
- state: 平台管理员、全部项目、全部报告期；未执行业务写入

## Browser evidence

1. 现场日报：表格最小宽度 `1312px`；全部行全部单元格 `scrollWidth <= clientWidth`，溢出单元格 `0`。长天气摘要在本列换行，日报标识、日期、项目、在场人数互不遮挡。
2. 签证变更：筛选表单位于 `.v2-card--page-heading .v2-card__header`；正文筛选表单计数 `0`；点击“查询”后显示 `4` 条记录。
3. 成本核对：全部项目模式默认读取服务端可访问范围，表格显示 `10` 个项目；“暂无成本汇总”为空。
4. 动态利润：全部项目模式显示 `10` 个项目、`5` 列平面汇总指标、`1` 个已有预测和 `9` 个未生成预测项目；“暂无动态利润数据”为空。
5. 库存台账：标题卡内共享 KPI 指标条 `4` 项；旧独立 KPI 容器计数 `0`。
6. 结算台账：标题卡内共享 KPI 指标条 `4` 列；旧独立 KPI 容器计数 `0`。

## Server fact boundary

- `GET /cost-summary` 和 `GET /cost-controls/overview` 按当前租户及可访问项目范围返回。
- 确认收入统一使用 `cost_type=REVENUE_CONFIRMED` 且 `cost_status=CONFIRMED`；动态利润只纳入 `ACTION_REQUIRED`、`CONTROLLED` 已确认预测，高版本草稿不能覆盖权威汇总。
- 金额、成本、利润和利润率由服务端 `BigDecimal` 计算并以字符串返回；前端只渲染，不聚合、不补算。
- 单项目查询、写入、权限和状态动作未改变。

## Automated verification

- 后端相关测试：`49/49`；统一旧辅助口径后摘要引擎复验：`24/24`；Maven compile/package：通过。
- 独立金额复核：确认收入字段、预测状态过滤及两条反污染回归均通过，无阻塞项。
- V2完整 unit：`48`个文件、`356/356`项通过。
- contracts type-check、Vue type-check、生产构建：通过。
- ESLint：`0 error`；6个未触及 E2E 文件既存 warning。

final result: passed

---

# 第55条主线 VCG-B01 单一设计系统预览展厅设计 QA

- implementation entry: `http://127.0.0.1:5174/v2/src/components/preview/index.html`
- desktop viewport: `1698 × 1114`
- mobile viewport: `390 × 844`
- screenshots: 当前任务内嵌浏览器桌面展厅、V3审批详情、移动展厅与移动编辑弹窗截图
- state: 独立 V2 模拟数据；未登录、未调用业务接口、未执行业务写入

## Baseline governance

1. 继续只保留一个设计系统预览入口，未新增平行预览页或第二套视觉基线。
2. “审批详情”继续作为唯一 V3 弹窗视觉基线；其他弹窗只展示共享尺寸与关闭行为。
3. 五个页内锚点覆盖基础令牌、页面与数据、操作与导航、表单与反馈、弹窗规格。
4. 展厅直接复用现有 V2 共享组件、全局令牌和原生语义结构，无新依赖、无业务私有视觉。

## Browser evidence

1. 桌面：页面宽 `1698px`、文档宽 `1683px`，全页无横向溢出；五个锚点、KPI、可访问表格、八业务域图标和三类页面状态均存在。
2. 操作菜单：共享 `V2ActionMenu` 展开后显示“查看详情、导出记录”。
3. 查看弹窗：V3审批详情为 `v2-dialog-standard v2-detail-dialog`，点背景后关闭。
4. 编辑弹窗：标准表单点背景后保持打开，点击取消后关闭。
5. 移动：页面宽 `390px`，文档宽 `375px`，无全页横向溢出；导航和表格仅在自身容器滚动。
6. 移动编辑弹窗：宽 `375px`，左右均在视口内；正文 `scrollHeight=258px`、`clientHeight=258px`，无额外空白或隐藏内容。
7. 浏览器 console error/warning：桌面、移动均为 `0`。

## Automated verification

- 预览静态契约：`54/54`项通过。
- V2完整 unit：`48`个文件、`357/357`项通过。
- 目标 ESLint、Vue type-check、contracts type-check、生产构建、Clean-room `173 + 16`、`git diff --check`：通过。

final result: passed

---

# 第55条主线 VCG-B01 唯一设计标准与门禁设计 QA

- canonical standard: `docs/standards/00-UI-Design-Baselines-and-Code-Specifications.md`
- implementation entry: `http://127.0.0.1:5174/v2/src/components/preview/index.html`
- desktop viewport: 默认浏览器视口
- mobile viewport: `390 × 844`
- state: V2 模拟数据；未执行业务写入

## Governance evidence

1. 仓库仅有一个 `UI-DESIGN-STANDARD: canonical` 标记；M1基线已降级为历史跳转页。
2. `docs/standards/05-前端开发规范.md` 不再把 Legacy `lg-*` 定义为现行目标。
3. 唯一标准以 `S01`—`S24` 统一页面、表格、表单、反馈、弹窗、响应式、权限和交付规则。
4. `pnpm check:design-system` 已进入 `frontend-v2-gate`；工作流合同同步锁定命令和包脚本。
5. 唯一预览 HTML 进入生产构建；迁移门自动发现并执行预览 E2E。

## Browser evidence

1. 页面身份：URL与标题匹配唯一设计系统预览，DOM存在五个章节及有效内容，无框架错误覆盖层。
2. V3只读详情：遮罩点击后关闭；编辑表单：遮罩点击后保持打开，点击取消后关闭。
3. 移动端：文档宽与视口宽均为 `375px`，无横向溢出；详情弹窗左右边界均在视口内。
4. 桌面、移动 console error/warning：`0`。

## Automated verification

- 唯一设计门：`3`个文件、`76/76`项通过。
- V2完整 unit：`48`个文件、`358/358`项通过。
- 预览专项 E2E：`2/2`；完整迁移 E2E：`81/81`。
- ESLint：`0 error`；未触及 `m1-shell.spec.ts` 的 `6`个既存格式 warning 不影响退出码。
- contracts type-check、Vue type-check、生产构建、Clean-room `174 + 16`、route ledger、bundle-size、工作流契约：通过。

final result: passed

---

# 第55条主线 VCG-B01 全页复审阻塞项3/5/6/7/8设计 QA

- implementation entry: `http://127.0.0.1:5174/v2/`
- desktop viewport: `1638 × 1114`
- mobile viewport: `390 × 844`
- screenshots: `C:\Users\SUMMAD~1\AppData\Local\Temp\cgc-pms-blockers-3-5-6-7-8-20260726`
- state: 平台管理员、真实业务数据；未执行业务写入

## Remediation evidence

1. 会话页使用原生唯一 H1，说明文字不再进入标题卡。
2. 七个审计详情入口按只读/编辑状态控制隐式关闭；分包可编辑草稿使用动态 `!selectedEditable`。
3. 分包工作区仅引用 `--v2-*` CSS变量；根节点计算间距为 `16px`，旧 `--space-4` 未定义。
4. 七个业务工作区根节点均为 `SECTION`；库存、结算、分包和资金财务真实页均为 `mainCount=1`、`nestedMainCount=0`、`h1Count=1`。
5. 十个受影响页面的边框宽度统一使用 `--v2-border-width`，页面目录裸 `1px` 边框扫描为 `0`。

## Interaction loop

1. 投标成本只读详情：Escape 后关闭；再次打开后点击弹窗外区域关闭。
2. 投标成本新建编辑弹窗：点击弹窗外区域后仍打开；Escape 后仍打开；点击取消后关闭。
3. 分包移动页：视口与文档宽均为 `390px`，无页面级横向溢出。
4. 桌面与移动页面无空白、框架错误覆盖层或控制台 error/warning。

## Automated verification

- 唯一设计门：`3`个文件、`77/77`项通过。
- V2完整 unit：`48`个文件、`359/359`项通过。
- 完整迁移 E2E：`81/81`项通过。
- contracts type-check、Vue type-check、生产构建、Clean-room `174 + 16`、route ledger `87/73/65`、bundle-size：通过。
- ESLint：`0 error`；未触及 `m1-shell.spec.ts` 的 `6`个既存格式 warning 不影响退出码。

final result: passed

---

# 第55条主线 VCG-B01 支线关闭裁决

- status: `CLOSED_BY_USER / CURRENT_V2_SCOPE_PASSED / SUPERSEDED_QA_CLOSED`
- 当前V2唯一标准、设计门、unit、迁移E2E和真实5174浏览器证据通过。
- 历史 `VCG-B01-QA-01` 双端完整验收没有完成，原 `final result: blocked` 保持历史有效；唯一标准将Legacy降为兼容冻结后，该旧验收前提不再是现行设计目标，按无明确现行价值关闭。
- 本记录不代表旧双端合同通过；本地commit另以Git结果为准，不代表远端CI通过、正式入口切换、Legacy退役或生产发布。

final result: closed
