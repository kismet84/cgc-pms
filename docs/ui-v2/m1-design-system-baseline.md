# Clean-room V2 M1 设计系统基线

状态：`ISSUE-053-001`～`ISSUE-053-004` 已完成，M1 退出门通过
适用范围：登录、应用壳和经营驾驶舱的最小无业务组件基线

## 1. 视觉真实源

- 用户选定经营驾驶舱视觉概念；当前可读原图 SHA-256：`BA1A7CC8391868F255AC7208753BD03AD2157E37018AEBF182E573B3D30D783B`。
- 稳定仓库锚点：`design-qa.md` 与 commit `4aaef899ad2624c36580745f7b69c900839831ab`。
- 原图无业务位图；品牌、导航、控件和数据图形均属于代码原生 UI。M1 不引入新图片、模拟业务数字或 Legacy UI 依赖。
- 本基线提取视觉语言，不复制或导入 Legacy Vue、CSS、Pinia、DOM 状态、Ant Design 适配层或业务组件。

## 2. 提取结果

| 维度     | V2 基线                                                                 |
| -------- | ----------------------------------------------------------------------- |
| 背景     | 冷灰蓝画布 `#f4f7fb`，面板使用真白 `#ffffff`                            |
| 文字     | 主文字 `#172033`，强标题 `#121b2e`，次级 `#53627a`，弱说明 `#64748b`    |
| 品牌     | 主蓝 `#2563eb`；focus ring 使用同色透明环                               |
| 语义     | 风险红 `#d71920`、预警橙 `#f97316`、成功绿 `#0f9f6e`、信息青 `#0f9fa8`  |
| 图表序列 | 蓝、青、橙、绿、紫、洋红六色序列；M1 只冻结 token，不实现业务图表       |
| 字体     | Inter、苹方、微软雅黑、系统无衬线；数字使用 tabular nums 的组件按需启用 |
| 间距     | 4px 基线，常用 8/12/16/20/24/28/32/40/48                                |
| 几何     | 3/6/8/12px 圆角；1px 轻边框；面板与浮层两级阴影                         |
| 控件     | 紧凑桌面 32/40px；移动可点目标 44px                                     |
| 动效     | 120/180/260ms；reduced-motion 下压缩到 1ms                              |
| 层级     | base、sticky、dropdown、dialog、toast 五级语义层                        |

实现入口：`frontend-admin-v2/src/styles/tokens.css`、`components.css`。业务组件只能消费 token，不散落重复核心颜色、阴影或圆角。

### 2.1 颜色语义标准

| 角色           | Token                                                                  | 使用边界                                                                     |
| -------------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 页面与容器     | `canvas`、`surface`、`surface-subtle`、`surface-hover`                 | 页面背景、卡片、弱分区和悬停；不表达业务状态                                 |
| 文字           | `text-strong`、`text`、`text-secondary`、`text-muted`、`text-disabled` | 按信息层级使用；禁用态不得只靠降低透明度                                     |
| 品牌与交互     | `primary`、`primary-hover`、`primary-active`、`primary-soft`           | 主操作、链接、焦点和常规导航激活态                                           |
| 信息           | `info`、`info-text`、`info-soft`                                       | 普通说明、建设/迁移状态，不表示风险                                          |
| 成功           | `success`、`success-text`、`success-soft`                              | 已完成、已通过、运行正常                                                     |
| 预警           | `warning`、`warning-text`、`warning-soft`                              | 需要关注但尚未失败的业务风险                                                 |
| 危险           | `danger`、`danger-text`、`danger-hover`、`danger-soft`                 | 错误、失败、高风险和破坏性操作                                               |
| 工作区标签例外 | `workspace-tab-accent`、`workspace-tab-accent-soft`                    | 仅用于用户确认的斜切标签激活线与激活态；不得代替危险色，也不得扩散到其他导航 |

同一信息只使用一种状态语义；颜色必须配合文字、图标或状态标签，不能作为唯一识别手段。业务组件禁止直接引用十六进制颜色，也不得借用 `danger` 表示普通选中态。

### 2.2 文字层级标准

| 层级       | 字号 / 字重 / 行高                      | 典型用途                    |
| ---------- | --------------------------------------- | --------------------------- |
| 页面标题   | `21–28` / `bold` / `tight`              | 页面唯一 `h1`               |
| 区块标题   | `17` / `semibold` / `tight`             | 卡片组、主要区块标题        |
| 卡片标题   | `15` / `semibold` / `ui`                | 卡片、对话框次级标题        |
| 正文       | `13–14` / `regular` / `body`            | 说明、数据正文、长文本      |
| 控件与标签 | `12–13` / `medium` 或 `semibold` / `ui` | 按钮、Tab、表单标签、状态值 |
| 辅助文字   | `11–12` / `regular` / `ui`              | Eyebrow、时间、提示、元数据 |

页面最多同时呈现三级标题；同一层级不得靠临时颜色或任意加粗制造新层级。正文默认使用 `text` 或 `text-secondary`，辅助文字使用 `text-muted`，禁用内容使用 `text-disabled`。业务数字仅在需要等宽对齐时启用 tabular nums。

## 3. 本切片组件

- 操作：`V2Button`，覆盖 primary/secondary/ghost/danger、small/medium/touch、loading、disabled 和 focus-visible。
- 表单：`V2Input`、`V2Select`，覆盖标签、必填、提示、错误、禁用、加载和 `aria-describedby`。
- 容器：`V2Card`，覆盖标题、说明、操作、正文、页脚和可交互状态。
- 状态：`V2Badge`、`V2Alert`、`V2Skeleton`，覆盖 neutral/info/success/warning/danger、可关闭反馈与加载语义。
- 浮层：`V2Dialog`，覆盖焦点进入/恢复、Escape、背景关闭、Tab 循环、标题/说明关联和移动底部形态。
- 布局：`V2Stack`、`V2Cluster`、`V2Grid`，只暴露 token 化间距、对齐和最小列宽。

非目标：认证、权限、导航、真实驾驶舱、业务表格/图表、Toast 队列、复杂表单适配、第三方 UI 库和正式入口切换。

## 4. 响应式与状态矩阵

| 视口 | 检查重点                                        |
| ---- | ----------------------------------------------- |
| 1440 | 面板节奏、双列网格、控件密度、颜色和层级        |
| 1024 | 网格自动收敛、文案和操作不裁切、无横向溢出      |
| 390  | 44px 控件、单列、对话框底部形态、焦点与触控可达 |

状态必须覆盖：default、hover、focus-visible、disabled、loading、error、success、warning、danger、dialog open/closed、reduced-motion。

## 5. 后续边界

- `ISSUE-053-002` 可复用表单、Button、Alert、Dialog 和布局原语实现安全会话；不得反向修改 token 含义来迁就单页。
- `ISSUE-053-003` 可复用布局原语和 Card 构建应用壳；导航与对象上下文另行实现。
- `ISSUE-053-004` 负责完整响应式、无障碍、403/404 和错误边界退出门；本切片只冻结基础组件行为。

## 6. M1 退出门回写

- 1440 展开侧栏、1024 图标栏、390 移动抽屉均无横向溢出；移动端账户、退出与关闭入口可见，主操作可触控和键盘到达。
- 跳转主内容、可见焦点、语义地标、可访问名称、移动焦点循环/恢复与 `prefers-reduced-motion` 已形成自动化证据。
- 403、404、全局异常、加载、空壳、错误和重复请求错误有独立语义；通知入口只展示壳级说明，不读取或伪造通知数据。
- 选定视觉语言保持不变；M1 只完成壳与失败状态，真实驾驶舱指标、图表和业务交互仍属于 M2。

## 7. 现行 V2 标准样式合同

本节固化后续业务迁移必须复用的现行样式。权威顺序为：

1. `frontend-admin-v2/src/styles/tokens.css` 定义颜色、字号、间距、圆角、阴影、控件高度、层级和动效数值。
2. `frontend-admin-v2/src/styles/base.css` 定义画布、全局字体、焦点、无障碍隐藏和 reduced-motion。
3. `frontend-admin-v2/src/styles/components.css` 与 `frontend-admin-v2/src/components/V2*.vue` 定义公共组件外观和交互状态。
4. 本文定义使用边界。业务页面只补业务布局，不复制公共组件样式，不新增裸颜色或裸 `px/rem` 字号。

### 7.1 字号、颜色与密度

| 场景                         | 标准                                             |
| ---------------------------- | ------------------------------------------------ |
| 页面标题                     | `--v2-font-size-21`、bold；每页只有一个语义 `h1` |
| 卡片或区块标题               | `--v2-font-size-15`、semibold                    |
| 详情重点信息                 | `--v2-font-size-14`、semibold                    |
| 表格、详情事实、分页、下拉项 | `--v2-font-size-12`                              |
| 表单控件正文                 | `--v2-font-size-13`                              |
| 辅助说明、元数据             | `--v2-font-size-11` 或 `--v2-font-size-12`       |

- 颜色只能使用 `--v2-color-*` 语义 token；状态色必须同时配文字或图标，不得只靠颜色表达。
- 页面标题通过 `V2Card.headingLevel/titleId` 或原生语义标题实现；不可用普通 `div` 模拟标题。
- 表单统一使用 `V2Input`、`V2Select`。详情/编辑弹窗内的输入框、文本域和下拉框使用透明背景，轮廓使用 `primary 22% + surface` 的浅蓝混色；focus、error、disabled 继续使用公共语义状态。

### 7.2 标准弹窗

- 确认操作统一使用 `V2ConfirmDialog`，禁止 `window.confirm`；破坏性确认使用 danger 动作，loading 时禁止重复提交和关闭。
- 详情、新建、编辑统一使用 `V2Dialog`。面板使用 `v2-dialog-standard`；业务详情再叠加 `v2-detail-dialog`。
- 标准宽度为 `32rem`；视口不小于 `64rem` 时为 `46rem`；移动端保持底部弹层形态。
- 弹窗内部允许滚轮、触控和键盘滚动，但隐藏可视滚动条。下拉菜单向控件下方锚定，可独立滚动，不得撑高或撑宽弹窗。
- 只读详情允许背景关闭；新建、编辑背景点击不得关闭，必须保留内容并提示用户保存或主动关闭。
- 标题区、内容区、操作区使用 `--v2-dialog-divider` 分隔；禁止页面重新定义另一套弹窗壳。

### 7.3 详情区与操作区

- 详情结构统一使用 `v2-detail-dialog__section`、`v2-detail-dialog__facts`、`v2-detail-dialog__actions` 和 `v2-detail-dialog__form-row`。
- 详情内嵌 `V2Card` 取消独立底色、圆角、阴影和四周边框，只保留分区所需的浅蓝分割线。
- 详情区与操作区之间的分割线上下各留 `10px`；不得用空卡片或背景色代替分区。
- 事实信息桌面端两列，窄视口单列。快速操作桌面端保持一行弹性布局，空间不足时换行，`400px` 以下按钮占满行宽。
- 详情表格正文、事实值、分页和卡片详情文字统一为 `12px` token；业务页不得单独放大。

### 7.4 按钮

- 常规主操作、次操作、幽灵操作和危险操作使用 `V2Button` 对应 variant；确认弹窗继续使用语义明确的普通按钮。
- 详情与编辑操作使用 `V2GlassButton`，接口固定为 `text`、`loading`、`disabled`、`onClick`、`className`。
- 玻璃按钮使用 50% 透明白灰渐变、白色高透明描边、顶部弧形高光、内部高光、底部环境反光和柔和内外阴影；不得使用蓝色实心填充。
- 同时使用 `backdrop-filter: blur(16px) saturate(160%)` 与 WebKit 写法。hover 轻微上浮，active 下压并缩小，focus-visible 显示蓝色柔和焦点环；disabled 必须有文字、背景和交互三重区分。
- reduced-motion 下取消位移和过渡，不得牺牲键盘焦点或可读性。

### 7.5 分页、下拉与反馈

- 分页右对齐，固定顺序为“上一页 — 第 N 页 — 下一页”；页码位于两个按钮中间并使用 `--v2-font-size-12`。
- `V2Select` 选择后立即提交页面筛选状态；空值、禁用、键盘焦点和 Escape 行为由公共组件处理，页面不得另造下拉层。
- 操作反馈复用 `V2Alert` 语义色。与按钮直接相关的短反馈可作为按钮上方气泡展示；短暂成功结果可在 3 秒后关闭，校验错误和未完成请求必须保持可读并允许手动关闭。

### 7.6 自动门禁

- `frontend-admin-v2/tests/unit/design-system.test.ts` 校验 token 引用、禁止裸颜色/字号、标题层级、标准弹窗、详情分区、玻璃按钮、分页顺序、表单轮廓和编辑防误关。
- 新迁移页面至少通过 `test:unit`、`type-check`、`lint:check`、`build` 和 `git diff --check`。
- 浏览器验收覆盖 `1440 / 1024 / 390`：检查弹窗宽度、内部滚动、下拉不溢出、焦点可见、按钮状态和分页顺序。
