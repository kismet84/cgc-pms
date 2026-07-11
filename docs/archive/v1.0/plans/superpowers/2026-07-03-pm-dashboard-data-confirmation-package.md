# 项目经理标签内容区数据确认执行包

## 1. 结论先行

- 当前判定：优先按“数据 / 环境确认包”推进，不直接判定为前端 bug。
- 当前结论：非阻塞，但需要确认。
- 依据：
  - 现有 E2E 证据只证明“项目经理”标签可切换，未证明内容区一定有数据。
  - 前端内容渲染明确依赖 `pmData` 存在后才挂载项目经理视图。
  - 后端已存在 `getProjectManagerView` 实现，且已有对应测试覆盖其核心返回结构与月份参数行为。
- 升级条件：
  - 若 `/dashboard/project-manager` 接口已有非空结构而页面仍不显示，再升级为前端渲染问题。

## 2. 当前证据

### 2.1 前端标签可切换

- `frontend-admin/e2e/dashboard.spec.ts`
  - 现有用例会点击“项目经理”标签，并确认激活态切换成功。
  - 同一用例中已显式保留“需要确认：当前‘项目经理’标签可切换，但本环境未返回项目经理内容区数据”的提示。

### 2.1A 权限前提已基本收敛

- `frontend-admin/src/pages/dashboard/composables/useDashboardData.ts`
  - 前端会在以下任一条件满足时放出项目经理角色入口：
    - 角色包含 `ADMIN`
    - 角色包含 `SUPER_ADMIN`
    - 权限包含 `dashboard:project-manager:view`
- `backend/src/main/java/com/cgcpms/dashboard/controller/DashboardController.java`
  - `/dashboard/project-manager` 接口鉴权为：
    - `hasAnyRole('ADMIN','SUPER_ADMIN')`
    - 或 `hasAuthority('dashboard:project-manager:view')`
- 当前可确认子结论：
  - 若执行账号为 `ADMIN` / `SUPER_ADMIN`，则“是否具备入口与接口访问前提”这一子问题可视为已满足，不再是当前主风险。
  - 因此后续确认重点应转向：项目样本、接口真实返回、空数据口径，而不是继续怀疑管理员权限本身。

### 2.2 项目经理视图渲染依赖 `pmData`

- `frontend-admin/src/pages/dashboard/composables/useDashboardData.ts`
  - `activeRole === 'pm'` 时，会调用 `getProjectManagerView(pid, month)`，结果写入 `pmData`。
  - 页面初始化后，如当前角色需要项目且已有项目列表，会自动选择第一个项目并再次拉取数据。
- `frontend-admin/src/pages/dashboard/index.vue`
  - 仅在 `activeRole === 'pm' && pmData` 时才渲染 `DashboardPmView`。
- `frontend-admin/src/pages/dashboard/components/DashboardPmView.vue`
  - 组件是纯展示组件，输入为 `data: ProjectManagerDashboardVO` 和 `loading`，不负责自行补数。

### 2.3 后端接口与测试已存在

- `backend/src/main/java/com/cgcpms/dashboard/service/DashboardService.java`
  - 存在 `getProjectManagerView(Long projectId)` 与 `getProjectManagerView(Long projectId, String month)`。
  - 返回内容包含待办任务、滞后项目、待审批、临期合同等项目经理视图结构。
- `backend/src/test/java/com/cgcpms/dashboard/service/DashboardServiceTest.java`
  - 已有项目经理视图测试，覆盖单项目返回、全部项目返回、月份参数过滤、非法月份不抛 500 等行为。

## 3. 决策建议

- 本包定位：执行包，不是执行结果。
- 当前最小可行路径：先确认权限、项目、接口返回，再决定是否升级为代码缺陷。
- 不建议当前直接进入前端修复：
  - 现有证据不足以证明 `pmData` 已有值但页面渲染失败。
  - 从代码结构看，空白更可能先由权限、选项项目、接口空结构或数据口径未解释导致。

## 4. 执行任务

### 任务 1：确认当前账号权限

- 目标：确认执行账号是否拥有 `dashboard:project-manager:view`。
- 执行方式：
  - 在当前登录态检查用户权限集合，或通过现有鉴权信息 / 用户资料接口确认。
  - 若账号为 `ADMIN` / `SUPER_ADMIN`，按前端逻辑视为具备全部驾驶舱角色访问能力。
- 预期：
  - 有该权限或具备管理员兜底权限，才能继续判定项目经理内容区是否应展示。
- 当前收敛说明：
  - 对管理员账号，这一步原则上已不构成阻塞，除非现场证据显示当前登录态并非管理员。

### 任务 2：确认项目选择器是否选中了真实有数据项目

- 目标：排除“标签切到了项目经理，但当前项目本身无可展示数据”的环境因素。
- 执行方式：
  - 记录当前项目选择器选中的项目 ID / 项目名称。
  - 至少切换 1 个确认存在流程、合同、项目计划数据的真实项目复核一次。
  - 若默认命中首个项目，需要确认该项目不是演示空项目、停用项目或无业务流水项目。
- 预期：
  - 至少有一个被确认的真实项目参与后续接口核验。

### 任务 3：确认 `/dashboard/project-manager` 接口是否返回非空结构

- 目标：确认页面空白是接口无数据，还是前端未渲染。
- 执行方式：
  - 在浏览器网络面板、现有 API 调试方式或后端日志中，记录项目经理接口请求参数：
    - 项目 ID
    - 月份参数
    - 当前账号
  - 记录接口响应是否成功，以及以下字段是否存在：
    - `pendingTaskCount`
    - `laggingProjectCount`
    - `pendingApprovalCount`
    - `expiringContractCount`
    - `pendingTasks`
    - `laggingProjects`
    - `pendingApprovals`
    - `expiringContracts`
- 预期：
  - 至少拿到一次真实响应证据，禁止口头判断“应该有数据”。

### 任务 4：如接口为空，区分“正常空数据”还是“口径缺解释”

- 目标：避免把业务口径问题误报为 bug。
- 判定方法：
  - 正常空数据：
    - 当前账号确实无待办 / 无待审批；
    - 当前项目无临期合同；
    - 当前环境项目计划数据不满足“滞后项目”口径；
    - 选择月份后，月份过滤将相关数据自然过滤为空。
  - 口径缺解释：
    - 页面空白或几乎全空，但产品未说明“项目经理视图允许全空且不展示占位解释”；
    - 用户期望“至少展示零值卡片或空状态”，而当前设计未给出解释。
- 处理建议：
  - 若属正常空数据，补验证记录即可，不直接报缺陷。
  - 若属口径缺解释，登记为产品 / 交互澄清项，不直接定性为后端或前端 bug。

### 任务 5：如接口有数据但页面不显示，再升级为前端渲染问题

- 升级条件：
  - 已确认有权限；
  - 已确认选中真实项目；
  - 已抓到 `/dashboard/project-manager` 成功返回非空结构；
  - 页面仍未渲染 `DashboardPmView` 或核心数据块。
- 升级后建议执行人：
  - 前端工程执行子智能体
- 升级后最小排查方向：
  - `pmData` 是否成功赋值；
  - `activeRole === 'pm' && pmData` 条件是否成立；
  - 接口字段与 `DashboardPmView` 期望字段是否对齐。

## 5. 建议执行角色

- 主执行角色：测试执行助手 / QA 验证执行人
- 协同角色 1：前端工程执行子智能体
  - 触发条件：接口已确认有非空数据，但页面未展示
- 协同角色 2：后端工程执行子智能体
  - 触发条件：接口返回异常、字段缺失、权限口径与后端实现不一致
- 协同角色 3：产品 / 业务负责人
  - 触发条件：接口空结构属正常业务结果，但页面是否应展示空状态缺少明确口径

## 6. 验收标准

- 必须完成以下最小验收项：
  - 已确认当前账号是否拥有 `dashboard:project-manager:view`
  - 已确认项目选择器命中的至少一个真实项目
  - 已留存一次 `/dashboard/project-manager` 的真实返回证据
  - 已明确空结构属于“正常空数据”还是“口径缺解释”
  - 仅在接口有数据但页面不显示时，才把问题升级为前端渲染问题

## 7. 风险点

- 权限风险：账号没有 `dashboard:project-manager:view` 时，继续追页面内容没有意义。
- 数据风险：默认项目可能是空项目，导致误判页面空白。
- 口径风险：项目经理视图允许全空时，如果没有空状态说明，容易被误报为缺陷。
- 环境风险：不同环境的流程任务、合同到期、项目计划基线数据差异很大，不能拿其他环境结论直接套用。
- 归因风险：未抓接口返回前就直接报前端 bug，结论不成立。

## 8. 通过 / 不通过判定口径

### 通过

- 已按本包完成权限、项目、接口、口径四层确认。
- 最终结论能落入以下之一：
  - 正常空数据，非缺陷；
  - 口径缺解释，转产品 / 交互澄清；
  - 接口有数据但页面不显示，升级为前端缺陷；
  - 接口本身返回异常或字段异常，升级为后端 / 数据问题。

### 不通过

- 仅凭页面现象就直接下结论，未留权限或接口证据。
- 未确认项目选择器命中真实项目。
- 未区分“正常空数据”和“口径缺解释”。
- 在接口证据缺失的情况下，直接定性为前端 bug。

## 9. 最小产物建议

- 验证记录
  - 记录执行账号、项目 ID / 名称、月份参数、时间、结论
- 接口结果截图或日志
  - 至少保留一次 `/dashboard/project-manager` 请求与响应摘要
- 判定结论
  - 明确写明更偏向“数据 / 环境确认”“前端渲染问题”“后端 / 数据问题”或“仍需确认”
- 文档回写建议
  - 若确认只是待验证项被关闭，可回写 `docs/未来开发计划.md`
  - 若仍未确认，不回写关闭结论，只补充待验证状态说明
