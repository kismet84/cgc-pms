# 阶段 1：仓库盘点

审计时间：2026-08-19（Asia/Shanghai）
基线：`master@8ff20a3c10bcad015883db59f600b19af7a4a729`，包含审计开始前 6 个已修改文件和 1 个未跟踪文件。结论针对整个工作区快照，不把 HEAD 与脏差异拆开裁决。

## 范围与规模

- Codemap：36 个节点、91 条边、18 条业务流；锁文件记录 2,936 个纳入扫描文件、4,200 个仓库文件。
- 后端：906 个 Java 主源码文件，Spring Boot 3.5.16、Java 21、MyBatis-Plus、Flyway、MySQL/H2、Redis、MinIO、ClamAV、Resilience4j、JWT、Maven、JaCoCo。
- 前端：254 个 `src` 文件，Vue 3、Vite、Pinia、TypeScript、pnpm 11、Vitest、Playwright。
- 测试：424 个后端测试资源/源码文件、89 个前端单测文件、40 个 E2E 文件。
- 部署：后端/前端/Office 预览 Dockerfile；dev、prod、monitoring、演练 Compose；GitHub Actions CI 与 post-merge 工作流。

## 基线保护

开始时既有文件内容 SHA-256 已冻结；审计报告位于本目录，不纳入被审对象。结束时必须复核分支、HEAD、既有文件散列和原有 tracked diff 散列。

## 排除与限制

- 按根规则未读取禁止目录。
- 未连接或写入数据库，未启动、刷新或修改本地运行态。
- 未执行 commit、push、PR、合并、发布或任何非本地环境操作。

结论：仓库范围可识别，Codemap 当前可验证；盘点阶段未发现独立风险。
