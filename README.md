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
├── backend/              # Spring Boot 后端 (91 源文件)
│   ├── auth/             #   JWT 鉴权 + 登录
│   ├── system/           #   用户/角色/菜单管理
│   ├── project/          #   项目管理 CRUD
│   ├── partner/          #   合作方管理 CRUD
│   ├── contract/         #   合同台账查询
│   ├── workflow/         #   审批引擎 (提交/同意/驳回/撤回/转办/加签)
│   └── common/           #   统一响应/异常处理/分页/操作日志
├── frontend-admin/       # Vue 3 管理后台 (29 源文件)
│   └── src/pages/
│       ├── login/        #   登录页
│       ├── dashboard/    #   首页
│       ├── contract/     #   合同台账页
│       ├── project/      #   项目列表页
│       ├── partner/      #   合作方列表页
│       └── approval/     #   我的待办 + 审批详情
├── mobile/               # uni-app 移动端（预留）
├── database/             # Flyway 迁移脚本 (V1~V6)
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
| 第 3 周 | 合同中心 | 🔲 | 新建合同、合同清单、付款条件、附件上传 |
| 第 4 周 | 审批闭环 | 🔲 | 合同提交审批、审批回调、成本生成 |

### 已完成功能

```
✅ 登录鉴权 (JWT + BCrypt + RBAC)
✅ 用户管理 / 角色管理 / 菜单管理
✅ 项目管理 CRUD（分页/新建/编辑/详情）
✅ 合作方管理 CRUD（分页/新建/编辑/黑名单标识）
✅ 合同台账查询（8 维筛选 + 详情 + 项目/合作方关联）
✅ 审批引擎 POC
   ├── 提交审批 / 我的待办 / 审批详情
   ├── 同意 / 驳回 / 撤回 / 重新提交
   ├── 转办 / 加签 / 会签 (COUNTERSIGN) / 或签 (OR_SIGN)
   ├── 乐观锁 (taskVersion) + 幂等 (idempotencyKey)
   └── availableActions 动态权限
✅ 前端：登录页 / 首页 / 合同台账 / 项目列表 / 合作方列表 / 待办列表 / 审批详情
✅ Flyway 数据库迁移 (V1~V6：系统表/业务表/审批表/字典/演示数据)
✅ 集成测试 11 用例全部通过 (H2 + MySQL 双环境)
```

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
