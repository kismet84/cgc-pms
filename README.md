# 建筑工程总包项目全过程管理系统 (CGC-PMS)

Construction General Contracting Project Management System

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia + VxeTable + ECharts |
| 后端 | Java 21 + Spring Boot 3.3 + MyBatis-Plus 3.5 + JWT (jjwt 0.12) |
| 数据库 | MySQL 8.0 + Flyway (H2 内存库用于本地无 MySQL 场景) |
| 缓存 | Redis 7 |
| 文件存储 | MinIO |
| 部署 | Docker + Docker Compose |

## 项目结构

```
cgc-pms/
├── backend/              # Spring Boot 后端 (301+ 源文件)
│   ├── auth/             #   JWT 鉴权 + 登录
│   ├── system/           #   用户/角色/菜单管理
│   ├── project/          #   项目管理 CRUD + 成员
│   ├── partner/          #   合作方管理 CRUD
│   ├── contract/         #   合同台账 + 新建/编辑 + 清单 + 付款条件 + 变更
│   ├── workflow/         #   审批引擎 (提交/同意/驳回/撤回/转办/加签)
│   ├── file/             #   文件上传 (MinIO 存储 + 预签名 URL)
│   ├── inventory/        #   仓库管理 + 库存台账 + 出入库
│   ├── invoice/          #   发票管理 (创建/登记/核验)
│   ├── notification/     #   站内消息 + SSE 实时推送
│   ├── alert/            #   八类预警规则 + 批处理
│   ├── org/              #   组织架构 (公司/部门/岗位)
│   ├── material/         #   物料字典
│   ├── cost/             #   成本科目 + 台账 + 归集 + 汇总
│   ├── payment/          #   付款申请 + 回写
│   ├── purchase/         #   采购申请 + 订单
│   ├── settlement/       #   结算管理
│   └── common/           #   统一响应/异常处理/分页/操作日志
├── frontend-admin/       # Vue 3 管理后台 (70+ 源文件)
│   └── src/
│       ├── components/   #   共享组件 (StepWizard / ContractItemEditor / PaymentTermEditor)
│       ├── stores/       #   Pinia 状态管理 (user / contract / notification)
│       └── pages/
│           ├── login/        #   登录页
│           ├── dashboard/    #   首页
│           ├── contract/     #   合同台账 + 新建合同 + 合同详情
│           ├── project/      #   项目列表页
│           ├── partner/      #   合作方列表页
│           ├── approval/     #   我的待办 + 审批详情
│           ├── inventory/    #   仓库管理 + 库存台账 + 出入库 + 采购申请
│           ├── invoice/      #   发票列表 + 核验
│           ├── notification/ #   消息中心 + SSE 实时通知
│           ├── org/          #   组织架构 (公司/部门/岗位)
│           └── alert/        #   预警列表 + 批量评估
├── mobile/               # uni-app 移动端（预留）
├── database/             # Flyway 迁移脚本 (V1~V40)
├── deploy/               # Docker Compose (MySQL + Redis + MinIO) + .env.example
├── doc/                  # 开发文档 + Backlog + 测试报告 + 审计修复报告
├── scripts/              # 辅助脚本
└── README.md
```

## 快速启动

### 前置条件

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 编译后端 (mvnw.cmd 会自动检测 `D:\projects-test\jdk-21`) |
| Node.js | 20+ | 前端开发 |
| pnpm | 9+ | 前端包管理 |
| MySQL | 8.0 | 数据库（或使用 H2 跳过） |

### 1. 数据库

**方式 A：Docker Compose（推荐）**

```bash
# 先复制环境变量模板并填入真实值
cd deploy
copy .env.example .env
# 编辑 .env 填入安全密码

docker compose up -d
# MySQL: localhost:3306
# Redis: localhost:6379（已启用密码认证）
# MinIO: http://localhost:9001
```

**方式 B：本地 MySQL**

确保 MySQL 8.0 已运行，创建数据库和用户：

```sql
CREATE DATABASE cgc_pms DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'cgc'@'localhost' IDENTIFIED BY 'cgc123';
GRANT ALL ON cgc_pms.* TO 'cgc'@'localhost';
```

**方式 C：H2 内存库（无需 MySQL）**

使用 `local` profile 自动启动 H2：

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. 启动后端

```bash
cd backend
# MySQL 环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 无 MySQL 环境（H2）
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Swagger 文档：`http://localhost:8080/api/swagger-ui.html`

默认管理员：`admin` / `admin123`

> **一键启动**：Windows 下可直接运行 `scripts\start-dev.bat`，自动启动 Docker → 后端 → 前端，并自动释放 8080 端口（防止旧进程残留导致端口占用）。

### 3. 启动前端

```bash
cd frontend-admin
pnpm install
pnpm dev
```

管理后台：`http://localhost:5173`

## 开发进度

| 阶段 | 周期 | 状态 | 内容 |
|------|------|------|------|
| 第 0 阶段 | 启动 | ✅ | 仓库、Docker Compose、README、代码规范 |
| 第 1 周 | 骨架 | ✅ | Spring Boot / Vue3 脚手架、Flyway、登录鉴权、RBAC、合同台账页 |
| 第 2 周 | 审批+基础数据 | ✅ | 审批引擎 POC、项目/合作方 CRUD、审批页面、11 集成测试 |
| 第 3 周 | 合同中心 | ✅ | 新建合同、合同清单、付款条件、附件上传 (2026-06-10) |
| 安全加固 | — | ✅ | 方法级RBAC、Refresh Token、文件上传安全、输入校验、密钥脱敏、幂等修复 (2026-06-11) |
| 第 4 周 | 审批闭环 | ✅ | 合同提交审批、审批回调、状态机、锁定成本生成 (2026-06-11) |
| 安全加固 2 | — | ✅ | 多租户数据隔离、子资源归属校验、文件 IDOR 修复、N+1 优化、前端缺陷修复 (2026-06-11) |
| 第 5-8 周 | 成本归集 | ✅ | 采购/验收/分包/计量/付款/签证 六条业务链路, 成本自动归集, 资金闭环 (2026-06-11) |
| 审计修复 3 | — | ✅ | 14 项遗留问题修复：合同余额校验/悲观锁/边界测试/M2/M3/Rule2/两阶段校验/实体统一/冗余flag/nvl/常量 (2026-06-11) |
| 第3阶段 | 成本分析与合同深化 | ✅ | 目标成本/合同变更/结算/动态成本/驾驶舱/预警/技术债修复 (2026-06-12) |
| 第4阶段 | 库存与发票 | ✅ | 仓库/库存台账/出入库/采购申请、发票创建/登记/核验、消息中心 SSE、组织架构 (2026-06-12) |
| 审计修复 4 | — | ✅ | 6 项审查问题修复：PayInvoice 字段映射/H2 schema 漂移/测试 profile 统一/SSE 异常处理/权限码对齐/测试 UserContext (2026-06-12) |
| P3 中期改进 | — | ✅ | H2 Flyway 自动生成 / 通知 bizId 统一 / 测试隔离强化 (2026-06-12) |
| 通知路由修复 | — | ✅ | 通知认证过滤链修复 / SSE 请求 JWT 鉴权 / 匿名端点白名单 (2026-06-13) |

### 已完成功能

```
✅ 登录鉴权 (JWT + BCrypt + RBAC + Refresh Token + Redis 黑名单)
✅ 方法级权限控制 (@PreAuthorize，10 个 Controller 全覆盖)
✅ 输入校验 (@Valid + Jakarta Validation，7 个实体类)
✅ 用户管理 / 角色管理 / 菜单管理（RBAC 权限码已定义到菜单）
✅ 项目管理 CRUD（分页/新建/编辑/详情/级联删除）
✅ 合作方管理 CRUD（分页/新建/编辑/黑名单标识/防误删）
✅ 合同台账查询（8 维筛选 + 详情 + 项目/合作方关联，N+1 已优化为批量查询）
✅ 合同中心
   ├── 新建合同（4 步分步表单：基本信息→清单→付款条件→提交审核）
   ├── 合同清单 CRUD（批量保存，自动金额计算）
   ├── 付款条件 CRUD（批量保存，比例合计校验）
   ├── 合同详情页（3 标签：清单/付款条件/审批记录）
   └── 自动生成合同编号 CT-yyyyMMdd-XXX
✅ 文件上传系统
   ├── MinIO 对象存储 + 预签名 URL（7 天有效）
   ├── 通用业务附件关联（businessType + businessId 模式）
   ├── 文件大小限制 (50MB) + 扩展名白名单（20 种）
   ├── businessType 路径注入防护
   └── sys_file 表 (V7 Flyway 迁移)
✅ 审批引擎 POC
   ├── 提交审批 / 我的待办 / 审批详情
   ├── 同意 / 驳回 / 撤回 / 重新提交
   ├── 转办 / 加签 / 会签 (COUNTERSIGN) / 或签 (OR_SIGN)
   ├── 乐观锁 (taskVersion) + 幂等 (idempotencyKey，已修复 TOCTOU 竞态)
   └── availableActions 动态权限
✅ 合同审批闭环 (Week 4)
   ├── 合同提交审批 (POST /contracts/{id}/submit, DRAFT→APPROVING)
   ├── 审批中合同编辑守卫 (APPROVING 状态禁止编辑)
   ├── ContractWorkflowHandler (isCritical=true, 审批事务一致性)
   ├── 审批记录查询 (GET /contracts/{id}/approval-records, Timeline时间轴)
   ├── 合同锁定成本生成 (CostGenerationService, 审批通过→自动生成cost_item)
   ├── 成本幂等机制 (uk_cost_source_item 唯一键 + DuplicateKeyException 兜底)
   ├── 状态枚举后端统一 (DRAFT/PERFORMING/SETTLED/TERMINATED + DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN)
   └── 前后端枚举对齐 (ContractStatusTag/ApprovalStatusTag 共享组件)
✅ 前端：登录页 / 首页 / 合同台账 / 合同新建 / 合同详情 / 项目列表 / 合作方列表 / 待办列表 / 审批详情
✅ 前端：API 错误不再静默回退 Mock 数据，统一弹窗提示
✅ 前端：401 响应自动静默刷新 token（request.ts 拦截器）
✅ Flyway 数据库迁移 (V1~V40：系统表/业务表/审批表/字典/演示数据/文件表/排序索引/成本归集/目标成本/变更/结算/驾驶舱/预警/组织架构/库存/发票/消息/抄送/权限补全)
✅ 集成测试 11 用例全部通过 (H2 + MySQL 双环境)
✅ CORS 配置支持多环境（dev/test/local/prod 各自配置 allowed-origins）
✅ OperationLog 切面敏感字段脱敏（password/token/secret 自动替换为 ***）
✅ Docker Compose 密钥使用 .env 文件 + Redis 密码认证 + MinIO 版本锁定
✅ 多租户数据隔离 (tenantId 全链路过滤, Contract/Project/Partner/File 模块)
✅ 子资源归属校验 (合同清单/付款条款按 contractId 校验)
✅ 文件访问权限 (预签名URL/删除按 tenantId 鉴权)
✅ Refresh Token 禁用账号拦截 (loginById 增加状态校验)
✅ 审批实例查看权限 (仅发起人/审批人可查看)
✅ 前端 N+1 优化 (用户角色 / 审批任务批量预取)
✅ 前端缺陷修复 (API 路径对齐, 401 刷新队列修复, Promise.await, JSON 容错)
✅ 采购订单 CRUD + 审批闭环 (PURCHASE_ORDER, PurchaseOrderWorkflowHandler, Flyway V13)
✅ 物料字典 CRUD (md_material, 启用/禁用)
✅ 成本科目树形管理 (cost_subject, 递归树, level 自动计算, 唯一码校验)
✅ 材料验收 CRUD + 审批 + 自动成本生成 (MAT_RECEIPT, MaterialReceiptWorkflowHandler)
✅ 分包任务 CRUD (sub_task, 编号自动生成 SUB-yyyyMMdd-XXX, 进度条)
✅ 分包计量 CRUD + 审批 + 自动成本生成 (SUB_MEASURE, SubMeasureWorkflowHandler)
✅ 成本台账 (cost_ledger, 来源追溯 + 科目/项目/类型筛选)
✅ 通用 CostGenerationService 策略模式 (4 种 source_type: CT_CONTRACT/MAT_RECEIPT/SUB_MEASURE/VAR_ORDER, Spring 自动注册)
✅ 动态成本汇总 (cost_summary, project_id+subject_id 维度, 定时刷新)
✅ 付款申请 CRUD + 金额校验 (PAY_REQUEST, PayApplicationWorkflowHandler, basis 批量保存)
✅ 财务回写 pay_record + 合同联动 + cost_summary 联动 (PayRecordWorkflowHandler)
✅ 签证变更 CRUD + 审批 + 成本调整 (VAR_ORDER, COST direction 过滤)
✅ 全链路集成测试 (7 tests, 采购→验收→成本→付款→回写, H2 local profile)
✅ Flyway 迁移 (V12~V17 业务表, V19 修复, V20 ct_contract.cost_generated_flag, 共 8 个迁移脚本)
✅ Phase 2 审计修复 (14 项)
   ├── 采购订单合同余额校验 (PurchaseOrderWorkflowHandler)
   ├── 悲观锁 + 两阶段校验 (SELECT FOR UPDATE, submit+approve)
   ├── 付款校验增强 (M2 重复依据, M3 合同匹配, Rule 2 付款比例)
   ├── 边界测试 (恰好等于余额/超余额1分, 7→9 PASS)
   ├── 架构清理 (双实体统一, 冗余flag移除, CtContract.costGeneratedFlag)
   ├── 代码规范 (nvl() 提取公共工具, businessType 常量统一)
   └── V20 Flyway 迁移 (ct_contract.cost_generated_flag)
✅ Phase 3 成本分析与合同深化
   ├── 目标成本管理 (cost_target 多版本 + 审批闭环, Flyway V21~V22)
   ├── 合同变更 CT_CHANGE (更新 currentAmount + 成本联动 + 审批闭环, V23~V24)
   ├── 结算管理 (总包/分包/采购结算, 纯只读汇总 + 不可变锁定, V25~V26)
   ├── 动态成本公式修正 + 利润测算 + backfill 接口 (V27)
   ├── 五角色经营驾驶舱 (ECharts 图表下钻, V28)
   ├── 八类预警规则 + @Scheduled 批处理 (V29)
   ├── 技术债修复 (H2 审批权限 @PreAuthorize + M4 HttpOnly Cookie + P18 vite 6.x 升级)
   ├── Flyway V21~V31 共 11 个迁移脚本
    ├── Phase3IntegrationTest 6/6 通过 (H2)
    └── MySQL 8.0 全栈验证通过
✅ Phase 4 库存与发票
    ├── 仓库管理 CRUD（mat_warehouse，编号 + 状态管理，V35 Flyway）
    ├── 库存台账（mat_stock 余额 + mat_stock_txn 流水，乐观锁 @Version）
    ├── 出入库操作（入库/出库，自动更新余额 + 生成流水）
    ├── 采购申请 CRUD + 审批闭环（PURCHASE_REQUEST，三级顺序审批，V35+V39）
    ├── 发票管理 CRUD（pay_invoice，创建/编辑/删除/核验，V36）
    ├── 发票登记（关联 pay_record + 防重唯一键）
    ├── 消息中心（站内通知 + SSE 实时推送，V37）
    ├── 组织架构（公司/部门/岗位三级树，V33）
    ├── 项目成员管理（pm_project_member，V34）
    ├── 抄送功能（wf_cc 表 + SSE 推送，V38）
    ├── 预警中心（八类规则 + @Scheduled 批处理 + 手动批量评估，V29）
    ├── 权限种子补全（V39 菜单 + 权限码 + 审批模板，V40 修复对齐）
    ├── Flyway V33~V40 共 8 个迁移脚本
    └── 全量测试 162/162 通过（H2 + MySQL 双环境）
✅ 审计修复 4 (2026-06-12)
    ├── P0: PayInvoice created_time/updated_time 字段映射对齐
    ├── P1: 测试 profile 统一为 H2 local
    ├── P1: H2 schema.sql 补 sys_user.org_id
    ├── P1: NotificationService SSE 异常处理增强
    ├── P1: 权限种子与 @PreAuthorize 码对齐 (V40)
    └── P2: TestUserContext helper + roleCodes 补充
✅ P3 中期改进 (2026-06-12)
    ├── H2 schema Flyway 自动生成 (V1~V40 migration-h2, 消除手工维护漂移)
    ├── 通知 bizId 语义统一 (TRANSFER/ADD_SIGN 改为 instance ID)
    └── 测试数据隔离强化 (BID_FIRST/LAST 常量 + cleanup 文档化)
```

## 合同中心 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/contracts` | GET | 合同台账分页查询（8 维筛选，N+1 已优化） |
| `/contracts` | POST | 新建合同（自动生成编号） |
| `/contracts/{id}` | GET | 合同详情 |
| `/contracts/{id}` | PUT | 编辑合同 |
| `/contracts/{id}/items` | GET | 合同清单列表 |
| `/contracts/{id}/items` | POST | 新增单条清单 |
| `/contracts/{id}/items/batch` | POST | 批量保存清单 |
| `/contracts/{id}/items/{itemId}` | PUT/DELETE | 编辑/删除清单 |
| `/contracts/{id}/payment-terms` | GET | 付款条件列表 |
| `/contracts/{id}/payment-terms` | POST | 新增单条付款条件 |
| `/contracts/{id}/payment-terms/batch` | POST | 批量保存付款条件 |
| `/contracts/{id}/payment-terms/{termId}` | PUT/DELETE | 编辑/删除付款条件 |
| `/contracts/{id}/submit` | POST | 提交审批 |
| `/contracts/{id}/approval-records` | GET | 审批记录 |

## 文件上传 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/files/upload` | POST | 上传文件（≤50MB，扩展名白名单） |
| `/files/{id}/url` | GET | 获取预签名下载 URL（7 天有效） |
| `/files` | GET | 按业务类型查询文件列表 |
| `/files/{id}` | DELETE | 删除文件 |

## 审批引擎 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/workflow/submit` | POST | 提交审批 |
| `/workflow/tasks/todo` | GET | 我的待办 |
| `/workflow/instances/{id}` | GET | 审批详情 (含 availableActions) |
| `/workflow/tasks/{id}/approve` | POST | 同意 |
| `/workflow/tasks/{id}/reject` | POST | 驳回 |
| `/workflow/instances/{id}/withdraw` | POST | 撤回 |
| `/workflow/instances/{id}/resubmit` | POST | 重新提交 |
| `/workflow/tasks/{id}/transfer` | POST | 转办 |
| `/workflow/tasks/{id}/add-sign` | POST | 加签 |

## 认证 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/auth/login` | POST | 登录（返回 access + refresh token） |
| `/auth/userinfo` | GET | 获取当前用户信息（含角色/权限） |
| `/auth/refresh` | POST | Token 轮换（refresh token 换新 access token，旧 refresh token 自动失效） |
| `/auth/logout` | POST | 退出（access token 加入 Redis 黑名单） |

## 库存管理 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/inventory/warehouses` | GET | 仓库分页查询 |
| `/api/inventory/warehouses/{id}` | GET | 仓库详情 |
| `/api/inventory/warehouses` | POST | 新建仓库 |
| `/api/inventory/warehouses/{id}` | PUT | 编辑仓库 |
| `/api/inventory/warehouses/{id}/status` | PUT | 仓库启用/禁用 |
| `/api/inventory/stock/ledger` | GET | 库存台账（余额+流水） |
| `/api/inventory/stock/in` | POST | 入库 |
| `/api/inventory/stock/out` | POST | 出库 |

## 发票管理 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/invoices` | GET | 发票分页查询 |
| `/api/invoices/{id}` | GET | 发票详情 |
| `/api/invoices` | POST | 创建发票 |
| `/api/invoices/{id}` | PUT | 编辑发票 |
| `/api/invoices/{id}` | DELETE | 删除发票 |
| `/api/invoices/{id}/verify` | PUT | 核验发票 |
| `/api/invoices/register` | POST | 登记发票（关联付款记录） |

## 消息通知 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/notifications` | GET | 通知分页查询 |
| `/api/notifications/unread-count` | GET | 未读数量 |
| `/api/notifications/{id}/read` | PUT | 标记单条已读 |
| `/api/notifications/read-all` | PUT | 全部已读 |
| `/api/notifications/stream` | GET | SSE 实时推送流 |

## 预警 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/alerts` | GET | 预警分页查询 |
| `/alerts/{id}/read` | PUT | 标记已读 |
| `/alerts/batch-evaluate` | POST | 手动批量评估 |

## 组织架构 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/org/companies` | GET/POST | 公司列表/新建 |
| `/api/org/departments` | GET/POST | 部门列表/新建 |
| `/api/org/positions` | GET/POST | 岗位列表/新建 |

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*`
- 提交信息：`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`
- 代码规范：ESLint + Prettier (前端)
- 后端遵循 `SysUserController` → `SysUserService` 分层模式，权限使用 `@PreAuthorize("hasRole('ADMIN') or hasAuthority('xxx:action')")` 声明式鉴权
- 多租户数据隔离：Service 层 LambdaQueryWrapper 统一追加 `.eq(Entity::getTenantId, UserContext.getCurrentTenantId())`，单条操作 `selectById` 后校验 `tenantId` 一致性
- 敏感配置使用 `${ENV_VAR:default}` 形式（环境变量优先），切勿将密钥硬编码提交
- 迁移数据使用 `INSERT IGNORE INTO` 确保幂等，新表索引在专用 V8+ 迁移中添加
- `WorkflowBusinessHandler.isCritical()` 控制审批回调事务一致性：返回 `true` 时异常传播触发回滚，`false` 时 swallow-and-log（向后兼容）

## 已知暂缓问题

（无 — 此前 2 个 H2 暂缓用例已于 2026-06-12 修复，全量 162/162 通过）

## 文档

| 文档 | 路径 |
|------|------|
| 开发任务拆解与 Backlog | `doc/第1阶段开发任务拆解与Backlog.md` |
| 审批引擎 POC 测试报告 | `doc/审批引擎POC测试报告.md` |
| 审计修复报告 (2026-06-11) | `doc/审计修复报告_2026-06-11.md` |
| 全量代码审查报告 (2026-06-11) | `doc/全量代码审查报告_2026-06-11.md` |
| 全量代码审查报告 (2026-06-12) | `doc/全量代码审查报告_2026-06-12.md` |
| 审查问题修复报告 (2026-06-12) | `doc/审查问题修复报告_2026-06-12.md` |
| 二次全量代码审查报告 (2026-06-12) | `doc/二次全量代码审查报告_2026-06-12.md` |
| 第4周开发计划_合同审批闭环 | `doc/第4周开发计划_合同审批闭环.md` |
| 第2阶段开发计划_成本归集与资金闭环 | `doc/第2阶段开发计划_成本归集与资金闭环.md` |
| 第2阶段成本归集与资金闭环测试报告 | `doc/第2阶段成本归集与资金闭环测试报告.md` |
| 第3阶段成本分析与合同深化测试报告 | `doc/第3阶段成本分析与合同深化测试报告.md` |
| P3 中期改进实施方案 | `doc/P3中期改进实施方案_2026-06-12.md` |
| H2 暂缓问题分析报告 | `doc/WorkflowEngineIntegrationTest_H2暂缓问题分析报告_2026-06-12.md` |
| 开发文档 | `doc/开发文档_v2.3/` |
