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
├── backend/              # Spring Boot 后端 (115+ 源文件)
│   ├── auth/             #   JWT 鉴权 + 登录
│   ├── system/           #   用户/角色/菜单管理
│   ├── project/          #   项目管理 CRUD
│   ├── partner/          #   合作方管理 CRUD
│   ├── contract/         #   合同台账 + 新建/编辑 + 清单 + 付款条件
│   ├── workflow/         #   审批引擎 (提交/同意/驳回/撤回/转办/加签)
│   ├── file/             #   文件上传 (MinIO 存储 + 预签名 URL)
│   └── common/           #   统一响应/异常处理/分页/操作日志
├── frontend-admin/       # Vue 3 管理后台 (38+ 源文件)
│   └── src/
│       ├── components/   #   共享组件 (StepWizard / ContractItemEditor / PaymentTermEditor)
│       ├── stores/       #   Pinia 状态管理 (user / contract)
│       └── pages/
│           ├── login/        #   登录页
│           ├── dashboard/    #   首页
│           ├── contract/     #   合同台账 + 新建合同 + 合同详情
│           ├── project/      #   项目列表页
│           ├── partner/      #   合作方列表页
│           └── approval/     #   我的待办 + 审批详情
├── mobile/               # uni-app 移动端（预留）
├── database/             # Flyway 迁移脚本 (V1~V31)
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
✅ Flyway 数据库迁移 (V1~V31：系统表/业务表/审批表/字典/演示数据/文件表/排序索引/成本归集/目标成本/变更/结算/驾驶舱/预警)
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

无（H2 审批权限已加 @PreAuthorize + M4 已改为 HttpOnly Cookie + P18 vite 已升级 6.x）

## 文档

| 文档 | 路径 |
|------|------|
| 开发任务拆解与 Backlog | `doc/第1阶段开发任务拆解与Backlog.md` |
| 审批引擎 POC 测试报告 | `doc/审批引擎POC测试报告.md` |
| 审计修复报告 (2026-06-11) | `doc/审计修复报告_2026-06-11.md` |
| 全量代码审查报告 (2026-06-11) | `doc/全量代码审查报告_2026-06-11.md` |
| 第4周开发计划_合同审批闭环 | `doc/第4周开发计划_合同审批闭环.md` |
| 第2阶段开发计划_成本归集与资金闭环 | `doc/第2阶段开发计划_成本归集与资金闭环.md` |
| 第2阶段成本归集与资金闭环测试报告 | `doc/第2阶段成本归集与资金闭环测试报告.md` |
| 第3阶段成本分析与合同深化测试报告 | `doc/第3阶段成本分析与合同深化测试报告.md` |
| 开发文档 | `doc/开发文档_v2.3/` |
