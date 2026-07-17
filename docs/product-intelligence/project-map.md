# CGC-PMS 项目地图

## 2026-07-17 增量：供应商综合履约现状复核完成

- `ISSUE-048-004` 已以当前代码、自动化、一次性 MySQL 8.4 V1→V210 和真实只读页面证明供应商综合履约闭环成立，A-05 的“只有交付维度”属于过时描述并已关闭。
- 现有闭环覆盖交付、验收质量、质量安全、人工服务、确认退货、已定案结算扣款、综合评价、确认锁定、黑名单异人审核和招采准入阻断；前端按招采、评价、黑名单权限分离动作。
- 人工服务分和内部商业规则不等同于外部客观评级；本项未修改公式、权重、权限、业务代码、迁移或业务数据。共享开发库迁移历史漂移已独立承接为 `OPS-DEV-MYSQL-FLYWAY-DRIFT`，不再误记为产品能力缺失。

## 2026-07-17 增量：采购前已审批在途余量提示

- `PI-2026-07-17-03` 已由 `ISSUE-048-003` 交付：库存补货判断增加同租户、同项目、同物料的已审批采购订单未收货余量，只读展示订单号、预计交付日期和剩余数量。
- 口径固定为订单与审批状态均为 `APPROVED`，单订单汇总 `max(采购数量－已收货数量, 0)`；结果不预占、不入库、不替代验收和库存写侧校验。
- 后端68项、前端6项、类型与目标 ESLint 通过；隔离开发 MySQL 的错误边界和真实库存页面空态通过，页面初始化控制台错误为0，未伪造业务数据。
- 本切片不实现工作日历、统计需求预测、真实调拨、自动采购、历史回填或迁移；A-02 聚合父项继续承接这些剩余能力。

## 2026-07-17 增量：采购补货前跨仓余量提示

- 当前库存余额已按租户、仓库和物料维护，启用仓库具备项目归属，安全库存可作为不可调拨下限；低库存页现有补货入口会直接预填采购申请。
- 已交付的最小切片只展示同项目其他启用仓库同物料的正可调拨余量，计算为可用量扣除来源仓安全库存；范围由服务端从当前仓库反查，不接受客户端项目范围作为权威输入。
- 本切片不实施调拨写入、跨项目资产转移、在途、预占、成本结转或自动采购。A-02 的真实调拨过账、工作日历和需求预测继续保留为后续独立决策。
- 后端64项、前端6项与真实页面空态通过；当前开发 MySQL 无库存余额，未为验收伪造业务数据。

## 2026-07-17 增量：分包付款来源业务单据选择器

- 分包进度款的 `SUB_MEASURE` 与终期款的 `SETTLEMENT` 已从手工来源 ID 改为当前租户、项目、合同、付款对象和合法付款路径内的业务单据选择器；候选显示单号和服务端可申请余额。
- 余额口径分别为计量净额扣除审批中/已审批占用，以及结算定案额扣除已付和审批中/已审批占用；DRAFT 不占用，非正余额不展示，写侧继续以加锁后的权威来源校验裁决。
- 后端48项、前端17项、真实 MySQL 和真实页面只取消验收通过；无迁移、无历史数据或财务事实修改。分包 P1 的审批结构化审定、终期超付退款/应收和合同清单版本失效仍是 Candidate，未被本切片代替。

## 2026-07-17 增量：WBS单前置FS现有闭环证据收口

- 分包任务现有模型继续限定为可空的单个 `predecessor_task_id`：前后置任务必须属于同一项目，计划日期不得倒挂，循环依赖和仍被引用的前置删除会被服务端拒绝。
- 未完成前置时，后续任务不能直接进入 `IN_PROGRESS` 或 `COMPLETED`；完成前置或显式解除前置后恢复独立流转。前端同步展示未完成/迟交风险，并保持门禁错误只提示一次。
- 后端14项、前端11项和真实浏览器只取消验收通过；`OBS-WBS-PREDECESSOR-EVIDENCE` 已关闭。本结论不外推为多前置、SS/FF/SF、lag、自动排程、基线、关键路径、甘特拖拽或资源平衡，A-04聚合父项继续保留这些平台化缺口。

## 2026-07-17 增量：数据库模型完整性与冗余治理 V210

- 数据基线由 V180/V194 升级到 MySQL/H2 V210：181 张业务表、3193 个基础表物理字段、24 个视图字段（合计 3217）、991 个独立字段名、366 个外键列、2 个共享治理视图，Flyway 失败记录 0。
- 权限与正确性边界：RBAC 关系、用户岗位、退料来源、银行回单—回款均升级为 tenant-aware 或显式权威关系；活动软删除唯一键、成本幂等、审批路由、材料分类、JSON 合法性和双预警中心完成治理。
- 采购/库存闭环新增 WBS/预算、质量处置、供应商退货和退料冲销；供应商退货统一复用 `sp_supplier_return` 头表并增加 `sp_supplier_return_item`，未保留重复头表；收入资金闭环支持银行回单到回款的事务内唯一转换和双向 Trace。
- 工程资产新增数据库设计规范、部署 preflight/postflight、发布回滚手册、自动结构字典和 ER；完整验收见 `docs/quality/database-model-remediation-acceptance.md`。
- V210 精确退役 14 个已经 V195/V131 替代的 `deleted_token`，保留 15 个现行 `active_unique_token`；没有合并语义不同的同名字段。
- 生产边界：历史 `overhead_allocation_record` 本地 0 行且无仓库运行时引用，但物理删除等待生产外部消费者确认，由 `ISSUE-049-001` 唯一承接。

## 2026-07-17 增量：项目预警与通知 P0 闭环

- 预警在既有规则、订阅和渠道能力上增加首次阅读、唯一接单责任人、响应/处置双 SLA、固定两级超时升级、分离的处理/归档操作人、乐观锁和不可变生命周期事件，禁止 `updated_by` 继续冒充处理责任人。
- 状态机固定为 `OPEN → PROCESSED → ARCHIVED` 或 `OPEN → INVALID`；处理必须由接单责任人完成，状态说明必填，终态不可修改，处理和归档通知以目标状态区分幂等键。
- 规则评估、现金日记账、审批通知失败和项目进度延期四类生产源统一写入 CREATED 事件；响应超时自动 L1 通知项目管理层，处置超时自动 L2 通知租户管理员，重复扫描保持幂等。
- Trace 可返回项目/合同/业务来源、生命周期、升级、渠道发送记录和站内信；规则效果复盘按规则统计命中、SLA内响应、升级、处置、归档和通知失败。P0 不宣称消息队列重试中心、值班排班、可配置升级矩阵、第三方渠道回执、外部工单或 AI 研判。

## 2026-07-17 增量：项目资金计划与现金预测 P0 闭环

- 新建资金预测版本、日级预测行和资金缺口措施三类持久化事实，直接复用既有收款计划、付款计划和现金日记账，禁止再建第二套应收、应付或实际收付台账。
- 预测按期初余额与日级计划收付连续计算余额和缺口；缺口必须由已提交措施覆盖，措施申请/审批及预测编制/审批均强制职责分离，批准的新版本才会使同场景旧版失效。
- 已批准预测可从现金日记账刷新实际收付与偏差，并滚动生成关联上期的新版本；Trace 可反查计划来源、措施、现金流水及带 SHA-256 摘要的审计事件。
- P0 不包含集团资金中心、多币种、授信计息、银企自动调拨、概率模拟和 AI 预测；旧 `cash_forecast` 手工行保留兼容但不是本闭环审批或版本事实。

## 2026-07-17 增量：项目竣工与收尾 P0 闭环

- 新建收尾主档、分部分项验收、竣工验收、质保责任、缺陷责任和档案移交六类持久化事实，串联计划 WBS、质量检查、业主最终结算、应收、回款分配、合同和工作流。
- 竣工验收使用真实三级审批；最终结算直接复用收入回款闭环，尾款和质保金不仅要求应收余额为零，还必须存在足额成功回款分配，禁止人工清零冒充回款。
- 质保责任绑定最终结算的 RETENTION 应收，缺陷整改与复验强制职责分离；质保到期、缺陷关闭、质保金回收和凭证齐备后才能释放，档案签收后才进入 `READY_TO_CLOSE`。
- 通用项目状态接口不再允许直接转 `CLOSED`；收尾服务在合同结清、应收清零、缺陷关闭、档案接收且无运行中审批时写入实际竣工日并关闭项目。P0 不包含电子签章、档案馆接口、BIM竣工模型、保函或项目后评价。

## 2026-07-17 增量：图纸、RFI 与技术方案 P0 闭环

- 新建技术方案、图纸版本、会审、RFI、设计回复、技术交底、施工引用和验收归档八类持久化事实，串联项目计划/现场日报 WBS 实绩、质量检查和工作流审批。
- 技术方案使用真实两级审批；设计回复接受与回复登记强制职责分离；改图回复只能生成同图纸链新版本，新版批准后上一版自动 `SUPERSEDED`，来源 RFI 自动关闭。
- 技术交底只允许使用当前批准版本且全部 RFI 已闭合；施工引用精确核验已提交日报、日期、WBS 和日报进度事实；验收归档必须绑定已提交且通过的质量检查和阶段化附件。
- `tech_item` 仅作为自动同步的驾驶舱摘要，领域表和 Trace 才是业务事实。P0 不包含 BIM/IFC、在线批注、设计门户、电子签章、材料报审、移动离线或 AI 审图。

## 2026-07-17 增量：供应商招采与履约评价 P0 闭环

- 新建招采事件、邀请供应商、报价、比价评审、供应商退货、履约评价和黑名单审批七类持久化事实，串联已审批采购需求、采购合同、采购订单、到货验收、终结结算和质量安全评价。
- 发布要求至少三家启用非黑名单供应商和需求附件，评审要求至少两份带原始附件的有效报价；定标、合同关联、退货确认、评价确认和黑名单审核均采用状态门禁与审计留痕。
- 明确 `sp_supplier_return` 是向供应商退货的唯一事实，既有 `mat_material_return` 继续表示领料退库；履约评分只汇总已审批验收、已确认退货、终结结算扣减和同期质量安全事实。
- 黑名单必须由低分已确认评价发起并由非申请人审核；通过后服务端立即阻断新采购合同和采购订单提交。P0 不包含供应商门户、重新招标、黑名单解除/申诉、电子招投标和集团级供应商共享。

## 2026-07-17 增量：质量安全整改 P0 闭环

- 新建质量/安全检查计划、检查记录、问题单、整改复验轮次、处罚成本后果及合作方评价六类持久化事实；所有节点绑定租户与项目，跨节点外键使用 RESTRICT，历史不因上游删除而丢失。
- 检查、问题、整改和复验采用四类阶段化证据；提交时必须存在病毒扫描 CLEAN 的附件，提交后文件授权器禁止事后替换。问题支持内部或供应商/分包商责任、期限、严重等级、多轮驳回重整和整改/复验职责分离。
- 外部责任问题关闭后可确认处罚、返工成本和履约评分；返工成本按质量安全后果来源幂等写入 `cost_item`，合作方评价作为后续招采履约评价闭环的稳定来源，问题 Trace 可反查计划、检查、全部整改轮次、成本与评价。
- P0 不直接冲抵分包/采购结算应付，不宣称移动离线巡检、AI 图像识别、IoT、BIM 定位或综合供应商评级；罚款结算核销、检查模板和逾期预警进入后续优先级评估。

## 2026-07-16 增量：目标成本与动态利润 P0 闭环

- 目标成本由单一目标金额升级为投标成本、目标成本、责任预算三算矩阵，生效版本必须经过多级审批，项目同一时刻只允许一个 ACTIVE 版本，责任科目与责任人均有服务端门禁。
- 完工预测按生效目标和成本事实冻结承诺成本、实际成本、预计剩余与 EAC 快照；系统重算成本偏差、主合同动态利润和利润率，确认后只允许新版本替代，不允许改写历史。
- 正偏差必须建立纠偏措施并完成审批与关闭，未闭合时禁止确认下一期预测；成本汇总绑定最新确认预测，Trace 可反查目标责任、成本来源、纠偏与审批证据。
- P0 不包含财务关账、资金预测、采购招标、BIM 量算、概率模拟或驾驶舱整体改版；这些能力不得绕过当前版本、金额和来源约束。

## 2026-07-16 增量：项目计划与施工履约 P0 闭环

- 项目计划现已从分包 `sub_task` 独立为版本化 `project_schedule_plan + project_wbs_task`，基线与修订均经多级审批，项目同一时刻只允许一个 ACTIVE 版本；WBS 父子/前置引用、日期边界、无环和权重 100% 均有服务端门禁。
- 生效计划可逐级建立月计划和所属周计划；现场日报只允许填报当日已审批周计划内 WBS，提交事务内推进累计实绩、生成 `SCHEDULE_PROGRESS_V1` 偏差快照，并按 `PROJECT_PROGRESS_DELAY` 阈值产生来源可追溯的预警。
- 延期/逾期快照可发起纠偏审批；审批通过自动复制当前计划与已确认实绩为 REVISION 草稿，修订再次审批后替换旧版本。项目计划工作台、日报实绩、审批入口、权限菜单、审计和全链 Trace 已接入，正常全链及权重、月周门禁、进度回退异常由集成测试覆盖。
- P0 不宣称关键路径、工作日历、多前置类型、资源平衡、BIM 4D 或外部 P6/MS Project 集成；这些能力按业务价值进入 P1—P3，不得回退本次计划版本与日报来源约束。

## 2026-07-16 增量：分包履约、终期结算与付款P0闭环

- 分包计量提交已强制绑定ACTIVE项目、履约中分包合同、合同乙方、可计量任务、期次、日期、合同清单明细和CLEAN附件；数量、单价、累计量和金额由服务端按合同清单校验。
- 计量审批成本改按净计量额比例分摊并保持分币守恒；终期结算提交冻结逐笔计量金额快照，新单采用“已审批计量+已确认签证-终期扣款”的V2口径，合同当前额只作履约上限。
- 统一付款来源新增SUB_MEASURE进度款路径，与SETTLEMENT终期款路径共同执行额度占用、重复付款、终结算锁定、冲销恢复；付款追溯可返回计量、任务和终结算快照。
- 边界仍明确：P0不含劳务实名制、工资代发、银行直联、电子签章或分包商门户；历史V1已定案结算不自动覆盖。

## 2026-07-16 增量：采购—验收—库存—领料—成本 P0 核心代码闭环

- 采购申请与订单明细已强制关联项目生效预算科目；申请提交占用、驳回/撤回释放、订单审批执行预算，并保留申请明细到订单明细的逐行来源。
- 订单补齐交付条件、税价、附件及例外采购门禁；验收补齐不合格处置与退供，领料审批和仓库实发分离，退料/退供均引用原事实并反向冲销库存、成本或预算。
- 通用手工出入库已 fail-close，验收、领料、退供、退料与统一 Trace 形成正反向业务主线；后端全量 1875 项和前端全量 647 项测试通过。
- 该结论只代表 P0 核心代码与自动化回归完成；历史数据治理、生产数据副本 MySQL 迁移、真实角色/租户 E2E、并发压测、驾驶舱口径和库存调整审批仍按唯一业务标准的上线门禁执行，当前不判定可生产上线。

## 2026-07-16 增量：站内通知隔离、幂等与通知铃证据

- 站内通知分页、未读计数、单条/全部已读均以租户+用户双维约束；跨边界返回不可见，重复单条已读幂等，SSE使用 `tenantId:userId` 复合键隔离同用户不同租户连接。
- 前端通知铃按需加载列表和未读数，打开时建立SSE；单条或全部已读只在用户明确点击时发生，失败反馈契约已有测试。
- `OBS-NOTIFICATION-INAPP-ISOLATION` 已关闭；这不代表邮件、短信、企业微信、钉钉、失败重试队列或外部真实渠道已经打通。

## 2026-07-16 增量：现金日记账同步CSV安全导出证据

- 现金日记账同步CSV会检查单元格首个非空白字符；命中 `=`、`+`、`-`、`@` 时在原始值前添加单引号，再执行双引号转义和字段包裹，避免电子表格公式求值并保留原始可读内容。
- 导出复用认证租户查询，Controller要求专用导出权限或ADMIN/SUPER_ADMIN，保留下载审计，并返回带BOM的UTF-8 `text/csv`附件。
- `OBS-CASHBOOK-CSV-SAFETY` 已关闭；这只证明当前同步导出，不代表异步任务、对象存储、外部报表平台或完整经营分析已形成。

## 2026-07-16 增量：供应商交付评分项目与空值边界证据

- 采购驾驶舱现有评分只覆盖可见项目采购订单的交付维度：已审批到货明细用于判断完成日，并区分迟交完成与逾期未完成；无订单供应商、无供应商订单和跨项目供应商不合成评分。
- 排序固定为交期表现评分降序、供应商名称、供应商ID；前端保持列表、空态、缺失字段占位和带既有 `partnerId` 筛选的采购订单钻取。
- `OBS-SUPPLIER-SCORE-BOUNDARIES` 已关闭；质量、价格、退货、服务、黑名单与综合评级仍属于A-05后续产品决策，不在本项能力范围。

## 2026-07-16 增量：日报人工天气摘要与在场人数契约证据

- 现场日报已具备可空人工天气摘要（最多200字）和可空在场总人数（0～100000整数），前后端维护、详情与负向边界在当前master通过。
- 本项只证明人工摘要与总人数，不代表自动天气、人员班组、设备、考勤、定位、离线或统计能力；`OBS-SITE-DAILY-WEATHER-HEADCOUNT` 已关闭。

## 2026-07-16 增量：日报Controller测试JWT环境隔离修复完成

- 日报Controller测试现在通过类级专用强密钥隔离外部 `TEST_JWT_SECRET`；JwtUtils强密钥校验、共享profile、生产配置和业务代码均未改变。
- 同一3项测试连续两轮通过，`AUTOPILOT-SITE-DAILY-JWT-ISOLATION` 关闭，日报天气/人数回归恢复。

## 2026-07-16 增量：日报Controller测试JWT环境隔离阻塞

- 日报天气/人数回归首轮尚未进入业务断言，Spring因外部144-bit `TEST_JWT_SECRET` 在JwtUtils构造阶段拒绝启动；这是测试环境隔离缺口，不改变日报能力事实。
- 已拆 `ISSUE-047-003`，只在目标测试类固定专用强密钥；共享local/test profile、生产JwtUtils、业务代码和用户环境变量保持不变。

## 2026-07-16 候选：跨域既有能力证据收口

- A-03已存在人工天气摘要与在场总人数，A-05已存在供应商交付评分，A-06已有现金日记账同步CSV安全导出，A-07已有站内通知，A-04已有单前置FS门禁；这些能力均有自动化测试但缺少当前master独立正式报告。
- 已登记 `OBS-SITE-DAILY-WEATHER-HEADCOUNT`、`OBS-SUPPLIER-SCORE-BOUNDARIES`、`OBS-CASHBOOK-CSV-SAFETY`、`OBS-NOTIFICATION-INAPP-ISOLATION`、`OBS-WBS-PREDECESSOR-EVIDENCE`，并拆为 `ISSUE-040-052`～`ISSUE-040-056` 回归证明。
- 本批只锁定现有契约与未实现边界，不新增自动天气、综合供应商评分、异步报表、外部通知渠道、多前置或自动排程。

## 2026-07-16 增量：供应商默认提前期与订单交货日期预填

- 合作方现已为 SUPPLIER 提供可空0～3650整数自然日默认提前期，API 与页面覆盖创建、更新、读取、清空、旧客户端省略字段和非供应商兼容边界。
- 新建采购订单复用采购合同乙方，在订单日期与供应商默认值齐备且用户未人工覆盖时预填交货日期；NULL、0、跨月、闰日和编辑既有订单边界均有测试证据。
- MySQL/H2 V155、连续180秒本地运行态与真实浏览器只取消不写入已通过；`A-02-SUPPLIER-DEFAULT-LEAD-DAYS` 关闭，A-02 剩余工作日历、需求预测和跨仓调拨，不扩供应商×物料、价格或自动下单。

## 2026-07-16 增量：V155 双数据库迁移镜像与测试隔离

- MySQL 与 H2 迁移目录现已同步具备 V155，均只为 `md_partner` 新增可空整数 `default_lead_days`；双方言文件内容与 SHA-256 一致。
- 迁移完整性、版本唯一性和真实 H2 Flyway 合作方服务回归连续两轮通过；当前测试 schema 不再依赖单类临时 DDL，供应商默认提前期产品任务可继续实施。
- `AUTOPILOT-FLYWAY-V155-H2-MIRROR` 已关闭；本项只修复迁移/测试隔离，不代表合作方字段与采购订单日期预填已经交付。

## 2026-07-16 已完成候选：供应商默认提前期与订单日期链

- 当前合作方 `md_partner` 没有提前期字段；采购订单通过采购合同乙方取得供应商，已有订单日期和交货日期，但交货日期完全人工维护。
- 库存项 `replenishmentLeadDays` 已服务仓库+物料补货申请，不能复用为供应商承诺；本候选新增可空供应商默认自然日，只在新建订单预填且允许人工覆盖。
- `PI-2026-07-16-01` 已由 `ISSUE-040-051` 完成；不扩供应商×物料、价格、工作日历、预测、自动下单或历史回填。

## 2026-07-16 增量：现场日报领料联动浏览器证据

- 现场日报详情的既有领料联动已在桌面1280×720与真实480×800窄视口完成视觉回归：140字施工内容可滚动读取，领料标题、数据表与关闭操作均可达。
- 有命中时仅展示领料单号、物料、数量、单位、使用部位；无命中时展示“当日暂无已审批且已出库领料”专用空态，未暴露价格、金额、合同、供应商或“已安装”语义。
- `OBS-DAILY-LOG-BROWSER` 已关闭；本项只补现有能力证据，不新增人员、设备、天气、定位、离线、统计能力或生产数据。

## 2026-07-16 增量：WBS前置门禁错误单次提示

- 前端请求层现在用不可枚举元数据标记已向用户提示的拒绝错误，覆盖业务码、非401错误和401刷新失败；WBS 页面只对未提示异常保留一次本地兜底。
- 请求层与 WBS 组件行为回归共17项通过，精确证明门禁错误总提示1次、普通异常兜底1次；真实浏览器同时确认前置未完成时仅1处页面门禁说明且开工/完成选项不可用。
- `OBS-WBS-DUPLICATE-ERROR` 已关闭；本项不扩展全站错误框架、不改变后端状态机、多前置模型或其他页面 catch 策略。

## 2026-07-16 增量：PowerShell 7控制面运行证据

- 本地 AutoPilot 已在 PowerShell 7.6.3 下通过控制面、连续 runner、状态机与 UTF-8 context delta 回归；pwsh 7+ 强制宿主、原子状态、fencing、恢复和收口语义均保持有效。
- UTF-8 中文上下文往返及无 BOM 输出专项通过；本轮只补正式运行证据，没有改动 PowerShell、配置或插件。
- `OBS-POWERSHELL7-COMPAT` 已关闭；结论限定为当前 Windows/pwsh 7.6.3 与仓库控制面，不外推到其他操作系统或预览版本。

## 2026-07-16 增量：WBS软删除墓碑事务原子性

- 分包任务删除现已具备故障注入证据：墓碑编号更新成功后若逻辑删除步骤异常，Spring 事务会把 task_code 与 deleted_flag 一起回滚，不遗留半完成墓碑。
- 正常删除继续使用 `DELETED-<taskId>` 唯一命名空间并释放原业务编号；连续两次 local H2 复验证明成功与失败路径均稳定。
- `OBS-WBS-TOMBSTONE-FAULT` 已关闭；本项只证明既有事务原子性，不扩展多前置、自动排程、物理删除或全局墓碑框架。

## 2026-07-16 增量：库存阈值与出库真实并发边界

- 库存补货设置现在具备真实 H2 事务级并发证据：阈值更新与出库从同一版本起步时，乐观锁拒绝一次旧出库快照，出库复读并重试后同时保留余额70与阈值60。
- 两次稳定复验均只生成一条出库流水，最终版本为2；专用测试夹具定向清理，未改变生产乐观锁、重试次数、库存规则或数据模型。
- `OBS-STOCK-CONCURRENCY` 已关闭；本项属于既有能力回归证明，不宣称新增跨仓、预测、工作日历或分布式库存并发平台。

## 2026-07-16 增量：补货设置负向边界与测试隔离

- 已完成的库存补货设置现在具备独立的只读权限拒绝、跨项目拒绝和模拟乐观锁冲突回归证据，三类失败均证明补货三字段不产生持久化变化。
- Controller 测试改为自带足够强度的测试 JWT 密钥及事务内专用夹具，可由 CI 单独选择方法执行；Service 与 Mockito 专项分别隔离数据库和持久化替身状态。
- 本轮仅强化既有能力证明与测试隔离，不新增补货业务能力或修改生产逻辑；`OBS-REPLENISHMENT-NEGATIVE-TESTS` 已关闭。

## 2026-07-16 增量：驾驶舱性能回归证据一致性

- 驾驶舱批量查询性能测试的类说明、步骤注释、显示名、失败信息和成功输出已统一采用 `≤20`，与两处真实 `count <= 20` 断言一致。
- 本轮只修复测试证据表达，不改变 DashboardService、SQL 数或生产行为；专项2项通过，约42次 N+1 对照基线仍保留。
- `OBS-DASHBOARD-PERF-LABEL` 已关闭，正式验收不再出现“输出称≤10、实际门槛≤20”的证据歧义。

## 2026-07-16 增量：间接费规则受控删除入口

- 成本列表的间接费规则表格已增加受 `overhead:delete` 或管理员控制的删除动作，二次确认绑定科目、依据与历史执行事实保护说明。
- 后端删除先隐藏跨租户/不存在规则，再检查认证租户的执行 run；已有执行事实时 fail-close，无引用时沿用软删除，不级联清理历史分摊。
- 自动化与连续180秒稳定运行态通过；dev 空规则数据下按禁止造数边界完成浏览器空态取消验收，DELETE 计数保持0。`A-01-OVERHEAD-DELETE` 已关闭，A-01 当前守恒为有用户入口248、前端调用无独立页面58、内部/集成/运维4、需补入口0、待废弃0、需要确认11，共321。

## 2026-07-16 增量：间接费规则受控修改入口

- 成本列表的间接费规则表格已增加受 `overhead:edit` 或管理员控制的修改入口，预填并只提交 costSubjectId、allocationBasis 和 allocationCycle。
- 后端 PUT 已改用白名单 DTO；Service 隐藏跨租户规则、验证当前租户启用的 OVERHEAD/COST 科目，并保持服务端既有状态，客户端 id、tenantId、status 和审计字段不能改变身份或状态。
- 自动化与连续180秒稳定运行态通过；dev 空规则数据下按禁止造数边界完成浏览器等价取消验收，PUT 计数保持0。`A-01-OVERHEAD-UPDATE` 已关闭，A-01 当前守恒为有用户入口247、前端调用无独立页面58、内部/集成/运维4、需补入口1、待废弃0、需要确认11，共321。

## 2026-07-16 增量：间接费规则受控新建入口

- 成本列表的间接费规则弹窗已增加受 `overhead:add` 或管理员控制的新建表单，科目候选只显示启用的 OVERHEAD 科目，提交只包含 costSubjectId、allocationBasis 和 allocationCycle。
- 后端 POST 已改用白名单 DTO；Service 固定认证租户和 ENABLE，并验证当前租户启用的 OVERHEAD/COST 科目，客户端 id、tenantId、status 和审计字段不能进入规则实体。
- 自动化、超过180秒稳定运行态与只取消不写入浏览器闭环通过；`A-01-OVERHEAD-CREATE` 已关闭，A-01 当前守恒为有用户入口246、前端调用但无独立页面58、内部/集成/运维4、需补入口2、待废弃0、需要确认11，共321。

## 2026-07-16 增量：投标成本标记未中标入口

- 既有投标成本页已为 BIDDING 记录提供桌面和移动“标记未中标”动作；入口复用独立 `bid:status`，二次确认展示投标名称与 BID_COST 费用核销后果，取消不请求、成功重新读取列表、失败不伪改状态。
- 前端精确调用 `PUT /bid-cost/{id}/lost` 且不发送 params、body、tenantId 或其他业务字段；后端沿用认证租户隐藏和仅 BIDDING 可转换的事务状态机，只将目标投标的 BID_COST 费用改为 WRITE_OFF，重复操作 fail-close。
- 自动化、超过180秒稳定运行态与只取消不写入的浏览器闭环通过；`A-01-BID-LOST` 已关闭，A-01 当前守恒为有用户入口245、前端调用但无独立页面58、内部/集成/运维4、需补入口3、待废弃0、需要确认11，共321。投标域明确叶子已清空，下一切片切换间接费规则域。

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
| MySQL Flyway migration | 189 |

## 业务能力地图

| 业务域 | 前端证据 | 后端证据 | 测试入口示例 | 状态 | 当前缺口 |
| --- | --- | --- | --- | --- | --- |
| 项目与成员 | `pages/project/`、`api/modules/project.ts` | `project/` | `PmProjectControllerTest`、`ProjectOverviewServiceTest`、`ProjectLedgerProduction.test.ts` | Partial | v1.5 真实角色、项目数据范围和运行态待复验 |
| 项目计划与施工履约 | `pages/project-schedule/`、`pages/site-daily/` | `schedule/`、`site/` | `ProjectScheduleClosedLoopIntegrationTest` | Complete(P0) | 版本化基线/月周计划、日报实绩、偏差预警、纠偏修订和 Trace 已闭环；关键路径、工作日历、多前置类型和外部计划软件属 P1+ |
| 合同与付款条件 | `pages/contract/`、`api/modules/contract.ts` | `contract/` | `CtContractServiceTest`、`ContractApprovalIntegrationTest`、`ContractLedgerPage.test.ts` | Partial | 合同履约、金额口径和审批联动需当前复验 |
| 变更、签证与索赔 | `pages/variation/`、`api/modules/variation.ts` | `variation/`、`contract/change/` | `VarOrderServiceTest`、`VariationClaimClosedLoopIntegrationTest`、`VariationOrderProduction.test.ts` | P0 Closed | 已贯通事件证据、双口径、内部审批、业主版本/核定、正式合同变更及下游计量追溯；P1 待增强下游明细来源展示 |
| 成本与目标成本 | `pages/cost/`、`pages/cost-target/` | `cost/`、`revenue/`、`overhead/`、`accounting/` | `TargetCostDynamicProfitClosedLoopIntegrationTest`、`CostSummaryServiceTest`、`CostLedgerServiceTest` | Complete(P0) | 三算矩阵、责任预算、EAC、动态利润、纠偏措施和 Trace 已闭环；概率模拟、BIM量算和集团成本库属 P1+ |
| 质量安全整改 | `pages/quality-safety/` | `quality/` | `QualitySafetyClosedLoopIntegrationTest`、`quality-safety/index.test.ts` | Complete(P0) | 计划、检查、问题、整改、复验、处罚成本与合作方评价已闭环；移动巡检、AI识别、IoT/BIM 和结算核销属 P1+ |
| 采购与采购申请 | `pages/purchase/`、`pages/inventory/purchase-request.vue` | `purchase/` | `MatPurchaseOrderServiceTest`、`PurchaseRequestServiceTest`、`purchase/order.test.ts` | Partial | 已完成安全阈值、人工补货目标量和自然日提前期预填；供应商级提前期、工作日历和预测仍缺失 |
| 供应商招采与履约评价 | `pages/supplier-sourcing/`、`api/modules/supplierSourcing.ts` | `supplier/` | `SupplierSourcingClosedLoopIntegrationTest`、`supplierSourcing.test.ts` | Complete(P0) | 邀请、报价、评审、定标、合同、到货、退货、结算、履约评价和黑名单阻断已闭环；供应商门户、申诉解除和集团共享属 P1+ |
| 收货、仓库与库存 | `pages/receipt/`、`pages/inventory/` | `receipt/`、`inventory/` | `MatReceiptServiceTest`、`MatStockServiceTest`、`stock-production.test.ts` | Partial | 已维护安全阈值并联动 KPI/预警；目标量、全量建议、预测和跨仓调拨仍缺 |
| 领料 | `pages/requisition/` | `requisition/` | `MatRequisitionServiceTest`、`useRequisitionForm.test.ts` | Partial | 与计划需用量、施工部位和损耗分析尚未闭环 |
| 分包与计量 | `pages/subcontract/` | `subcontract/` | `SubMeasureServiceTest`、`SubTaskControllerTest`、`subcontract/measure.test.ts` | Partial | 已完成单前置 FS、状态门禁、延期风险和软删除编号冲突修复；仍无多前置、多类型、自动排程和完整履约档案 |
| 结算 | `pages/settlement/` | `settlement/` | `StlSettlementServiceTest`、`StlSettlementControllerMockMvcTest`、`settlement/index.test.ts` | Partial | 合同、变更、计量、付款汇总需当前一致性复验 |
| 付款与资金日记账 | `pages/payment/`、`pages/cash-journal/` | `payment/`、`accounting/` | `PaymentFinancialConsistencyTest`、`PayRecordCashJournalIntegrationTest`、`payment/save-chain.test.ts` | Partial | 金额、财务回写、附件和权限需当前复验 |
| 发票与识别 | `pages/invoice/` | `invoice/` | `InvoiceServiceTest`、`InvoiceRecognitionTest`、`invoice-pdf.test.ts` | Partial | 识别可靠性、付款关联和文件安全需当前复验 |
| 图纸、RFI 与技术方案 | `pages/technical-management/`、`api/modules/technicalManagement.ts` | `tech/` | `TechnicalManagementClosedLoopIntegrationTest`、`technical-management/index.test.ts` | Complete(P0) | 方案、图纸版本、会审、RFI、设计回复、交底、施工引用、验收归档和 Trace 已闭环；BIM/IFC、在线批注、设计门户和 AI 审图属 P1+ |
| 项目竣工与收尾 | `pages/project-closeout/`、`api/modules/projectCloseout.ts` | `closeout/` | `ProjectCloseoutClosedLoopIntegrationTest`、`project-closeout/index.test.ts` | Complete(P0) | 分项/竣工验收、最终结算、尾款质保金、缺陷、档案移交及项目关闭门禁已闭环；电子签章、档案馆接口和项目后评价属 P1+ |
| 财务核算与月结 | `pages/accounting-entry/`、`pages/financial-close/` | `accounting/`、`financeclose/` | `FinancialAccountingMonthEndClosedLoopIntegrationTest`、`financial-close/index.test.ts` | Complete(P0) | 凭证复核、过账、冲销、银行/子账对账、月结检查、关账/反结账和 Trace 已闭环；多账簿、多币种、合并和税务属 P1+ |
| 项目资金计划与现金预测 | `pages/cash-forecast/`、`api/modules/cashForecast.ts` | `cashforecast/` | `ProjectCashForecastClosedLoopIntegrationTest`、`cash-forecast/index.test.ts` | Complete(P0) | 预测版本、计划收付、缺口措施、审批、实际回写、滚动预测和 Trace 已闭环；集团资金、多币种、授信计息和概率预测属 P1+ |
| 审批、抄送与通知 | `pages/approval/` | `workflow/`、`notification/` | `WorkflowCoreServiceTest`、`ApproverResolverTenantIntegrationTest`、`ApprovalWorkList.test.ts` | Partial | 真实角色矩阵、跨业务状态一致性待复验 |
| 预警 | `pages/alert/` | `alert/` | `AlertEvaluationServiceTest`、`AlertControllerTest`、`AlertNotificationDispatcherTest`、`alert/index.test.ts` | Complete(P0) | 阅读、唯一接单、响应/处置双 SLA、固定两级升级、规则效果复盘、通知证据和 Trace 已闭环；失败重试、可配置升级矩阵与第三方回执属 P1+ |
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
| 总工程师 | 技术方案、图纸版本、会审、RFI、设计回复、交底和验收归档 | Complete(P0) | BIM/IFC、在线批注、外部设计门户和 AI 审图仍属后续能力 |

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
- 现有长期计划中所有“已完成”项在 v1.5 的复验状态。
# 2026-07-15 增量：工作流个人效率统计入口

- `ISSUE-040-034` 在既有审批工作台分析栏复用已实现的个人效率接口，按当前认证 userId、tenantId 与页面筛选展示待办、逾期待办、已办、已处理任务和平均处理分钟数；前端不提交身份或租户参数。
- 后端 32 项、前端 13 项、类型检查、目标 ESLint、差异检查、180 秒稳定运行态与真实浏览器筛选交互通过；快速筛选的旧响应覆盖风险和窄栏标签截断均已在本轮修复复验。
- `A-01-WORKFLOW-EFFICIENCY` 已关闭；A-01 当前守恒为有用户入口239、前端调用但无独立页面58、内部/集成/运维4、需补入口9、待废弃0、需要确认11，共321。产品候选排序不变。
## 2026-07-17 增量：财务核算与月结 P0 闭环

- 会计凭证新增待复核、通过、驳回状态以及制单/复核职责分离；凭证只有复核通过且会计期间可写时才能过账，冲销改为生成可复核、可过账、与原凭证互链的反向调整凭证。
- 新增会计期间、月结检查、应收应付对账和银行对账事实；检查覆盖未过账、借贷不平、成功收付款缺凭证、银行回单差异和 AR/AP 子账差异，任一异常均阻断月结。
- 银行回单按方向强约束 `OUT→PAY_RECORD`、`IN→COLLECTION_RECORD`，同时校验金额并关联现金日记账；月结后期间写保护，反结账必须留原因并重新检查/结账。
- 财务工作台提供期间生命周期、检查、对账、试算平衡、应收应付、现金流和审计 Trace。P0 不包含多账簿、多币种、合并报表、税务申报、固定资产和银行直连。
