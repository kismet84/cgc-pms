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
├── backend/              # Spring Boot 后端 (103+ 源文件)
│   ├── auth/             #   JWT 鉴权 + 登录
│   ├── system/           #   用户/角色/菜单管理
│   ├── project/          #   项目管理 CRUD
│   ├── partner/          #   合作方管理 CRUD
│   ├── contract/         #   合同台账 + 新建/编辑 + 清单 + 付款条件
│   ├── workflow/         #   审批引擎 (提交/同意/驳回/撤回/转办/加签)
│   ├── file/             #   文件上传 (MinIO 存储 + 预签名 URL)
│   └── common/           #   统一响应/异常处理/分页/操作日志
├── frontend-admin/       # Vue 3 管理后台 (36+ 源文件)
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
├── database/             # Flyway 迁移脚本 (V1~V7)
├── deploy/               # Docker Compose (MySQL + Redis + MinIO)
├── doc/                  # 开发文档 + Backlog + 测试报告
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
cd deploy
docker compose up -d
# MySQL: localhost:3306 (root/root123, cgc_pms)
# Redis: localhost:6379
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
| 第 4 周 | 审批闭环 | 🔲 | 合同提交审批、审批回调、成本生成 |

### 已完成功能

```
✅ 登录鉴权 (JWT + BCrypt + RBAC)
✅ 用户管理 / 角色管理 / 菜单管理
✅ 项目管理 CRUD（分页/新建/编辑/详情）
✅ 合作方管理 CRUD（分页/新建/编辑/黑名单标识）
✅ 合同台账查询（8 维筛选 + 详情 + 项目/合作方关联）
✅ 合同中心
   ├── 新建合同（4 步分步表单：基本信息→清单→付款条件→提交审核）
   ├── 合同清单 CRUD（批量保存，自动金额计算）
   ├── 付款条件 CRUD（批量保存，比例合计校验）
   ├── 合同详情页（3 标签：清单/付款条件/审批记录）
   └── 自动生成合同编号 CT-yyyyMMdd-XXX
✅ 文件上传系统
   ├── MinIO 对象存储 + 预签名 URL（7 天有效）
   ├── 通用业务附件关联（businessType + businessId 模式）
   └── sys_file 表 (V7 Flyway 迁移)
✅ 审批引擎 POC
   ├── 提交审批 / 我的待办 / 审批详情
   ├── 同意 / 驳回 / 撤回 / 重新提交
   ├── 转办 / 加签 / 会签 (COUNTERSIGN) / 或签 (OR_SIGN)
   ├── 乐观锁 (taskVersion) + 幂等 (idempotencyKey)
   └── availableActions 动态权限
✅ 前端：登录页 / 首页 / 合同台账 / 合同新建 / 合同详情 / 项目列表 / 合作方列表 / 待办列表 / 审批详情
✅ Flyway 数据库迁移 (V1~V7：系统表/业务表/审批表/字典/演示数据/文件表)
✅ 集成测试 11 用例全部通过 (H2 + MySQL 双环境)
```

## 合同中心 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/contracts` | GET | 合同台账分页查询（8 维筛选） |
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

## 文件上传 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/files/upload` | POST | 上传文件（multipart, businessType + businessId） |
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

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*`
- 提交信息：`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`
- 代码规范：ESLint + Prettier (前端)
- 后端遵循 `SysUserController` → `SysUserService` 分层模式

## 文档

| 文档 | 路径 |
|------|------|
| 开发任务拆解与 Backlog | `doc/第1阶段开发任务拆解与Backlog.md` |
| 审批引擎 POC 测试报告 | `doc/审批引擎POC测试报告.md` |
| 开发文档 | `doc/开发文档_v2.3/` |
