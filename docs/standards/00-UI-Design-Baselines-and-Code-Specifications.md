<!-- UI-DESIGN-STANDARD: canonical -->

# CGC-PMS 唯一设计系统标准与门禁

状态：现行、唯一权威

适用范围：`frontend-admin-v2` 全部页面；`frontend-admin` 仅作兼容维护

唯一可视入口：`http://127.0.0.1:5174/v2/src/components/preview/index.html`

## 1. 权威边界

本文件是仓库内唯一设计规范。其他计划、验收报告、历史基线、截图说明和组件 README 只能记录实施事实或链接本文，不得定义第二套颜色、字号、间距、组件、弹窗或页面结构规则。

权威顺序：

1. 本文定义设计语义、使用边界、验收标准和门禁。
2. `frontend-admin-v2/src/styles/tokens.css` 定义 V2 数值 token。
3. `frontend-admin-v2/src/styles/base.css` 定义画布、字体、焦点、无障碍和 reduced-motion。
4. `frontend-admin-v2/src/styles/components.css`、`frontend-admin-v2/src/components/index.ts` 与 `V2*.vue` 定义共享组件外观和行为。
5. 业务页面只定义业务布局、列宽、换行和字段语义，不复制公共组件材质。

`design-qa.md`、`docs/plans/`、`docs/quality/` 与 `docs/ui-v2/m1-design-system-baseline.md` 均为历史或验收记录，不是规范源。发生冲突时以本文和当前实现门禁为准。

## 2. 双前端边界

| 目录 | 状态 | 允许 |
| --- | --- | --- |
| `frontend-admin-v2` | 现行设计系统 | 只使用 `--v2-*` token、V2共享组件和本文规则 |
| `frontend-admin` | Legacy兼容冻结 | 保留既有 Ant Design Vue、`tokens.ts` 与样式护栏；只做缺陷和可用性维护，不产生新标准 |

禁止跨端复制组件、CSS、DOM适配层或 token。禁止把 Legacy 的 `lg-*`、`pt-*`、`app-*`、Ant Modal 或颜色值用于 V2；也禁止为统一外观反向改写 Legacy 业务页。

## 3. 唯一视觉基线

- 设计系统只保留一个预览地址：`/v2/src/components/preview/index.html`。
- 预览页集中展示基础令牌、页面与数据、操作与导航、表单与反馈、弹窗规格。
- “审批详情”是唯一 V3 详情弹窗视觉基线，使用 `V2Dialog + v2-dialog-standard + v2-detail-dialog`。
- 标准表单、宽详情、底部抽屉和确认框只展示共享规格，不形成第二套详情视觉。
- 业务页复用共享结构和 token；禁止 `preview-*` 视觉复制、页面私有弹窗壳和页面级 `.v2-dialog__*` 覆盖。

## 4. 统一检查清单

本表是设计、开发、评审和验收的唯一规则索引。P0 失败立即阻断迁移或合并；P1 必须本轮整改，或进入唯一正式承接项。

| ID | 分类 | 标准 | 级别 |
| --- | --- | --- | --- |
| S01 | 页面语义 | 每页只有一个可见语义 `h1`；标题、导航和路由使用同一文案，删除重复标题、eyebrow 与实现说明。 | P0 |
| S02 | 公共壳 | 项目、报告期、账户和主导航只由 `AppShell` 提供；页面不得复制公共壳上下文控件。 | P0 |
| S03 | 项目范围 | “全部项目”读取服务端授权范围内全部有效数据；有时间维度的页面按报告期查询，无时间维度页面只保留上下文。 | P0 |
| S04 | 工作区导航 | 三级 Tab 使用公共样式；路由、Tab 与标题禁止简称、倒序和近义别名。 | P0 |
| S05 | 视觉 token | 字号、颜色、间距、圆角、阴影、控件高度、层级和动效只使用 `--v2-*` token。 | P0 |
| S06 | 文字层级 | 页面标题 21px/bold；H1与标题操作同置70px标题卡；桌面控件32px、移动控件44px；H2/H3为14px/semibold。 | P0 |
| S07 | 页面结构 | 公共壳内容区保留10px内边距；页面按H1标题卡和必要数据区组织，不渲染空标题正文、重复“某某列表”或无意义留白。 | P1 |
| S08 | 卡片边界 | 列表表格与分页同属一个 `V2Card`；禁止记录卡片化、卡片嵌套和用并列卡片模拟同一账簿。 | P0 |
| S09 | 表格结构 | 可比较记录使用语义表格；编码、名称、日期、金额和状态分列，操作列有明确列标题。 | P0 |
| S10 | 表格密度 | 表头、事实和分页使用12px token；文本左对齐、数字右对齐、状态和操作居中；宽表使用具名可聚焦容器。 | P0 |
| S11 | 分页 | 分页列表固定 `pageSize=10`，顺序为“上一页 — 第 N 页 — 下一页”。 | P0 |
| S12 | 筛选 | 搜索与筛选进入标题栏，保留可访问名称；页面不得复制公共壳筛选或自建下拉层。 | P1 |
| S13 | 表单控件 | 文本、搜索和下拉复用 `V2Input`、`V2Select`；日期、数字、文件、复选、单选和多行文本可使用原生语义控件。 | P0 |
| S14 | 按钮与菜单 | 页面操作复用 `V2Button`；标题与行内操作使用 `size="small"`；超过两个动作使用 `V2ActionMenu`；业务弹窗页脚禁止 `V2GlassButton`。 | P0 |
| S15 | 标准弹窗 | 新建、编辑、只读详情复用 `V2Dialog`；确认复用 `V2ConfirmDialog`；禁止 `window.confirm` 和私有弹窗壳。 | P0 |
| S16 | 弹窗安全 | 只读查看允许点击遮罩、Escape或关闭按钮退出；新建、编辑和写入态禁止遮罩与Escape关闭，只能由关闭、取消、保存或提交等明确按钮结束。 | P0 |
| S17 | 详情形态 | 完整对象查看统一使用 `v2-detail-dialog` 覆盖原台账；详情路由继续提供深链和刷新恢复，关闭后返回原列表状态；禁止列表下方内联完整详情。 | P0 |
| S18 | 状态反馈 | 状态使用中文映射与 `V2Badge`；短暂结果使用公共 Toast；表单校验或持续阻断信息使用 `V2Alert`；颜色不是唯一证据。 | P0 |
| S19 | 数据状态 | 验收加载、典型、全部项目、具体项目、空数据、部分数据、接口失败、403和超长内容；失败态与空态互斥。 | P0 |
| S20 | 权限边界 | 无权限时 fail-close，不加载越权数据；隐藏按钮不能替代服务端权限校验。 | P0 |
| S21 | 交互稳定 | Tab、筛选、分页、弹窗和预设视图切换保持应用壳及页面骨架稳定，不整页闪烁。 | P1 |
| S22 | 响应式与无障碍 | 1440、1024、390无遮挡、裁切和滚动陷阱；地标、标题、字段关联、焦点、键盘路径和reduced-motion完整。 | P0 |
| S23 | 运行态证据 | CSS、路由、代理或运行配置变化后刷新精确运行态；裁决以真实URL、DOM、computed style和控制台为准。 | P0 |
| S24 | 迁移与交付 | 保持URL、route name、权限、状态机、金额与业务事实；通过设计系统门、unit、lint、type-check、build、迁移 E2E和diff-check。 | P0 |

清单裁决只允许“通过”“不通过”“需要确认”。不设置泛化 P2；纯偏好且无复现证据、用户价值或验收方式的建议直接关闭。

## 5. 公共组件合同

现行公共出口：

`V2ActionMenu`、`V2Alert`、`V2Badge`、`V2Button`、`V2Card`、`V2Cluster`、`V2ConfirmDialog`、`V2Dialog`、`V2ErrorBoundary`、`V2GlassButton`、`V2Grid`、`V2Input`、`V2PageState`、`V2Select`、`V2Skeleton`、`V2Stack`、`V2ToastHost`、`showToast`、`useToastMessage`。

使用边界：

- `V2GlassButton` 只允许设计预览和明确批准的只读上下文次操作；业务页面标题区、筛选区、表格、正文和弹窗页脚均使用 `V2Button`。
- `V2ConfirmDialog` 使用普通或危险语义按钮，不改成玻璃材质。
- `V2Dialog` 自动提供标准壳；页面只追加已登记的 `v2-dialog-standard`、`v2-detail-dialog`、`v2-dialog-wide`、`v2-dialog-bottom-sheet`。
- 详情统一复用 `v2-detail-dialog__section`、`__facts`、`__table`、`__actions`、`__form-row`；禁止嵌套 `V2Card`。
- 弹窗页脚次操作在左、主操作在右，同尺寸、同基线，使用共享半透明液态背景且只保留顶部分隔线。

## 6. 页面、表格与反馈

- H1标题卡默认插槽必须为空；页面级搜索、下拉和按钮进入 `actions`。
- 汇总标签必须紧随对应 `h2` 并通过 `title-extra` 使用 `V2Badge`，不得用副标题承载数量。
- 单一列表的数据区不得重复渲染“某某列表”等可见标题。
- 顶层列表表格与分页必须置于同一个 `V2Card`。
- 同一主对象或生命周期的阶段账册合并为一个复合数据区；跨项目子记录不得在概览下失去项目归属后直接铺开。
- 普通单据明细行保持透明；弹窗表格必须取消页面级最小宽度继承。
- 页面标题操作和表格行内操作统一使用 `size="small"`。
- 禁止卡片嵌套、私有按钮材质、原始数据库主键、未映射英文状态和“权威、回读、后端阶段”等实现语言。
- 公共壳主内容区必须可独立纵向滚动。
- 刷新、读取和短暂操作结果只使用一次公共 Toast；禁止同时渲染 Toast 与页面级 `V2Alert`。
- 空态只在请求成功且结果为空时出现；失败态与空态互斥。

## 7. 全 V2 强制退出门

不得维护会遗漏新路由的第二份手工页面清单。静态门禁从 `src/pages/**/*.vue`、公共组件出口和导航目录动态发现现行页面与组件。

本地唯一入口：

```powershell
cd frontend-admin-v2
pnpm check:design-system
```

完整交付门：

```powershell
pnpm check:design-system
pnpm check:boundary
pnpm check:route-ledger
pnpm lint:check
pnpm test:unit
pnpm type-check:contracts
pnpm type-check
pnpm build
pnpm check:bundle-size
pnpm test:e2e:migration-gate
git diff --check
```

门禁职责：

- `design-system.test.ts`：token、共享组件、页面结构、弹窗、表格、分页、表单、反馈、唯一标准与唯一预览入口。
- `v2-ui-remediation-gate.test.ts`：动态扫描所有V2业务页，阻止私有材质和浏览器批注回归。
- `global-context-contract.test.ts`：公共项目/报告期上下文与浏览器门。
- `design-system-preview.spec.ts`：唯一预览页、V3弹窗、关闭策略、桌面/移动溢出和控制台。
- `check-clean-room-boundary.mjs`：只负责V2与Legacy隔离，不替代设计门。

CI的 `frontend-v2-gate` 必须显式执行 `pnpm check:design-system`，并继续执行完整 unit、类型、构建、迁移E2E和依赖审计。PR证据仍由同一 `frontend-v2-gate` job绑定HEAD SHA。

## 8. 变更与回滚

- 修改设计语义时，同一diff必须更新本文、实现token/组件、预览、静态门禁和浏览器门；计划或验收报告不得先行变成规范。
- 未增加新视觉方向、复杂交互或现有模式无法覆盖时，不引入第二组件库、Storybook、Figma流程或新预览地址。
- 门禁失败时回滚该批设计规则、组件和测试；不得放宽扫描、增加页面白名单或删除失败用例换取绿灯。
- 历史计划和质量报告保留审计价值，不批量改写；只停止把它们作为现行设计依据。
