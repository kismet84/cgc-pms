# CGC-PMS — 建筑工程总包项目全过程管理系统

Construction General Contracting Project Management System

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia + VxeTable + ECharts |
| 后端 | Spring Boot 3.3 + MyBatis-Plus 3.5 + JWT (jjwt 0.12) · Java 21 |
| 数据库 | MySQL 8.0 + Flyway 迁移 · H2 (本地开发) |
| 缓存 | Redis 7 |
| 文件存储 | MinIO (S3 兼容) |
| 部署 | Docker + Docker Compose + Nginx HTTPS |
| UI 语言 | 清爽企业级工作台 — 30 页面统一 pt-* 设计系统 |

## 快速启动

### 前置条件

- JDK 21 · Node.js 20+ · pnpm 9+ · MySQL 8.0 (可选)

### 1. 基础设施

```bash
cd deploy
cp .env.example .env
docker compose up -d    # MySQL:3306 / Redis:6379 / MinIO:9001
```

### 2. 后端

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # H2
```

Swagger: http://localhost:8080/api/swagger-ui.html
默认账号: `admin` / `admin123`

### 3. 前端

```bash
cd frontend-admin
pnpm install
pnpm dev      # http://localhost:5173
```

### 一键启动 (Windows)

```bash
scripts\start-dev.bat    # Docker → 后端 → 前端
```

## 项目结构

```
cgc-pms/
├── backend/                         # Spring Boot
│   └── src/main/java/com/cgcpms/
│       ├── auth/                    # JWT 鉴权 + Token 刷新
│       ├── system/                  # 用户/角色/菜单 (RBAC)
│       ├── project/                 # 项目管理 + 成员
│       ├── partner/                 # 合作方
│       ├── contract/                # 合同台账 + 清单 + 付款条件 + 变更
│       ├── cost/                    # 成本科目 + 台账 + 汇总 + 目标成本
│       ├── payment/                 # 付款申请 + 财务回写
│       ├── purchase/                # 采购订单
│       ├── subcontract/             # 分包任务 + 计量
│       ├── settlement/              # 结算管理
│       ├── variation/               # 变更签证 + 审批
│       ├── inventory/               # 仓库 + 库存台账 + 出入库
│       ├── invoice/                 # 发票管理
│       ├── workflow/                # 审批引擎 (提交/同意/驳回/撤回/转办/加签/会签)
│       ├── dashboard/               # 经营驾驶舱 (五角色)
│       ├── alert/                   # 预警规则 + 定时批处理
│       ├── notification/            # 站内消息 + SSE 推送
│       ├── file/                    # 文件上传 (MinIO + 预签名 URL)
│       ├── org/                     # 组织架构
│       ├── material/                # 物料字典
│       ├── receipt/                 # 物料收货
│       ├── config/                  # CORS / Jackson / MyBatis 全局配置
│       └── common/                  # 统一响应/异常/分页/操作日志/多租户
├── frontend-admin/                  # Vue 3 管理后台
│   └── src/
│       ├── components/              # 共享组件
│       ├── stores/                  # Pinia 状态管理
│       ├── pages/                   # 30 页面 (dashboard/contract/project/cost/payment/purchase/subcontract/settlement/variation/inventory/invoice/approval/alert/org/material/system/settings)
│       └── router/                  # 路由配置
├── deploy/                          # Docker Compose + .env.example
└── .github/workflows/ci.yml        # CI/CD 流水线
```

## 功能模块

| 模块 | 路由 | 说明 |
|------|------|------|
| 工作台 | `/dashboard`、`/alert` | 五角色驾驶舱、经营与风险预警 |
| 项目与主数据 | `/project`、`/partner`、`/org`、`/material` | 项目、合作方、组织架构、材料字典 |
| 合同管理 | `/contract`、`/variation` | 合同全生命周期、变更签证与审批 |
| 成本管理 | `/cost`、`/cost-target` | 成本科目、台账、动态汇总、目标成本 |
| 采购与库存 | `/purchase`、`/inventory` | 采购申请、订单、验收、仓库、库存与出入库 |
| 分包管理 | `/subcontract` | 分包任务与计量 |
| 付款与发票 | `/payment`、`/invoice` | 付款申请、财务回写、发票登记与核验 |
| 结算管理 | `/settlement` | 结算列表 + 详情 |
| 审批中心 | `/approval` | 我的待办、我的已办、抄送我的、流程管理 |
| 系统管理 | `/system` | 用户、角色、字典、数据管理 |

## 测试

| 层级 | 框架 | 用例 | 通过率 |
|------|------|------|--------|
| 后端 | JUnit 5 + MockMvc | 206 | 100% |
| 前端 | Vitest | 136 | ~99% (1 预存) |
| E2E | Playwright | 36 | 12 模块覆盖 |

```bash
cd backend && ./mvnw test          # 后端
cd frontend-admin && pnpm build    # 前端构建 + 类型检查
cd frontend-admin && pnpm vitest   # 前端测试
```

## 部署

```bash
cd deploy
cp .env.example .env
mkdir -p ssl
docker compose -f docker-compose.prod.yml up -d
```

服务规格: MySQL (512M) + Redis (256M) + MinIO (512M) + Backend (1G) + Nginx (128M)
端口: HTTP 80 / HTTPS 443 → Nginx → Backend :8080

## CI/CD

`.github/workflows/ci.yml` — push main 触发: backend-test → frontend-build → flyway-check → docker-build → deploy

## 常见问题

### Flyway 校验失败 → API 500

**根因**: 已应用的迁移脚本 (`V*.sql`) 被修改，checksum 不匹配。

**预防**: 不要修改已执行的迁移脚本，新增表结构用新的 `V48__xxx.sql`。

### 修复步骤

```sql
-- 进入 MySQL 容器
docker exec -it cgc-pms-mysql-dev mysql -u cgc -p cgc_pms

-- 修复 checksum
UPDATE flyway_schema_history SET checksum = <当前checksum> WHERE version = '47';

-- 补充缺失列 (如 sys_user_preference)
ALTER TABLE sys_user_preference
  ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER preferences,
  ADD COLUMN updated_by BIGINT NULL COMMENT '更新人' AFTER created_by,
  ADD COLUMN deleted_flag SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER updated_by,
  ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER deleted_flag;

docker restart cgc-pms-backend-dev
```

## 文档

| 分类 | 路径 |
|------|------|
| 审计审查 | `doc/01-审计审查/` |
| 开发计划 | `doc/02-开发计划/` |
| 测试报告 | `doc/03-测试报告/` |
| 验收文档 | `doc/04-验收文档/` |
| 上线部署 | `doc/05-上线部署/` |
| 用户手册 | `doc/06-用户手册/` |
| 开发文档 | `doc/07-开发文档/v2.3/` |
| 系统说明与技术规范 | `doc/08-系统说明与技术规范/` |
