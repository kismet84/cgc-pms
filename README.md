# CGC-PMS - 建筑工程总包项目全过程管理系统

CGC-PMS 是面向建筑工程总包项目的全过程管理系统，覆盖项目、合同、成本、采购库存、分包、付款、发票、审批、预警和经营驾驶舱。

## 当前状态

- 当前文档入口：[docs/README.md](docs/README.md)
- 多助手协作规范：[docs/12-多助手协作规范.md](docs/12-多助手协作规范.md)
- 历史审计结论见：[docs/README.md#质量与审计](docs/README.md#质量与审计)
- 历史测试数量、覆盖率和上线结论只作为历史记录；上线前必须重新执行当前测试命令。
- 当前已放行驾驶舱：商务经理、项目经理、采购经理、生产经理
- 当前冻结驾驶舱：总工程师
- 默认演示环境已补齐采购经理、生产经理的最小 demo 数据；页面验收不再按空态作为默认结果

## 技术栈

| 层级 | 当前使用 |
|------|----------|
| 前端 | Vue 3.5、TypeScript 5.6、Vite 6、Ant Design Vue 4、Pinia、Vue Router、VxeTable、ECharts |
| 后端 | Java 21、Spring Boot 3.3.5、Spring Security、MyBatis-Plus 3.5.9、Flyway、JJWT 0.12、Jasypt |
| 数据 | MySQL 8、H2 MySQL 模式、Redis 7、MinIO |
| 质量 | JUnit 5、MockMvc、Vitest、Playwright、vue-tsc、bundle size check |
| 部署 | Docker Compose、Nginx、Actuator、Prometheus 指标 |

## 快速启动

Windows 本地推荐：

```bash
scripts\start-dev.bat
```

手动启动：

```bash
cd deploy
copy .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd ..\backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

cd ..\frontend-admin
pnpm install
pnpm dev
```

访问入口：

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| Swagger | http://localhost:8080/api/swagger-ui.html |
| MinIO 控制台 | http://localhost:9001 |

## 项目结构

```text
cgc-pms/
├── backend/                 # Spring Boot 后端
├── frontend-admin/          # Vue 管理后台
├── deploy/                  # Docker Compose、Nginx、环境模板
├── docs/                    # 当前文档中心
├── memory/                  # 本仓库经验记录
├── scripts/                 # 启动、重建、备份、检查脚本
├── mobile/                  # 移动端相关代码或预留目录
└── .archive/                # 历史归档
```

## 后端模块

当前后端业务模块位于 `backend/src/main/java/com/cgcpms/`：

`accounting`、`alert`、`audit`、`auth`、`bid`、`common`、`config`、`contract`、`cost`、`dashboard`、`file`、`inventory`、`invoice`、`material`、`notification`、`org`、`overhead`、`partner`、`payment`、`project`、`purchase`、`receipt`、`requisition`、`revenue`、`settlement`、`subcontract`、`system`、`variation`、`workflow`。

## 前端页面域

当前前端页面位于 `frontend-admin/src/pages/`：

`alert`、`approval`、`contract`、`cost`、`cost-subject`、`cost-target`、`dashboard`、`error`、`help`、`inventory`、`invoice`、`login`、`material`、`org`、`partner`、`payment`、`profile`、`project`、`purchase`、`receipt`、`requisition`、`settings`、`settlement`、`subcontract`、`system`、`variation`。

## 常用验证

后端：

```bash
cd backend
$env:TEST_JWT_SECRET="<运行前临时生成的测试密钥>"
.\mvnw.cmd test
.\mvnw.cmd verify
```

`backend/src/main/resources/application-dev.yml` 与 `backend/src/test/resources/application-test.yml`
中的数据库、MinIO、JWT 配置不再提供可用默认凭据；本地运行或测试前请通过环境变量注入。

前端：

```bash
cd frontend-admin
pnpm build
pnpm test:unit
pnpm test:coverage
pnpm check:bundle-size
pnpm exec playwright test
```

文档改动：

```bash
git diff -- README.md
git check-ignore -v docs/README.md memory/MEMORY.md
```

当前 `.gitignore` 会忽略 `docs/` 和 `memory/`；这些目录的本地改动需要用文件检查和链接检查验证，不能只看 `git diff`。

## 开发规则入口

- 项目规则优先级：[AGENTS.override.md](AGENTS.override.md) > [AGENTS.md](AGENTS.md)
- 快速开始：[docs/01-快速开始.md](docs/01-快速开始.md)
- 系统架构：[docs/02-系统架构.md](docs/02-系统架构.md)
- 后端规范：[docs/04-后端开发规范.md](docs/04-后端开发规范.md)
- 前端规范：[docs/05-前端开发规范.md](docs/05-前端开发规范.md)
- 数据库与迁移：[docs/07-数据库与迁移规范.md](docs/07-数据库与迁移规范.md)
- 测试规范：[docs/09-测试规范.md](docs/09-测试规范.md)
- 安全规范：[docs/11-安全规范.md](docs/11-安全规范.md)
