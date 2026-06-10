# 建筑工程总包项目全过程管理系统

# 第 0 阶段工程启动与第 1 阶段开发任务拆解 Backlog

版本：V1.1  
适用文档基线：V1.2 修正版开发文档   
适用阶段：开发启动阶段 / 第 1 阶段 POC 阶段 / 安全加固  
适用对象：项目负责人、产品经理、前端开发、后端开发、测试、实施、运维

---

## 1. 文档目的

开发文档已经完成审查，下一步应从“方案编制”切换到“工程落地”。

本文件用于明确开发启动后的第一批工作，包括：

1. 工程仓库如何建立；
2. 开发环境如何准备；
3. 前后端脚手架如何搭建；
4. 数据库初始化脚本如何落地；
5. 审批引擎 POC 如何验证；
6. 合同台账页面如何组件化；
7. 第 1 阶段 Backlog 如何拆分；
8. 每项任务的优先级、依赖关系和验收标准。

本文件不是替代 V1.2 修正版开发文档，而是把 V1.2 文档转化为可执行的开发任务。

开发文档路径：'D:\projects-test\cgc-pms\doc\开发文档_v2.3'
---

## 2. 当前阶段结论

当前已完成：

```text
需求方案审查通过
业务主线审查通过
模块边界审查通过
审批流程审查通过
API 契约审查通过
数据库设计审查通过
技术架构审查通过
开发计划审查通过
测试运维方案审查通过
架构基线修订审查通过
```

下一步不建议继续扩写总方案，而应立即进入：

```text
工程启动
→ 开发环境搭建
→ 前后端脚手架
→ 数据库 Flyway 初始化
→ 审批引擎 POC
→ 合同台账 POC
→ 项目 / 合作方 / 合同中心开发
```

---

## 3. 开发启动总目标

第 0 阶段和第 1 阶段的总目标是：

```text
把文档变成一个可运行、可登录、可初始化数据库、可展示合同台账、可跑通审批 POC 的系统骨架。
```

第 1 阶段结束时，系统至少应达到以下状态：

| 类别 | 目标 |
|---|---|
| 前端 | Vue 3 管理后台可启动，具备登录页、主布局、合同台账页面、审批待办页面雏形 |
| 后端 | Spring Boot 服务可启动，具备统一返回、异常处理、Swagger、登录鉴权基础能力 |
| 数据库 | MySQL 可通过 Flyway 一键初始化核心表和字典数据 |
| 审批 | 审批引擎 POC 跑通提交、同意、驳回、撤回、会签、或签、驳回重提 |
| 合同 | 合同台账查询接口和页面可联调 |
| 基础数据 | 项目、合作方基础 CRUD 可用 |
| 工程 | Git 分支、代码规范、接口规范、部署脚本初步建立 |

---

## 4. 技术栈冻结

正式开发阶段不得继续在基础技术栈上摇摆，统一采用以下技术栈。

### 4.1 前端技术栈

| 项目 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue 3 | |
| 语言 | TypeScript | |
| 构建工具 | Vite | 6.4.3 |
| UI 组件库 | Ant Design Vue |
| 状态管理 | Pinia |
| 路由 | Vue Router |
| 请求库 | Axios 封装 |
| 表格 | VxeTable |
| 图表 | ECharts |
| 表单 | Ant Design Vue Form + 自定义动态表单渲染器 |
| 代码规范 | ESLint + Prettier |

### 4.2 后端技术栈

| 项目 | 技术 |
|---|---|
| 语言 | Java 21 |
| 框架 | Spring Boot |
| ORM | MyBatis-Plus |
| 权限 | JWT + RBAC + 数据权限 |
| 数据库 | MySQL 8.0 |
| 数据迁移 | Flyway |
| 缓存 | Redis |
| 文件存储 | MinIO |
| 接口文档 | OpenAPI / Swagger |
| 日志 | Logback + 结构化日志 |
| 部署 | Docker + Nginx |

### 4.3 数据库和中间件

| 项目 | 建议版本 |
|---|---|
| MySQL | 8.0.x |
| Redis | 7.x |
| MinIO | 最新稳定版 |
| Docker | 24+ |
| Docker Compose | v2+ |

### 4.4 本地开发工具

| 工具 | 建议 |
|---|---|
| JDK | 21 |
| Node.js | 20 LTS |
| pnpm | 9+ |
| IDE | IntelliJ IDEA + VS Code |
| API 调试 | Apifox / Postman |
| 数据库工具 | DataGrip / DBeaver |
| Git | 最新稳定版 |

---

## 5. 代码仓库与目录结构

建议采用 monorepo 结构，便于开发文档、数据库脚本、前端、后端和部署脚本统一管理。

```text
construction-pm-system/
├── backend/                  # Spring Boot 后端工程
├── frontend-admin/            # PC Web 管理后台
├── mobile/                    # 后续 uni-app 移动端，当前只预留目录
├── database/                  # Flyway SQL、初始化数据、字典数据
├── docs/                      # V1.2 修正版开发文档
├── deploy/                    # Docker、Nginx、CI/CD 脚本
├── scripts/                   # 本地辅助脚本
└── README.md
```

当前阶段重点建设：

```text
backend/
frontend-admin/
database/
deploy/
docs/
```

移动端目录可以先保留，不进入正式业务开发。

---

## 6. Git 分支规范

建议采用以下分支模型。

```text
main         生产稳定分支
release/*    预发布分支
develop      日常集成分支
feature/*    功能开发分支
hotfix/*     线上紧急修复分支
```

示例：

```text
feature/backend-init
feature/frontend-init
feature/database-flyway
feature/workflow-poc
feature/auth-rbac
feature/project-crud
feature/partner-crud
feature/contract-ledger
feature/contract-form
```

提交信息建议采用：

```text
feat: 新增功能
fix: 修复问题
docs: 文档变更
style: 格式调整
refactor: 重构
test: 测试相关
chore: 工程配置
```

示例：

```text
feat(workflow): add workflow submit api
feat(contract): add contract ledger page
fix(auth): fix token refresh error
```

---

## 7. 第 0 阶段：工程启动任务

周期建议：第 1 周前半周。

### 7.1 阶段目标

```text
完成代码仓库、开发环境、基础目录、数据库迁移、前后端启动能力。
```

### 7.2 任务清单

| 编号 | 任务 | 优先级 | 负责人 | 前置依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|---|
| ENV-001 | 建立 Git 仓库和目录结构 | P0 | 技术负责人 | 无 | 仓库包含 backend、frontend-admin、database、deploy、docs | ✅ 完成 |
| ENV-002 | 建立分支规范 | P0 | 技术负责人 | ENV-001 | main、develop、feature 分支可用 | ✅ 完成 |
| ENV-003 | 建立本地 Docker Compose | P0 | 后端 / 运维 | ENV-001 | MySQL、Redis、MinIO 可本地启动 | ✅ 完成 |
| ENV-004 | 整理 README | P0 | 技术负责人 | ENV-001 | 新人可按 README 启动项目 | ✅ 完成 |
| ENV-005 | 配置代码格式化规范 | P1 | 前后端负责人 | ENV-001 | 前后端格式化规则生效 | ✅ 完成 |

---

## 8. 第 1 阶段：开发任务总览

周期建议：第 1 周至第 4 周。

### 8.1 阻塞依赖链

实际开发应按以下依赖链推进：

```text
开发环境
→ 数据库初始化
→ 后端基础框架
→ 前端基础框架
→ 登录鉴权
→ RBAC 权限
→ 审批引擎 POC
→ 项目 / 合作方 CRUD
→ 合同台账 POC
→ 新建合同
→ 合同审批闭环
```

### 8.2 阶段交付物

| 类别 | 交付物 |
|---|---|
| 工程 | 可运行的前端、后端、本地中间件环境 |
| 数据库 | Flyway 初始化脚本和核心表 |
| 权限 | 登录、用户、角色、菜单、按钮权限基础能力 |
| 审批 | 审批引擎 POC |
| 业务 | 项目、合作方、合同台账基础功能 |
| 前端 | 主布局、登录页、合同台账页面、审批待办页面 |
| 测试 | 审批 POC 测试用例、登录测试、合同台账测试 |

---

## 9. 后端工程 Backlog

### 9.1 后端基础框架

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| BE-001 | 初始化 Spring Boot 工程 | P0 | ENV-001 | 后端服务可启动 | ✅ 完成 |
| BE-002 | 配置多环境 application.yml | P0 | BE-001 | dev/test/prod 配置分离 | ✅ 完成 |
| BE-003 | 集成 MyBatis-Plus | P0 | BE-001 | 可访问数据库 | ✅ 完成 |
| BE-004 | 集成 Flyway | P0 | DB-001 | 启动时可执行迁移脚本 | ✅ 完成 |
| BE-005 | 统一响应结构 | P0 | BE-001 | 所有接口返回 code/message/data/traceId | ✅ 完成 |
| BE-006 | 全局异常处理 | P0 | BE-001 | 业务异常、参数异常统一返回 | ✅ 完成 |
| BE-007 | 分页对象封装 | P0 | BE-003 | 支持 pageNo/pageSize/total/records | ✅ 完成 |
| BE-008 | Swagger / OpenAPI | P0 | BE-001 | Swagger 页面可访问 | ✅ 完成 |
| BE-009 | 操作日志基础拦截器 | P1 | BE-001 | 可记录关键操作日志 | ✅ 完成 |

### 9.2 登录鉴权与权限

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| AUTH-001 | 用户登录接口 | P0 | BE-001、DB-001 | 用户可登录并返回 Token | ✅ 完成 |
| AUTH-002 | JWT 校验过滤器 | P0 | AUTH-001 | 未登录请求被拦截 | ✅ 完成 |
| AUTH-003 | 当前用户上下文 | P0 | AUTH-002 | 后端可获取当前用户 ID | ✅ 完成 |
| AUTH-004 | 用户管理 CRUD | P1 | DB-001 | 可新增、编辑、禁用用户 | ✅ 完成 |
| AUTH-005 | 角色管理 CRUD | P1 | DB-001 | 可维护角色 | ✅ 完成 |
| AUTH-006 | 菜单管理 CRUD | P1 | DB-001 | 可维护菜单 | ✅ 完成 |
| AUTH-007 | 用户角色分配 | P1 | AUTH-004、AUTH-005 | 用户可绑定角色 | ✅ 完成 |
| AUTH-008 | 菜单权限返回接口 | P1 | AUTH-006 | 前端可渲染菜单 | ✅ 完成 |
| AUTH-009 | 按钮权限返回接口 | P1 | AUTH-006 | 前端可控制按钮展示 | ✅ 完成 |
| AUTH-010 | 方法级权限控制 | P0 | AUTH-002 | @PreAuthorize 全量覆盖 10 个 Controller，JWT 携带权限码 | ✅ 完成 |
| AUTH-011 | Refresh Token + 黑名单 | P0 | AUTH-001、DB-001 | access/refresh 双 token，logout 黑名单，/auth/refresh 轮换 | ✅ 完成 |

### 9.3 项目管理

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| PM-001 | 项目表实体和 Mapper | P0 | DB-002 | 可访问 pm_project | ✅ 完成 |
| PM-002 | 项目分页查询 | P0 | PM-001 | 支持项目列表分页 | ✅ 完成 |
| PM-003 | 新建项目 | P1 | PM-001 | 可创建项目 | ✅ 完成 |
| PM-004 | 编辑项目 | P1 | PM-001 | 可修改项目信息 | ✅ 完成 |
| PM-005 | 项目详情 | P1 | PM-001 | 可查看项目详情 | ✅ 完成 |
| PM-006 | 项目状态字典 | P1 | DB-005 | 项目状态来自字典 | ✅ 完成 |

### 9.4 合作方管理

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| PARTNER-001 | 合作方实体和 Mapper | P0 | DB-002 | 可访问 md_partner | ✅ 完成 |
| PARTNER-002 | 合作方分页查询 | P0 | PARTNER-001 | 支持供应商、分包商筛选 | ✅ 完成 |
| PARTNER-003 | 新建合作方 | P1 | PARTNER-001 | 可新增合作方 | ✅ 完成 |
| PARTNER-004 | 编辑合作方 | P1 | PARTNER-001 | 可编辑合作方 | ✅ 完成 |
| PARTNER-005 | 黑名单字段控制 | P1 | PARTNER-001 | 黑名单合作方可被识别 | ✅ 完成 |

### 9.5 合同管理

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| CT-001 | 合同实体和 Mapper | P0 | DB-002 | 可访问 ct_contract | ✅ 完成 |
| CT-002 | 合同台账分页查询 | P0 | CT-001 | 支持合同编号、名称、类型、状态筛选 | ✅ 完成 |
| CT-003 | 合同详情接口 | P1 | CT-001 | 可查看合同详情 | ✅ 完成 |
| CT-004 | 新建合同基础信息 | P1 | CT-001、PM-001、PARTNER-001 | 可保存合同草稿 | ✅ 完成 |
| CT-005 | 合同清单保存 | P1 | CT-004 | 可保存合同清单 | ✅ 完成 |
| CT-006 | 合同付款条件保存 | P1 | CT-004 | 可保存付款条件 | ✅ 完成 |
| CT-007 | 合同附件关联 | P1 | FILE-001 | 可上传并关联合同附件 | ✅ 完成 |
| CT-008 | 合同提交审批 | P0 | WF-002 | 合同可调用审批引擎提交审批 | 🔲 待开发 |
| CT-009 | 合同审批回调 Handler | P0 | WF-010 | 审批通过后合同状态变更 | 🔲 待开发 |
| CT-010 | 合同锁定成本生成 | P1 | COST-001 | 合同通过后可生成锁定成本 | 🔲 待开发 |

---

## 10. 数据库 Backlog

### 10.1 Flyway 脚本拆分

建议将数据库脚本拆分为：

```text
database/
└── migration/
    ├── V1__init_system_tables.sql
    ├── V2__init_project_partner_contract.sql
    ├── V3__init_workflow_tables.sql
    ├── V4__init_cost_payment_tables.sql
    ├── V5__init_dict_data.sql
    ├── V6__init_demo_data.sql
    ├── V7__init_file_tables.sql
    └── V8__add_missing_indexes.sql
```

### 10.2 数据库任务

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| DB-001 | 初始化系统权限表 | P0 | ENV-003 | sys_user、sys_role、sys_menu 等表可创建 | ✅ 完成 |
| DB-002 | 初始化项目、合作方、合同表 | P0 | ENV-003 | pm_project、md_partner、ct_contract 可创建 | ✅ 完成 |
| DB-003 | 初始化审批 wf_* 表 | P0 | ENV-003 | wf_instance、wf_task 等表可创建 | ✅ 完成 |
| DB-004 | 初始化 cost_item 表 | P0 | ENV-003 | cost_item 含 source_item_id | ✅ 完成 |
| DB-005 | 初始化字典数据 | P0 | DB-001 | 合同类型、审批状态等字典可用 | ✅ 完成 |
| DB-006 | 初始化测试数据 | P1 | DB-001~DB-005 | 可登录并看到演示项目、合同 | ✅ 完成 |
| DB-007 | 增加核心索引 | P0 | DB-001~DB-004 | 查询字段具备索引 | ✅ 完成 |
| DB-008 | 增加成本来源唯一索引 | P0 | DB-004 | cost_item 防重复生成 | ✅ 完成 |
| DB-009 | V8 排序索引补充 | P0 | DB-001~DB-002 | sys_user/pm_project/md_partner/ct_contract 的 created_at 索引 | ✅ 完成 |

### 10.3 核心索引要求

成本来源幂等索引：

```sql
UNIQUE KEY uk_cost_source_item (
  source_type,
  source_id,
  source_item_id,
  cost_type
)
```

审批任务并发控制建议索引：

```sql
KEY idx_wf_task_approver_status (approver_id, task_status),
KEY idx_wf_task_instance (instance_id),
KEY idx_wf_task_node (node_instance_id)
```

合同台账查询建议索引：

```sql
KEY idx_ct_contract_project (project_id),
KEY idx_ct_contract_partner (partner_id),
KEY idx_ct_contract_type_status (contract_type, contract_status),
KEY idx_ct_contract_approval (approval_status)
```

---

## 11. 审批引擎 POC Backlog

### 11.1 POC 目标

审批引擎是系统的核心依赖，第 1 阶段必须独立跑通。

POC 使用测试业务类型：

```text
PAY_REQUEST_TEST
```

验证流程：

```text
提交审批
→ 项目经理顺序审批
→ 商务经理 + 成本经理会签
→ 财务或签
→ 总经理审批
→ 审批通过
```

### 11.2 审批 POC 任务

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| WF-001 | 审批模板表建模 | P0 | DB-003 | 可保存模板和节点 | ✅ 完成 |
| WF-002 | 提交审批接口 | P0 | WF-001 | 可生成实例、节点实例、任务 | ✅ 完成 |
| WF-003 | 我的待办接口 | P0 | WF-002 | 当前审批人可看到待办 | ✅ 完成 |
| WF-004 | 审批详情接口 | P0 | WF-002 | 可返回实例、节点、任务、记录 | ✅ 完成 |
| WF-005 | 同意接口 | P0 | WF-002 | 当前节点可流转下一节点 | ✅ 完成 |
| WF-006 | 驳回接口 | P0 | WF-002 | 实例状态变为 REJECTED | ✅ 完成 |
| WF-007 | 重新提交接口 | P0 | WF-006 | currentRound + 1，生成新任务 | ✅ 完成 |
| WF-008 | 撤回接口 | P0 | WF-002 | 未处理任务取消，记录保留 | ✅ 完成 |
| WF-009 | 转办接口 | P1 | WF-003 | 生成新任务，旧任务转办 | ✅ 完成 |
| WF-010 | 加签接口 | P1 | WF-003 | 支持前加签、后加签、并行加签 | ✅ 完成 |
| WF-011 | 会签规则 | P0 | WF-005 | ALL 通过后节点通过 | ✅ 完成 |
| WF-012 | 或签规则 | P0 | WF-005 | 任一人同意后其他任务取消 | ✅ 完成 |
| WF-013 | availableActions | P0 | WF-004 | 后端返回当前用户可操作按钮 | ✅ 完成 |
| WF-014 | taskVersion 乐观锁 | P0 | WF-005 | 并发审批只能成功一次 | ✅ 完成 |
| WF-015 | idempotencyKey 幂等 | P0 | WF-005 | 重复请求不重复写记录 | ✅ 完成 |
| WF-016 | 审批记录留痕 | P0 | WF-005~WF-010 | 所有动作写入 wf_record | ✅ 完成 |
| WF-017 | WorkflowBusinessHandler 注册机制 | P0 | WF-005 | 业务模块可注册审批回调 | ✅ 完成 |

### 11.3 审批业务回调接口

建议定义统一业务回调接口：

```java
public interface WorkflowBusinessHandler {

    BusinessType supportType();

    void beforeSubmit(WorkflowSubmitCommand command);

    void onRunning(WorkflowStateChangedCommand command);

    void onApproved(WorkflowStateChangedCommand command);

    void onRejected(WorkflowStateChangedCommand command);

    void onWithdrawn(WorkflowStateChangedCommand command);

    void onVoided(WorkflowStateChangedCommand command);
}
```

注册器：

```java
@Component
public class WorkflowBusinessHandlerRegistry {

    private final Map<BusinessType, WorkflowBusinessHandler> handlerMap;

    public WorkflowBusinessHandler get(BusinessType businessType) {
        WorkflowBusinessHandler handler = handlerMap.get(businessType);
        if (handler == null) {
            throw new BusinessException("未找到审批业务处理器");
        }
        return handler;
    }
}
```

审批通过后处理原则：

```text
审批引擎状态流转、业务单据状态变更、关键业务结果生成，应在同一事务边界内完成。
通知、消息推送、财务系统同步、BI 同步等，可在事务提交后通过 Outbox 或异步事件处理。
```

---

## 12. 成本生成 POC Backlog

### 12.1 POC 目标

验证“业务事实审批通过后自动生成成本”。

第一条验证链路建议选择：

```text
材料验收 MAT_RECEIPT
→ 审批通过
→ 生成 cost_item
```

### 12.2 成本生成任务

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 |
|---|---|---|---|---|
| COST-001 | cost_item 实体和 Mapper | P0 | DB-004 | 可访问成本明细表 |
| COST-002 | 成本生成服务 | P0 | COST-001 | 可根据来源生成成本 |
| COST-003 | 来源幂等校验 | P0 | DB-008 | 重复调用不生成重复成本 |
| COST-004 | 材料验收成本生成 POC | P1 | MAT-001、WF-017 | 验收审批通过后生成材料成本 |
| COST-005 | 分包计量成本生成 POC | P1 | SUB-001、WF-017 | 计量审批通过后生成分包成本 |

### 12.3 source_item_id 规则

| source_type | source_id | source_item_id | 说明 |
|---|---|---|---|
| MAT_RECEIPT | mat_receipt.id | mat_receipt_item.id | 一条验收明细生成一条成本 |
| SUB_MEASURE | sub_measure.id | sub_measure_item.id | 一条计量明细生成一条成本 |
| VAR_ORDER | var_order.id | var_order_item.id | 一条签证 / 变更明细生成一条成本调整 |
| CT_CONTRACT | ct_contract.id | ct_contract_item.id | 合同清单可生成合同锁定成本 |
| MANUAL_ADJUST | cost_adjust.id | cost_adjust_item.id | 手工调整成本 |

成本生成必须具备幂等性：

```text
同一个 source_type + source_id + source_item_id + cost_type 只能生成一条有效成本。
```

---

## 13. 前端工程 Backlog

### 13.1 前端基础框架

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| FE-001 | 初始化 Vue 3 + Vite 工程 | P0 | ENV-001 | 前端服务可启动 | ✅ 完成 |
| FE-002 | 集成 Ant Design Vue | P0 | FE-001 | 可使用 Button、Table、Form 等组件 | ✅ 完成 |
| FE-003 | 集成 Pinia | P0 | FE-001 | 可保存用户和系统状态 | ✅ 完成 |
| FE-004 | 集成 Vue Router | P0 | FE-001 | 路由可跳转 | ✅ 完成 |
| FE-005 | Axios 请求封装 | P0 | FE-001 | 支持 Token、错误提示、统一响应 | ✅ 完成 |
| FE-006 | 主布局 Layout | P0 | FE-002、FE-004 | 左侧菜单、顶部栏、内容区可用 | ✅ 完成 |
| FE-007 | 登录页 | P0 | FE-005、AUTH-001 | 可登录并进入后台 | ✅ 完成 |
| FE-008 | 权限菜单渲染 | P1 | AUTH-008 | 根据后端菜单渲染路由 | ✅ 完成 |

### 13.2 合同台账 POC

目标：把参考 HTML 页面组件化为 Vue 3 页面。

组件拆分：

```text
ContractLedgerPage
├── ContractFilter
├── ContractKpiCards
├── ContractTable
├── ContractColumnSetting
├── ContractStatusTag
├── ContractSideCharts
└── ContractPagination
```

任务：

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| FE-CT-001 | 合同台账路由和页面骨架 | P0 | FE-006 | 可进入合同台账页面 | ✅ 完成 |
| FE-CT-002 | 筛选区组件 | P0 | FE-CT-001 | 支持合同编号、类型、状态筛选 | ✅ 完成 |
| FE-CT-003 | KPI 卡片组件 | P1 | FE-CT-001 | 显示合同数量、金额等指标 | ✅ 完成 |
| FE-CT-004 | VxeTable 表格组件 | P0 | FE-CT-001 | 支持宽表格展示 | ✅ 完成 |
| FE-CT-005 | 列设置组件 | P1 | FE-CT-004 | 可控制列显示隐藏 | ✅ 完成 |
| FE-CT-006 | 状态 Tag 组件 | P1 | FE-CT-004 | 不同状态显示不同标签 | ✅ 完成 |
| FE-CT-007 | 分页组件 | P0 | FE-CT-004 | 可切换分页 | ✅ 完成 |
| FE-CT-008 | 右侧图表组件 | P2 | FE-CT-001 | 显示合同类型、金额分布 | ✅ 完成 |
| FE-CT-009 | 合同台账接口联调 | P0 | CT-002 | 页面可展示真实合同数据 | ✅ 完成 |

### 13.3 审批页面

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| FE-WF-001 | 我的待办页面 | P0 | WF-003 | 可查看待办列表 | ✅ 完成 |
| FE-WF-002 | 审批详情页面 | P0 | WF-004 | 可查看审批流程和记录 | ✅ 完成 |
| FE-WF-003 | availableActions 渲染 | P0 | WF-013 | 按后端返回按钮渲染 | ✅ 完成 |
| FE-WF-004 | 同意 / 驳回弹窗 | P0 | WF-005、WF-006 | 可提交审批动作 | ✅ 完成 |
| FE-WF-005 | 转办 / 加签弹窗 | P1 | WF-009、WF-010 | 可选择目标用户 | ✅ 完成 |
| FE-WF-006 | 审批时间轴 | P1 | WF-004 | 可展示审批记录 | ✅ 完成 |

---

## 14. 文件上传 Backlog

| 编号 | 任务 | 优先级 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|
| FILE-001 | MinIO 配置 | P1 | ENV-003 | 后端可连接 MinIO | ✅ 完成 |
| FILE-002 | 文件上传接口 | P1 | FILE-001 | 可上传文件并返回 fileId | ✅ 完成 |
| FILE-003 | 文件类型/大小校验 | P0 | FILE-002 | 50MB 限制 + 20 种扩展名白名单 + 路径注入防护 | ✅ 完成 |
| FILE-004 | 文件下载 / 预览接口 | P1 | FILE-001 | 可访问文件 URL | ✅ 完成 |
| FILE-005 | 业务附件关联表 | P1 | DB-001 | 文件可关联业务单据 | ✅ 完成 |
| FILE-006 | 前端上传组件 | P1 | FILE-002 | 合同页面可上传附件 | ✅ 完成 |

---

## 15. 第 1-4 周详细排期

### 15.1 第 1 周：工程骨架 ✅ 已完成（2026-06-10）

| 方向 | 任务 | 状态 |
|---|---|---|
| 工程 | 建仓库、分支、目录结构 | ✅ |
| 环境 | Docker Compose 启动 MySQL、Redis、MinIO | ✅ |
| 后端 | Spring Boot 初始化、统一响应、异常处理、Swagger | ✅ |
| 数据库 | Flyway 初始化系统表、项目表、合同表、审批表 | ✅ |
| 前端 | Vue3 初始化、Ant Design Vue、Layout、登录页 | ✅ |
| 审批 | 建立 wf_* 表和基础实体 | ✅ |
| 合同 | 参考 HTML 拆解组件方案 | ✅ |

第 1 周验收标准：

```text
✅ 后端服务可启动（101+ 源文件 BUILD SUCCESS）
✅ 前端构建通过（vite 6.4.3，vue-tsc + vite build 零错误）
✅ MySQL 可初始化（8 个 Flyway 迁移脚本 V1~V8）
✅ Swagger 可访问
✅ 登录页可访问（JWT + RBAC）
✅ 合同台账静态页面可访问（725行完整组件）
```

### 15.2 第 2 周：审批 POC + 基础数据 ✅ 已完成（2026-06-10）

| 方向 | 任务 | 状态 |
|---|---|---|
| 审批 | 提交审批、待办、详情、同意、驳回、撤回 | ✅ |
| 审批 | roundNo、taskVersion、idempotencyKey | ✅ |
| 审批 | 转办、加签、会签、或签、availableActions | ✅ |
| 审批 | WorkflowBusinessHandler 回调机制 | ✅ |
| 基础数据 | 项目 CRUD（Controller + Service + VO） | ✅ |
| 基础数据 | 合作方 CRUD（Controller + Service + VO） | ✅ |
| 基础数据 | 合同台账查询/详情接口 | ✅ |
| 前端 | 项目列表页 | ✅ |
| 前端 | 合作方列表页 | ✅ |
| 前端 | 审批待办列表页 | ✅ |
| 前端 | 审批详情页（节点流程 + 操作 + 记录时间轴） | ✅ |
| 测试 | 集成测试 11 用例（H2 + MySQL 双环境验证） | ✅ |

第 2 周验收标准：

```text
✅ 审批 POC 主流程跑通（WorkflowEngine 450行，9 个 API 端点）
✅ 项目和合作方可维护（完整 CRUD，前端页面就绪）
✅ 审批待办和详情页面可查看（含同意/驳回/撤回/重提交交互）
✅ 并发审批只能成功一次（taskVersion @Version 乐观锁，3线程并发验证）
✅ 重复提交不会重复写记录（wf_idempotency 幂等表，重复key被拒绝）
✅ MySQL 8.0 全栈可用（Flyway 迁移 + 登录 + 项目/合作方/合同查询 + 审批提交/待办）
✅ 测试报告：doc/审批引擎POC测试报告.md
```

第 2 周修复的问题：

| 问题 | 影响 | 修复 |
|------|------|------|
| Flyway V3 缺 BaseEntity 审计列 | MySQL 下 workflow INSERT 报 SQLSyntaxErrorException | V3 脚本补全 created_by/updated_by/remark |
| MyMetaObjectHandler 未填 updatedAt | 新建记录 updated_at 为 NULL | insertFill 同步填充 updatedAt |
| MySQL root 密码丢失 | 无法使用本地 MySQL | --init-file 重置为 root123 |
| JDK 缺失 (仅有 JRE) | mvnw compile 失败 | 安装 Eclipse Temurin JDK 21 |
| mvnw.cmd 缺 JAVA_HOME | 每次需手动设置 | 自动检测 D:\projects-test\jdk-21 |

### 15.3 第 3 周：合同中心基础 ✅ 已完成（2026-06-10）

| 方向 | 任务 | 状态 |
|---|---|---|
| 合同 | 合同台账查询接口 | ✅ |
| 合同 | 新建合同基础信息 | ✅ |
| 合同 | 合同清单 | ✅ |
| 合同 | 付款条件 | ✅ |
| 文件 | 附件上传 | ✅ |
| 前端 | 合同台账完整筛选、分页、列设置 | ✅ |
| 前端 | 新建合同分步表单 | ✅ |

第 3 周验收标准：

```text
✅ 合同可新建草稿（POST /contracts，自动生成编号 CT-yyyyMMdd-XXX）
✅ 合同清单和付款条件可保存（POST /items/batch + /payment-terms/batch）
✅ 合同台账可查询、筛选、分页（Week 2 已有，前端完整联调）
✅ 合同附件可上传（POST /files/upload → MinIO 存储 + 预签名 URL）
```

第 3 周交付物：

```text
后端新增 12 文件：CtContractService/CtContractController 扩展，CtContractItemService/Controller，
  CtContractPaymentTermService/Controller，MinioConfig，SysFile 实体/Mapper/VO，FileService/Controller，
  V7 Flyway 迁移（sys_file 表）
前端新增 7 文件：StepWizard、ContractItemEditor、PaymentTermEditor 组件，ContractFormPage（4 步向导），
  ContractDetailPage（3 标签），contract Pinia Store，转办/加签弹窗（FE-WF-005）
```

### 15.4 第 4 周：合同审批闭环

| 方向 | 任务 |
|---|---|
| 合同 | 合同提交审批 |
| 合同 | 审批通过后合同生效 |
| 审批 | WorkflowBusinessHandler 回调机制 |
| 成本 | 合同锁定成本生成规则 |
| 前端 | 合同详情、审批记录、状态展示 |
| 测试 | 合同审批全流程测试 |

第 4 周验收标准：

```text
合同从草稿到提交审批再到审批通过可完整闭环
审批通过后合同状态变为已签订 / 履约中
审批记录可追溯
业务回调机制可被合同模块复用
合同锁定成本可生成或预留生成入口
```

---

## 16. 测试与验收清单

### 16.1 审批 POC 测试

| 场景 | 验收标准 |
|---|---|
| 提交审批 | 生成实例、节点实例、任务、记录 |
| 同意 | 当前任务完成，进入下一节点 |
| 会签 | 多人全部同意后节点通过 |
| 或签 | 一人同意后其他任务取消 |
| 驳回 | 实例状态变为 REJECTED |
| 重新提交 | currentRound + 1，新任务生成，旧记录保留 |
| 撤回 | 未处理任务取消，记录保留 |
| 转办 | 原任务转办，新审批人收到任务 |
| 加签 | 可生成加签任务 |
| 并发审批 | 同一任务两人/两请求只能成功一次 |
| 重复提交 | 同一 idempotencyKey 不重复执行 |
| availableActions | 前端按钮完全由后端返回 |

### 16.2 合同台账测试

| 场景 | 验收标准 |
|---|---|
| 分页查询 | 页码、页大小、总数正确 |
| 条件筛选 | 按项目、合同类型、状态筛选正确 |
| 列设置 | 可显示/隐藏列 |
| 金额展示 | 金额格式正确，保留两位小数 |
| 状态展示 | 审批状态、合同状态标签正确 |
| 权限控制 | 无权限用户不可访问或按钮隐藏 |

### 16.3 合同新建与附件测试

| 场景 | 验收标准 |
|---|---|
| 新建合同 | 自动生成合同编号 CT-yyyyMMdd-XXX，默认草稿状态 |
| 合同清单编辑 | 可新增、删除清单行，金额自动计算 |
| 付款条件编辑 | 可新增、删除付款条件，比例合计校验 |
| 分步表单 | 4 步向导可自由切换，数据跨步骤保持 |
| 文件上传 | 可上传合同附件，返回预签名下载 URL |
| 文件关联 | 文件可按 businessType + businessId 关联业务单据 |
| 文件下载 | 预签名 URL 有效期 7 天内可访问 |

### 16.4 数据库测试

| 场景 | 验收标准 |
|---|---|
| Flyway 初始化 | 空库可一键执行成功 |
| 重复执行 | 已执行脚本不会重复执行 |
| 字典数据 | 合同类型、审批状态等可查询 |
| 成本幂等 | 重复来源不能生成重复成本 |
| V7 文件表迁移 | Flyway 自动创建 sys_file 表（含 3 索引） |
| V8 排序索引迁移 | 补充 sys_user/pm_project/md_partner/ct_contract 的 created_at 索引 |
| 种子数据幂等 | V5/V6 使用 INSERT IGNORE，repair 后重跑不报错 |
| 索引检查 | 核心查询字段具备索引 |

---

## 17. 风险与控制措施

| 风险 | 影响 | 控制措施 | 状态 |
|------|------|---------|------|
| 审批引擎过晚完成 | 所有业务流程阻塞 | 第 1 阶段独立 POC，优先级 P0 | ✅ 已解决 |
| DDL 不可执行 | 后端开发受阻 | 使用 Flyway，第一周完成初始化脚本 | ✅ 已解决 |
| 前端页面只停留在静态 HTML | 页面无法扩展 | 第 1 周完成 Vue 组件化 POC | ✅ 已解决 |
| 业务审批回调缺失 | 审批通过后业务状态不同步 | 建立 WorkflowBusinessHandler 机制 | 🔲 Week 4 |
| 成本重复生成 | 成本数据错误 | source_type + source_id + source_item_id + cost_type 唯一索引 | 🔲 Week 4+ |
| Token 无刷新技术 | 用户频繁登录 | Refresh Token + Redis 黑名单已实现 | ✅ 已解决 |
| RBAC 仅在 UI 层 | 后端无权限校验 | @PreAuthorize 方法级权限已全覆盖 | ✅ 已解决 |
| 密钥硬编码 | 安全泄露 | 所有 profile 改用 ${ENV_VAR:default} | ✅ 已解决 |
| 文件无校验 | 存储滥用/攻击 | 50MB 限制 + 扩展名白名单 + 路径注入防护 | ✅ 已解决 |
| Mock 静默回退 | 后端故障不可见 | 移除全部 5 处 mock 回退，统一 message.error 提示 | ✅ 已解决 |
| vite/esbuild CVE | 开发服务器安全 | vite 5→6.4.3 + esbuild 0.25.0，修复 GHSA-4w7w-66w2-5vf9 / GHSA-67mh-4wv8-2f99 | ✅ 已解决 |

---

## 18. 团队分工建议

### 18.1 5-7 人团队

| 角色 | 人数 | 主要职责 |
|---|---:|---|
| 项目负责人 / 产品 | 1 | 需求优先级、验收、协调 |
| 后端负责人 | 1 | 架构、审批引擎、数据库设计 |
| 后端开发 | 1-2 | 项目、合作方、合同、成本 |
| 前端负责人 | 1 | 前端架构、Layout、表格组件 |
| 前端开发 | 1 | 合同台账、审批页面、表单页面 |
| 测试 / 实施 | 1 | 测试用例、数据准备、部署验证 |

### 18.2 最小团队

如果团队人数较少，至少需要：

```text
1 名后端核心开发
1 名前端核心开发
1 名产品 / 测试 / 实施复合角色
```

优先保证：

```text
审批引擎
数据库脚本
合同台账
合同审批闭环
```

---

## 19. 第 1 阶段完成标准

第 1 阶段完成后，应满足以下条件：

```text
1. ✅ 本地开发环境可一键启动。
2. ✅ MySQL 可通过 Flyway 初始化核心表（含 V7 sys_file 表）。
3. ✅ 后端服务可启动并访问 Swagger。
4. ✅ 前端管理后台可启动并登录。
5. ✅ 用户、角色、菜单基础权限可用。
6. ✅ 项目和合作方可维护（完整 CRUD + 前端页面）。
7. ✅ 合同台账页面可查询真实数据（完整筛选、分页、列设置、KPI 卡片）。
8. ✅ 合同可新建、保存草稿（4 步分步表单：基本信息→清单→付款条件→提交审核）。
9. ✅ 合同清单和付款条件可独立维护（CRUD + Batch API）。
10. ✅ 文件上传系统就绪（MinIO 存储 + 预签名 URL + 通用业务附件关联）。
11. ✅ 审批引擎 POC 跑通顺序、会签、或签、驳回、撤回、重提。
12. ✅ availableActions 由后端返回（已含转办/加签按钮）。
13. ✅ taskVersion 和 idempotencyKey 验证通过。
14. ✅ 安全审计全部高危项修复完成（方法级RBAC、Refresh Token、文件安全、输入校验、密钥脱敏、幂等修复）[2026-06-11]
14-1. ✅ vite 5→6.4.3 升级完成，2 个中等漏洞已修复 (2026-06-11)
15. 🔲 合同提交审批与审批回调（Week 4，CT-008/CT-009）。
16. 🔲 WorkflowBusinessHandler 支持合同审批回调（Week 4，已定义接口）。
17. 🔲 成本生成幂等机制实现（Week 4+，COST-001~005）。
```

---

## 20. 下一份建议输出物

在本 Backlog 确认后，下一份建议输出物为：

```text
《第 1 阶段详细任务排期表》
```

建议格式：

| 日期 | 任务编号 | 任务名称 | 负责人 | 依赖 | 交付物 | 状态 |
|---|---|---|---|---|---|---|
| 第 1 周 D1 | ENV-001 | 建立仓库 | 技术负责人 | 无 | Git 仓库 | 未开始 |
| 第 1 周 D1 | BE-001 | 初始化后端工程 | 后端负责人 | ENV-001 | backend 工程 | 未开始 |
| 第 1 周 D1 | FE-001 | 初始化前端工程 | 前端负责人 | ENV-001 | frontend-admin 工程 | 未开始 |
| 第 1 周 D2 | DB-001 | 初始化系统表 | 后端负责人 | ENV-003 | Flyway SQL | 未开始 |

---

## 21. 最终执行建议

开发文档已经通过审查，当前最关键的是控制节奏：

```text
先骨架
再 POC
再合同中心
再执行到成本
再付款结算
最后驾驶舱和移动端增强
```

不建议一开始同时铺开所有模块。应先打通最小闭环：

```text
登录
→ 项目
→ 合作方
→ 合同
→ 合同审批
→ 审批记录
→ 合同状态生效
```

然后再扩展到：

```text
采购订单
→ 材料验收
→ 成本生成
→ 付款申请
→ 财务回写
→ 结算归档
```

一句话总结：

```text
现在的核心任务不是继续写方案，而是建立工程骨架，跑通审批 POC，并打通第一条合同业务闭环。
```
