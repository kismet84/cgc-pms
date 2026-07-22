# 第53条主线：CGC-PMS 全量 UI Clean-room V2 重构任务计划书

状态：M0、M1、M2、M3 已完成并通过；M3 `ISSUE-053-010～016`全部收口，10个目标路由达到`68/19/0`；未切正式入口或发布生产。

**Goal:** 在不修改 CGC-PMS 既有业务语义、URL、权限码、后端 API 和数据事实的前提下，新建一套与旧 Vue 组件、旧布局、旧 CSS 和旧页面状态完全隔离的 frontend-admin-v2 应用，按第50条主线确定的八个业务域全量重新设计、实现和验收现有页面；已完成的新版经营驾驶舱作为 V2 首个视觉基线保留，但必须解除对任何旧 UI 组件的依赖。

**Architecture:** 采用“同仓库双前端应用 + 独立构建与容器 + 同源后端 + 无 UI 契约共享 + /v2 并行验收 + 整站切换”架构。frontend-admin 作为 Legacy 应用冻结，frontend-admin-v2 作为 Clean-room 新应用，两者分别拥有入口、路由、Pinia、设计系统、样式、测试、构建产物和 Docker 容器。V2 严禁导入 frontend-admin/src 下的任何代码；只允许通过无 Vue、无 DOM、无 CSS 的契约包共享 DTO、权限码、API 路径和纯函数。最小可行原则是先建立隔离底座和新版驾驶舱，再按业务域垂直迁移；不复制后端、不扩展业务功能、不同步重写数据库、不在迁移中删除 Legacy。

## 1. 决策摘要

| 编号 | 决策 | 结论 |
| --- | --- | --- |
| DR-01 | 隔离形式 | 同仓库新建 frontend-admin-v2，不在旧应用内做双布局混合迁移。 |
| DR-02 | Legacy 处理 | frontend-admin 冻结，只修影响当前运行或 V2 对照验收的阻塞缺陷。 |
| DR-03 | 驾驶舱 | 保留新版经营指挥台的视觉方向与真实数据语义；在 V2 重新装配，不携带旧角色视图组件。 |
| DR-04 | 设计来源 | Stitch 中的真实可编辑设计是每个域实施的视觉源；截图仅作验收参考。 |
| DR-05 | 后端 | 新旧前端共用现有 Spring Boot /api，本主线默认不新增业务 API。 |
| DR-06 | 容器 | 过渡期新增 cgc-pms-frontend-v2-dev 和 cgc-pms-frontend-v2；不新增数据库、Redis、MinIO 或后端容器。 |
| DR-07 | 路由 | 过渡期以 /v2 为部署 base，V2 内部仍使用现有业务路径；最终切换后恢复 /dashboard 等原 URL。 |
| DR-08 | 上线 | Legacy 保持正式入口，V2 在 /v2 全量验收；全部路由通过后再整站切换。 |
| DR-09 | 数据 | 本主线无数据库 migration、无数据回填、无金额事实改写。 |
| DR-10 | 第52条主线 | 第52条的干净基线和演示项目可作 V2 验收数据源，但不与本主线共享实施 diff。 |

## 2. 当前基线与承接关系

### 2.1 当前源码盘点

截至 2026-07-18 当前工作区：

| 指标 | 当前值 | 口径 |
| --- | ---: | --- |
| 路由视图 import 记录 | 73 | router/index.ts 中页面 import 出现次数 |
| 独立路由视图 | 65 | 页面 import 路径去重 |
| src/pages Vue 文件 | 128 | 递归统计，不等于路由数 |
| 页内 components Vue 文件 | 62 | src/pages 下局部组件 |
| 共享 src/components Vue 文件 | 17 | Legacy 共享 UI，V2 全部禁止导入 |
| 一级业务域 | 8 | 第50条主线已落地 |
| Legacy 开发前端容器 | 1 | cgc-pms-frontend-dev，端口 5173 |
| Legacy 生产前端容器 | 1 | cgc-pms-frontend，端口 80/443 |

实施启动时必须重新生成路由、视图、组件和权限基线；以上数字只作为计划冻结候选。

### 2.2 与第50条主线的关系

第50条主线是 V2 信息架构与导航基线，已确定八域、工作区、Tab、对象上下文、URL 和权限语义。本主线：

- 复用第50条的信息架构和路由归属，不发明第二套导航分类。
- 在独立 V2 应用中重新实现导航，不导入 Legacy 导航组件。
- 保留原 URL、route name、permission、adminOnly 和路由守卫结果。
- 若发现第50条归属错误，先提交变更决策，不在页面重构中静默修改。

### 2.3 与第52条主线的关系

第52条主线负责数据库最终基线、平台 bootstrap 和显式演示项目。本主线不修改其计划，也不执行数据重置。

- M0—M2 可在现有 dev 数据上完成隔离、会话和驾驶舱验收。
- M3 起若第52条演示项目可用，优先作为跨域、多角色和金额对账数据源。
- 第52条未完成时，不得为截图向共享 dev 库写入伪造业务数据；使用真实空态、受控测试 fixture 或既有 dev 数据。
- 两条主线并行时，阶段验收必须冻结 backend、数据库版本和演示包标识，避免失败无法归因。

## 3. 目标、范围与非目标

### 3.1 主线目标

1. 新建可独立开发、测试、构建、部署和回滚的 V2 前端应用。
2. 用自动化规则证明 V2 未导入任何 Legacy UI 组件和样式。
3. 建立统一 V2 设计令牌、基础组件、业务模式和可访问性标准。
4. 以 Stitch 设计为视觉真实源，重建全部当前可达路由视图。
5. 保留新版经营驾驶舱方向，完成多角色 V2 化和 Clean-room 脱钩。
6. 保持登录、刷新、CSRF、权限、租户、项目数据范围、金额、状态机和审计语义不变。
7. 保持深链、query、hash、返回路径和浏览器刷新行为不变。
8. 完成整站 V2 验收、受控切换和 Legacy 回滚能力。

### 3.2 实施范围

- frontend-admin-v2 应用、Dockerfile、Nginx、Vite、TypeScript、ESLint、Vitest 和 Playwright。
- V2 token、基础组件、反馈组件、数据展示、表单、导航和响应式规则。
- 登录、应用壳、八个业务域、全局工具、错误页和个人设置。
- 当前路由视图的功能对等重建：列表、详情、表单、Tab、弹窗、抽屉、图表、空态、加载、失败和无权限。
- 新旧应用并行容器、/v2 路由、健康检查、缓存和 API/SSE 代理。
- 迁移矩阵、功能对等证据、视觉 QA、正式验收报告和项目地图回写。

### 3.3 非目标

- 不新增或重设业务规则、金额口径、审批状态机、租户边界和数据权限。
- 不因 UI 重构修改后端 DTO、数据库结构、migration 或历史数据。
- 不把按钮可见性当作新增写权限。
- 不扩成微前端平台、低代码平台、BIM 平台或报表设计器。
- 不直接将 Stitch 生成代码作为生产业务代码；Stitch 输出只作设计源。
- 未获得单独授权时，不提交、不 push、不合并、不发布生产、不删除 Legacy。

## 4. 目标架构

### 4.1 仓库结构

~~~text
cgc-pms/
├─ frontend-admin/                  # Legacy，冻结
├─ frontend-admin-v2/               # Clean-room V2
│  ├─ src/
│  │  ├─ app/                       # 入口、router、stores、providers
│  │  ├─ design-system/
│  │  │  ├─ tokens/
│  │  │  ├─ primitives/
│  │  │  └─ patterns/
│  │  ├─ layouts/
│  │  ├─ features/                  # 按八个业务域组织
│  │  ├─ pages/                     # 路由级装配
│  │  ├─ shared/
│  │  │  ├─ api/
│  │  │  ├─ auth/
│  │  │  └─ utils/
│  │  └─ tests/
│  ├─ e2e/
│  ├─ Dockerfile
│  └─ nginx.conf
├─ packages/
│  └─ frontend-contracts/           # 无 UI 共享契约
├─ backend/
└─ deploy/
~~~

Legacy 文件不在 M0 批量移动或重命名，避免制造大规模无业务价值 diff。

### 4.2 依赖矩阵

| 消费方 | 允许依赖 | 禁止依赖 |
| --- | --- | --- |
| frontend-admin-v2 | Vue、Router、Pinia、Axios、ECharts、V2 设计系统、frontend-contracts | frontend-admin/src、Legacy CSS、Legacy Vue 组件、Legacy store |
| V2 业务页面 | V2 design-system 和本域 feature | 直接使用旧共享组件；在业务页散落第三方 UI 组件 |
| frontend-contracts | TypeScript 类型、常量、无副作用函数 | Vue、Pinia、DOM、CSS、消息提示、业务写状态 |
| Legacy | 原有依赖 | 反向导入 V2 页面或样式 |

V2 ESLint 必须使用 no-restricted-imports 禁止 Legacy 路径；CI 再用静态扫描复核。任一违规 import 直接失败。

### 4.3 无 UI 契约包

frontend-contracts 首版只接纳：

- ApiResponse、分页类型和稳定请求/响应 DTO。
- 权限码、route name、业务状态常量。
- API 路径生成器和无副作用格式函数。

旧 request.ts 与 Ant 消息、Pinia 和页面跳转耦合，不直接移入共享包。V2 重写请求客户端，但必须保持：

- /api 运行时 base URL 和 withCredentials。
- XSRF-TOKEN 到 X-XSRF-TOKEN。
- 401 单飞刷新、等待队列、硬超时和 fail-close 登出。
- 已提示错误去重。
- SSE 长连接和通知已读行为。

### 4.4 运行拓扑

~~~mermaid
flowchart LR
    U["Browser"] --> L["Legacy Nginx :80/:443"]
    L -->|"/"| OLD["Legacy assets"]
    L -->|"/v2/**"| NEW["frontend-v2:80"]
    L -->|"/api/** and SSE"| API["backend:8080"]
    NEW -->|"browser same-origin /api"| L
~~~

过渡期不新增第三个 edge 容器。现有前端 Nginx 暂时承担 /v2 代理，V2 容器仅在 Docker 内网暴露 80。

开发环境：

~~~text
cgc-pms-frontend-dev       localhost:5173  Legacy
cgc-pms-frontend-v2-dev    localhost:5174  V2
cgc-pms-backend-dev        localhost:8080  Shared API
~~~

## 5. Stitch 与 V2 设计系统

### 5.1 设计准入

每个实施阶段必须在 Stitch 交付可编辑设计，至少包含：

1. 1440px 桌面主视图。
2. 1024px 紧凑桌面布局。
3. 390px 移动端核心流程。
4. 加载、空数据、部分数据、失败、无权限和超长内容状态。
5. 列表筛选、分页、详情、编辑、确认、危险操作和成功/失败反馈。
6. 动态数据的最小、典型和最大容量样例。

没有用户确认的视觉目标，不启动对应业务域的大规模页面实现。

### 5.2 设计令牌

V2 统一定义：

- 品牌色、功能色、中性色、风险色、数据可视化序列色。
- 字体、字号、字重、行高和数字对齐。
- 4/8 间距、网格、容器宽度、表单栅格和响应式断点。
- 边框、圆角、阴影、动效、层级和 focus ring。
- 表格密度、图表尺寸、卡片间距和页面节奏。

### 5.3 基础组件

业务页面实施前，至少完成支持应用壳、登录和驾驶舱的最小组件集：

- Button、IconButton、Link、Tag、Badge、Avatar、Tooltip、Dropdown。
- Input、NumberInput、Select、TreeSelect、DatePicker、Upload、Checkbox、Radio、Switch。
- FormField、FormSection、ValidationMessage、StickyActionBar。
- Modal、Drawer、ConfirmDialog、Toast、InlineAlert。
- Table、ColumnSettings、Pagination、MobileCardList、DescriptionList。
- PageHeader、FilterBar、KpiStrip、AnalysisRail、Tabs、ObjectContextBar。
- Loading、Skeleton、EmptyState、ErrorState、ForbiddenState。
- ChartFrame、Legend、MetricCard、RiskIndicator、TrendBlock。

允许第三方 UI 库作为交互原语，但只在 design-system/adapters 内直接导入；业务页面必须依赖 V2 组件 API。

### 5.4 可访问性与响应式

- 关键流程支持全键盘操作。
- 图标按钮具备可读名称；表单错误与字段关联。
- 正文、控件和 focus 状态满足 WCAG 2.1 AA。
- 移动端可点目标原则上不小于 44×44px。
- 大表格在移动端转为卡片或受控横向滚动。
- reduced-motion 下关闭非必要动效。

## 6. 业务域迁移矩阵

| 阶段 | 业务域 | 核心路由 | 最小完整性要求 |
| --- | --- | --- | --- |
| M2 | 工作台 | /dashboard、/approval/todo/done/cc/mine、/alert、/dashboard/reports | 驾驶舱多角色、待办、预警、报表深链 |
| M3 | 项目履约 | /project、/project-schedule、/site/daily-log、/quality-safety、/technical-management、/project-closeout | 项目列表到对象上下文、现场到收尾 |
| M4 | 商务合约 | /contract、/variation、/bid-cost、/cost-target、/cost、/budget、/production-measurement | 合同、变更、目标成本、成本与利润口径不变 |
| M5 | 供应链与物资 | /supplier-sourcing、/inventory/purchase-request、/purchase、/inventory | 申请到订单、验收、库存和领用连续流程 |
| M6 | 分包与结算 | /subcontract、/settlement | 分包履约、计量、结算和详情 |
| M6 | 资金财务 | /payment、/revenue、/invoice、/finance-operations、/cash-journal、/cash-forecast、/accounting-entry、/financial-close | 金额、来源、幂等、期间、冲销和审批不变 |
| M7 | 基础资料 | /partner、/org、/material、/cost/subject | 主数据、树、版本、引用保护和影响追踪 |
| M7 | 系统管理 | /approval/process、/system、/profile、/settings、/help、错误页 | adminOnly、访问控制、审计、模板和全局工具 |

详细页面不重复抄写第50条全表。M0 从当前 router 自动生成唯一迁移台账；每个 route name 记录 Legacy 视图、V2 视图、permission、adminOnly、状态、Stitch 设计、测试和验收证据。

## 7. 实施阶段

### M0：Clean-room 隔离底座

目标：建立可证明的新旧隔离，不改变 Legacy 功能。

任务：

1. 盘点 route name、URL、视图、permission、adminOnly、角色驾驶舱和主要 API。
2. 建立迁移台账；页面初始为 LEGACY_ONLY，驾驶舱为 V2_SOURCE_AVAILABLE。
3. 新建 frontend-admin-v2 最小 Vue 3 + TypeScript + Vite 应用。
4. 建立独立 package、lockfile、tsconfig、ESLint、Vitest 和 Playwright。
5. 建立 Legacy import 禁止规则和 CI 扫描。
6. 新建 frontend-contracts 最小包，只纳入登录和驾驶舱必需稳定类型。
7. 新增 V2 开发 Docker service，端口 5174，共用 backend dev 网络。
8. 建立 V2 静态健康检查。

验收：

- Legacy 测试、类型检查和构建不回归。
- V2 空壳可在 5174 访问，API 代理可达但无业务写入。
- 故意导入 Legacy Vue/CSS 时 Lint 或 CI 必须失败。
- 当前未跟踪文件和用户改动无损。

回滚：删除新 V2 目录和 Compose 新 service；Legacy 与数据不变。

#### M0 实施结果（2026-07-18）

- 已在 `codex/mainline-53-ui-v2-m0` 建立独立 `frontend-admin-v2`，使用独立 package、lockfile、TypeScript、ESLint、Vitest、Playwright、Vite、Dockerfile 和 Nginx。
- 已自动生成 87 个命名路由、73 个路由视图引用、65 个独立页面模块的迁移台账；86 个路由为 `LEGACY_ONLY`，驾驶舱为 `V2_SOURCE_AVAILABLE`。
- 已建立 `packages/frontend-contracts`，首版仅冻结登录、用户、驾驶舱角色/权限/API 和成本驾驶舱最小 DTO；边界扫描拒绝 Legacy 源码、Vue、Pinia、DOM、浏览器状态和 CSS 进入共享契约。
- V2 dev service 为 `cgc-pms-frontend-v2-dev`，端口 5174；连续稳定窗口内 `/v2/health` 为 200、`/api/actuator/health` 代理为 UP、Legacy 5173 为 200、容器为 healthy。
- V2 4 个测试文件 7 项、契约/应用类型、Lint、构建、包体和 Edge Playwright 1 项通过；Legacy 129 个测试文件 727 项、类型与构建通过。
- 本地镜像 `cgc-pms-frontend-v2:m0` 构建与临时 Nginx `/v2/health`、`/healthz` 烟测通过；未修改数据库、后端业务、Legacy UI、正式入口或生产环境。
- M0 视觉实现门不适用；用户已选新版经营驾驶舱概念继续作为 M2 唯一视觉基线，M0 未调用 Stitch 生成新设计。
- 正式证据见 `docs/quality/第53条主线-M0-Clean-room隔离底座验收报告.md`。

### M1：设计系统、认证和应用壳

目标：交付可承载所有业务页的新设计系统、安全会话和响应式应用壳。

任务：

1. 从已选驾驶舱设计提取 token，在 Stitch 完成桌面/移动应用壳。
2. 实现支持壳、登录和驾驶舱的最小组件集。
3. 实现登录、用户信息恢复、登出、CSRF、401 刷新和错误去重。
4. 实现八域导航、工作区 Tab、对象上下文、全局项目和报告期。
5. 实现桌面、紧凑桌面、移动端和减少动效模式。
6. 实现 403、404、全局错误边界、加载壳和通知入口。

验收：

- ADMIN、普通有权、无权和未登录用户的导航与守卫结果同 Legacy。
- 同源会话有效，不把 token 写入 localStorage。
- 1440、1024、390 视口无横向溢出和遮挡。
- axe 核心扫描无 serious/critical 违规。

### M2：工作台与新版驾驶舱

目标：将已选新版驾驶舱完整落入 Clean-room V2，并完成工作台域。

任务：

1. 保留经营健康度、最高风险、利润/偏差/资金/进度指标、趋势、预警待办和快捷入口。
2. 将无 UI 数据逻辑迁入 V2 feature 或契约层，禁止导入 Legacy Dashboard 角色组件。
3. 为项目经理、商务/成本、采购、生产、总工、财务和管理层建立 V2 组合。
4. 重建我的工作、预警中心和报表目录。
5. 实现项目、报告期、角色切换、刷新、钻取、空态和部分失败隔离。

验收：

- 驾驶舱 Legacy Vue/CSS import 为零。
- 各角色只发起有权 API；项目级角色不回退租户全量数据。
- 真实空数据展示空态，不注入本地 mock 数字。
- Stitch 对照 QA 通过，控制台无 warning/error。

### M3：项目履约

范围：项目列表、总览、成员、编辑、项目计划、现场日报、质量安全、技术 RFI、竣工收尾。

验收：项目 ID 来源、数据范围、对象上下文、query/hash、表单字段、状态机、附件和深链全部对等；桌面和移动核心流程通过。

实施结果：已通过。`ISSUE-053-010～016`完成，十路由台账`68/19/0`；正式证据见`docs/quality/第53条主线-M3-项目履约全量退出门验收报告.md`。

### M4：商务合约

范围：合同、变更、投标、目标成本、成本台账、成本核对、动态利润、预算和产值。

特别门禁：

- 合同当前额、签证、结算、成本、利润和税额不在前端重新推导为权威事实。
- 金额统一使用精确字符串或定点转换，禁止浮点替代后端口径。
- 新建、编辑和状态操作权限不得因页面合并而放宽。
- 成本科目中心的规则、范围和追踪在 M7 完成；M4 只消费稳定契约。

验收：新建/编辑/详情回读、金额对账、历史快照、钻取、负权限和跨项目拒绝通过。

### M5：供应链与物资

范围：供应商、采购申请、订单、验收、仓库、库存、出入库和领料。

特别门禁：库存数量、价值、来源流水、幂等键、调拨、验收和领退料状态完全依赖后端事实；前端不乐观修改权威库存。

验收：正反向流程、部分验收、超长物料名称、空库存、缺权限、跨仓候选和失败重试通过。

### M6：分包结算与资金财务

范围：分包任务、计量、结算、付款、费用、收入回款、发票、资金运营、日记账、预测、凭证和月结。

特别门禁：

- 付款来源、可付余额、结算快照、发票校验、会计生成、月结期间和冲销只展示后端事实。
- 权限与业务对象访问必须同时满足。
- 浏览器写侧验收只使用 dev/test/demo 受控数据并记录前后对账。

验收：金额守恒、来源追踪、重复提交幂等、失败原子性、多角色审批、单据预览/下载和负权限通过。

### M7：基础资料、系统管理和全局工具

范围：合作方、组织、材料字典、成本科目中心、流程配置、用户、角色、权限、字典、数据管理、审计、业务单据模板、个人、设置和帮助。

特别门禁：

- ADMIN/SUPER_ADMIN 与普通角色边界不放宽。
- 用户、角色、权限、字典、审计和模板读写权限保持分离。
- 成本科目中心保持版本、引用保护、规则、项目范围和影响追踪。
- 审计页只读，不新增修改历史能力。

验收：隐藏路由、账户入口、错误页、帮助、深链、adminOnly 和按钮权限通过；迁移台账无 LEGACY_ONLY 页面。

### M8：全量对等、切换演练和 Legacy 退役门

任务：

1. 锁定最终路由、权限、API 和角色矩阵。
2. 运行 V2 全量单元、集成、E2E、可访问性、构建、包体和浏览器 QA。
3. 使用代表性多角色、多项目、金额、库存和审批数据对账。
4. 在 dev/test/demo 演练 V2 接管根路径、回滚 Legacy、再次恢复 V2。
5. 生成正式验收报告和上线决策清单。
6. 只有获得生产授权后才创建正式发布变更。

验收：

- 全部可达 route name 均有 V2 实现或正式裁决不再可达。
- V2 导入图中 Legacy Vue/CSS 边为零。
- 权限、租户/项目范围、金额和库存对账通过。
- 切换与回滚都能恢复健康状态。
- 无未分类发现项或口头悬空问题。

## 8. 单页迁移标准流程

每个路由视图必须按顺序执行：

1. 记录 URL、route name、query/hash、permission、adminOnly、API、字段、操作和全部状态。
2. 确认 Stitch 桌面、移动和状态设计。
3. 只将稳定 DTO、路径和常量纳入共享契约，不复制 Legacy UI 状态。
4. 使用 V2 设计系统、store、router 和 request client 实现。
5. 对照 Legacy 请求、输出、权限、错误、返回和深链。
6. 同视口并排完成 Stitch 视觉 QA。
7. 验证 1440、1024、390、键盘、focus、语义和 axe。
8. 迁移台账改为 V2_ACCEPTED，附测试和 QA 证据。

单页完成必须同时满足：

- Legacy Vue/CSS import 为零。
- URL、route name、query/hash、刷新和深链正确。
- 路由权限、按钮权限、adminOnly 和无权限路径正确。
- API 方法、路径、参数、写侧时机和错误边界一致。
- loading、empty、partial、error、forbidden、long-content 状态齐全。
- 桌面、紧凑桌面和移动布局通过。
- 单元、域 E2E、类型、Lint、构建和视觉 QA 通过。
- 控制台无未解释 warning/error。

## 9. 安全、权限和数据一致性

### 9.1 会话与 CSRF

- Legacy 与 V2 在同一主机共享 HttpOnly 会话，不引入第二套 token。
- V2 写请求从 XSRF-TOKEN 设置 X-XSRF-TOKEN。
- 401 刷新使用隔离 client，禁止递归触发自身拦截器。
- 刷新失败或超时清空队列并 fail-close 登出。

### 9.2 权限和数据范围

- 导航可见、路由可达和按钮能力分层验证，均不替代后端授权。
- dashboard:view 及角色专用 dashboard 权限兼容保持。
- ADMIN/SUPER_ADMIN 与 adminOnly 语义保持，不新建前端角色硬编码矩阵。
- 项目、合同、结算、库存、付款和财务页面分别验证跨租户、同租户无项目范围、无权限和对象不存在。

### 9.3 金额、库存和状态机

- 前端指标只展示已返回事实，不作为过账、可付、可用库存或状态转换权威。
- 金额、库存、审批和会计等待后端成功回读，不做权威数据乐观更新。
- 业务码失败、401/403、网络失败和超时必须保留输入或提供明确恢复动作。

## 10. Docker、Nginx 和发布

### 10.1 开发 Compose

V2 service 使用 Node 22、端口 5174、独立 node_modules，复用现有 pnpm store、Corepack volume 和 dev 网络；VITE_API_TARGET 指向 backend:8080。

### 10.2 生产过渡态

- 新增 cgc-pms-frontend-v2 镜像，只加入内部网络，不映射宿主机 80/443。
- 现有 cgc-pms-frontend 保持入口，新增 /v2 代理。
- /api 和 SSE 仍由同一入口代理 backend；SSE 保持关闭 buffering。
- V2 资源使用 /v2/assets，避免与 Legacy /assets 缓存键冲突。
- 新旧前端使用独立 tag、healthcheck 和回滚记录。

### 10.3 最终切换

最终切换需单独授权：

1. 锁定 Legacy、V2、backend 镜像 tag 和数据库版本。
2. 备份 Nginx、Compose 和运行时配置。
3. 将 V2 BASE_URL 改为根路径并生成正式镜像。
4. 将 80/443 切到 V2，保留 Legacy 回滚镜像。
5. 验证登录、驾驶舱、八域代表路由、API、SSE、深链刷新和缓存。
6. 健康门失败立即恢复 Legacy，不执行数据回滚。

## 11. 测试与证据矩阵

V2 固定提供：

~~~text
pnpm test:unit
pnpm type-check
pnpm lint:check
pnpm build
pnpm check:bundle-size
pnpm test:e2e:<domain>
~~~

分层证据：

| 层级 | 证据 |
| --- | --- |
| 静态隔离 | restricted imports、依赖图、Legacy CSS/import 扫描 |
| 单元/组件 | token、组件交互、formatter、store、router guard、request interceptor |
| 契约 | API 方法/路径/参数、permission、route name、query/hash |
| 集成 | Pinia、Router、Axios 边界、并发和陈旧响应 |
| 浏览器 | 真实后端、真实权限、真实空态/数据、深链、刷新、控制台 |
| 视觉 | Stitch 对照、1440/1024/390、全部状态 |
| 可访问性 | axe、键盘、focus、表单标签、对比度、reduced motion |
| 性能 | 路由懒加载、ECharts 按需、包体门禁、关键页请求数量 |
| 运维 | V2 health、API/SSE 代理、缓存、切换与回滚演练 |

定量门禁：

- 任一生产 JS chunk 不超过 500 KiB；超限需拆分或专项签认。
- 核心页 axe serious/critical 为 0。
- 浏览器验收控制台未解释 warning/error 为 0。
- 当前全部可达 route name 有且仅有一个迁移处置结果。
- 不为视觉验收向共享 dev 库写入伪造业务数据。

每阶段正式证据至少包含：迁移台账、命令结果摘要、Stitch 标识、视觉 QA、权限与数据风险结论、阻塞/剩余风险和回滚路径。

## 12. 风险与停止条件

| 风险 | 控制 |
| --- | --- |
| V2 误导入 Legacy | 独立 package + ESLint + CI 依赖图 |
| 重复业务逻辑导致口径漂移 | 业务权威留在后端，代表数据对账 |
| 为设计修改 API/数据 | 缺口另立决策，本主线默认不改后端 |
| 权限只做前端可见性 | 路由、按钮、API 正负样本和真实角色 E2E |
| 多应用会话不一致 | 同源 /v2、共享 cookie 契约、刷新队列测试 |
| /v2 资源或刷新 404 | Vite base、Router base、Nginx fallback 三层测试 |
| 两套组件长期并行 | 迁移台账、阶段完成门和最终整站切换 |
| 驾驶舱仍混用旧角色视图 | 只保留视觉成果，重建多角色组合 |
| 表格/图表性能下降 | 懒加载、按需图表、分页、陈旧请求取消、包体门禁 |
| 第52条同时改变运行态 | 阶段验收冻结 backend、DB 和演示包版本 |

必须停止：

- V2 只能通过导入 Legacy Vue/CSS 才能继续。
- API 不能表达权威事实，继续需要猜测金额、库存、权限或状态。
- 发现跨租户、跨项目、越权、金额或库存不守恒。
- 登录刷新、CSRF 或 SSE 在 /v2 下不能保持同源安全语义。
- Stitch 缺少关键状态，且实现选择会改变用户流程或权限。
- 工作区有重叠的未知用户修改，无法安全保留。
- 验收指向生产写操作、生产数据重置或未授权部署。

### 12.1 失败分类

| 分类 | 判定示例 | 处置 |
| --- | --- | --- |
| `tool_config` | 浏览器、图谱、凭据或依赖工具未就绪 | 修复工具前置；不判业务失败 |
| `tool_invocation` | 命令参数、转义、Schema 或调用方式错误 | 修正调用后做一次最小复验 |
| `environment_prerequisite` | Docker、端口、代理、测试数据或稳定等待未就绪 | 恢复本地 dev/test/demo 环境后复验 |
| `ready_issue_config` | Ready 的路径、选择器或验证契约与当前事实不一致 | 最小修正规则并保留等价验收目标 |
| `retrieval_gap` | 图谱或索引未召回已知源码 | 改用当前源码和 `rg` 交叉核验，不作不存在断言 |
| `quality_or_security` | 可复现的功能、权限、安全、数据一致性或构建失败 | 本轮修复并复验；无法修复则停止并判不通过 |
| `unknown` | 证据冲突或不足 | 补充更强证据；禁止强行归因或通过 |

## 13. 回滚和 Legacy 退役

### 13.1 开发阶段

- Legacy 始终是正式入口；V2 问题通过停止 V2 容器或移除 /v2 代理隔离。
- 不需要数据回滚，因为本主线不修改数据结构。
- 阶段未通过时，其路由继续由 Legacy 承担，不伪造已迁移状态。

### 13.2 整站切换后

1. 保留最后 Legacy 镜像 tag、Nginx/Compose 配置和 backend tag。
2. 健康、登录、权限、核心路由、API 或 SSE 失败时恢复 Legacy 映射。
3. 前端回滚不执行 Flyway clean，不还原业务数据。

### 13.3 Legacy 物理删除门

只有以下条件全部满足并获得单独授权，才能删除 Legacy：

- M8 正式验收通过。
- 完成一次受控整站切换和一次回滚演练。
- 无 Legacy-only 路由、组件或业务操作。
- 深链、书签、报表链接和外部入口已验证。
- 回滚观察期和删除窗口已明确确认。

## 14. 阶段裁决与变更控制

每阶段开始前确认：

- 当前分支和工作区状态。
- 上阶段结论为通过。
- Stitch 设计、功能契约、权限矩阵和验收数据已准备。
- 同期 backend、DB、runtime 版本和重叠 diff 已标记。
- 阶段不需要未授权生产、Git 或数据操作。

以下变化必须停止当前 UI 实施并另立决策：

- 新增或修改后端 API、数据库、权限码、金额或库存口径。
- 删除或改名现有 URL/route name。
- 引入新 UI 平台、状态同步平台或微前端框架。
- 需要迁移或重置第52条主线数据库。
- 需要生产切换、commit、push、PR 或合并。

通过标准：

- 安全、权限、租户、项目范围、金额、库存、数据一致性或回滚问题未解决时不通过。
- 纯视觉偏好且无可复现影响时有依据关闭，不制造 backlog。
- 超出当前域且有证据、价值和验收标准时，去重后写入唯一治理载体。
- 所有发现已修复、正式承接或有依据关闭后才能通过。

## 15. 工作量和顺序估算

以当前 65 个独立路由视图为基线，本主线为 XL 级重构。下表是单一实施主线的粗估，不是日期承诺；M0 后必须更新。

| 阶段 | 规模 | 粗估工作日 | 主要不确定性 |
| --- | --- | ---: | --- |
| M0 | M | 3—5 | 路由台账、独立工程、Compose |
| M1 | L | 7—12 | 设计系统、认证、导航、响应式 |
| M2 | L | 6—10 | 多角色驾驶舱与工作台 |
| M3 | L | 8—12 | 项目对象、现场、质量和技术 |
| M4 | XL | 12—18 | 合同、成本、预算、利润与金额验收 |
| M5 | L | 9—14 | 采购和库存正反向闭环 |
| M6 | XL | 12—18 | 分包、结算、资金、凭证与审批 |
| M7 | L | 9—14 | 主数据、系统权限、模板与审计 |
| M8 | L | 6—10 | 全量对等、切换和回滚演练 |

不建议并行重写具有直接数据链的业务域，例如采购库存和付款结算。只有设计系统、独立静态页和证据整理可在无 diff 冲突时有限并行。

## 16. 交付物

1. frontend-admin-v2 独立应用与 lockfile。
2. packages/frontend-contracts 无 UI 契约包。
3. V2 设计令牌、组件、业务模式和开发规则。
4. V2 八域、全局工具、登录和错误页。
5. V2 Dockerfile、Nginx、dev/prod Compose service 和 healthcheck。
6. 迁移台账、路由/权限/API 对等矩阵。
7. 单元、集成、E2E、可访问性、视觉 QA 和包体门禁。
8. 各阶段质量证据和 M8 正式验收报告。
9. 切换、回滚和 Legacy 退役清单。
10. 项目地图、迭代决策和 Current Focus 回写。

## 17. 最终完成定义

第53条主线仅在以下条件全部满足时完成：

1. frontend-admin-v2 可独立安装、测试、构建和生成镜像。
2. V2 对 Legacy Vue/CSS/store/layout 的直接或间接 import 为零。
3. 全部可达路由和隐藏上下文路由均有 V2 处置结果。
4. 第50条八域、工作区、Tab、对象上下文和账户区完整可用。
5. 新版驾驶舱保留既定视觉方向、真实数据和多角色范围。
6. URL、route name、query/hash、permission、adminOnly、登录、CSRF、刷新和 SSE 对等。
7. 租户、项目范围、金额、库存、审批和会计风险证据通过。
8. 全量单元、集成、E2E、类型、Lint、构建、包体、可访问性和浏览器 QA 通过。
9. dev/test/demo 整站切换和回滚演练通过。
10. 正式验收报告为“通过”，所有发现已修复、承接或关闭。

生产裁决：本计划不授权生产发布。即使 M8 通过，生产切换仍需单独授权、发布窗口、镜像/配置锁定、备份、回滚演练和人工批准。

## 18. 收口要求

- 每阶段更新计划状态、迁移台账和实际验收数量。
- 记录正式交付物、命令结果摘要、视觉 QA、Git 状态、阻塞和剩余风险。
- 最终回报列出新增后续项、关闭后续项和后续项净变化；存在悬空项时不得判定通过。
- 临时截图、日志、构建产物、Playwright 报告、容器状态和 Secret 不进入版本管理。
- 未获用户明确授权时，不提交、不 push、不合并、不生产发布、不删除 Legacy。
