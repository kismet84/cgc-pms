# CGC-PMS 项目地图

## 2026-07-16 增量：AutoPilot 收口区块有界幂等

- closeout 对目标 Ready 的完成状态判定已限制在目标 Issue 区块内；后续历史 Done 不再导致当前 Ready 被误判为已合并，双提交、报告投影和登记链可继续执行。
- 临时 Git 仓库回归同时证明“目标 Ready 后存在 Done”会进入真实 closeout，以及目标自身已 Done 且提交已合并时仍幂等；分支、基线、脏工作区、存量问题关闭、评分和 fast-forward 门禁未改变。
- `AUTOPILOT-CLOSEOUT-BLOCK-BOUNDARY` 已关闭；该修复只恢复本地迭代控制面确定性，不改变 CGC-PMS 产品能力、业务数据或产品候选排序。

## 2026-07-16 增量：投标成本标记中标入口

- 既有投标成本页已为 BIDDING 记录提供桌面和移动“标记中标”动作；项目候选复用授权后的项目列表，二次确认展示投标名称和目标项目名称，取消不请求、成功重新读取列表、失败不伪改状态。
- 前端精确调用 `PUT /bid-cost/{id}/won` 且只发送 params=`{ projectId }`；V154 双方言只注册独立 `bid:status`，后端在任何投标或费用写入前校验目标项目的租户与用户数据范围，重复请求不会二次结转。
- 自动化、超过180秒稳定运行态与只取消不写入的浏览器闭环通过；`A-01-BID-WON` 已关闭，A-01 当前守恒为有用户入口244、前端调用但无独立页面58、内部/集成/运维4、需补入口4、待废弃0、需要确认11，共321。本切片未扩展失标、撤销中标、批量迁移、项目创建或金额算法，产品候选排序不变。

## 2026-07-15 增量：投标成本受控删除入口

- 既有投标成本页已为 BIDDING 记录提供桌面和移动危险删除动作；二次确认展示目标名称与“仅投标中可删除”，成功后重新读取服务端列表，失败不会在客户端移除行或伪报成功。
- 前端精确调用 `DELETE /bid-cost/{id}` 且不发送 params、body、tenantId 或其他业务字段；V153 双方言只注册 `bid:delete`，后端沿用认证租户隐藏和 BIDDING 状态门禁，WON/LOST 均 fail-close。
- 自动化、超过180秒稳定运行态与只取消不写入的浏览器闭环通过；`A-01-BID-DELETE` 已关闭，A-01 当前守恒为有用户入口243、前端调用但无独立页面58、内部/集成/运维4、需补入口5、待废弃0、需要确认11，共321。本切片未扩展批量/恢复删除、中标、失标、项目关联或金额事实，产品候选排序不变。

## 2026-07-15 增量：投标成本受控修改入口

- 既有投标成本页已为 BIDDING 记录提供桌面和移动编辑动作，仅允许修改项目名称与备注；成功后重新读取服务端列表，失败不关闭表单或伪改数据。
- 前端精确调用 `PUT /bid-cost/{id}` 且不发送租户、项目、状态或金额字段；后端专用 DTO 与按需更新共同形成白名单，V152 只注册 `bid:edit` 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER。
- 审查已将中标/失标端点从 `bid:edit` 解耦为 `bid:status`，避免成本经理获得编辑权限时同时获得状态迁移能力。自动化、三项 health gate 与只取消不写入的浏览器闭环通过；`A-01-BID-UPDATE` 已关闭，A-01 当前守恒为有用户入口242、前端调用但无独立页面58、内部/集成/运维4、需补入口6、待废弃0、需要确认11，共321。本切片未扩展删除、中标、失标、金额或项目转换，产品候选排序不变。

## 2026-07-15 增量：项目受控归档入口

- 桌面项目列表已提供受控归档动作，二次确认明确目标项目及合同、付款、结算、审批前置；成功后重新读取服务端列表，失败不在客户端伪改状态。
- 前端精确调用既有 `PUT /projects/{id}/archive`，入口受 `project:edit` 或管理员身份约束；后端归档补齐统一项目数据范围检查，并保留跨租户隐藏、重复归档和四类活动依赖门禁。
- 自动化、180秒运行态稳定观察与只取消不写入的浏览器闭环通过；`A-01-PROJECT-ARCHIVE` 已关闭，A-01 当前守恒为有用户入口241、前端调用但无独立页面58、内部/集成/运维4、需补入口7、待废弃0、需要确认11，共321。本切片未扩展移动端写操作、批量/撤销归档或物理删除，产品候选排序不变。

## 2026-07-15 增量：间接费规则只读列表入口

- 既有成本台账页已提供间接费规则只读弹窗，展示成本科目 ID、分摊依据、分摊周期与状态，并支持服务端分页；失败和切页不会残留旧数据。
- 前端仅调用既有 `GET /overhead-allocation/rules` 并发送 pageNo/pageSize；入口受 `overhead:query` 或管理员身份约束，后端既有认证租户过滤保持不变，专项覆盖401、403、合法读取与租户边界。
- 自动化、180秒运行态稳定观察与真实浏览器闭环通过；`A-01-OVERHEAD-LIST` 已关闭，A-01 当前守恒为有用户入口240、前端调用但无独立页面58、内部/集成/运维4、需补入口8、待废弃0、需要确认11，共321。本切片未扩展规则新建、修改、删除或执行权限，产品候选排序不变。

## 2026-07-15 增量：投标成本详情只读入口

- 既有 `/bid-cost` 页面已提供桌面和移动只读详情入口，展示投标项目名称、状态、关联项目、备注、创建和更新时间；快速切换或关闭弹窗会使旧请求结果失效，避免跨记录残留。
- 前端仅调用既有 `GET /bid-cost/{id}`，不发送 tenantId、params、body 或写请求；后端既有 `bid:query`、认证租户和 `BID_COST_NOT_FOUND` 隐藏语义保持不变，专项覆盖401、403、合法读取、跨租户与不存在记录。
- 自动化、三项 health gate 与桌面/390×844移动浏览器闭环通过；`A-01-BID-DETAIL` 已关闭，A-01 当前守恒为有用户入口238、前端调用但无独立页面58、内部/集成/运维4、需补入口10、待废弃0、需要确认11，共321。本切片未扩展编辑、删除、中标、失标、金额或项目转换，产品候选排序不变。

## 2026-07-15 增量：投标成本受控新建入口

- 既有 `/bid-cost` 页面已提供最小受控新建入口；前端仅提交投标项目名称和可选备注，后端专用 DTO 与 Service 共同强制当前租户、`BIDDING`、空 projectId，客户端不能写入租户、项目关联、状态、ID 或金额事实。
- MySQL/H2 V151 仅新增 `bid:add` BUTTON 并绑定既有 SUPER_ADMIN、ADMIN、COST_MANAGER；查询、编辑、删除和状态动作权限未放宽，投标头创建不会生成 `cost_item`。
- 自动化、180秒运行态稳定观察与不提交数据的真实浏览器验收通过；`A-01-BID-CREATE` 已关闭，A-01 当前守恒为有用户入口237、前端调用但无独立页面58、内部/集成/运维4、需补入口11、待废弃0、需要确认11，共321。本切片未扩展详情、编辑、删除、中标、失标或项目转换，产品候选排序不变。

## 2026-07-15 增量：投标成本只读列表入口

- 管理端已提供 `/bid-cost` 只读列表，支持投标项目名称、投标状态、刷新和分页；前端请求仅调用既有 `GET /bid-cost`，不发送 tenantId 或写请求。
- 路由、侧栏和 MySQL/H2 V150 菜单统一使用 `bid:query`；后端既有认证租户过滤保持不变，专项证明未登录401、无权限403、持权限成功与跨租户记录隐藏。
- 自动化、180秒运行态稳定观察与真实浏览器闭环通过；`A-01-BID-LIST` 已关闭，A-01 当前守恒为有用户入口236、前端调用但无独立页面58、内部/集成/运维4、需补入口12、待废弃0、需要确认11，共321。本切片未扩展投标写操作或状态流转，产品候选排序不变。

## 2026-07-15 增量：成本汇总历史只读入口

- 既有成本核对页已提供选定项目后的历史快照只读入口，展示汇总日期、成本科目、成本目标、实际成本、动态成本和成本偏差；当前聚合与历史行级类型保持分离。
- 前端只发送既有历史 GET 路径，不携带 tenantId 或写参数；服务端既有 `cost:summary:view`、认证租户和项目访问范围保持不变，专项覆盖401、无权限403、同租户无项目访问403、跨租户隐藏以及项目经理合法读取。
- 自动化、超过180秒运行态稳定观察与真实浏览器闭环通过；`A-01-COST-HISTORY` 已关闭，A-01 当前守恒为有用户入口235、前端调用但无独立页面58、内部/集成/运维4、需补入口13、待废弃0、需要确认11，共321，产品候选排序不变。

## 2026-07-15 增量：共享列表响应式表格高度链

- 共享 `lg-list-page` 在 ≤1200px 单栏态下已为 `lg-left`、`ct-main-column` 和 `lg-grid` 三种既有表格面板嵌套提供有界最小高度，修复表格 flex 高度链归零；角色页不再需要局部响应式特例。
- 1036px、1200px 中窄断点与1201px桌面边界的真实浏览器证据均通过，首行、操作菜单和分页保持可达；1201px 以上固定工作区与双栏布局未改变。
- `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT` 已关闭；本次为共享前端缺口修复，不改变产品能力边界、权限、数据模型或产品候选排序。

## 2026-07-15 增量：系统角色修改管理员入口

- 既有 admin-only 角色管理页已提供 ADMIN/SUPER_ADMIN 可见的“编辑角色”交互；编码只读，名称、状态和数据范围可编辑，失败保留表单，成功刷新列表，不触碰菜单授权链。
- 服务端继续以路径 ID 与认证租户锁定目标，SYSTEM、ADMIN、SUPER_ADMIN、roleLevel<2 角色均 fail-close；请求 ID、租户、类型、等级和菜单字段不能覆盖服务端事实，合法更新仅写回3个白名单字段。
- 自动化、180秒运行态稳定观察和真实浏览器闭环均通过；`A-01-ROLE-UPDATE` 已关闭，A-01 当前守恒为有用户入口234、前端调用但无独立页面58、内部/集成/运维4、需补入口14、待废弃0、需要确认11，共321，产品候选排序不变。

## 2026-07-15 增量：系统角色删除管理员入口

- 既有 admin-only 角色管理页已提供 ADMIN/SUPER_ADMIN 可见的危险删除交互，精确调用 `DELETE /system/roles/{id}`；二次确认展示目标角色名称，失败保留列表并展示后端错误，成功刷新列表。
- 服务端继续以当前租户为边界，SYSTEM、ADMIN、SUPER_ADMIN、roleLevel<2 及任何仍绑定用户的角色均 fail-close；合法未绑定自定义角色只在同一事务中删除目标角色及其菜单关系。
- 自动化、180秒运行态稳定观察和真实浏览器闭环均通过；`A-01-ROLE-DELETE` 已关闭。浏览器同时发现并正式登记 `UI-ROLE-RESPONSIVE-TABLE-ZERO-HEIGHT`：≤1200px 既有全局列表布局会令角色表格高度归零，该工程治理项不改变产品候选排序。

## 2026-07-14 增量：Codex 桌面原生 AutoPilot 执行宿主

- AutoPilot 生产控制权从 PowerShell 嵌套 Planner/Executor/Reviewer 模型进程迁移到当前 Codex 桌面主线程；脚本保留为确定性原子工具，默认 runner 入口在桌面宿主下只返回结构化 handoff。
- 旧 CLI 路径保留为显式兼容和紧急回退，缺失新字段的历史配置仍可读取；`desktop-native` 对所有旧模型进程入口 fail-close，不允许静默降级。state、Issue checkpoint、run lock 与只读 checkpoint 汇总共同记录执行宿主和恢复事实。
- 本次是本地治理控制面迁移，不改变产品业务能力、权限、租户、金额或候选排序。自动化兼容矩阵已通过，但真实单 Issue 金丝雀尚未由用户启动；在 closeout ledger、state、KG cursor 与 flags 完整读回前，N>1/无界继续阻塞。

## 2026-07-14 增量：AutoPilot Evidence v2 与收口事实链

- AutoPilot 已具备模型调用、Context Base/Delta 构造、验证执行/复用和报告投影的去重计数；RUN Planner 与 Issue Executor/Reviewer 保持作用域隔离，缺失 token 数据不再伪记为 0。
- 上下文从重复全量包收敛为不可变基础事实和阶段增量；验证证据按类别与完整身份指纹裁决，只有同 Issue、同命令、同上下文、同 diff、同环境的 `UNIT_BUILD` 可复用，静态廉价检查、集成和浏览器证据继续重跑。
- 正式报告只投影不含自引用字段的提交前事实；包含 closeout commit、报告/结果哈希和可选图谱游标的最终事实由 Closeout Record v2 承担，checkpoint 退休后仍可从 ledger 证明正式关闭。
- 本次是本地治理控制面优化，不改变产品业务能力或候选排序。低风险标准路径没有可消除的额外模型调用，因此未建设 Owner 快速通道；新控制面指纹在用户授权的单 Issue 金丝雀通过前不得放量。

## 2026-07-14 增量：系统角色新建管理员入口

- 既有 admin-only 角色管理页已提供 ADMIN/SUPER_ADMIN 可见的新建交互，精确调用 `POST /system/roles`；普通用户即使仅持 `system:role:add` 也不获得页面入口，后端细粒度授权仍独立保留。
- 服务端忽略客户端 ID 和 tenantId，固定 CUSTOM、roleLevel=2，并以 ENABLE/SELF 为安全默认；保留编码、SYSTEM、roleLevel 0/1 和非法枚举 fail-close，创建不写菜单关系。
- 自动化、真实浏览器、localhost dev 数据库边界及精确清理和独立 Reviewer 均通过；`A-01-ROLE-CREATE` 已关闭，产品候选排序不变。

## 2026-07-14 增量：间接费执行分摊用户入口

- 成本台账新增月度“执行间接费分摊”入口，不新增页面路由、菜单或默认角色授权；管理员可用，非管理员必须同时具备页面查询与 `overhead:execute`，客户端 tenantId 不能覆盖认证租户。
- V149 新增 `overhead_allocation_run`，以租户、规则、自然月月末形成持久化唯一门禁；成本来源键使用执行事实+项目，定时与手工并发只产生一组有效成本。来源金额排除 `OVERHEAD_ALLOCATION`，EQUAL、DIRECT_LABOR、CONTRACT_AMOUNT 保持原语义并按分守恒，零金额安全跳过，执行事实/成本/汇总同事务回滚。
- `A-01-OVERHEAD-EXECUTE` 已关闭；A-01 当前守恒为有用户入口230、前端调用但无独立页面58、内部/集成/运维4、需补入口18、待废弃0、需要确认11，共321。本切片未扩展规则 CRUD、总账、结账、冲销、批量补算或历史重算，未改变现有产品候选排序。

## 2026-07-14 增量：AutoPilot 分层补货、同轮续跑与策略契约

- Ready 为空时，排名第一且携带完整权威 ReadySpec 的存量候选可确定性生成一条 Ready；不完整候选继续走有界语义 Planner。Planner v2 必须逐候选给出 CREATED/REJECTED/BLOCKED，允许零 Ready 安全结束。
- 补货提交后不再固定退出：RUN 作用域 StageResult 与唯一 transition writer 完成 `transitionId + generation` 写后读回，同一 run 重新经过 checkpoint 后可选择新 Ready。候选证据提交和实施基线提交保持独立。
- AutoPilot 行为策略已从主线负责人 Skill 拆到 `plugins/cgc-pms-autopilot/references/control-plane-policy.md`，策略版本与哈希纳入控制面指纹、运行上下文、阶段上下文和结果证据；动态版本、权重、阈值、模型和超时继续由配置、Schema 与批准来源承担。
- 自动化控制面与兼容矩阵通过；真实单 Issue 金丝雀仍是新指纹允许 N>1/无界放量前的用户授权门。

## 2026-07-14 增量：AutoPilot 控制面模块化与状态机瘦身

- 第43条主线将原 1725 行连续 Runner 拆为薄入口、显式 RuntimeContext、run coordinator、Issue lifecycle、Executor supervisor、StageResult 和 transition writer；Ready、恢复、Reviewer、评分、stop/pause 与两阶段收口语义不变。
- 活动 Issue 的 checkpoint 迁移现在由唯一 transition writer 校验合法边并读回 `transitionId + generation`；阶段执行结果采用结构化 `StageResult`，不再把自由文本作为阶段路由契约。
- 原连续 Runner 场景矩阵保留为兼容测试，新增主题测试入口和确定性 Git fixture。真实 Ready 金丝雀仍是新控制面指纹允许 N>1/无界放量前的用户授权门。

## 2026-07-14 增量：AutoPilot PowerShell 7 与控制面一致性

- AutoPilot 控制面固定使用 PowerShell 7 `pwsh`，不再支持 Windows PowerShell 5.1；普通临时 Git fixture 使用仓库本地换行规则，CRLF warning 专项 fixture 单独保留。
- 执行模式、原生命令结果、原子运行锁、fencing token、控制面指纹、state/checkpoint/result 过渡和语义 stall 已形成统一控制面事实；stderr warning、tool_config 与环境前置不再自动进入业务 quarantine 或 BC repair。
- 任务执行效率证据新增业务阶段耗时与控制面返工耗时拆分，继续沿用已批准 v2 35/25/20/10/10，不按固定分钟数扣分。
- 当前工程能力状态为“实现与夹具验收通过、真实单任务金丝雀待用户启动”；金丝雀登记前 N>1/无界连续执行保持 fail-close。本次未改变任何产品业务能力或候选排序。

## 2026-07-13 增量：系统菜单修改管理员入口与树约束

- `ISSUE-040-023` 在既有 admin-only `/system/permissions` 页面增加“修改菜单”入口，从完整菜单树选择目标并加载详情，精确发送 `PUT /system/menus/{id}`；成功后刷新菜单树、平铺列表与详情，失败保留目标和表单。
- 页面入口仅 ADMIN/SUPER_ADMIN 可见；后端既有 ADMIN/SUPER_ADMIN 或 `system:menu:edit` 授权保持不变。更新以路径 ID 和当前租户锁定目标，只复制10个业务字段，并拒绝非法类型、非法父节点、自环/后代环与带子节点改 BUTTON；角色菜单关系保持不变。
- `A-01-MENU-UPDATE` 已关闭；A-01 当前守恒为有用户入口229、前端调用但无独立页面58、内部/集成/运维4、需补入口19、待废弃0、需要确认11，共321。本切片未扩展为拖拽排序、批量修改、动态路由或完整菜单管理平台。

## 2026-07-13 增量：AutoPilot 跨 Run 阶段恢复与任务执行效率评分

- 控制面新增原子 Issue phase checkpoint，绑定 Ready 内容、base、worktree/branch、scope、diff/evidence、阶段产物和派发指标；死进程接管不再删除有效 worktree 或重新派发 implementation，而是从 validation、Reviewer 或 closeout 的首个未完成阶段继续。
- Reviewer 结果新增结构化 `tool_blocked` 路由。Windows sandbox/tool_config 失败只重试同一 diff 的 Reviewer，累计两次仍失败暂停当前 Issue；业务 `NEEDS_REPAIR` 继续要求完整 finding 并执行有界 repair。
- `autopilot-task-score/v2` 已按35/25/20/10/10正式激活，10分维度升级为 `taskExecutionEfficiency`，覆盖 `runResumeCount`、各阶段派发、人工恢复、工具/环境重试与证据完整性；从批准配置提交后的下一项新实施型 Ready 生效。v1 历史记录保持不变，不回算、不覆盖、不与 v2 双计数。
- 控制面指纹覆盖行为配置并阻止未经单任务证明的 N>1/无界放量；真实金丝雀仍须用户明确执行 `启动迭代-1`，只有 closeout ledger、state 与知识图谱 Git cursor 全部读回才登记成功，不会由本主线自动启动。

## 2026-07-13 增量：系统菜单平铺列表管理员入口

- `ISSUE-040-022` 在既有 admin-only `/system/permissions` 页面增加“菜单列表”只读入口，按需调用无 body、无 params 的 `GET /system/menus`，复用 `SysMenuVO[]`，关闭后重新打开会重新获取确定性快照。
- 页面仅 ADMIN/SUPER_ADMIN 可见；后端既有 ADMIN/SUPER_ADMIN 或 `system:menu:query` 授权、当前租户过滤、`orderNum` 升序与字段白名单保持不变。真实页面展示目录、菜单、按钮及空权限码节点，字段固定为名称、类型、父节点、路径、权限码、排序、状态和可见性。
- `A-01-MENU-LIST` 已关闭；A-01 当前守恒为有用户入口228、前端调用但无独立页面58、内部/集成/运维4、需补入口20、待废弃0、需要确认11，共321。本切片未扩展为菜单编辑、动态路由或完整菜单管理平台。
- 本轮控制面暴露的 `OPS-AUTOPILOT-STALL-FINGERPRINT` 已修复并从唯一台账移除：进度指纹不再纳入子进程树 CPU 累计值，只认持久化工作树变化；显式声明的有界长命令继续使用独立豁免门禁。

## 2026-07-13 增量：全部历史任务专项复盘

- 已按正式台账与验收报告完成 131 个历史任务单元的一次性复盘：v1.0 归档 88 项、v1.5 当前 43 项；私有禁止区未纳入范围。
- v1.5 完成台账原漏记 30 项且 3 项字段不完整，现已按 Issue、日期、提交、报告、验证和风险格式补齐；历史任务与正式报告、Git 提交可双向定位。
- 历史重复成本集中在真实质量/安全缺陷、Ready/工具/环境配置噪声和旧全量测试无关红灯；前两类已有现行失败分类与契约门禁，旧红灯已由第38条和第40条新鲜证据取代。
- 本次专项复盘不回算 `autopilot-task-score/v1`、不增加 20 项计数；A-01 与 3 个生产发布前置继续由现有唯一问题载体承接，未新增悬空后续项。

## 2026-07-13 增量：AutoPilot 任务评分与20任务自动改进回顾候选控制面

- 已落地确定性五维评分器、评分 schema、`implementationCommit` / `closeoutCommit` 两阶段收口、v2→v3 状态迁移、跨批次回顾计数、无界20任务门禁、有界18+3整批回顾、改进提案聚合、稳定 Episode CLI 与可恢复阶段状态。
- v1 曾按35/25/20/10/10正式生效并保留历史评分；2026-07-13 用户进一步批准 `autopilot-task-score/v2`，当前配置为 `enabled=true`、`activeVersion=autopilot-task-score/v2`、`approvalStatus=APPROVED`，同权重中的10分维度改为 `taskExecutionEfficiency`，从本次批准配置提交后的下一项新实施型 Ready 起正式计入20任务回顾周期。
- 历史样本回放显示交付、零悬空和存量变化证据可用，但首次验收、周期效率及两阶段提交缺少结构化字段，因此不回算历史任务。低分仍不改变硬门禁裁决，回顾改进提案仍须逐项用户确认后才能实施。

## 2026-07-13 增量：知识图谱优先问题路由与 Ready 契约门禁

- 第41条主线将普通存量问题查询和 AutoPilot 补货统一到 `kg_status` / `kg_list_issues` 同语义入口；脚本侧通过轻量 `issues` CLI 复用 `listIssues`，不复制 Cypher、不建立第二份问题缓存。
- AutoPilot 仅在最近采集健康、失败数为零、问题计数一致且 Git 游标覆盖当前 HEAD 时读取图谱候选；游标落后最多执行一次 `autopilot-refill` 增量采集，仍不一致或 Neo4j/CLI 异常时安全停止，不静默回退文件选题。`current-issues.json` 继续承担唯一正式写回源。
- 候选进入 Ready 前必须按 `sourceRefs`、当前代码/配置和唯一载体核实；Ready allow/forbid 的可证明完全覆盖矛盾在 executor/worktree 前以 `ready_issue_config` 拒绝，合法“宽允许 + 窄禁止”安全 carve-out 与运行时 forbidden 优先门禁保持有效。

## 2026-07-13 增量：系统菜单详情管理员入口

- `ISSUE-040-021` 在既有 admin-only `/system/permissions` 权限清单页增加“查看详情”入口；详情目标来自完整菜单树，目录、菜单、按钮及无权限码节点均可选择，选择后按需调用既有详情接口。
- 前端类型化 API 固定发送无请求体、无额外参数的 `GET /system/menus/{id}` 并复用 `SysMenuVO`；加载新目标时清空旧详情，失败保留目标并显示错误，成功仅展示名称、类型、父节点、路径、组件、权限码、图标、排序、状态与可见性。
- 页面入口仅 ADMIN/SUPER_ADMIN 可见；后端原 ADMIN/SUPER_ADMIN 或 `system:menu:query` 授权、当前租户 fail-close 与响应字段白名单保持不变，专项证明 200/403/401、跨租户/不存在 `MENU_NOT_FOUND` 及敏感字段不暴露。
- `A-01-MENU-DETAIL` 已关闭；A-01 当前守恒为有用户入口227、前端调用但无独立页面58、内部/集成/运维4、需补入口21、待废弃0、需要确认11，共321。本切片未扩展菜单列表、编辑、动态路由或完整菜单管理平台。

## 2026-07-13 增量：系统菜单删除管理员入口

- `ISSUE-040-020` 在既有 admin-only `/system/permissions` 权限清单页增加受控删除入口，删除目标直接来自完整菜单树，目录、菜单、按钮及无权限码节点均可选择；确认区展示目标名称、不可逆提示和子节点/角色引用拒绝说明。
- 前端仅 ADMIN/SUPER_ADMIN 可见，类型化 API 固定调用无请求体的 `DELETE /system/menus/{id}`；成功重新获取菜单树和角色列表并关闭交互，失败保留目标、显示后端错误且不刷新为成功态。
- 后端生产删除路由、授权、租户过滤、子节点和角色引用约束保持不变；专项证明 ADMIN/SUPER_ADMIN/`system:menu:delete` 正样本、无权限403、跨租户与不存在目标 fail-close，以及 `MENU_HAS_CHILDREN` / `MENU_REFERENCED_BY_ROLES` 拒绝。
- `A-01-MENU-DELETE` 已关闭；该 Issue 收口时 A-01 守恒为有用户入口226、前端调用但无独立页面58、内部/集成/运维4、需补入口22、待废弃0、需要确认11，共321。本切片未扩展菜单编辑、批量/级联删除、强制解绑、排序或完整菜单管理平台。

## 2026-07-13 增量：系统菜单新建管理员入口

- `ISSUE-040-019` 在既有 admin-only `/system/permissions` 权限清单页增加受控的新建菜单入口，仅 ADMIN/SUPER_ADMIN 可见，复用菜单树选择父节点；前端只向 `POST /system/menus` 发送类型化业务载荷，成功刷新树，失败保留表单。
- 后端原 ADMIN/SUPER_ADMIN 或 `system:menu:add` 授权未放宽；创建服务拒绝非法菜单类型、不存在/跨租户/BUTTON 父节点，并强制从当前用户上下文取得租户。专项证明管理员与显式权限成功、无权限403及树/租户正负边界。
- `A-01-MENU-CREATE` 已关闭；该 Issue 收口时 A-01 守恒为有用户入口225、前端调用但无独立页面58、内部/集成/运维4、需补入口23、待废弃0、需要确认11，共321。该结果不扩展为菜单编辑、删除、排序或完整管理平台。

## 2026-07-13 增量：AutoPilot 存量问题优先补货

- `ISSUE-040-018` 将补货顺序统一为“已有 Ready → 结构化存量 → 当前 focus 阻塞 → 已过决策门 Ad-hoc → 产品情报刷新”，删除连续 runner 从长期增强计划直接生成 Ready 草稿的旁路。
- 可拆存量必须来自 `docs/backlog/current-issues.json`，具备验收标准和来源证据，并排除阻塞项、发布门禁、冻结/需要确认、聚合父项；排序为 P0→P2、Open→Observation、叶子优先，使用 `[stock:问题键]` 跨载体去重。
- closeout 新增反悬空门禁：存量任务完成前，原问题必须从当前台账移除或正式重分类为不可继续拆分状态。该能力只改变本地治理任务选择，不自动启动迭代或改变生产边界。

## 2026-07-13 增量：当前问题结构化知识图谱

- `ISSUE-040-017` 新增机器可读当前问题快照、Issue/父子/证据图关系和 `kg_list_issues`；当前57个问题可直接按状态、分类、优先级、父项、阻塞性查询，默认摘要不展开文档或历史版本。
- 该能力属于工程治理基础设施，不改变产品候选排序；仓库、Git、正式backlog和验收报告仍是事实源，图谱不自动猜测问题状态。

## 2026-07-13 增量：A-01 财务核算入口治理

- `ISSUE-040-012～016` 为会计凭证列表、详情、过账和冲销建立前端 API、路由、菜单与同页状态操作，复用既有租户过滤和 `DRAFT → POSTED → REVERSED` 门禁。
- V148 为 FINANCE、ADMIN、SUPER_ADMIN 授予 `accounting:query` / `accounting:edit`；未授权 `accounting:add`。生产代码中没有任何 `EntryGenerationStrategy` 实现，生成接口已改为对不支持来源显式失败并保持“需要确认”。
- A-01 当前守恒：有用户入口 228、前端调用但无独立页面 58、内部/集成/运维 4、需补入口 20、待废弃 0、需要确认 11，共 321。

## 2026-07-13 增量：第40条首个权限历史债闭环

- `ISSUE-040-001` 将预警租户级 `batch-evaluate` 从普通 `alert:edit` 拆为独立 `alert:evaluate`，关闭第10A历史安全门禁曾否决但未修复的高低风险共码。
- V146 仅更新菜单 768，菜单 767 和既有角色—菜单关系保持不变；普通权限负样本 403、独立权限/管理员正样本 200，H2 实际迁移与 22 项专项通过。
- 该结果只关闭 V-01 的一个最小债务点，不代表权限注册表、审批入口、动态菜单和真实角色全域复验完成；MySQL 实跑仍需环境或远端门禁证据。

## 2026-07-13 增量：v1.0 历史问题四态分类

- 254 个 v1.0 正式历史 Markdown 已纳入可追溯历史索引；历史节点统一标记，不参与默认 current 搜索。
- 92 个唯一历史 Issue 保留“v1.0 已解决”语义，但不得替代 v1.5 当前验证。
- 当前仍适用的产品问题归并为 10 个 Candidate/Frozen 域；权限、租户、数据一致性、真实角色、外部集成与工具链共 8 个风险域保持“需要复验”。
- 旧 SHA/PR、旧工作区、旧测试选择器、旧浏览器能力和旧批次时间窗等 6 类记录判为已过期，不再进入当前优先级排序。

## 2026-07-13 增量：项目知识图谱采集机制

- 本地工程知识索引已从单次文档采集升级为可追溯增量机制：正式文档保留 ArtifactVersion，Git 使用 commit 游标，采集运行具备 SourceCursor、CollectionRun 和失败状态。
- Codex MCP 只开放受限查询与固定 Schema 的 Episode 写入；会话和日志仅保存结构化摘要与来源，敏感字段入图前脱敏，私有目录继续禁止内容读取。
- Windows 本地每 30 分钟执行一次补漏对账，调度和脚本均防止并发实例；该能力属于工程治理基础设施，不是 CGC-PMS 产品功能，不改变产品候选排序。
- 验收结果：13 项测试、MCP smoke、依赖审计、幂等采集、来源覆盖、敏感值与定时任务均通过；可用于本地知识管理，不代表生产服务已部署。

## 2026-07-13 增量：develop/1.5 全量审计根因闭环

- 原全量审计的 7 项阻塞已完成本地闭环：测试态全局写限流隔离、陈旧菜单数字 ID 契约、前端 lint 与 3 项静态契约、SQL 安全标记、Playwright/合法空态 E2E、`master` 分支保护。
- 后端最终 `verify` 为 184 suites、1723 tests、0 failures、0 errors、1 skipped；另补结算金额快照直接测试，并修复 Dashboard 合同到期测试夹具从每月 13 日起漂移的问题。
- 前端主机依赖已恢复；413 条纯 Prettier 告警已治理至 lint 0 error / 0 warning；ECharts 已升级到 6.1.0、vue-echarts 升级到 8.0.1，依赖审计为 0 漏洞；类型检查/构建/bundle 通过、91 files / 505 tests 全过；用户提供的 Chrome 149 完成 UI smoke 7/7，内置浏览器也已真实到达驾驶舱。
- 供应链门禁确认 `trivy fs` 不覆盖 post-build JAR，现由 `supply-chain-security` 对构建目录执行 `trivy rootfs`；本地识别 fat JAR 并得到 HIGH/CRITICAL 0。
- `master` 已启用 `enforce_admins=true` 与 `required_conversation_resolution=true`，原 11 个 required checks、`strict=true`、禁止 force push/delete 保持不变。
- PR #334 目标提交的 11 个 required checks 已全绿，并于 2026-07-13 合并为 `master` 提交 `76ec42a0`；CI 合并门禁与原管理员绕过阻塞均已解除，生产仍未发布。

## 2026-07-12 增量：AutoPilot Windows PowerShell 5.1 编码阻塞

- 当前 AutoPilot 统一控制面在 Windows PowerShell 5.1 `-File` 入口稳定产生 ParserError；显式 UTF-8 AST 解析无错误，根因是含中文的 UTF-8 无 BOM 脚本被系统 ANSI/GBK 解码。
- 该缺口直接阻塞 Ready 选单、实施和正式收口，按 `ISSUE-037-022` 进入最小治理 Ready；范围仅限编码兼容、控制面回归与状态回写，不替代产品方向。
- 实施回写：`ISSUE-037-022` 已统一脚本源码 BOM 与运行期显式 UTF-8 读取；Windows PowerShell 5.1 控制面自测、连续 runner 自测及真实 `ISSUE-037-022` 路由均通过，Ready 准入恢复为可用。

## 2026-07-12 增量：CI/CD 与上线门禁 v1.5 复验（历史快照，已由第40条 M0 更新）

- `ISSUE-037-021` 首次只读复验时，master commit `781b41661cd96b2a2f7eed825f98ff3d9bdf137b` 的 `frontend-lint`、`frontend-test`、`e2e` 为 failure，当时结论为不通过、阻塞、不可上线。
- 当时 required checks 与 workflow job 一一对应且 `strict=true`，但 `enforce_admins=false`；该状态已被后续治理和第40条 M0 新鲜证据取代。
- 红灯与治理缺口已按责任域进入 Blocked；本次未修改业务代码、workflow 或远端设置。
- 以上为 2026-07-12 首次复验快照；第40条 M0 已用 PR #334 合并、11 checks 全绿及 `enforce_admins=true` 的新鲜证据将三条 Blocked 统一关闭。

## 2026-07-12 增量：系统审查与 Ready 恢复

- `PI-2026-07-12-12` 的停止动作保持为历史事实，但“无合格 Candidate”因候选范围过窄和地图回写滞后被后续复核撤销。
- `ISSUE-037-017` 已完成：共享 `BaseEntity.remark` 恢复正常 JSON 写入，ID、租户、审计和逻辑删除字段继续只读；未修改 Controller、Service、前端或数据库。
- 插件 Ready 预演修复了标题正则缺少多行匹配的问题；合法的二、三级标题现可通过 Select Gate，并有最小回归脚本保护。
- `ISSUE-037-018` 已完成：执行器 300/600 秒 inspect/retire、一次 repair、第二次 stall blocked、退役证据和有界长命令声明已闭环；PID 复用与延迟启动计时边界有回归保护。
- 本轮只修复共享根因并保护 ID、租户和审计字段，不扩成 DTO 重构或全接口治理。

## 2026-07-12 增量：WBS 软删除编号冲突

- `ISSUE-037-015` 验收稳定复现：当天自动编号只查询未删除任务，历史软删除编号会被复用；再次删除复用编号的任务时，`(tenant_id, task_code, deleted_flag)` 唯一键冲突并返回 409。
- `ISSUE-037-016` 已完成：逻辑删除前把编号改为按任务 ID 唯一的墓碑值，修复复用编号再次删除的冲突；未改表、编号展示规则或全局软删除框架。

## 2026-07-12 增量：WBS 单前置 FS 状态门禁

- 现有分包任务已支持单前置 FS、同项目/环/日期校验和延期风险展示。
- `ISSUE-037-015` 已完成：统一 Service 在有效前置未完成时拒绝后续任务进入 `IN_PROGRESS` / `COMPLETED`，前端同步禁用并提示。
- 结果继续保持单前置、无新状态、无关系表、无自动排程和无数据迁移；多前置与完整计划平台仍未实现。

## 2026-07-12 增量：现场日报与领料出库候选

- 日报已有同日已审批到货、计划任务和审计历史，但尚未呈现同日已审批并真实出库的领料事实。
- 现有 `mat_requisition` / `mat_requisition_item` 已具备租户、项目、领料日期、审批状态、出库标记、物料和数量，可在不新增表的前提下形成只读联动。
- `ISSUE-037-014` 已完成：只展示同租户、同项目、同日期、`APPROVED` 且 `stock_out_flag = 1` 的领料明细；不把领料解释为已安装，不披露价格、金额、合同或供应商信息。

## 2026-07-12 增量：现场日报变更历史

- 日报写操作已进入统一操作审计，但 CREATE 未绑定业务 ID，详情页也未展示可读历史。
- `ISSUE-037-013` 已完成：CREATE 审计归属具体日报，详情只读展示最小变更记录；未建设字段级版本或独立历史表。

## 2026-07-12 增量：计划任务与现场日报对照

- 分包 WBS 已具备项目、计划日期、状态、进度和单前置 FS；现场日报尚未呈现当天计划任务。
- `ISSUE-037-012` 已完成：日报详情只读展示计划日期覆盖当天的同项目任务，连接计划与每日现场事实；未新增排程或日报任务副本。

## 2026-07-12 增量：材料到货与现场日报联动

- 现场日报已具备项目/日期、草稿提交、附件、天气摘要和在场人数，但尚未展示已有材料验收事实。
- 材料验收已具备项目、验收日期、审批状态、供应商和物料数量；审批通过后原子触发库存与成本，日报不得复制或改写该事实。
- `ISSUE-037-011` 已完成：日报详情只读聚合同项目同日已审批验收明细，作为现场到货证据；设备、消耗、安装量和生产率继续后置。

## 地图基线

| 项目 | 当前值 |
| --- | --- |
| 产品版本 | `1.5.0-dev.0` |
| 分支 | `develop/1.5` |
| Commit | 当前 `develop/1.5` 工作区 |
| 生成时间 | 2026-07-12 |
| 证据类型 | 当前代码、配置、现行规范、测试入口静态核对 |
| 验证新鲜度 | 业务测试和真实角色运行态待本轮后续复验 |
| 下次刷新 | 下一条产品 Candidate 完成后，或当前事实变化时 |

> 本地图中的 `Partial` 不等于功能不存在，表示真实代码链路已经存在，但尚未取得 v1.5 当前周期的完整运行或业务验收证据。v1.0 历史测试结论不用于升级状态。

## 产品定位

CGC-PMS 是面向建筑工程总包企业的项目经营与全过程管理平台，主线是项目、合同成本、采购库存、分包结算、付款发票、审批预警和角色驾驶舱的一体化管理。

明确不是：

- 通用软件研发项目管理工具。
- 通用 ERP 的完整替代品。
- 以多智能体、MCP 或编码工具为产品卖点的平台。

## 核心业务闭环

```text
项目立项
  → 合同 / 签证 / 采购 / 分包
  → 成本与收入归集
  → 收货 / 库存 / 领料 / 计量
  → 付款 / 发票 / 结算
  → 审批 / 通知 / 预警
  → 驾驶舱与经营分析
```

## 技术地图

```text
Vue 3 + TypeScript + Vite
        ↓ /api
Spring Boot 3 + Java 21 + Spring Security + MyBatis-Plus
        ↓
MySQL/H2 + Redis + MinIO
        ↓
Docker Compose + Nginx + Actuator + Prometheus
```

| 层级 | 现行入口 |
| --- | --- |
| 前端入口 | `frontend-admin/src/main.ts`、`frontend-admin/src/App.vue` |
| 前端路由 | `frontend-admin/src/router/` |
| 页面 | `frontend-admin/src/pages/` |
| API 封装 | `frontend-admin/src/api/modules/` |
| 后端入口 | `backend/src/main/java/com/cgcpms/CgcPmsApplication.java` |
| 后端业务域 | `backend/src/main/java/com/cgcpms/` |
| MySQL migration | `backend/src/main/resources/db/migration/` |
| H2 migration | `backend/src/main/resources/db/migration-h2/` |
| 部署 | `deploy/`、`docker-compose*.yml` |

## 当前规模快照

以下是路径级粗计数，不代表完成度：

| 指标 | 数量 |
| --- | ---: |
| 后端一级业务/技术域 | 32 |
| Controller 文件 | 53 |
| 前端 Vue 文件 | 129 |
| MySQL Flyway migration | 134 |

## 业务能力地图

| 业务域 | 前端证据 | 后端证据 | 测试入口示例 | 状态 | 当前缺口 |
| --- | --- | --- | --- | --- | --- |
| 项目与成员 | `pages/project/`、`api/modules/project.ts` | `project/` | `PmProjectControllerTest`、`ProjectOverviewServiceTest`、`ProjectLedgerProduction.test.ts` | Partial | v1.5 真实角色、项目数据范围和运行态待复验 |
| 合同与付款条件 | `pages/contract/`、`api/modules/contract.ts` | `contract/` | `CtContractServiceTest`、`ContractApprovalIntegrationTest`、`ContractLedgerPage.test.ts` | Partial | 合同履约、金额口径和审批联动需当前复验 |
| 变更与签证 | `pages/variation/`、`api/modules/variation.ts` | `variation/` | `VarOrderServiceTest`、`VarOrderControllerMockMvcTest`、`VariationOrderProduction.test.ts` | Partial | 变更收入/成本联动和审批边界待复验 |
| 成本与目标成本 | `pages/cost/`、`pages/cost-target/` | `cost/`、`revenue/`、`overhead/`、`accounting/` | `CostSummaryServiceTest`、`CostLedgerServiceTest`、`CostSummaryProduction.test.ts` | Partial | 多来源成本、月份快照和下钻口径待复验 |
| 采购与采购申请 | `pages/purchase/`、`pages/inventory/purchase-request.vue` | `purchase/` | `MatPurchaseOrderServiceTest`、`PurchaseRequestServiceTest`、`purchase/order.test.ts` | Partial | 已完成安全阈值、人工补货目标量和自然日提前期预填；供应商级提前期、工作日历和预测仍缺失 |
| 收货、仓库与库存 | `pages/receipt/`、`pages/inventory/` | `receipt/`、`inventory/` | `MatReceiptServiceTest`、`MatStockServiceTest`、`stock-production.test.ts` | Partial | 已维护安全阈值并联动 KPI/预警；目标量、全量建议、预测和跨仓调拨仍缺 |
| 领料 | `pages/requisition/` | `requisition/` | `MatRequisitionServiceTest`、`useRequisitionForm.test.ts` | Partial | 与计划需用量、施工部位和损耗分析尚未闭环 |
| 分包与计量 | `pages/subcontract/` | `subcontract/` | `SubMeasureServiceTest`、`SubTaskControllerTest`、`subcontract/measure.test.ts` | Partial | 已完成单前置 FS、状态门禁、延期风险和软删除编号冲突修复；仍无多前置、多类型、自动排程和完整履约档案 |
| 结算 | `pages/settlement/` | `settlement/` | `StlSettlementServiceTest`、`StlSettlementControllerMockMvcTest`、`settlement/index.test.ts` | Partial | 合同、变更、计量、付款汇总需当前一致性复验 |
| 付款与资金日记账 | `pages/payment/`、`pages/cash-journal/` | `payment/`、`accounting/` | `PaymentFinancialConsistencyTest`、`PayRecordCashJournalIntegrationTest`、`payment/save-chain.test.ts` | Partial | 金额、财务回写、附件和权限需当前复验 |
| 发票与识别 | `pages/invoice/` | `invoice/` | `InvoiceServiceTest`、`InvoiceRecognitionTest`、`invoice-pdf.test.ts` | Partial | 识别可靠性、付款关联和文件安全需当前复验 |
| 审批、抄送与通知 | `pages/approval/` | `workflow/`、`notification/` | `WorkflowCoreServiceTest`、`ApproverResolverTenantIntegrationTest`、`ApprovalWorkList.test.ts` | Partial | 真实角色矩阵、跨业务状态一致性待复验 |
| 预警 | `pages/alert/` | `alert/` | `AlertEvaluationServiceTest`、`AlertControllerTest`、`alert/index.test.ts` | Partial | 规则治理、通知渠道和抑制升级仍是后续方向 |
| 驾驶舱与报表 | `pages/dashboard/`、`pages/report/` | `dashboard/` | `DashboardServiceTest`、`DashboardControllerTest`、`DashboardDataLoading.test.ts` | Partial | 指标来源、下钻和不同角色数据边界待复验 |
| 用户、角色、菜单与审计 | `pages/system/`、`api/modules/system.ts` | `auth/`、`system/`、`audit/` | `WorkflowControllerAuthTest`、`system/permissions/index.test.ts` | Partial | 不能用超级管理员替代真实角色验收 |
| 文件 | `api/modules/file.ts` | `file/` | 现有文件安全与业务绑定测试 | Partial | 上传、病毒扫描占位和业务绑定边界需当前复验 |

## 角色驾驶舱地图

| 角色 | 当前业务基础 | 状态 | 边界 |
| --- | --- | --- | --- |
| 项目经理 | 项目总览、待办、预警、审批、合同履约 | Partial | 不用经营财务图表冒充执行协同 |
| 商务经理 | 合同、变更、成本、结算、付款、预警 | Partial | 现有成本经理语义统一为商务经理 |
| 采购经理 | 采购、验收、库存、领料 | Partial | 不复用商务经理利润/结算主视图 |
| 生产经理 | 验收、领料、库存、分包计量近似数据 | Partial | 不是完整进度、劳务、机械和产值驾驶舱 |
| 总工程师 | 当前只有零散设计变更相关数据 | Frozen | 必须先建立技术方案、设计协调、技术审核和重大技术问题对象 |

## 数据与安全边界

| 边界 | 当前规则 | 地图结论 |
| --- | --- | --- |
| 租户 | 后端强制隔离，Service 必须校验 | ISSUE-040-003 的第二租户与跨租户专项通过；V-04 三类真实角色浏览器正负样本通过 |
| 项目 | 大多数业务对象的主线维度 | ISSUE-040-003 的跨项目、成员与附件项目校验通过；V-04 浏览器体验通过 |
| 权限 | `@PreAuthorize` 是安全边界，前端隐藏仅为体验 | V-01 三类真实角色正负 API、前端 42 项与 V-04 浏览器交互通过 |
| 审批 | 合同、变更、付款、结算等必须校验状态 | 跨业务状态一致性是高风险验收项 |
| 金额 | 成本、合同、采购、库存、分包、付款、结算共同影响 | 任何改动必须给出来源、月份和回滚证据 |
| 数据库 | 只新增 migration，不修改已应用脚本 | v1.5 业务候选默认优先无 migration 的最小闭环 |

## 工程与实施地图

| 能力 | 当前入口 | 状态 | 说明 |
| --- | --- | --- | --- |
| CI 门禁 | `.github/workflows/` | Partial | 本地整改与 11 项门禁映射已通过，`enforce_admins=true`、`required_conversation_resolution=true`；等待本轮整改 commit/push 后同一待合并 SHA 的远端 required checks 全绿证据 |
| 本地运行 | `scripts/rebuild.py`、Docker Compose | Partial | ISSUE-037-001 已完成 8080、5173、dev-login health gate 与真实角色浏览器验收 |
| 现场日报验收直达 | `DevAuthController`、`/site/daily-log` | Implemented | `ISSUE-037-008` 已补 `/site`，直达与站外/遍历安全回落均有测试和运行态证据 |
| Ready 准入 | `docs/backlog/ready-issues.md`、`autopilot-ready.ps1`、插件 loop runner | Implemented | 当前 Ready 可被严格解析器和插件预演识别；插件标题多行匹配已有回归保护 |
| 候选补货 | `autopilot-refill.ps1`、知识图谱 `issues` CLI | Implemented | 先过图谱健康与 HEAD 游标门禁，再有界拉取并核实存量候选；图谱异常 fail-close，不回退文件或长期计划凑任务 |
| 连续执行 | `autopilot-run-continuous.ps1` | Implemented | 已具备隔离执行、本地提交、上限停止、300/600 秒停滞处置、一次有限重派、第二次 blocked 和有界长命令声明 |
| 质量归档 | `docs/quality/` | Implemented | 已归档第37条主线与 ISSUE-037-001 至 ISSUE-037-021 正式验收报告 |
| Windows MySQL 备份恢复与本机凭据轮换 | `scripts/mysql-backup.ps1`、`scripts/mysql-restore.ps1`、`deploy/.env`（忽略） | Implemented（本地） | ISSUE-040-006 已修复二进制安全问题，隔离恢复与轮换后均保留 74 表；MySQL/Redis/MinIO/JWT/Jasypt 注入、旧 JWT 失效和新登录通过。未来生产轮换由上线门禁重新验收 |

## 当前明确缺口

### 产品候选

- 采购补货建议：已完成数量预填、安全阈值、可空人工目标量和可空自然日提前期计划日期预填；供应商级提前期、工作日历、预测与全量建议治理仍后置。
- 现场日报 / 施工日志：`ISSUE-037-005` 已建立日报对象、状态、项目范围与附件链，`ISSUE-037-007` 已增加人工天气摘要和可空在场人数；人员明细、设备材料、移动离线和质量安全继续后置。
- WBS 任务依赖与延期预警：`ISSUE-037-004` 已在分包任务上完成单前置 FS、项目数据范围和前置延期风险；仍不支持多前置、多类型、依赖连线、拖拽、自动改期或独立计划模型。
- 供应商交付档案：`ISSUE-037-002` 已用订单明细与已审批验收累计数量还原交付完成日，并区分按期完成、迟交完成和逾期未完成；质量、价格和退货仍不具备稳定口径，页面明确不是综合评级。
- 后端接口无前端入口治理：`ISSUE-037-019` 静态基线纳入 53 个 Controller、321 个唯一 HTTP 方法；经增量治理后，当前已验收分类为有用户入口 244、前端调用但无独立页面 58、内部/集成/运维 4、需补用户入口 4、待废弃 0、需要确认 11。当前剩余明确叶子为投标失标与间接费规则新建、修改、删除；其余11项保持需要确认，均需按域验收，不是新业务域。
- 驾驶舱项目数据范围：`ISSUE-037-003` 已统一项目经理与管理驾驶舱的指定项目、全项目任务/审批/合同/风险聚合，空关联与不可见项目 fail-close。

### 工程治理候选

- 子智能体超时、悬挂线程退役与有限重派治理。
- 实体直绑更新字段白名单、前端重复错误提示和接口—入口映射仍需按独立证据拆分，不因备注契约修复自动视为完成。

工程治理候选必须与产品候选分组排序，默认不能用泛化工具或流程改进替代产品方向判断。若当前证据证明治理缺口直接阻塞已选产品目标、安全边界或正式验收，可按 `缺口修复` 或 `运维治理` 进入 Ready；必须绑定产品目标、阻塞证据、解除条件、非目标和回滚方式。

## Unknown 与待复验

- CI 红灯与管理员绕过已于第40条 M0 复核解除；required pull-request review 与 push restrictions 是否作为附加治理要求仍需确认。
- 当前本地 Docker、后端和前端 health gate 及三类低权限浏览器正负样本已于 ISSUE-040-005 验证通过。
- 五类驾驶舱角色的真实账号与数据可见范围。
- 当前全量后端、前端单元测试结果。
- 现有长期计划中所有“已完成”项在 v1.5 的复验状态。
# 2026-07-15 增量：工作流个人效率统计入口

- `ISSUE-040-034` 在既有审批工作台分析栏复用已实现的个人效率接口，按当前认证 userId、tenantId 与页面筛选展示待办、逾期待办、已办、已处理任务和平均处理分钟数；前端不提交身份或租户参数。
- 后端 32 项、前端 13 项、类型检查、目标 ESLint、差异检查、180 秒稳定运行态与真实浏览器筛选交互通过；快速筛选的旧响应覆盖风险和窄栏标签截断均已在本轮修复复验。
- `A-01-WORKFLOW-EFFICIENCY` 已关闭；A-01 当前守恒为有用户入口239、前端调用但无独立页面58、内部/集成/运维4、需补入口9、待废弃0、需要确认11，共321。产品候选排序不变。
