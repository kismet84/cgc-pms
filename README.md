# CGC-PMS

CGC-PMS 是面向建筑工程总包项目全过程管理的前后端分离系统。系统以项目履约为主线，覆盖合同、成本、供应链、资金、现场管理和经营治理，并保留可审计的业务事实与过程留痕。

## 当前代码基线

| 项目 | 当前基线 |
| --- | --- |
| 发布线 | v1.5 开发中 |
| 后端 | `1.5.0-SNAPSHOT`（Java 21、Spring Boot 3） |
| 前端 | `1.5.0-dev.0`（Vue 3、TypeScript、Vite） |
| 数据库迁移 | MySQL Flyway 脚本已包含至 V211；H2 迁移用于本地兼容与测试 |
| 交付原则 | 历史测试、审计和上线结论不替代当前分支的验证结果 |

## 核心能力范围

| 领域 | 覆盖能力 |
| --- | --- |
| 项目履约 | 项目与组织、计划/WBS、现场日报、技术管理、质量安全、变更签证、竣工收尾 |
| 合约与经营 | 投标成本、合同、目标成本与动态利润、成本、收入回款、分包计量/结算/付款、发票 |
| 供应链与库存 | 采购申请与订单、到货验收、库存、领料退料、同项目跨仓调拨、供应商招采与履约评价 |
| 资金与核算 | 付款、现金日记账、资金计划与预测、会计凭证、期间控制与月结 |
| 过程治理 | 审批、RBAC 权限、通知与预警、审计留痕、驾驶舱、文件与对象存储 |

具体业务边界、已交付闭环与非目标以[业务标准](docs/README.md)和[项目地图](docs/product-intelligence/project-map.md)为准。

## 技术栈

| 层级 | 当前使用 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia |
| 后端 | Java 21、Spring Boot 3、Spring Security、MyBatis-Plus、Flyway |
| 数据与中间件 | MySQL 8、H2、Redis、MinIO |
| 部署与运维 | Docker Compose、Nginx、Spring Actuator、Prometheus 指标 |

## 快速启动

准备好 Docker Desktop，并基于 `deploy/.env.example` 配置本地 `deploy/.env` 后，在仓库根目录运行：

```bat
scripts\start-dev.bat
```

脚本会在缺少后端 JAR 时构建后端，并启动开发环境所需容器。手动启动、运行态刷新、环境变量和故障排查见：

- [快速开始](docs/standards/01-快速开始.md)
- [部署运维手册](docs/standards/10-部署运维手册.md)

## 本地访问地址

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| Swagger | http://localhost:8080/api/swagger-ui.html |
| MinIO 控制台 | http://localhost:9001 |
| MySQL | `localhost:3307` |
| Redis | `localhost:6379` |

## 文档入口

- [文档中心](docs/README.md)
- [系统架构](docs/standards/02-系统架构.md)
- [业务模块说明](docs/standards/03-业务模块说明.md)
- [API 契约](docs/standards/06-API契约规范.md)
- [测试规范](docs/standards/09-测试规范.md)
- [部署运维手册](docs/standards/10-部署运维手册.md)
- [安全规范](docs/standards/11-安全规范.md)
- [当前工作焦点](docs/backlog/current-focus.md)
- [项目地图与迭代决策](docs/product-intelligence/README.md)

## 协作规则

- [AGENTS.override.md](AGENTS.override.md)：项目协作、授权与收口规则的最高优先级入口
- [AGENTS.md](AGENTS.md)：仓库级基础规则与常用入口
- [Codex 任务执行策略](docs/standards/codex-task-execution-policy.md)：任务状态、验证、Git 和沟通约定

## 验证与交付提醒

- 不复用历史测试数量、覆盖率、审计结论或上线状态作为当前交付证据
- 前后端运行态刷新后，按现行规范等待 `180 秒`，再执行健康、接口或页面验收
- 本地前端验收默认入口为 `http://localhost:5173`
