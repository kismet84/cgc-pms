# Current Focus

## 2026-07-16 增量：间接费规则受控新建入口已选定

- `ISSUE-040-041` 从投标域切换到间接费规则域，先补新建入口；修改和删除继续保持独立叶子，避免合并权限与回滚边界。
- 既有后端直接接收规则实体，开放前必须收敛为白名单 DTO，并验证当前租户、科目 ENABLE 与 OVERHEAD 类别；前端仅复用现有规则弹窗和科目列表。
- 浏览器只打开新建表单后取消，不提交 POST；`A-01-OVERHEAD-CREATE` 在正式收口前继续保留为唯一 OPEN 载体。

## 2026-07-16 增量：投标成本标记未中标入口完成

- `ISSUE-040-040` 已在既有投标成本桌面和移动 BIDDING 行增加“标记未中标”，仅 `bid:status` 或管理员可见；二次确认同时展示投标名称与 BID_COST 费用核销后果，成功刷新，失败保留服务端状态。
- 前端精确调用 `PUT /bid-cost/{id}/lost` 且不发送 params、body 或 tenantId；后端生产逻辑与 V154 不变，专项证明未登录、无权限、仅编辑权限、显式状态权限、管理员、租户隐藏、非法状态、重复操作及费用目标隔离。
- 后端39项、前端14项、类型检查、目标 ESLint、Ready lint和差异检查通过；当前 worktree Vite 超过180秒稳定，浏览器打开确认后取消，目标仍为“投标中”、未中标 PUT 计数保持0、控制台 error/warn=0。
- `A-01-BID-LOST` 已从唯一载体移除；A-01 守恒为有用户入口245、前端调用但无独立页面58、内部/集成/运维4、需补入口3、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1；下一任务切换间接费规则域。

## 2026-07-16 增量：AutoPilot 收口区块边界阻塞已解除

- `ISSUE-047-001` 将 closeout 幂等判断收敛为目标 Issue 标题和下一 Issue 标题之间的有界区块；目标仍为 Ready 时，后续其他条目的 Done 不再被误读。
- 临时 Git 仓库夹具新增“目标 Ready 后紧邻历史 Done”场景，旧实现会跳过存量关闭门禁，修复后继续执行双提交；原有真正 Done 目标重试幂等、分支、基线、评分和 fast-forward 回归保持通过。
- closeout 自测与完整控制面自测通过，独立风险复核 `PASS`、findings=无；`AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY` 已从唯一台账移除。新增后续项0、关闭后续项1、净变化-1，剩余迭代可继续。

## 2026-07-16 增量：AutoPilot 收口区块边界阻塞已立项

- `ISSUE-040-039` 双提交前暴露 `Complete-AutopilotIssueCloseout` 幂等检查未限制 Ready 区块：目标仍为 Ready，但后续任一条目为 Done 时会错误返回“已合并”，跳过实现/收口提交；主线程使用等价原子步骤完成当前 Issue，未造成交付丢失。
- 唯一问题 `AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY` 已作为 P0 运行治理阻塞正式承接，`ISSUE-047-001` 只修复目标区块匹配并增加“Ready 后紧邻 Done”回归，不扩展状态机、评分或合并协议。
- 解除条件：目标 Ready 后存在 Done 条目时仍执行双提交，真正 Done 目标仍幂等；closeout 自测和控制面回归通过。该项直接阻塞剩余19个任务，优先于新能力任务处理。

## 2026-07-16 增量：投标成本标记中标入口完成

- `ISSUE-040-039` 已为 BIDDING 投标记录增加桌面和移动“标记中标”入口，仅 `bid:status` 或管理员可见；候选项目复用既有项目列表，二次确认同时展示投标名称和目标项目名称，成功刷新、失败保留上下文。
- V154 MySQL/H2 仅注册独立 `bid:status` 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER；Service 在任何投标状态、projectId、费用来源类型和成本汇总写入前复用 `ProjectAccessChecker`，同租户无项目范围 fail-close。
- 后端 Controller/Service 共38项、前端专项12项、类型检查、目标 ESLint、Prettier、Ready lint、范围与差异检查通过。原生 Vite 稳定观察超过180秒，浏览器选中候选项目、核对双名称确认并取消，后端中标 PUT 计数保持0，控制台 error/warn=0。独立风险复核结论 `PASS`、findings=无。
- `A-01-BID-WON` 已从唯一存量问题载体移除；A-01 守恒为有用户入口244、前端调用但无独立页面58、内部/集成/运维4、需补入口4、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1；下一叶子为 `A-01-BID-LOST`，产品候选排序不变。

## 2026-07-15 增量：投标成本受控删除入口完成

- `ISSUE-040-038` 已为 BIDDING 投标记录增加桌面和移动受控删除入口，仅 `bid:delete` 或管理员可见；二次确认包含目标名称和状态边界，成功重新读取列表，失败保留确认态并显示服务端错误。
- V153 MySQL/H2 仅注册独立 `bid:delete` 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER；后端生产逻辑保持当前租户隐藏和仅 BIDDING 可删，专项25项覆盖401/403、显式权限、管理员、跨租户、WON/LOST及迁移隔离。
- 前端专项10项、类型检查、目标 ESLint、SQL 安全、Ready lint和差异检查通过。Docker 5173 转发故障归类为环境前置，切换同工作区原生 Vite 后稳定205秒；浏览器确认目标文案并取消，后端 DELETE 计数前后均为0，当前会话新增 error/warn=0。
- `A-01-BID-DELETE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口243、前端调用但无独立页面58、内部/集成/运维4、需补入口5、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：投标成本受控修改入口完成

- `ISSUE-040-037` 已在投标中记录的桌面和移动操作区增加 `bid:edit` 或管理员可见的受控编辑入口；请求仅提交 trim 后的项目名称和可选备注，成功刷新，失败保留表单。
- 后端使用专用 DTO 收敛白名单，专项22项覆盖401/403、合法修改、字段校验、租户隐藏、非 BIDDING 拒绝及 ID、tenantId、projectId、bidStatus、金额不可覆盖；审查中将状态迁移权限解耦为 `bid:status`，明确证明 `bid:edit` 不能中标或失标。
- 前端专项8项、类型检查、目标 ESLint、Ready lint、SQL 静态和差异检查通过；三项 health gate 通过，浏览器回填既有名称并取消，后端写请求计数前后均为0，控制台新增 error/warn=0。
- `A-01-BID-UPDATE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口242、前端调用但无独立页面58、内部/集成/运维4、需补入口6、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：项目受控归档入口与项目数据范围完成

- `ISSUE-040-036` 已在桌面项目列表既有操作菜单增加 `project:edit` 或管理员可见、仅非 ARCHIVED 行展示的受控归档动作；请求精确调用 `PUT /projects/{id}/archive`，不发送 params、body 或 tenantId。
- 后端归档在活动合同、付款、结算、流程查询和状态更新前复用 `ProjectAccessChecker`；专项22项覆盖401、无权限403、同租户无项目范围403、跨租户隐藏、重复归档及四类活动依赖门禁。
- 前端专项11项、类型检查、目标 ESLint、Ready lint 与差异检查通过。前端刷新并稳定观察180秒后，浏览器打开归档确认并取消，确认文案含目标项目和四类前置；后端归档 PUT 计数前后均为0，控制台 error/warn=0。
- `A-01-PROJECT-ARCHIVE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口241、前端调用但无独立页面58、内部/集成/运维4、需补入口7、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：间接费规则只读列表入口与租户边界完成

- `ISSUE-040-035` 已在既有成本台账页增加 `overhead:query` 或管理员可见的规则只读弹窗，精确调用 `GET /overhead-allocation/rules`，仅发送 pageNo/pageSize，不携带 tenantId、body 或写请求。
- 前端请求序号阻止较早分页响应覆盖新结果，加载与失败均清空旧记录及总数；后端专项与租户边界共21项、前端专项13项、类型检查、目标 ESLint、Ready lint 和差异检查通过。
- 前端运行态刷新并稳定观察180秒后三项 health gate 通过；内置浏览器确认入口唯一、弹窗只读、空态正常、控制台 error/warn=0。E 审查结论 `PASS`、findings=无。
- `A-01-OVERHEAD-LIST` 已从唯一存量问题载体移除；A-01 守恒为有用户入口240、前端调用但无独立页面58、内部/集成/运维4、需补入口8、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：投标成本详情只读入口与租户边界完成

- `ISSUE-040-033` 已在既有 `/bid-cost` 页面增加桌面与移动只读详情入口，复用 `GET /bid-cost/{id}` 展示项目名称、状态、关联项目、备注及时间；请求不携带 tenantId、params 或 body，未增加任何写操作。
- 后端专项17/17覆盖详情未登录401、无 `bid:query` 403、持权限成功、跨租户与不存在记录统一 `BID_COST_NOT_FOUND`；前端专项6/6、类型检查、目标 ESLint、Ready lint 与差异检查通过。
- 三项 health gate 均为200；内置浏览器在桌面与390×844移动视口只读打开现有记录，字段完整、控制台 error/warn=0。E 审查发现并本轮修复快速切换/关闭时旧请求回填竞态，最终绑定差异 `5fae590e1a8acd26c2deba50fa64f98fce361722ef6c945ef95d6f34f6b2912b`，结论 `PASS`、findings=无。
- `A-01-BID-DETAIL` 已从唯一存量问题载体移除；A-01 守恒为有用户入口238、前端调用但无独立页面58、内部/集成/运维4、需补入口10、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：投标成本受控新建入口与租户状态边界完成

- `ISSUE-040-032` 已在既有 `/bid-cost` 页面增加受 `bid:add` 或管理员控制的新建弹窗；请求 DTO 仅接受去空格后的投标项目名称与可选备注，Service 强制当前租户、`BIDDING`、空 projectId，并清空客户端 ID。
- MySQL/H2 V151 仅在 V150 投标菜单下注册 `bid:add` BUTTON，并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER；未授予编辑或删除权限，也未创建 `cost_item` 或任何金额事实。
- Ready lint、后端26项、前端4项、类型检查、目标 ESLint、SQL 安全和差异检查均通过；本地 backend/frontend 经180秒稳定观察后三项 health gate 通过。浏览器确认表单只含名称与备注，取消关闭成功，控制台 error/warn=0，后端浏览器阶段 POST 次数为0。
- 首次后端测试因测试进程 JWT 密钥前置不足失败，分类为环境前置；改用正确的 `TEST_JWT_SECRET` 后稳定通过。主线程对高风险写入、权限、租户、状态、项目关联与金额边界复核，绑定差异 `a33ef02100b69d06b547b1fe7aabd2ca624cc8bb0740c52ba0013dc7c02e3327`，结论 `PASS`、findings=无。
- `A-01-BID-CREATE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口237、前端调用但无独立页面58、内部/集成/运维4、需补入口11、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：投标成本只读列表入口与租户边界完成

- `ISSUE-040-031` 已新增 `/bid-cost` 只读列表、类型化 GET API、项目经营侧栏与 `bid:query` 路由门禁；页面只提供关键字、状态、刷新和分页，不携带 tenantId，不暴露新建、编辑、删除、中标或失标动作。
- MySQL/H2 V150 同步注册只读菜单，并仅为现有 SUPER_ADMIN、ADMIN、COST_MANAGER 角色绑定 `bid:query`；后端专项24/24覆盖401、无权限403、持权限200、跨租户隐藏及迁移绑定，前端专项38/38、类型检查、目标 ESLint、SQL 安全、Ready lint 与差异检查均通过。
- 初始 JWT 密钥不足与 H2 迁移范围遗漏分别归类为环境前置和 `ready_issue_config`，补测试密钥、双数据库迁移后稳定通过；浏览器发现分页 total 字符串告警后本轮归一化并复验，180秒稳定观察后三项 health gate 200，`/bid-cost` 控制台 error/warn=0。主线程对绑定差异 `24559c75feb182aa449857c8cfed53227f550762e46de62b164a3428ab7207b8` 复核结论 `PASS`、findings=无。
- `A-01-BID-LIST` 已从唯一存量问题载体移除；A-01 守恒为有用户入口236、前端调用但无独立页面58、内部/集成/运维4、需补入口12、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：成本汇总历史只读入口与项目数据边界完成

- `ISSUE-040-030` 已在既有 `/cost/summary` 成本核对页增加选定项目后可用的“历史快照”只读弹窗；前端精确调用 `GET /cost-summary/{projectId}/history`，不发送 tenantId、请求体或写参数，并使用独立行级历史类型展示日期、科目和关键金额。
- Ready lint、前端9项、后端38项、类型检查、Prettier、目标 ESLint、差异检查、超过180秒运行态稳定观察与真实浏览器闭环均通过；首次后端红灯归类为测试环境缺少合格 `TEST_JWT_SECRET`，补齐当前测试进程前置后接口断言全部通过。
- 主线程对绑定差异 `520b86c605b142ab6284b53a84ce0e381e08c9600a34418247a53fe7a95d4049` 完成高风险金额只读与项目数据范围复核，结论 `PASS`、findings=无；浏览器历史弹窗显示3组既有快照，关闭重开成功，控制台仅2条 debug、error/warn=0，未触发重算。
- `A-01-COST-HISTORY` 已从唯一存量问题载体移除；A-01 守恒为有用户入口235、前端调用但无独立页面58、内部/集成/运维4、需补入口13、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1，产品候选排序不变。

## 2026-07-15 增量：共享列表中窄视口表格高度链修复完成

- `ISSUE-040-029` 已在共享 ≤1200px 响应式规则中为三种既有表格面板嵌套建立 `clamp(420px, 58vh, 640px)` 有界最小高度；未修改角色页、业务组件、接口、权限或数据路径，1201px 以上桌面工作区规则保持不变。
- Ready lint、共享 CSS 专项3项、类型检查、Prettier、目标 ESLint、差异检查和180秒运行态稳定观察通过；真实浏览器在 CSS 宽度1036/1200px 测得表格高度约280px、首行及操作按钮可见可交互，1201px 恢复 flex 桌面布局且表格高度约480px、分页可见，控制台无 error/warn。
- 主线程对绑定差异 `c9a0e3aa46a36c11fbacad590fa0303740f7e842260feb42dab2e473c0a91368` 完成共享布局结构化复核，结论 `PASS`、findings=无；`UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT` 已从唯一存量问题载体移除。
- 新增后续项0、关闭后续项1、后续项净变化-1；未发现本次差异直接引入的跨页面回归或悬空问题，产品候选排序不变。

## 2026-07-15 增量：系统角色修改入口与安全边界完成

- `ISSUE-040-028` 已在既有 admin-only 角色页提供 ADMIN/SUPER_ADMIN 可见的普通自定义角色修改入口；前端回传只读且必须与现值一致的 `roleCode` 以满足现有实体校验，后端拒绝编码变化并只更新 `roleName`、`status`、`dataScope`。
- Ready lint、后端56项、前端45项、类型检查、目标 ESLint、差异检查、180秒稳定观察与真实浏览器均通过；唯一验收角色已完成失败保留、成功回读和精确删除，SUPER_ADMIN 行无修改项，控制台无 error/warn。主线程结构化复核为 `PASS`，findings=无。
- `A-01-ROLE-UPDATE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口234、前端调用但无独立页面58、内部/集成/运维4、需补入口14、待废弃0、需要确认11，共321。
- 新增后续项0、关闭后续项1、后续项净变化-1；既有 `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT` 仍由唯一台账承接，未重复立项，当前产品候选排序不变。

## 2026-07-15 增量：系统角色删除入口与安全边界完成

- `ISSUE-040-027` 已在既有 admin-only 角色页提供 ADMIN/SUPER_ADMIN 可见的危险删除入口；后端保留细粒度授权与租户 fail-close，并新增系统/保留/高等级角色保护、用户绑定前置门禁和事务内角色菜单清理。
- Ready lint、后端48项、前端38项、类型检查、目标 ESLint、差异检查、180秒稳定观察与真实浏览器均通过；唯一验收角色已确认删除且零残留，SUPER_ADMIN 行无删除项，控制台无 error/warn。主线程结构化复核为 `PASS`，findings=无。
- `A-01-ROLE-DELETE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口233、前端调用但无独立页面58、内部/集成/运维4、需补入口15、待废弃0、需要确认11，共321。
- 新增后续项1（`UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT`）、关闭后续项1、后续项净变化0；新项已在唯一台账正式承接，当前产品候选排序不变。

## 2026-07-14 第46条主线：Codex 桌面原生 AutoPilot 执行宿主

- 生产默认配置已切换为 `executionHost=desktop-native`：精确触发连续迭代后，由当前 Codex 桌面主线程读取 durable checkpoint 并直接推进 A-F；PowerShell 仅承担 checkpoint、状态迁移、验证、失败分类、Ready/复核/收口结果校验和 Git 边界等确定性原子动作。
- 旧 Planner、Executor、Reviewer 与 Executor supervisor 在启动模型链前统一执行宿主门禁；桌面宿主只返回结构化 handoff，新增自测证明嵌套模型 CLI 调用数为 0。缺少宿主字段的旧 fixture 继续按 `cli-legacy` 兼容，回退不得静默发生。
- state、Issue checkpoint 与 run lock 支持 `executionHost` 事实；历史 JSON Schema 保持可选字段兼容，读取后迁移。只读 checkpoint 汇总当前 config/state/checkpoint/run-lock/指纹/worktree，不改写恢复现场。
- 自动化实现验收通过；`ISSUE-040-025` 的 `REPAIRING` checkpoint 与 worktree 原样保留，`pause.flag` 阻止下一任务派发。本次未执行真实 `启动迭代-1`，因此新指纹的 N>1/无界放量及 desktop-native 默认宿主最终可用性仍等待用户另行明确金丝雀。

## 2026-07-14 第45条主线：AutoPilot 模型往返证据复用与收口事实源

- Executor、Reviewer 与 Planner 的真实进程启动点写入稳定 invocationId；Issue 与 RUN 分开计数，Planner 只保存候选引用、不向候选 Issue 扇出，token 不可得时明确标记 `not_available`。
- 单 Issue 上下文改为一个不可变 Context Base 加 implement/repair/validate/review 阶段 Delta；Evidence v2 绑定 Ready、上下文、候选取证提交、执行基线、策略、命令、diff 和环境指纹，仅 `UNIT_BUILD` 在全部身份一致时允许有界复用，v1 只读不复用。
- 收口改为 `PreCloseoutFacts → 报告自动事实区 → closeout commit → 冻结 final result → Closeout Record v2 → REGISTERED`；旧 `key + registeredAt` ledger 只读兼容，同 key 异 payload 按 `integrity_conflict` 拒绝。
- M0b 基线证明低风险标准路径已经是 1 次 Executor、0 次 Reviewer，Owner 快速通道以 `NO_MEASURABLE_BENEFIT` 关闭，未新增 route/config/Skill 分支。控制面指纹已变化，N>1/无界放量继续等待用户另行明确执行一次 `启动迭代-1` 金丝雀。

## 2026-07-14 增量：系统角色详情入口完成

- `ISSUE-040-026` 已在既有 admin-only 角色页提供 ADMIN/SUPER_ADMIN 可见的只读详情入口，复用 `GET /system/roles/{id}`，展示角色名称、编码、类型、状态、数据范围、菜单 ID 集合和创建时间，不引入修改、删除或菜单授权写操作。
- 后端专项、前端专项、类型检查、目标 ESLint、Ready lint、差异检查与真实浏览器均通过；独立 Reviewer 首轮要求补齐 SUPER_ADMIN Controller 成功样本，修复并全量复验后对差异哈希 `8417e82ec3d8a4f4db9a982795b311c681baa3cb1243beafec2f196057652524` 给出 `PASS`，findings=无。
- `A-01-ROLE-DETAIL` 已从唯一存量问题载体移除；A-01 守恒为有用户入口232、前端调用但无独立页面58、内部/集成/运维4、需补入口16、待废弃0、需要确认11，共321。
- 新增后续项0、关闭后续项1、后续项净变化-1；当前产品候选排序不变。

## 2026-07-14 增量：系统角色新建入口完成

- `ISSUE-040-025` 已在既有 admin-only 角色页提供 ADMIN/SUPER_ADMIN 可见的新建入口；后端保留 ADMIN/SUPER_ADMIN 或 `system:role:add` 授权，并固定认证租户、CUSTOM、roleLevel=2、ENABLE/SELF 安全默认和初始空菜单集合。
- 自动化、真实浏览器与 localhost dev 数据库读回/精确清理均通过；独立 Reviewer 对绑定实现差异给出 `PASS`，findings=无。首轮证据哈希/阶段边界错误已修复并全量复验，不构成业务失败。
- `A-01-ROLE-CREATE` 已从唯一存量问题载体移除；A-01 守恒为有用户入口231、前端调用但无独立页面58、内部/集成/运维4、需补入口17、待废弃0、需要确认11，共321。
- 新增后续项0、关闭后续项1、后续项净变化-1；当前产品候选排序不变。

## 2026-07-14 增量：间接费执行入口与金额安全边界

- `ISSUE-040-024` 已在既有 `/cost/ledger` 提供受控的月度间接费执行入口：仅 ADMIN/SUPER_ADMIN 或同时具备 `cost:ledger:query` 与 `overhead:execute` 的用户可见，月份统一转换为自然月月末并经过二次确认。
- 后端新增租户+规则+月份执行事实与 V149 MySQL/H2 migration，定时和手工执行复用同一幂等门禁；来源排除既有分摊，三种既有依据保持不变，尾差按分归集，执行事实、成本明细和汇总刷新同事务回滚。
- `A-01-OVERHEAD-EXECUTE` 已关闭；A-01 当前守恒为有用户入口230、前端调用但无独立页面58、内部/集成/运维4、需补入口18、待废弃0、需要确认11，共321。新增后续项0、关闭后续项1、后续项净变化-1；独立 Reviewer 已对高风险权限、租户、金额与 migration 绑定证据给出 `PASS`，当前优先级判断未改变。

## 2026-07-14 第44条主线：AutoPilot 分层补货与同轮续跑

- 存量候选新增权威 ReadySpec 确定性快路径；字段、来源、范围、验证入口或候选证据提交不完整时降级到有界 Planner，不推断补值。Ready Plan v2 对每个候选强制记录 CREATED、REJECTED 或 BLOCKED，零 Ready 合法且不写 backlog。
- Planner 硬超时收敛到配置的 300 秒并维护独立心跳；补货创建 Ready 后通过 RUN 级 transition writer 写后读回，再在同一 run 重新检查 stop/pause、回顾与迭代上限后选单。
- StageResult v2 区分 RUN/ISSUE，并兼容读取 v1；`candidateEvidenceHead` 与 `executionBaseCommit` 分离绑定，策略版本/哈希/引用进入指纹、RuntimeContext、context pack 和 StageResult。
- 主线负责人 Skill 已收窄为明确主线/Backlog/AutoPilot/正式裁决/跨模块收口场景；动态评分、阈值、模型和超时改从配置、Schema 与批准来源读取，AutoPilot 专项行为统一引用插件策略契约。
- 自动化验收通过，未启动真实 Ready、未提交、未 push。控制面指纹已变化，N>1/无界放量仍阻塞于用户另行明确启动并成功收口一次单 Issue 金丝雀。

## 2026-07-14 第43条主线：AutoPilot 控制面模块化与状态机瘦身

- 控制面入口已瘦身为参数、PowerShell 7 门禁和协调器调用；运行上下文、run 协调、Issue 生命周期、Executor 监管、阶段结果与 transition writer 已分离，外部命令行与第42-1安全语义保持兼容。
- 阶段退出统一为可校验 `StageResult`；活动 Issue checkpoint 阶段变化只经 transition writer，合法边与 `transitionId + generation` 必须写后读回。底层 state/checkpoint 继续只承担模型和原子存储。
- 连续 Runner 兼容矩阵保留，并新增执行模式、锁/fencing、恢复、semantic stall、Reviewer、closeout 等独立主题入口；普通临时 Git 仓库固定本地换行策略，CRLF warning 仅由原生命令专项场景制造。
- readiness已按模块化布局组合扫描Runner能力和测试覆盖，启动前复验为15项通过、1项Ready为空警告、0项失败；Ready为空仍按知识图谱优先补货，不视为业务代码失败。
- 本主线不自动运行真实 Ready。自动化验收通过后，控制面新指纹仍需用户另行明确启动一次 `启动迭代-1` 金丝雀；金丝雀未完成前，N>1/无界放量继续阻塞。

## 2026-07-14 第42-1条主线：AutoPilot 控制面一致性修复

- 控制面实现与自动化回归已收敛到 PowerShell 7 `pwsh`；缺失时按 `tool_config/AUTOPILOT_POWERSHELL7_REQUIRED` 安全停止，不再回退 Windows PowerShell 5.1。原生命令按退出码裁决，stderr warning 只保留诊断。
- DRY_RUN/EXPLAIN/APPLY 使用单一不可变模式对象；APPLY 先原子获取或接管带 `runInstanceId + leaseEpoch` 的锁，再重读恢复现场。state、Issue checkpoint、child result 与 Git 收口均受 fencing token 和控制面指纹约束。
- stall 只采信工作区、checkpoint、result 和 evidence 的语义变化；CPU、PID、心跳或无产物 MCP 活动不再续命。同一 `failureFingerprint + phase + diffHash` 自动恢复最多一次。
- 自动化实现验收通过；本轮未运行真实 AutoPilot，也未派发业务 Ready。控制面指纹变化后的 N>1/无界放量仍须用户另行明确启动一次 `启动迭代-1`，并满足 implementationCommit、closeoutCommit、ledger、state、知识图谱 Git cursor 与金丝雀指纹全部读回一致；未完成前保持放量阻塞。
- 原 2026-07-12 “本机未安装 PowerShell 7、5.1 为当前控制面”的记录仅保留历史语义，已被本节取代。

## 当前版本

- 分支：`develop/1.5`
- 基线：`v1.0.0`
- 阶段：v1.5 产品情报首轮闭环与下一主线准入
- v1.0 backlog：[只读快照](../archive/v1.0/backlog-snapshot/)

## 当前执行边界

- 第37条主线已建立项目地图、竞品情报和首轮迭代决策闭环。
- `ISSUE-037-001`、`ISSUE-037-002` 已完成并通过验收；当前 Ready 队列进入补货。
- 下一项实施任务必须先进入 [Ready 队列](ready-issues.md)。
- 补货顺序固定为：已有合格 Ready → [当前问题唯一快照](current-issues.json) 中可执行存量 → 当前 focus 可解除阻塞 → [Ad-hoc 计划](ad-hoc-plan.md) 中已过决策门候选 → 产品情报刷新；[长期增强计划](cgc-pms-production-enhancement-plan.md) 只提供研究输入，不得直接拆 Ready。
- v1.0 的完成记录、测试数量和质量结论不得直接作为 v1.5 验收证据。

## 2026-07-13 AutoPilot 单任务恢复：系统菜单修改入口

- `ISSUE-040-023` 已复用 durable checkpoint 保留既有 B/C diff，并完成 D/E/F：管理员可在既有权限清单页修改单个菜单，后端补齐类型、父节点、环和子节点约束；未重做或扩大 B/C 实现。
- Ready lint、后端专项59/59、前端专项37/37、类型检查与差异检查通过；权限、租户、路径 ID、字段白名单、角色绑定与拒绝路径原子性均有自动化和静态复核证据。
- `A-01-MENU-UPDATE` 已从唯一当前问题台账移除；A-01 当前守恒为有用户入口229、前端调用无独立页面58、内部/集成/运维4、需补入口19、待废弃0、需要确认11，共321。
- 本 Issue 业务验收已通过；控制面仍须在两阶段提交、closeout ledger、state 与知识图谱 Git cursor 全部读回后，才能登记本次单任务金丝雀成功，本文不提前替代该控制面裁决。

## 2026-07-13 第42条主线：AutoPilot 跨 Run 恢复与效率评分

- `AUTOPILOT-CROSS-RUN-PHASE-RECOVERY` 已完成实现与自动化验收：活动 Issue 使用 durable phase checkpoint 绑定 Ready/base/worktree/branch/scope/diff/evidence；有效现场只从 validation、Reviewer 或 closeout 恢复，closeout 已合并后的崩溃窗口也可幂等完成 ledger/state/图谱登记，禁止删除 worktree 后重派 B/C。
- Reviewer `tool_config` 与业务 repair 已隔离；同 diff 只允许一次跨 run Reviewer 重试，累计两次仍失败进入 `PAUSED/REVIEW_TOOL_BLOCKED`；绑定同一 Issue/diff 的人工结构化 PASS 可接管，只有完整 blocking finding 才能进入 repair。
- `AUTOPILOT-END-TO-END-EFFICIENCY-EVIDENCE` 已完成并获用户批准：`autopilot-task-score/v2` 按35/25/20/10/10正式激活，`runResumeCount` 与各阶段 dispatch、人工恢复、工具/环境重试共同决定 `taskExecutionEfficiency=10`；自批准配置提交后的下一项新实施型 Ready 生效，v1 历史不回算且不双计数。
- 控制面行为文件和行为配置形成稳定指纹；指纹变化后 N>1/无界执行必须先由用户明确启动并成功收口一次 `启动迭代-1`，并读回 closeout ledger、state 与知识图谱 Git cursor。当前 `pause.flag` 继续生效，真实金丝雀和下一任务均未派发。

## 2026-07-13 AutoPilot 启动迭代-10 恢复状态

- `ISSUE-040-022` 已从重复 BC 启动中安全恢复到 D/E/F：业务实现专项、类型检查、差异检查、独立 Reviewer 与 Issue worktree 浏览器验收均通过；本批完成 1/10。
- `pause.flag` 保持生效，不再派发下一项 Ready。`OPS-AUTOPILOT-STALL-FINGERPRINT` 已修复并复验：纯子进程 CPU 活动不再刷新进度，既有 inspect/retire、一次有限重派和总超时门禁保持通过；该唯一问题已从当前台账移除。
- A-01 当前守恒为有用户入口228、前端调用无独立页面58、内部/集成/运维4、需补入口20、待废弃0、需要确认11，共321；`A-01-MENU-LIST` 已从当前问题台账移除。

## 2026-07-13 项目知识图谱治理状态

- `ISSUE-040-018` 已把 AutoPilot 补货入口接到结构化当前问题台账：Ready 为空时优先按 P0→P2、Open→Observation、叶子优先拆证据完整的非阻塞存量；`[stock:问题键]` 用于跨 Ready/Done/Blocked 去重，任务收口前原问题必须移出台账或正式重分类。当前单批上限5个时返回5个 A-01 菜单叶子项；`pause.flag` 继续阻止实际派发。
- `ISSUE-040-017` 已关闭当前问题查询性能根因：`docs/backlog/current-issues.json` 成为机器可读当前问题快照，采集器生成57个 Issue、35条 A-01 父子关系和61条完整证据关系；`kg_list_issues` 默认只返回有界摘要，多轮实测摘要低于50ms、57条全量明细低于125ms，不再通过宽泛全文检索重建台账。
- 第39条主线已完成本地知识采集闭环：文档版本、Git 游标、采集运行、失败状态、会话/日志摘要脱敏、MCP 观测和 30 分钟定时对账均已落地。
- 该能力是旁路工程治理工具，不替代仓库、Git、backlog 或正式质量报告，也不得替代产品 Candidate/Ready 决策。
- 非阻塞边界：不扫描 Codex 私有历史、不保存原始大日志、不做 LLM 正式事实抽取、不自动删除历史版本；如需扩展必须重新立项。
- 第40条主线已建立 [v1.0 历史问题四态分类](v1.0-historical-issue-classification.md)：92 个历史 Issue 保留“v1.0 已解决”语义；原 10 个仍适用域经 M2 关闭 A-08 后剩 9 个，6 类旧状态保持已过期，原 8 个需要复验域均已取得当前证据。
- 正式历史 `docs/archive/v1.0/**` 可只读入图并带 `historical=true/versionScope=v1.0`；默认搜索排除历史。`archive/v1.0/private/**` 继续禁止读取、扫描和入图。
- 索引风险已闭环：目录或文档语料外文件的显式链接现在以不读取正文的轻量路径节点建立 `REFERENCES` 边；16 个 `referenceOnly` 节点正文数量为 0，连续两轮采集 `unresolvedReferences=0`。非 Markdown 日志中的 `[值](类型)` 不再误判为链接，私有封存区继续禁止入图。
- 第40条主线持续执行：M0 已关闭 3 条旧阻塞，M1/M2 已关闭 8 个复验域和 A-08；后续按 [存量问题全量修复计划](../plans/第40条主线-v1.0历史问题分类治理任务计划书.md) 处理 9 个仍适用域，不批量越过 Candidate/Ready 决策门。
- `ISSUE-040-001` 已完成 V-01 首个权限债修复：预警普通处理保留 `alert:edit`，租户级批量评估收紧为 `alert:evaluate`；V146 保留菜单/角色关系，22 项专项与 SQL safety 通过。后续三类真实角色与双端权限复验已在 ISSUE-040-002 中关闭 V-01 M1。
- `ISSUE-040-002～006` 的五个 M1 风险域均已通过：V-02 53 项、V-03 112 项；V-04 三类真实角色浏览器正负样本；V-06 修复 Windows 备份恢复脚本、隔离恢复 74 表并完成本机 MySQL/Redis/MinIO/JWT/Jasypt 真实轮换。
- M1 已整体通过。A-01 的入口建设台账进入 M2；未来生产发布必须在实际目标环境重新执行凭据轮换和双人复核，但当前无可识别生产环境，不再作为本地历史债阻塞 M1。
- M2 两批已完成：A-08、V-05、V-07、V-08 通过；A-01 已接入预警处理报告、会计凭证列表/详情/过账/冲销及系统菜单新建/删除/详情/平铺列表入口，重分类发票核验与凭证生成接口，当前剩余20个需补入口、11个需要确认。生产存量文件复扫属于 A-08 发布前置，不得直接批量标记 `CLEAN`。

## 2026-07-13 全量审计闭环状态

- `docs/quality/code-audit-2026-07-12-full-develop-1.5.md` 的 7 项原阻塞已完成本地根因闭环；后端 1723 tests、前端 505 tests、MySQL 8 Flyway、SQL safety、构建后 JAR Trivy、UI smoke 与内置浏览器验收均通过。
- `master` 分支保护已启用管理员强制与 required conversation resolution，11 个 required checks 和 strict 语义保持不变。
- PR #334 的 11 个 required checks 曾全绿，并于 2026-07-13 合并为 `master` 提交 `76ec42a0`；该结论只代表当时目标提交，不再作为当前 `master` 绿灯证据。
- 2026-07-16 复核显示当前 `master` 提交 `ed47aabb` 的 CI run #186 在 `backend-test` 失败，`supply-chain-security` 被跳过；`ISSUE-037-021` 已在 [blocked-issues.md](blocked-issues.md) 以同一根因键复发承接。失败已分类为 `quality_or_security`，根因为 `StlSettlementServiceTest` 污染共享 V105 数据并造成 Dashboard 顺序依赖。当前功能分支已完成原值恢复式隔离修复，目标类 13 项、显式失败顺序 26 项及后端全量 1814 项均通过；用户要求暂不提交、不推送，因此上线门禁仍只阻塞于修复提交的远端 11 项 required checks 与 `supply-chain-security` 证据。
- 已完成依赖与格式治理：413 条纯 Prettier 告警已降为 0，ECharts 6.1.0 / vue-echarts 8.0.1 升级后 `pnpm audit --audit-level moderate` 为 0 漏洞；格式化后全量前端与 E2E 通过。
- Trivy Java DB 已在 `supply-chain-security` 增加按 UTC 日期缓存与历史缓存回退；国内网络风险收敛为首次冷缓存下载时延，不采用未经项目验证的第三方镜像。
- GitHub Actions Node.js 20 弃用注解已关闭：官方 actions 已升级到声明 Node 24 的主版本，`pnpm/action-setup@v6` 改为读取 `frontend-admin/package.json` 的精确 `packageManager`；全量 CI 后 Node 20 annotation 为 0。
- 本机临时项：损坏 ACL 的旧 `node_modules` 已隔离并尝试清理，残留文件仍受原 ACL 保护；用户 Chrome 与 Trivy DB 缓存位于 `.codex-autopilot/runs/`，均不进入版本管理或运行构建。

## 当前方向决策

- 决策周期：`PI-2026-07-11-01`。
- 已完成方向：采购低库存补货建议最小闭环。
- 当前状态：`Done`，真实采购经理角色已完成“低库存库存项 → 采购申请预填 → 保存 → 测试数据清理”的本地闭环。
- 决策依据：[项目地图](../product-intelligence/project-map.md)、[竞品情报](../product-intelligence/competitor-analysis.md)、[迭代决策](../product-intelligence/evolution-decision.md)。
- 补货结论：`PI-2026-07-11-02` 已确认现有订单与已审批验收数据足以支撑“仅交付维度”的供应商档案，原阻塞已解除。
- 已完成：`ISSUE-037-002` 供应商交付档案最小闭环，真实采购经理角色已看到迟交完成与逾期未完成两类交付状态。
- 已完成：`ISSUE-037-003` 驾驶舱项目数据范围统一收口，项目经理与管理驾驶舱的项目型聚合已按可见 ACTIVE projectIds fail-close。
- 补货结论：分包 WBS 已有任务、计划/实际日期、只读时间轴和延期提示，单前置 FS 能在现有模型内形成更小且可验证的产品闭环；现场日报仍需新增日报事实模型与权限/审计边界，当前先保持 Candidate。
- 已完成：`ISSUE-037-004` 分包 WBS 单前置 FS 依赖与延期风险；只完成可空同表前置引用和最小风险展示，不扩展自动排程或独立计划平台。
- 已完成：`ISSUE-037-005` 现场日报最小闭环；仅覆盖项目/日期、施工内容、问题/延误、次日计划、草稿提交和附件，不扩展现场管理大全。
- 已完成：`ISSUE-037-006` 库存项安全库存阈值与补货建议联动；真实采购经理已完成阈值更新，KPI 动态变化和测试数据恢复通过。
- 已完成：`ISSUE-037-007` 现场日报人工天气摘要与可空在场人数；NULL/0、严格整数、提交后不可变和真实 API 清理闭环通过。
- 已完成：`ISSUE-037-008` 为 dev/local 的站内 redirect 白名单补充 `/site`；现场日报直达 302/200 与站外回落均通过。
- 已完成：`ISSUE-037-009` 人工补货目标量；真实采购经理已完成目标设置、台账回读、非法关系拒绝和 NULL 恢复，KPI 触发口径保持不变。
- 已完成：`ISSUE-037-010` 人工补货提前期与采购计划日期预填；V145、严格整数/日期、旧客户端省略字段兼容与真实角色恢复闭环通过。
- 已完成：`ISSUE-037-011` 现场日报已审批材料到货只读联动；复用验收事实，按租户/项目/日期/APPROVED 批量聚合，不新增重复材料表。
- 已完成：`ISSUE-037-012` 现场日报当日计划任务只读联动；按租户/项目和计划日期闭区间展示最小任务字段，不新增排程副本或写入口。
- 已完成：`ISSUE-037-013` 现场日报变更历史只读展示；CREATE 审计绑定日报 ID，详情按租户/业务键展示最小非敏感记录。
- 已完成：`ISSUE-037-014` 现场日报当日已审批领料只读联动；仅聚合同租户/项目/日期、已审批且真实出库明细，不披露金额或宣称已安装。
- 已完成：`ISSUE-037-015` WBS 单前置 FS 开工门禁；统一 Service 拒绝未完成前置下的开工/完成状态，前端只做同步禁用与提示。
- 历史状态：此前一轮 `启动迭代-10` 已达到 10/10；不代表当前 2026-07-12 新启动轮次的计数。
- 候选取舍：日报设备维度缺少下游消费，WBS 多前置需关系表与多源环检测；人工补货提前期能以最小迁移和前端预填形成真实时间闭环。
- 域切换理由：上一条为路由治理，本轮回到采购库存产品能力；目标量是 Odoo 最小/最大库存事实中尚未覆盖的最小下一步，但不等同完整最大库存策略。
- 候选比较：两字段直接复用 V141 状态与权限，价值和证据明确，实施风险低于人员名单/班组、自动天气、设备材料子表、移动离线或完整计划平台。
- 候选比较：安全库存阈值直接复用现有库存与采购申请链，范围和验证成本低于供货周期/预测、现场结构化子域或完整计划平台，故本轮优先；不得借机扩成全量补货工作台。
- 非阻塞观察：`DashboardPerformanceTest` 实际断言门槛为 `<=20`，显示名/输出仍写 `<=10`，后续测试治理时统一文案。
- 非阻塞观察：阈值更新复用 `MatStock.@Version` 并已具备冲突返回；后续可补阈值更新与出入库的真实并发专项，不阻塞本轮。
- 非阻塞观察：补货设置新接口可后续补独立的只读权限拒绝、跨项目和模拟乐观锁冲突专项；当前共用授权路径、既有锁测试及真实采购经理闭环已足够支撑本轮通过。
- 非阻塞观察：人工提前期只按本地自然日预填采购申请计划日期，不含供应商、工作日历或预计到货承诺；这些能力如需继续必须重新过产品决策门。
- 后续候选：待本轮产品情报刷新后裁决；工程治理 Candidate 默认不得替代产品方向。只有当前证据证明其直接阻塞已选产品目标、安全边界或正式验收时，才可按 `缺口修复` 或 `运维治理` 进入 Ready，并写明关联目标、阻塞证据、解除条件、非目标和回滚方式。
- 当前 `启动迭代-3` 已完成 3/3 并停止下一任务派发；Ready 为空，未来新启动需重新刷新产品情报。
- `启动迭代-5` 在 3/5 的停止保持为历史事实；系统复核已撤销“无合格 Candidate”的当前效力，并按 `PI-2026-07-12-13` 补入 `ISSUE-037-017`。
- `ISSUE-037-017` 已完成：共享 JSON 契约允许写入 `remark`，其余 ID、租户、审计与逻辑删除字段继续只读；下一步进入产品情报补货，不扩成 DTO 重构或全接口治理。
- `ISSUE-037-018` 已完成：300/600 秒 inspect/retire、一次新执行单元 repair、第二次 stall blocked 与有界长命令声明已形成闭环；本次 `启动迭代-10` 当前完成 2/10，下一步继续产品情报补货。
- 非阻塞观察：`ISSUE-037-014` 未做真实浏览器视觉验收；代码级只读边界与自动化验收已通过。
- 非阻塞观察：`ISSUE-037-015` 的请求拦截器与页面 catch 可能重复展示错误，前端测试仍为源码契约断言；后端统一 Service 门禁已通过。
- 非阻塞观察：`ISSUE-037-016` 未做事务故障注入；若未来开放任务编号编辑，应保留 `DELETED-` 命名空间或增加格式校验。
- `ISSUE-037-019` BC 静态盘点已覆盖 53 个 Controller、321 个唯一接口：有用户入口 219、前端调用但无独立页面 57、内部/集成/运维 4、需补用户入口 30、待废弃 0、需要确认 11。
- 独立 Reviewer 已全量复核 30 个需补入口、11 个需要确认，并抽查 24 个存在用户入口的业务域；分类、权限注解和后端证据通过。
- A-01 当前增量状态：`ISSUE-040-007`、`ISSUE-040-012～016` 和 `ISSUE-040-019～022` 共处理11个子项；有用户入口228、前端调用无独立页面58、内部4、需补入口20、需要确认11，总数仍为321。`A-01-MENU-CREATE`、`A-01-MENU-DELETE`、`A-01-MENU-DETAIL`、`A-01-MENU-LIST` 已从当前问题台账移除，原静态报告仅保留历史基线语义。
- 后续边界：入口补建、接口废弃或兼容性清理按责任域另拆 Ready；外部客户端调用和动态菜单证据不足项先保持需要确认。
- 本次 `启动迭代-10` 当前完成 5/10；Ready 清空后继续补货。静态盘点不代表真实角色、动态菜单或运行态可见性通过。
- `ISSUE-037-020` 已完成：长期计划补货只接纳第 7–9 章开发计划，2–6 章现状/对标/差距/目标标题不再误入 Candidate；本次当前完成 5/10，继续补货。
- `ISSUE-037-021` 的红灯与 `enforce_admins=false` 仅保留为首次复验历史快照；第40条 M0 已以 PR #334 合并、11 checks 全绿和当前分支保护 API 将 A/B/C 三条阻塞统一裁决为 `VerifiedResolved`。
- 当前 API 未返回 required pull-request review 和 push restrictions；原管理员绕过已关闭，但是否额外要求审批人数或推送主体白名单保持“需要确认”，未获授权前不修改远端。
- CI 工具链观察：GitHub annotation 提示 Node 20 action 被强制运行于 Node 24；当前不是三个红灯的退出根因，升级 Actions 需另行授权。
- `停止迭代` 已生效：本次 `启动迭代-10` 最终完成 4/10 条实施型 Ready；`ISSUE-037-021` 仅作为不通过的回归证明归档，不计入实施数，当前不再派发下一任务。
- 新一轮 `启动迭代-1` 在控制面选单前稳定复现 Windows PowerShell 5.1 ParserError；已按 `tool_config` 分类并建立 `ISSUE-037-022`，仅恢复 AutoPilot 脚本 UTF-8 解析兼容与回归保护，不改变业务或生产边界。
- `ISSUE-037-022` 已完成：含非 ASCII 的 AutoPilot PowerShell 脚本统一具备 UTF-8 BOM，运行期文本读取显式使用 UTF-8；控制面、连续 runner 与真实 Ready 路由复验通过。`启动迭代-1` 已达到 1/1，上限停止下一任务派发。
- 非阻塞观察：本机未安装 PowerShell 7，本轮只证明 Windows PowerShell 5.1 默认 `-File` 路径；未来引入 `pwsh` 时需另做跨版本回归，不阻塞当前 Windows 控制面。
# ISSUE-040-034 工作流个人效率统计入口已通过（2026-07-15）

- 在现有审批工作台分析栏接入 `GET /workflow/statistics/efficiency`，展示待办、逾期待办、已办、已处理任务和平均处理分钟数；请求只携带当前筛选与固定 48 小时阈值，不携带 userId/tenantId。
- 自动化、请求竞态防护、180 秒前端稳定观察、筛选交互、完整标签布局与控制台检查通过；运行态刷新前旧 Vite 模块问题已按环境前置恢复，不属于业务失败。
- `A-01-WORKFLOW-EFFICIENCY` 已从唯一存量问题载体移除；A-01 守恒为有用户入口239、前端调用但无独立页面58、内部/集成/运维4、需补入口9、待废弃0、需要确认11，共321。
- 新增后续项0、关闭后续项1、后续项净变化-1；下一轮可继续选择合格缺口修复任务，产品候选排序不变。
