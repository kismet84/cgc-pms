# 第93条主线：项目成员 API 审计字段防篡改与同根写入边界整改

**Goal:** 以 2026-08-13 审计的 3 项发现为输入，在仓库“仅本地 dev/test/demo”边界内，阻断项目成员新增/编辑 API 对 `created_at` / `updated_at` 等服务端字段的客户端赋值路径，清理 V45 后过时的影子映射与注释，并收口已静态确认的同根写入路径。 **Architecture:** 复用现有 Spring MVC Validation、`JsonProperty.Access.READ_ONLY`、`UserContext`、`ProjectAccessChecker`、MyBatis-Plus `BaseEntity` / `MyMetaObjectHandler`、`PmProjectMemberVO`、V34/V45 迁移链和现有 H2/MySQL/浏览器验证入口；项目成员采用“独立 Create/Update Request DTO → Service 白名单赋值内部 Entity → Mapper”防护全部服务端字段，同根实体优先复用仓库已有的 `READ_ONLY` 字段防护并用参数化绑定测试封口，只在存在非审计服务端字段可达性时才增加端点 DTO/白名单。不修改已应用 Flyway migration，不增加通用映射框架、全局 Jackson 宽放/收紧、平行审计模型或生产发布链。

> 编制日期：2026-08-13
>
> 计划状态：`COMPLETED / GIT_DELIVERY_MERGED / REUSED_PUSH_CI / POST_MERGE_VERIFIED`
>
> 审计与实施基线：`master@afc8b74ff483ba8fd1955f11d31276d917727d01`；任务分支 `codex/mainline-93-audit-field-hardening`
>
> 环境边界：仅本地 dev/test/demo；不存在 prod、生产或目标环境
>
> 当前授权：用户已明确授权实施、处理阻塞、完成本地验证并执行受保护 Git 交付；不授权生产、非本地环境、Tag 或 Release
>
> 唯一整改载体：本计划的 `M93-F01～M93-F03`；不在 `current-issues.json` 制造重复 Issue

## 1. 基线、审计裁决与去重

### 1.1 证据边界

1. 引用审计锁定的远端 `master@afc8b74f` 与当前本地 HEAD 一致；其静态发现可作本计划输入，但不代替未来实施 SHA 的本地测试、MySQL、运行态或浏览器证据。
2. 编制时 `node scripts/codemap/generate-codemap.mjs --verify` 通过，输出仍显示 `generation_base_commit=e8b6c11c` 与 `input_scope_dirty=true`。地图只能回答 `project-contract-cost` 模块级关系，未单列 `PmProjectMember`、其调用方和定向测试；实施前必须在稳定工作树重生成三件套，并用源码证据补足文件级三问。
3. 仓库根规则明确禁止规划或把非本地环境验收列为阻塞。因此，本计划既不能建设生产链，也不能将本地收口宣称为“具备生产发布条件”。
4. 只读四联复核已确认：项目成员之外，合同变更、组织公司/部门/岗位、采购申请及明细、目标成本及明细、仓库的部分新增/更新路径同时满足“活动写端点可达、审计字段可反序列化、映射 `created_at/updated_at`、Service/Mapper 未清空”。本计划必须一并收口，不留同根已确认漏洞。

### 1.2 三项审计输入裁决

| ID | 原审计结论 | 当前裁决 | 本计划归属 |
| --- | --- | --- | --- |
| `M93-F01` | `AUD-20260813-P1-001`：未启用生产部署、上线验证和回滚链，发布阻塞 | 在“试图生产发布”假设下逻辑成立；但当前仓库显式 local-only，该假设不成立 | `ready_issue_config / NOT_APPLICABLE_LOCAL_ONLY`；有据关闭，不新建生产阻塞，不给出生产通过结论 |
| `M93-F02` | `AUD-20260813-P2-001`：项目成员 API 可将客户端 `createdTime` / `updatedTime` 写入审计列 | 当前源码静态可达，P2，高置信；且已确认 9 类同根实体的部分写路径 | `M1～M3`：项目成员 DTO/白名单、影子字段清理、同根字段 `READ_ONLY` 收口、参数化绑定和 DB 回读负例 |
| `M93-F03` | `AUD-20260813-P3-001`：`PmProjectMember` 注释仍称 V34 使用 `*_time`，与 V45 及当前映射相反 | 成立，P3；与 F02 的重复影子字段同根 | `M2`：以 V45 + `BaseEntity` 为当前事实，清理过时字段/注释；禁止修改 V34/V45 |

### 1.3 当前静态证据

- Controller 在 `POST /projects/{projectId}/members` 和 `PUT /projects/{projectId}/members/{id}` 直接反序列化 `PmProjectMember`：`PmProjectMemberController.java:39-49`。
- `PmProjectMember` 重新声明并映射 `createdTime -> created_at`、`updatedTime -> updated_at`，未标记 `READ_ONLY`，同时屏蔽父类字段：`PmProjectMember.java:17-25,62-78`。
- `BaseEntity.createdAt/updatedAt` 本已使用 `JsonProperty.Access.READ_ONLY` 与 MyBatis fill：`BaseEntity.java:16-32`。
- `create()` 只覆盖 tenant/project/status 后执行 `insert(member)`；`update()` 只恢复 id/tenant/project/user 后执行 `updateById(member)`：`PmProjectMemberService.java:80-130`。
- 软删除恢复 SQL 使用业务字段白名单并强制 `updated_at=CURRENT_TIMESTAMP`，不直接接收审计时间：`PmProjectMemberMapper.java:24-45`。
- V34 原建表使用 `created_time/updated_time`；V45 已将 `pm_project_member` 重命名为 `created_at/updated_at`：`migration-legacy/V34__add_project_member_and_user_org.sql:13-34`、`V45__unify_audit_columns.sql:43-45`，H2 legacy 有对应迁移。
- 现有 Controller 测试只证明 tenant/project 未从常规请求进入；Service 测试只证明 tenant/project 被覆盖，没有审计时间攻击负例或 DB 回读：`PmProjectMemberControllerValidationTest.java`、`PmProjectMemberServiceTest.java`。
- 同根四联检查的当前结果：

| 类型 | 已确认可达入口 | 最小整改边界 |
| --- | --- | --- |
| `CtContractChange` | POST + PUT；`createdTime/updatedTime`，且自行声明的 `createdBy/updatedBy/deletedFlag` 未只读 | 全部服务端审计/删除字段 `READ_ONLY` + 绑定负例 |
| `OrgCompany`、`OrgPosition` | POST + PUT | `createdTime/updatedTime` 只读 + 绑定负例 |
| `OrgDepartment` | POST；PUT 使用已加载实体白名单合并 | 审计时间只读，保留安全 PUT 服务层 |
| `MatPurchaseRequest`、`MatPurchaseRequestItem` | POST/with-items/PUT 与明细批量写入 | 主表/明细审计时间只读 + 嵌套/批量绑定负例 |
| `CostTarget`、`CostTargetItem` | 表头直接 POST；明细 draft/批量写入；表头 PUT DTO 已安全 | 审计时间只读，不改已安全 PUT DTO |
| `MatWarehouse` | POST；PUT 使用已加载实体白名单合并 | 审计时间只读，保留安全 PUT 服务层 |

`MatRequisition`、`MatRequisitionItem`、`PayInvoice` 已复用 `JsonProperty.Access.READ_ONLY`，按有据关闭，只作安全模式和参数化测试对照，不重写。

### 1.4 去重与唯一载体

1. `REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION` 已以 `FROZEN / NOT_APPLICABLE_LOCAL_ONLY / blocking=false` 保留历史语义；`M93-F01` 不重开它们。
2. 第92条已对其审计输入中的生产监控建议按 local-only 关闭；本计划沿用同一证据语义，不建第二套发布或监控载体。
3. 第89条已完成项目成员七角色合同与权限收敛，但未关闭审计时间 Mass Assignment；本计划不重做角色改造。
4. `M93-F02/F03` 与上表已确认同根路径由本计划唯一承接，不为每个类复制 Backlog Issue；已安全对照类按证据关闭。

## 2. 范围与非目标

### 2.1 范围

1. 为项目成员新增/编辑分别建立明确 Request DTO，Controller 不再接收 `PmProjectMember` 持久化实体。
2. Service 新增时构造内部实体，更新时先加载现有实体再只赋值允许的业务字段；客户端 id/tenant/project/user/audit/delete/version 不得进入持久化决策。
3. 删除 `PmProjectMember` 中 V45 后过时的 `createdTime/updatedTime` 及父类影子字段，统一使用 `BaseEntity.createdAt/updatedAt`，同步排序、VO 转换和相关注释。
4. 保持软删除恢复的原始 `created_at`，由服务端刷新 `updated_at`；保持租户、项目、用户和七角色契约。
5. 对上表已确认同根路径补齐 `READ_ONLY` 字段边界与参数化 Jackson/Spring 绑定负例；只有复核发现非审计服务端字段仍可达时，才对该端点增加 DTO/白名单。
6. 补齐 Jackson/Controller、Service、H2/MySQL 回读、恢复、权限/租户回归、前端契约和本地页面操作证据。
7. 更新 Codemap 三件套、本计划状态、计划索引和正式质量报告，完成 G0～G5 与零悬空统计。

### 2.2 非目标

- 不创建 staging/production、GitHub production Environment、部署 workflow、生产 Secret、迁移预检、真实回滚/灾备或非本地演练；不做生产发布裁决。
- 不修改已应用的 V34/V45，不新增 schema migration；只验证当前迁移后结构与 Java 映射一致。
- 不改造项目角色集、权限表达式、`ProjectAccessChecker`、租户拦截、软删除语义或项目成员前端交互。
- 不把所有业务入参一次性重写为 DTO；同根扩展只覆盖“活动写端点 + 实体直接入参 + 服务端字段可反序列化 + 存在持久化可达路径”的交集。
- 不引入 MapStruct、BeanUtils 封装、通用 Command 基类、新安全框架或全局 `FAIL_ON_UNKNOWN_PROPERTIES` 变更；项目成员 Service 使用可审查白名单，其余同根路径优先复用字段级只读边界。
- 不删除历史迁移、历史数据或他人 worktree，不重置当前开发库。
- 不执行 Tag、Release、生产发布或非本地环境操作；任务自有改动按受保护分支、同 SHA CI、PR、合并、post-merge 验真和安全分支清理交付。

## 3. 核心不变量与目标契约

### 3.1 项目成员请求边界

| 路径 | 客户端允许输入 | 只作不可变预条件 | 永不从客户端持久化 |
| --- | --- | --- | --- |
| Create | `userId`、`roleCode`、`positionName`、`startDate`、`endDate`、`status`、`remark` | 无 | `id`、`tenantId`、`projectId`、`createdBy/At`、`updatedBy/At`、`deletedFlag`、版本/内部状态 |
| Update | `roleCode`、`positionName`、`startDate`、`endDate`、`status`、`remark` | 为保持当前前端 wire contract，`userId` 可作与现有值一致的预条件，但不得被赋值 | 同 Create，且 `userId` 不可改 |

1. `CreateProjectMemberRequest` 与 `UpdateProjectMemberRequest` 分离，验证规则明确；Controller 不得在 DTO 后再通过反射或属性拷贝回填 Entity。
2. Create 由 Service 从 request + path + `UserContext` 新建 Entity；Update 对已加载 Entity 逐字段赋值，不将 request 对象直接交给 Mapper。
3. 发送 `createdTime`、`updatedTime`、`createdAt`、`updatedAt`、`createdBy`、`updatedBy`、`tenantId`、`projectId`、`deletedFlag`等字段时，无论未知字段是被忽略还是请求被拒绝，DB 均不得使用客户端值。不依赖全局 Jackson 默认保证安全。
4. `userId` 更改、路径 projectId 与实体不一致、错误 tenant、无项目权限均失败关闭；拒绝后 DB 回读不变。
5. 新增的 `created_at/updated_at/created_by/updated_by` 只由 `BaseEntity` + `MyMetaObjectHandler`/数据库契约管理；更新保持 `created_at`，服务端单调更新 `updated_at`。
6. API 响应仍为 `PmProjectMemberVO.createdAt/updatedAt`，前端合法请求和页面字段无破坏性变化。

### 3.2 恢复、迁移与同根边界

1. 软删除恢复只可更新 role/position/date/status/remark、`updated_by/updated_at`和 `deleted_flag`；原 `created_at/created_by`不变。
2. V45 是当前列名事实；无论 fresh 还是 legacy upgrade，最终只存在 `created_at/updated_at`，Java 不再为该表保留 `*_time` 影子属性。
3. 上表同根路径已同时满足四项可达条件：活动写端点直接或嵌套接收 Entity；服务端字段可被 Jackson 赋值；字段映射到 DB；Service/Mapper 未覆盖、清空或白名单保存而存在 insert/update 可达性。G0 只在当前实施 SHA 上重确认精确方法和字段，不重新降格为推测项。
4. 已有 `READ_ONLY`、服务端构造或显式白名单 SQL 的路径用证据关闭，不为“风格统一”重写。
5. 本主线不修复无法证明实际持久化可达性的推测项；但任一已证明同根路径不得以“非阻塞”无载体遗留。

## 4. G0～G5 阶段计划

### G0：稳定基线、所有权、Codemap 与可达性冻结

1. 另获实施、任务分支/隔离 worktree 授权后，记录 branch、status、HEAD/origin SHA、ahead/behind、worktree 占用和任务路径白名单；未获授权时停止。
2. 在稳定实施工作树重生成 `docs/codemap/codemap.html/json/lock`，运行 `--verify`；以地图的 `spring-api -> project-contract-cost -> mysql-flyway` 为模块边界，再以 Controller/Service/Mapper/tests 证据表回答文件级“谁调用、影响谁、哪些测试”。
3. 重取 `M93-F01～F03` 的当前 SHA 证据；确认第89/92条、REL Frozen 项和本计划不重复。
4. 在实施 SHA 重取已确认路径的“端点—JSON 字段—Service—Mapper/DB 列—测试”表，同时用固定检查式确认没有遗漏活动 Entity 入参；新命中项仍须经四联证据才纳入。
5. 锁定任务专用本地测试记录、租户/项目/用户 ID、时钟容差和精确清理方式；无需且禁止重置数据库。

**G0 通过条件：** 稳定 SHA、干净隔离工作树、任务所有权、Codemap 三问、3 项输入裁决、已确认与新命中同根路径四联表、测试数据和恢复边界全部冻结；否则保持 `G0_PENDING`，禁止业务代码写入。

### G1：输入契约、失败测试与批次锁定

1. 锁定 Create/Update DTO 字段、校验、未知字段行为、不可变字段处置和稳定错误码；保持现有前端合法 payload 可用。
2. 先建立失败契约：POST 伪造创建/更新时间，PUT 伪造创建/更新时间、user/tenant/project/delete/audit actor，恢复携带伪造时间，都必须与 DB 回读断言绑定。
3. 对每个确认同根端点建立最小攻击负例与合法请求基线，按模块分批，禁止多模块同文件并行写入。
4. 给出无迁移结论、API 兼容结论、风险和回滚点；若发现必须改 schema 或公共 wire contract，重开 G1 并另获相应授权。

**G1 通过条件：** 所有可写/服务端字段有唯一契约，确认可达路径均有先失败测试、回滚点和文件所有者，不存在无载体同根发现。

### G2：数据与迁移一致性

1. 以 fresh H2、legacy H2 V34→V45 和本地任务专用 MySQL 结构证明 `pm_project_member` 最终为 `created_at/updated_at`，无 `created_time/updated_time`；不编辑已应用 migration。
2. 为测试记录读取 create/update/restore 前快照，使用服务端时间窗口而非脆弱的精确毫秒断言；验证 `created_at` 不变、`updated_at` 单调且不等于攻击值。
3. 跨租户、错项目、错用户和无权限请求失败后，对业务字段与审计字段同时回读不变。
4. 清理仅删除任务创建的精确测试记录；不重置库、不动开发数据、不运行生产语义迁移。

**G2 通过条件：** H2/MySQL 列名、映射、新增/更新/恢复时间与租户/项目/用户不变量全部回读通过，且 migration diff 为空。

### G3：服务端白名单闭环与同根整改

1. 实现 `CreateProjectMemberRequest` / `UpdateProjectMemberRequest`，替换 Controller/Service 入参；使用显式构造/赋值，不用泛化属性拷贝。
2. 移除 `PmProjectMember` 过时时间影子字段，排序和 VO 改读 `getCreatedAt()/getUpdatedAt()`；复核 `MyMetaObjectHandler` 对其他模块的 `createdTime/updatedTime` fill 用途，不因本表清理误删共享兼容逻辑。
3. Update 只修改已加载 Entity 的业务白名单；恢复 SQL 保持字段白名单与服务端审计时间。
4. 对同根路径逐模块给服务端审计/删除字段补 `JsonProperty.Access.READ_ONLY`，复用 `PayInvoice` / `MatRequisition` 已验证模式；只有非审计服务端字段仍可达时才增加该端点 DTO/白名单。已有等价防护的类用证据关闭，不做样式性重构。
5. 运行 Controller/Service/Mapper 定向测试、模块回归和后端构建；任一合法流程、角色契约、租户过滤或恢复回归立即阻塞 G3。

**G3 通过条件：** 客户端可写边界与持久化白名单一致，所有已确认同根路径均阻断，审计、租户、项目、用户、角色和恢复契约无回归。

### G4：本地运行态、API 与浏览器验真

1. 按运行态 Skill 先核对实际 URL、listener、Docker mount、backend SHA、JDBC/Flyway 和 `/api/actuator/health`，禁止用旧 worktree/容器证据验收。
2. 在本地任务专用记录上通过页面完成新增、编辑、移除、软删除恢复与历史角色可读回归；记录目标 URL、DOM、关键网络请求和 console 0 error。
3. 用相同身份向 POST/PUT 发送伪造审计/归属字段，立即 DB 回读；验证伪造值未落库，时间线、排序和 VO 显示正确。
4. 使用无权限、错项目和跨租户身份验证失败关闭；拒绝后 DB 全字段回读不变。
5. 确认前端发出的合法 payload 与新 DTO 兼容，无 400/500、无字段丢失或页面时间格式回归。

**G4 通过条件：** 当前实施 SHA 的本地健康、真实 API、DB 回读、项目成员页面与控制台证据同时通过；不以源码、MockMvc 或构建成功代替。

### G5：全量门禁、治理与零悬空

1. 运行项目成员及所有确认同根模块的定向测试、后端完整验证、H2/MySQL 迁移/回归、前端合约/单测/类型/构建、相关 contract/live 与安全门禁。
2. 运行 Codemap generator self-test、重生成与 `--verify`，重新回答实际 diff 的调用方、影响面与测试覆盖。
3. 逐项回写 `M93-F01～F03` 和 G0 同根路径表；每个发现只能是本轮修复并复验、超出范围且正式承接、或有据关闭。
4. 更新本计划状态、`docs/plans/README.md`、`docs/quality/`正式报告和必要项目地图。只有产生跨计划、唯一且可验收的后续项时才更新 `current-issues.json`。
5. 输出本地通过/不通过、阻塞/非阻塞、依据、剩余风险、回滚条件和新增/关闭/净变化；禁止输出生产发布通过。
6. 用户已授权完整受保护 Git 交付；实现合并后以独立文档收口 PR 回写不可预知的 run/PR/merge SHA，禁止直接推送 `master`。

**G5 通过条件：** G0～G4 当前 SHA 证据齐全，全部已确认可达路径关闭，合法功能无回归，Codemap/计划/质量报告一致，零无载体遗留项。

## 5. 实施批次与文件范围

| 批次 | 责任 | 主要文件范围 | 最小验证 | 边界 |
| --- | --- | --- | --- | --- |
| `M0` | 基线、去重与可达性 | 本计划、`docs/codemap/*`、Controller/Entity/Service/Mapper/tests 只读表 | Git/worktree、Codemap generate + verify、四联证据表 | 未冻结前不改业务代码 |
| `M1` | 项目成员输入边界 | `project/controller`、新增 `project/dto`、`project/service`、定向 Controller/Service tests | DTO 序列化、白名单 captor、不可变字段负例 | 不改角色/权限契约 |
| `M2` | 映射、恢复与 DB 一致 | `PmProjectMember`、`PmProjectMemberMapper`、VO 转换、H2/MySQL 定向 tests | fresh/legacy 列名、create/update/restore 回读 | 禁止修改 V34/V45；默认无新 migration |
| `M3` | 同根写入路径 | contract/org/inventory/cost/purchase 中已确认 Entity 的字段级只读注解、过时注释和绑定 tests；只在新证据要求时改 Controller/DTO/Service | 参数化攻击绑定 + 合法序列化/模块回归 | 不做全仓 DTO 样式重构；数据库单写入者 |
| `M4` | 前端契约与本地验真 | 默认仅现有 `frontend-contracts` / projects service/page tests；只在 wire contract 必需时修改 | 合法 payload、页面流程、攻击 API、DB 回读、console | 不重设计 UI |
| `M5` | 验收与治理 | 质量报告、计划/索引、Codemap 三件套 | 全门禁、diff check、零悬空 | Git 与正式裁决由主线串行 |

实施顺序固定为 `M0 → M1 → M2 → M3 → M4 → M5`。数据库、Codemap、共享 Service/Entity 和 Git 由主线串行；如分配实现型子任务，必须在 G0 后按互不重叠的模块文件给出唯一所有权。

## 6. 验证矩阵

| 验收项 | 必须证明 | 失败判定 |
| --- | --- | --- |
| `A-01` 基线/所有权 | 实施 SHA、干净隔离工作树、路径白名单、回滚点 | 任一未知 hunk、工作树或分支占用冲突 |
| `A-02` Codemap 三问 | 地图三件套当前且 verify；项目成员与确认同根模块的调用/影响/测试有证据 | 沿用粒度不足或 stale 地图就修改代码 |
| `A-03` 输入边界 | 项目成员 Controller 只接收明确 DTO；同根 Entity 的服务端字段为 `READ_ONLY`；客户端无法把任一服务端字段交给 Mapper | 项目成员仍直接入参 Entity、同根字段仍可绑定、泛化属性拷贝或服务端字段可达 DB |
| `A-04` Create 审计完整性 | 攻击时间/人/租户/项目/id 未落库，服务端创建值在合理时间窗口 | 任一客户端服务端字段落库 |
| `A-05` Update 不变量 | `created_at`、user/tenant/project/id/delete 不变，`updated_at` 服务端单调，业务白名单正常更新 | 直接 `updateById(requestEntity)` 或拒绝后数据变化 |
| `A-06` Restore | 原创建审计保留，更新审计和 `deleted_flag` 由服务端处理 | 恢复接受攻击字段或破坏原时间线 |
| `A-07` 迁移/映射 | fresh/legacy H2 与 MySQL 最终列名为 `*_at`，Entity/VO/排序一致，migration diff 为空 | 编辑 V34/V45、新增无必要 migration 或仍保留误导影子字段 |
| `A-08` 权限/租户/角色 | 无权限、跨租户、错项目/用户拒绝；七角色与历史一次性兼容无回归 | 前端隐藏代替后端验证，或合法用户失权 |
| `A-09` 同根复核 | 已确认路径全部修复复验；新命中项有四联裁决；已安全对照类无多余重写 | 只修项目成员而留下已证明同根路径，或为无可达性类做全仓重构 |
| `A-10` 运行态/浏览器 | 当前 SHA、实际 JDBC/Flyway、真实 API、页面 DOM/交互/网络、console 0 error | 只有单测/构建，或浏览器连到旧容器/工作树 |
| `A-11` 零悬空 | 3 项审计输入 + 全部已确认/新命中同根路径有唯一裁决；新增/关闭/净变化可复算 | 任一已确认问题无载体或以“非阻塞”遗留 |

实施时最小测试集：

- 扩展 `PmProjectMemberControllerValidationTest` 的 POST/PUT 攻击 JSON 与 DTO captor，扩展 `PmProjectMemberServiceTest` 的白名单和恢复契约。
- 新增一个参数化 Jackson/Spring 绑定测试，覆盖 `PmProjectMember` + 9 类同根实体的服务端字段，并以 `MatRequisition/Item`、`PayInvoice` 作安全对照；断言输入被忽略、输出仍可读。
- 回归 `ProjectMemberServiceTest`、`CtContractChangeControllerTest/ServiceTest`、`CostTargetControllerTest`、`MatWarehouseControllerTest`、`OrgDepartmentControllerTest`、`MatPurchaseRequestControllerTest`；组织公司/岗位由参数化测试补齐直接边界证据。
- 在 `BaselineFlywayCompatibilityTest` 与 gated `BaselineMySqlUpgradeTest` 验证 `created_at/updated_at` 存在、`created_time/updated_time` 不存在，但不新增或修改 migration。
- 回归前端 `m3-project-object`、`m3-project-request-baseline` 及项目成员相关 contract/live 流程，再执行 G4 真实 API/DOM 验收。

建议实施验证入口在 G0 按当前 Maven/Node/Compose 能力和实际文件锁定。计划阶段至少运行：

```powershell
pwsh -NoProfile -File scripts/codex-autopilot/test-mainline-owner-flow.ps1 `
  -PlanPath "docs/plans/第93条主线-项目成员API审计字段防篡改与同根写入边界整改任务计划书.md" `
  -Profile HighRisk
Get-Content -Raw docs/backlog/current-issues.json | ConvertFrom-Json | Out-Null
git diff --check
```

上述命令只验证计划、JSON 与差异静态契约；不是业务修复、数据库、运行态或浏览器验收证据。编制前 Codemap `--verify` 已通过；新计划改变 documentation 指纹后，三件套重生成写入留待实施授权，G0 必须执行 `node scripts/codemap/generate-codemap.mjs` 及 `--verify`。

## 7. 失败分类

| 分类 | 示例 | 动作 |
| --- | --- | --- |
| `tool_config` | PowerShell/Maven/Node/Compose 命令、引号或 profile 调用错误 | 仅修正一次调用或切权威备用入口；不判产品缺陷 |
| `environment_prerequisite` | Docker/backend/frontend 未运行、端口占用、实际 mount/JDBC 不是任务基线 | 停止 G4，先恢复本地前置并重新核对服务链 |
| `test_or_fixture` | 攻击 JSON 未经真实 Jackson、时间断言过窄、测试数据冲突 | 修复任务夹具/时间窗口，不放宽审计不变量 |
| `product_bug` | 攻击值落库、`created_at` 被更改、错 tenant/project/user 更新成功、合法请求回归 | 阻塞当前门禁，回到 DTO/Service/Mapper 根因修复后重跑 |
| `quality_or_security` | 项目成员仍暴露 Entity、同根服务端字段仍可绑定、用全局 ignore/反射拷贝代替明确边界、删除负例求通过 | 阻塞 G3/G5，恢复最小可审查边界并独立只读复核 |
| `ready_issue_config` | 把 `M93-F01` 重开为生产阻塞、计划/授权/载体冲突、候选未证就扩大范围 | 先修治理和证据表，不修业务代码 |
| `external_dependency` | 审计要求 staging/production、真实 Secret、非本地回滚演练 | 按 `NOT_APPLICABLE_LOCAL_ONLY` 关闭；只有用户先明确修改根环境规则才重新立项 |

## 8. 风险、金丝雀与恢复

### 8.1 主要风险

- DTO 分离可能改变 Update 对 `userId`、null/空字符串和日期的兼容行为，导致当前前端合法请求 400。
- 移除影子字段后，方法引用排序、VO 时间转换、MyBatis fill 或序列化名称可能回归。
- 软删除恢复若改用错误 request/entity，可能覆盖原创建审计或破坏唯一约束。
- 已确认同根路径横跨多模块；全部改 DTO 会无必要扩大回归面，只修项目成员又会留下已证明风险，因此同根批次固定为字段级只读收口优先。
- 时钟分辨率、DB `CURRENT_TIMESTAMP` 与 JVM 时间的差异可能造成脆弱测试；必须使用窗口/不等于攻击值/单调性断言。
- 共享 `master` 与多个历史 worktree 并存；实施时若未隔离，可能混入其他主线或让浏览器连到旧 mount。

### 8.2 金丝雀

1. 先只处理 `PmProjectMember` 一个端点族：DTO 序列化→Service 白名单→H2/DB 回读→页面新增/编辑；全部通过后才处理同根确认批次。
2. 同根批次按模块一次一个，每批先用参数化测试跑一个攻击负例和一个合法序列化，再扩到模块回归；禁止一次替换所有 Entity 入参。
3. 本地运行态先用任务专用项目/用户记录跑一次新增、一次编辑和一次攻击请求，回读守恒后才跑完整页面/回归。

### 8.3 恢复矩阵

| 失败点 | 立即动作 | 恢复方式 | 重开条件 |
| --- | --- | --- | --- |
| 基线/所有权不清 | 停在 G0，不改代码 | 逐文件/hunk 核对 HEAD、worktree 和任务归属 | 稳定 SHA、路径白名单和回滚点齐全 |
| DTO/wire 兼容失败 | 停止后续模块 | 恢复上一批 Controller/DTO/Service，保留攻击测试 | 合法前端 payload 与攻击负例同时通过 |
| 审计值落库 | 保留证据，删除任务攻击记录 | 回到白名单赋值或 Mapper SQL；不用前端隐藏修补 | create/update/restore DB 回读全部通过 |
| 映射/fill 回归 | 停止 G3/G4 | 恢复上一批 Entity/VO 修改，不编辑 migration | H2/MySQL 列名、fill、排序、VO 复验通过 |
| 同根范围无法冻结 | 不进入 G1 | 已证明路径继续在本计划分批；新命中项用四联条件证明或关闭 | 已确认与新命中路径均有可复算证据和唯一归属 |
| 运行态服务链漂移 | 停止浏览器验收 | 核对 URL、listener、mount、JDBC/Flyway，只刷新已证明的本地容器 | 实际服务与实施 SHA/数据源一致 |
| Codemap 漂移 | 不使用旧地图裁决 | 在任务工作树重生成三件套并 verify | generator test、`--verify` 与文件级三问同时通过 |

所有回滚只撤销本主线任务自有文件和精确测试记录；无 schema 回滚，禁止整树 reset、覆盖共享工作区、删除他人 worktree 或清理未证明归属的数据。

## 9. 最终验收标准

1. `M93-F01` 以 `NOT_APPLICABLE_LOCAL_ONLY` 有据关闭，未新建任何生产/目标环境操作或阻塞，也未输出“生产可发布”。
2. 项目成员 POST/PUT Controller 不再接收持久化实体，Create/Update DTO 仅表达客户端契约，Service 只白名单赋值。
3. POST 携带任意审计/归属字段时，DB 的 create/update audit 均由服务端决定；PUT 不改变原 `created_at`，伪造 `updated_at` 不落库。
4. PUT 不能篡改 id/tenant/project/user/delete/audit actor；跨租户、错项目、错用户和无权限拒绝后 DB 回读不变。
5. 软删除恢复保留原创建审计，刷新服务端更新审计，七角色和历史角色可读契约不变。
6. `PmProjectMember` 只使用 V45 后的 `BaseEntity.createdAt/updatedAt`，无过时 `createdTime/updatedTime` 影子映射或误导注释；V34/V45 内容不变。
7. 合同变更、组织、采购申请/明细、目标成本/明细和仓库的已确认路径均已修复复验；新命中路径有四联证据，不可达项已有据关闭。
8. 项目成员及所有实际受影响模块的 Controller/Service/H2/MySQL/前端契约/构建回归通过，不存在 skip/flaky 冒充通过。
9. 本地实际 URL、backend SHA、JDBC/Flyway、真实 API、页面 DOM/交互/网络和 console 证据绑定同一实施 SHA。
10. Codemap 三件套重生成/verify 通过，能以地图 + 源码补充证据回答实际 diff 的调用方、影响面和测试覆盖。
11. G0～G5、质量报告、风险/恢复、计划索引和零悬空统计完整；无无载体遗留项。

## 10. 实施状态、授权与收口

- `G0 PASS`：基线、任务分支、所有权、Code map 与 `M93-F01～F03` 去重完成；`current-issues.json` 无重复载体。
- `G1 PASS`：13 类审计字段绑定契约先建立失败基线，初始 10 类可写、3 类安全对照；另冻结 9 组非审计服务端字段契约。
- `G2 PASS`：fresh/legacy H2 8/0；任务专用 MySQL 1/0，最终 Flyway V293；10 张目标表均为 `created_at/updated_at`，无 `created_time/updated_time`，未修改迁移。
- `G3 PASS`：项目成员与合同变更改为 Create/Update DTO + Service 白名单；组织、采购、成本、仓库同根实体补齐只读边界及服务端覆盖。实施中发现的成员目标用户归属、采购明细路径字段、合同事项登记一致性和仓库状态绕过均本轮修复。
- `G4 PASS`：当前本地源挂载 backend 健康为 `UP`；真实成员页完成新增、编辑、移除、恢复及 POST/PUT 恶意字段回读，console 0 error，任务数据已精确清理。实际 UI 库 Flyway V296 高于源码 V293，属于既有本地数据迁移漂移；目标表列、角色数据、当前 API 与页面行为已逐项兼容验证，不作为同基线证据。
- `G5 PASS`：后端 `clean test` 2773/0（28 skip）；覆盖率阈值、前端 build、570/0 unit、98/0 browser contract、Lint 0 error、共享契约、bundle、clean-room、route ledger、设计系统、SQL 安全、工作流合同与 HighRisk 计划门禁均通过；正式报告、Code map 重生成/verify 已完成。
- 独立只读复核：当前实现树 P0～P2 为 0；代表性高风险路径已有 HTTP + DB 持久化攻击证据，通用九实体以参数化 Jackson 契约封口。
- 3 项审计输入裁决：`M93-F01` 有据关闭 1；`M93-F02/F03` 本轮修复并复验 2；正式承接 0，未收口 0。
- 后续项统计：新增后续项 0、关闭后续项 0、后续项净变化 `0`；无无载体遗留项，`current-issues.json` 不改。
- Git 授权：允许任务自有改动 commit、push、受保护 PR/合并、post-merge 验真及已合并源分支清理；不允许直接推送 `master`、强推、绕过保护、Tag、Release 或生产发布。
- 实现源 HEAD `c1eb9c0eb4465996c8706b57944b05512c38ea02` 的 push CI run `31663379551` 完整通过，pre-PR exact-SHA verifier 为 `PASS`。
- 实现 PR #441 的 PR run `31664096721` 成功复用同 SHA push 证据；受保护 squash 合并 SHA 为 `4ed199de9a97e609f919b7f3a871c5b2cf2247e3`。
- Post-merge run `31664155763` 通过；verifier 为 `PASS / REUSED_PUSH_CI`，源树与合并树同为 `cdda49e1ea769e0b6b28a6f0d425ffde918464af`。
- 独立收口分支首次 push CI run `31664340025` 唯一失败项为 `reliability-contracts`：MySQL/MinIO 备份均已完成，`backup-batch.sh` 在 `pipefail` 下用 `head` 截断多行 `mc --version` 时发生 SIGPIPE；4096 行探针将旧管道稳定复现为退出码 141。该项分类 `quality_or_security`，已改为完整采集后由 shell 内建提取首行，并以原子性契约、运行部署契约和真实恢复演练复验；原失败 run 保留。
- 收口源 HEAD `1055afcac33b477806ab8e932e326023b6b32520` 的 push CI run `31665797312` 与 PR #442 run `31666503147` 全绿；pre-PR verifier 为 `PASS`。PR #442 受保护 squash 合并为 `6e8dfafb8b7d3b64443694f7b034703678d0d6b0`，post-merge run `31667213820` 与 verifier 均通过；模式 `REUSED_PUSH_CI`，源树与合并树同为 `421f5e8a3de9bb9dd2a54359d1ab2bc9b962abab`。
- 交付中新发现 1 项，已本轮修复并复验；正式承接 0、未收口 0，后续项净变化仍为 `0`。
- 当前结论：`LOCAL_G0-G5_PASS / GIT_DELIVERY_MERGED / REUSED_PUSH_CI / POST_MERGE_VERIFIED`；实现与交付期直接发现均已修复复验，无无载体遗留项；不扩展为生产、Tag 或 Release 裁决。
