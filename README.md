# CGC-PMS

CGC-PMS 是面向建筑工程总包项目全过程管理的前后端分离系统，覆盖项目、合同、成本、采购库存、分包、付款、发票、审批、预警和驾驶舱等核心业务。

## 核心能力范围

- 围绕工程项目主线管理合同、成本、采购、库存、分包、结算、付款和发票流程
- 提供审批、通知、预警、审计留痕等过程管控能力
- 提供管理后台、接口服务、对象存储和基础运行支撑，满足本地开发与部署运维需要

## 技术栈摘要

| 层级 | 当前使用 |
|------|----------|
| 前端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia |
| 后端 | Java 21、Spring Boot 3、Spring Security、MyBatis-Plus、Flyway |
| 数据与中间件 | MySQL 8 / H2、Redis、MinIO |
| 部署与运维 | Docker Compose、Nginx、Spring Actuator、Prometheus 指标 |

## 快速启动入口

本地优先使用：

```bash
scripts\start-dev.bat
```

需要手动启动、运行态刷新或常见故障处理时，直接查看：

- [快速开始](docs/01-快速开始.md)
- [部署运维手册](docs/10-部署运维手册.md)

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| Swagger | http://localhost:8080/api/swagger-ui.html |
| MinIO 控制台 | http://localhost:9001 |

## 文档入口

- [文档总入口](docs/README.md)
- [快速开始](docs/01-快速开始.md)
- [系统架构](docs/02-系统架构.md)
- [业务模块说明](docs/03-业务模块说明.md)
- [部署运维手册](docs/10-部署运维手册.md)

需要了解更细的模块边界、开发规范、测试规范或上线结论时，从 `docs/README.md` 继续进入对应专题文档，不在此处重复展开。

## 规则入口

- [AGENTS.override.md](AGENTS.override.md)
- [AGENTS.md](AGENTS.md)

项目协作与执行边界以 `AGENTS.override.md` 为最高优先级，`AGENTS.md` 提供仓库级基础规则和常用入口。

## 当前验证与交付提醒

- 历史审计、测试数量、覆盖率和上线结论只作参考；当前交付前必须重新执行并确认本次验证结果
- 前后端运行态刷新后，统一等待 `180秒` 再做健康检查、接口验收或页面验收
- 本地前端默认验证入口为 `http://localhost:5173`
