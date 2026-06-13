# CGC-PMS 建筑工程总包项目全过程管理系统

Construction General Contracting Project Management System

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia + VxeTable + ECharts | - |
| 后端 | Spring Boot + MyBatis-Plus + JWT (jjwt) | Java 21 / Boot 3.3 / MP 3.5 / jjwt 0.12 |
| 数据库 | MySQL + Flyway 迁移 (H2 用于本地开发) | MySQL 8.0 / H2 |
| 缓存 | Redis | 7 |
| 文件存储 | MinIO (S3 兼容) | - |
| 部署 | Docker + Docker Compose + Nginx HTTPS | - |

## 项目结构

```
cgc-pms/
├── backend/                         # Spring Boot 后端
│   └── src/main/java/com/cgcpms/
│       ├── auth/                    # JWT 鉴权 + 登录 + Token 刷新
│       ├── system/                  # 用户 / 角色 / 菜单 (RBAC)
│       ├── project/                 # 项目管理 CRUD + 成员
│       ├── partner/                 # 合作方管理
│       ├── contract/                # 合同台账 + 新建 / 清单 / 付款条件 / 变更
│       ├── workflow/                # 审批引擎 (提交/同意/驳回/撤回/转办/加签/会签)
│       ├── file/                    # 文件上传 (MinIO + 预签名 URL)
│       ├── inventory/               # 仓库 + 库存台账 + 出入库
│       ├── invoice/                 # 发票创建 / 登记 / 核验
│       ├── notification/            # 站内消息 + SSE 实时推送
│       ├── alert/                   # 八类预警规则 + @Scheduled 批处理
│       ├── org/                     # 组织架构 (公司/部门/岗位)
│       ├── material/                # 物料字典
│       ├── cost/                    # 成本科目 / 台账 / 归集 / 汇总 / 目标成本
│       ├── payment/                 # 付款申请 + 财务回写
│       ├── purchase/                # 采购申请 + 采购订单
│       ├── settlement/              # 结算管理
│       └── common/                  # 统一响应 / 异常 / 分页 / 操作日志 / 多租户
├── frontend-admin/                  # Vue 3 管理后台
│   └── src/
│       ├── components/              # 共享组件 (StepWizard / ContractItemEditor 等)
│       ├── stores/                  # Pinia 状态管理
│       └── pages/                   # 页面 (登录/首页/合同/项目/合作方/审批/库存/发票/消息/组织/预警/成本)
├── mobile/                          # uni-app 移动端 (预留)
├── deploy/                          # Docker Compose 配置 + .env.example
├── doc/                             # 开发文档 + 测试报告 + 上线清单
├── scripts/                         # 辅助脚本 (含 start-dev.bat 一键启动)
└── .github/workflows/ci.yml        # CI/CD 流水线 (5 jobs)
```

## 快速启动

### 前置条件

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 后端编译 |
| Node.js | 20+ | 前端开发 |
| pnpm | 9+ | 前端包管理 |
| MySQL | 8.0 | 数据库 (可选，可用 H2 替代) |

### 1. 数据库

```bash
# 推荐方式: Docker Compose
cd deploy
cp .env.example .env      # 编辑 .env 填入安全密码
docker compose up -d       # MySQL:3306 / Redis:6379 / MinIO:9001

# 或本地 MySQL
CREATE DATABASE cgc_pms DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'cgc'@'localhost' IDENTIFIED BY 'cgc123';
GRANT ALL ON cgc_pms.* TO 'cgc'@'localhost';

# 或 H2 内存库 (无需 MySQL)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # MySQL 环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=local  # H2 环境

# Windows 一键启动: scripts\start-dev.bat (自动 Docker → 后端 → 前端)
```

Swagger: `http://localhost:8080/api/swagger-ui.html`
默认管理员: `admin` / `admin123`

### 3. 启动前端

```bash
cd frontend-admin
pnpm install
pnpm dev        # 管理后台: http://localhost:5173
```

## 功能概览

**认证与权限**: JWT 双 Token (access 15min + refresh 7d), BCrypt 密码加密, RBAC 角色权限, Redis Token 黑名单, 多租户数据隔离

**合同管理**: 合同台账 (8 维筛选 + N+1 优化), 4 步新建向导 (基本信息→清单→付款条件→提交), 合同变更 (金额联动成本), 自动编号 CT-yyyyMMdd-XXX

**审批引擎**: 提交/同意/驳回/撤回/重新提交/转办/加签/会签/或签, 乐观锁 taskVersion, 幂等 idempotencyKey, availableActions 动态权限, 审批回调事务一致性 (isCritical)

**成本管理**: 成本科目树形管理, 成本台账 (来源追溯), 策略模式 CostGenerationService (4 种来源), 动态成本汇总, 目标成本多版本

**采购与库存**: 采购申请 + 采购订单 (三级顺序审批), 仓库管理, 库存台账 (乐观锁 @Version), 出入库自动更新余额

**付款与结算**: 付款申请 (金额校验 + basis 批量保存), 财务回写 (合同联动 + cost_summary 联动), 总包/分包/采购结算 (不可变锁定)

**发票与预警**: 发票创建/编辑/核验/登记, 八类预警规则 + @Scheduled 批处理 + 手动批量评估

**消息与组织**: 站内通知 + SSE 实时推送 + 抄送, 公司/部门/岗位三级组织架构, 五角色经营驾驶舱 (ECharts 图表下钻)
## API 参考

| 模块 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 认证 | `/auth/login` | POST | 登录, 返回 access + refresh token |
| | `/auth/userinfo` | GET | 当前用户信息 (角色+权限) |
| | `/auth/refresh` | POST | Token 轮换, 旧 refresh 自动失效 |
| | `/auth/logout` | POST | 退出, access token 加入黑名单 |
| 审批 | `/workflow/submit` | POST | 提交审批 |
| | `/workflow/tasks/todo` | GET | 我的待办 |
| | `/workflow/instances/{id}` | GET | 审批详情 (含 availableActions) |
| | `/workflow/tasks/{id}/approve` | POST | 同意 |
| | `/workflow/tasks/{id}/reject` | POST | 驳回 |
| | `/workflow/instances/{id}/withdraw` | POST | 撤回 |
| | `/workflow/instances/{id}/resubmit` | POST | 重新提交 |
| | `/workflow/tasks/{id}/transfer` | POST | 转办 |
| | `/workflow/tasks/{id}/add-sign` | POST | 加签 |
| 合同 | `/contracts` | GET/POST | 台账查询 (8 维筛选) / 新建 |
| | `/contracts/{id}` | GET/PUT | 详情 / 编辑 |
| | `/contracts/{id}/items` | GET/POST | 清单列表 / 新增 |
| | `/contracts/{id}/items/batch` | POST | 批量保存清单 |
| | `/contracts/{id}/payment-terms` | GET/POST | 付款条件列表 / 新增 |
| | `/contracts/{id}/payment-terms/batch` | POST | 批量保存付款条件 |
| | `/contracts/{id}/submit` | POST | 提交审批 |
| | `/contracts/{id}/approval-records` | GET | 审批记录 |
| 文件 | `/files/upload` | POST | 上传 (<=50MB, 扩展名白名单) |
| | `/files/{id}/url` | GET | 预签名下载 URL (7天有效) |
| | `/files` | GET | 按业务类型查询 |
| | `/files/{id}` | DELETE | 删除 |
| 库存 | `/api/inventory/warehouses` | GET/POST | 仓库列表 / 新建 |
| | `/api/inventory/warehouses/{id}` | GET/PUT | 仓库详情 / 编辑 |
| | `/api/inventory/warehouses/{id}/status` | PUT | 启用/禁用 |
| | `/api/inventory/stock/ledger` | GET | 库存台账 (余额+流水) |
| | `/api/inventory/stock/in` | POST | 入库 |
| | `/api/inventory/stock/out` | POST | 出库 |
| 发票 | `/api/invoices` | GET/POST | 发票列表 / 创建 |
| | `/api/invoices/{id}` | GET/PUT/DELETE | 详情 / 编辑 / 删除 |
| | `/api/invoices/{id}/verify` | PUT | 核验 |
| | `/api/invoices/register` | POST | 登记 (关联付款记录) |
| 通知 | `/api/notifications` | GET | 通知分页查询 |
| | `/api/notifications/unread-count` | GET | 未读数量 |
| | `/api/notifications/{id}/read` | PUT | 标记已读 |
| | `/api/notifications/read-all` | PUT | 全部已读 |
| | `/api/notifications/stream` | GET | SSE 实时推送流 |
| 预警 | `/alerts` | GET | 预警分页查询 |
| | `/alerts/{id}/read` | PUT | 标记已读 |
| | `/alerts/batch-evaluate` | POST | 手动批量评估 |
| 组织 | `/api/org/companies` | GET/POST | 公司列表 / 新建 |
| | `/api/org/departments` | GET/POST | 部门列表 / 新建 |
| | `/api/org/positions` | GET/POST | 岗位列表 / 新建 |

其他模块 (项目管理 / 合作方 / 成本 / 付款 / 采购 / 结算 / 物料) CRUD 均遵循标准 REST 规范, 详见 Swagger 文档。

## 安全加固

- **方法级权限**: `@PreAuthorize` 覆盖全部 38+ Controller, 使用 `hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('xxx:action')` 声明式鉴权
- **JWT Token**: Access Token TTL 15 分钟, Refresh Token 7 天轮换, 旧 Token 自动加入 Redis 黑名单
- **密码安全**: BCrypt 加密存储, 禁用账号拦截 Refresh, 防暴力破解
- **数据校验**: Jakarta Validation 覆盖 PayInvoice/PayApplication/PayRecord 等实体
- **防 Mass Assignment**: `@JsonProperty(READ_ONLY)` 保护 5 个敏感实体字段
- **CORS**: `allowedHeaders` 严格限制, 禁用通配符
- **日志脱敏**: Logback 自动掩码 password/token/secret/authorization 字段
- **HTTPS**: 生产 Nginx SSL 终止, 后端 useSSL=true
- **SSE**: Nginx proxy_buffering off, 认证过滤链修复, 匿名端点白名单
- **Docker**: backend + frontend 均有 HEALTHCHECK, Dockerfile 安全哨兵默认值, 无空密码
- **文件安全**: 扩展名白名单 (20 种), 大小限制 50MB, businessType 路径注入防护, tenantId 鉴权

## 开发规范

- **分支模型**: `main` / `develop` / `feature/*` / `hotfix/*`
- **提交信息**: `feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`
- **前端**: ESLint + Prettier, 零 as any / 零 @ts-ignore / 零 console.log
- **后端**: Controller → Service 分层, 48 个 Service 统一 @Slf4j, 零空 catch 块
- **多租户**: Service 层 LambdaQueryWrapper 统一追加 .eq(Entity::getTenantId, ...), 单条操作 selectById 后校验 tenantId
- **配置**: 敏感值使用 ${ENV_VAR:default}, 禁止硬编码, .env.example 含 JWT_SECRET 模板
- **迁移**: Flyway V1~V46 (MySQL + H2 双版本完全同步), INSERT IGNORE INTO 幂等
- **Profile**: `dev` (MySQL) / `local` (H2 内存库) / `test` / `prod`
- **工作流**: WorkflowBusinessHandler.isCritical() 控制事务一致性 (true=回滚, false=swallow-and-log)

## 测试

| 层级 | 框架 | 用例数 | 通过率 |
|------|------|--------|--------|
| 后端单元+集成 | JUnit 5 + MockMvc | 206 | 100% |
| 前端单元 | Vitest | 16 | 100% |
| E2E | Playwright | 36 (9 specs) | 12 业务模块覆盖 |
| 业务验收 | 手动场景 | 83 场景 | 77.1% |

- 后端: `cd backend && ./mvnw test` (BUILD SUCCESS, H2 + MySQL 双环境)
- 前端: `cd frontend-admin && pnpm build` (零 TypeScript 错误)

## 部署

```bash
# 生产环境
cd deploy
cp .env.example .env        # 配置所有密钥 (含 JWT_SECRET)
mkdir -p ssl                # 放置 SSL 证书
docker compose -f docker-compose.prod.yml up -d
```

服务: MySQL 8.0 (512M) + Redis 7 (256M) + MinIO (512M) + Backend (1G) + Nginx Frontend (128M)
端口: HTTP 80 / HTTPS 443 → Nginx → Backend :8080
HEALTHCHECK: 全部 5 个服务均配置健康检查

CI/CD: `.github/workflows/ci.yml` (5 jobs) — push main/develop 触发: backend-test → frontend-build → flyway-check → docker-build; workflow_dispatch 触发 deploy (push 镜像 + SSH 远程部署)

## 文档

| 文档 | 路径 |
|------|------|
| 开发文档 | `doc/开发文档_v2.3/` |
| 上线就绪检查清单 | `doc/上线就绪检查清单_2026-06-13.md` |
| 验收总结报告 | `doc/验收总结报告_2026-06-13.md` |
| 部署与回滚手册 | `doc/部署与回滚手册_2026-06-13.md` |
| 备份恢复方案 | `doc/备份恢复方案_2026-06-13.md` |
| 监控告警清单 | `doc/监控告警清单_2026-06-13.md` |
| 二期 Backlog | `doc/二期Backlog与范围说明_2026-06-13.md` |
| E2E 测试报告 | `doc/E2E测试报告_2026-06-13.md` |
