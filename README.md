# 建筑工程总包项目全过程管理系统 (CGC-PMS)

Construction General Contracting Project Management System

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia |
| 后端 | Java 21 + Spring Boot + MyBatis-Plus + JWT |
| 数据库 | MySQL 8.0 + Flyway |
| 缓存 | Redis 7 |
| 文件存储 | MinIO |
| 部署 | Docker + Docker Compose |

## 项目结构

```
cgc-pms/
├── backend/              # Spring Boot 后端
├── frontend-admin/       # Vue 3 管理后台
├── mobile/               # uni-app 移动端（预留）
├── database/             # Flyway 迁移脚本
├── deploy/               # Docker Compose 配置
├── docs/                 # 开发文档
├── scripts/              # 辅助脚本
└── README.md
```

## 快速启动

### 1. 启动中间件

```bash
cd deploy
docker compose up -d
```

启动后可用服务：
- MySQL: `localhost:3306` (root/root123, 数据库 cgc_pms)
- Redis: `localhost:6379`
- MinIO Console: `http://localhost:9001` (minioadmin/minioadmin123)
- MinIO API: `http://localhost:9000`

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger 文档：`http://localhost:8080/api/swagger-ui.html`

### 3. 启动前端

```bash
cd frontend-admin
pnpm install
pnpm dev
```

管理后台：`http://localhost:5173`

## 开发规范

- 分支模型：`main` / `develop` / `feature/*` / `hotfix/*`
- 提交信息：`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`
- 代码规范：ESLint + Prettier (前端) / Checkstyle (后端)

## 第 1 阶段目标

- [x] 工程骨架搭建
- [ ] 数据库初始化和 Flyway 迁移
- [ ] 登录鉴权 (JWT + RBAC)
- [ ] 审批引擎 POC
- [ ] 合同台账 POC
- [ ] 项目管理 CRUD
- [ ] 合作方管理 CRUD

## 开发文档

详见 `docs/开发文档_v2.3/`
