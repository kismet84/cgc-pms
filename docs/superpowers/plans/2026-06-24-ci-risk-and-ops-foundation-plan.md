# CI 风险解除与运维基础设施就绪计划

> 编写日期：2026-06-24  
> 目标：按 **#1 JaCoCo → #2 SQL CI → #3 Prometheus → #4 备份** 的顺序推进，优先解除 CI 阻塞风险，再完成最小可观测性与恢复能力建设。

---

## 一、任务拆解

### 模块 A：CI 风险解除（JaCoCo）

#### 任务 A1：确认 JaCoCo 当前基线与门禁差距
- **任务名称与简要说明**  
  统一当前覆盖率真实值、CI 阈值、Maven/JaCoCo 检查口径，消除“本地能过、CI 阻塞”的不确定性。
- **输入/输出**  
  - 输入：`backend/target/site/jacoco/jacoco.csv`、`.github/workflows/ci.yml`、`backend/pom.xml`  
  - 输出：一份“当前值 / 目标值 / 差距 / 风险说明”表
- **涉及的文件建议**  
  - 🔨修改 `.github/workflows/ci.yml`  
  - 🔨修改 `backend/pom.xml`（如 JaCoCo 门禁在 Maven 侧）  
  - 🔨修改 `docs/未来开发计划.md`
- **复杂度**：P0
- **验收标准**
  - **Given** 当前代码基线  
    **When** 对比 JaCoCo 报告与 CI 规则  
    **Then** 能明确指出 instruction / branch 的当前值、阈值和阻塞差值
  - 检查项：
    - CI workflow 的覆盖率判定入口可定位
    - Maven 本地 `verify` 与 CI 覆盖率口径一致
    - 文档中不存在互相冲突的覆盖率数字

#### 任务 A2：执行 JaCoCo 风险缓释策略
- **任务名称与简要说明**  
  通过“临时下调门禁到稳定基线”或“补最后一批高收益测试”来解除 CI 风险。
- **输入/输出**  
  - 输入：任务 A1 的基线差距结论  
  - 输出：CI 不再因 JaCoCo 门禁无意义阻塞
- **涉及的文件建议**  
  - 🔨修改 `.github/workflows/ci.yml`  
  - 🔨修改 `backend/pom.xml`  
  - 🔨修改 `backend/src/test/java/**`（若选择补测）
- **复杂度**：P0
- **依赖**：任务 A1
- **验收标准**
  - **Given** 当前主分支代码  
    **When** 运行 backend CI 或 `mvn verify`  
    **Then** JaCoCo 不再成为当前阶段的合并阻塞项
  - **边界条件**
    - 若临时下调阈值，则必须明确写成“当前稳定基线”而非随意放宽
  - **异常场景**
    - 若补测引入新失败，可回退到“先调整阈值再继续补测”的策略

#### 任务 A3：补最后一批高收益低分支 Service 测试（可选但推荐）
- **任务名称与简要说明**  
  继续补测 `AuthService` 等低分支高收益服务，为恢复长期 80%/70% 门禁铺路。
- **输入/输出**  
  - 输入：JaCoCo CSV 低分支类排序  
  - 输出：新增测试文件或增强测试场景
- **涉及的文件建议**  
  - ✨新增 `backend/src/test/java/com/cgcpms/auth/service/AuthServiceTest.java`
- **复杂度**：P1
- **依赖**：任务 A2（若选择补测优先）
- **验收标准**
  - **Given** 目标 service 当前低分支  
    **When** 增加失败/禁用/空权限等分支测试  
    **Then** 该 service 分支覆盖率有实质性提升

---

### 模块 B：SQL 安全门禁接入 CI

#### 任务 B1：接入 `check-sql-safety.ps1` 到 GitHub Actions
- **任务名称与简要说明**  
  把已存在的 SQL 注入静态扫描脚本正式纳入 PR / push 自动检查。
- **输入/输出**  
  - 输入：`scripts/check-sql-safety.ps1`  
  - 输出：CI 中新增独立 SQL Safety 步骤
- **涉及的文件建议**  
  - 🔨修改 `.github/workflows/ci.yml`  
  - 🔨修改 `scripts/check-sql-safety.ps1`（若需统一 exit code / 输出）
- **复杂度**：P0
- **依赖**：任务 A2
- **验收标准**
  - **Given** 含 SQL 改动的 PR  
    **When** 触发 CI  
    **Then** SQL Safety 步骤自动执行
  - **异常场景**
    - Given 构造危险 SQL 模式  
      When 扫描执行  
      Then job 明确失败并输出命中原因

#### 任务 B2：定义误报处理与豁免机制
- **任务名称与简要说明**  
  确保门禁上线后可持续使用，不因误报而被团队绕过。
- **输入/输出**  
  - 输入：扫描规则与历史误报样本  
  - 输出：文档化豁免与处理流程
- **涉及的文件建议**  
  - 🔨修改 `docs/11-安全规范.md`
- **复杂度**：P1
- **依赖**：任务 B1
- **验收标准**
  - 检查项：
    - 误报处理路径有文档
    - 豁免机制可追踪
    - 安全门禁不会因临时问题被整体关闭

---

### 模块 C：Prometheus 监控基础

#### 任务 C1：接入 Prometheus Registry 并暴露 `/actuator/prometheus`
- **任务名称与简要说明**  
  在现有 Actuator 基础上接入 Micrometer Prometheus 导出能力。
- **输入/输出**  
  - 输入：当前 Spring Boot Actuator 配置  
  - 输出：后端服务可输出 Prometheus 指标
- **涉及的文件建议**  
  - 🔨修改 `backend/pom.xml`  
  - 🔨修改 `backend/src/main/resources/application-dev.yml`  
  - 🔨修改 `backend/src/main/resources/application-prod.yml`
- **复杂度**：P1
- **依赖**：任务 B1
- **验收标准**
  - **Given** 后端启动成功  
    **When** 请求 `/actuator/prometheus`  
    **Then** 返回标准 Prometheus 文本格式指标
  - **边界条件**
    - dev/prod 暴露策略明确
  - **异常场景**
    - endpoint 不应影响现有 `/actuator/health`、业务 API 正常访问

#### 任务 C2：新增 Prometheus 抓取配置与最小指标清单
- **任务名称与简要说明**  
  让指标不是“能导出就算完成”，而是可以被真正抓取和运维使用。
- **输入/输出**  
  - 输入：任务 C1 提供的指标 endpoint  
  - 输出：Prometheus target 配置 + 基础监控指标清单
- **涉及的文件建议**  
  - ✨新增 `deploy/monitoring/prometheus.yml`  
  - 🔨修改 `deploy/docker-compose*.yml`  
  - 🔨修改 `docs/10-部署运维手册.md`
- **复杂度**：P1
- **依赖**：任务 C1
- **验收标准**
  - **Given** Prometheus 启动  
    **When** 查看 targets  
    **Then** backend target 为 `UP`
  - 检查项：
    - JVM / GC 指标可见
    - HTTP 请求量与耗时指标可见
    - 数据源连接池指标可见

---

### 模块 D：数据库备份与恢复基础

#### 任务 D1：设计并落地 MySQL 全量 + binlog 增量备份脚本
- **任务名称与简要说明**  
  建立每天全量、持续增量的最小可靠备份能力。
- **输入/输出**  
  - 输入：当前 MySQL 部署方式、binlog 配置、存储路径约束  
  - 输出：备份脚本 + 命名规则 + 保留策略
- **涉及的文件建议**  
  - ✨新增 `scripts/mysql-backup.ps1` 或 `scripts/mysql-backup.sh`  
  - 🔨修改 `deploy/.env.example`  
  - 🔨修改 `docs/10-部署运维手册.md`
- **复杂度**：P1
- **依赖**：任务 C2 可并行，但建议在监控基础完成后推进
- **验收标准**
  - **Given** MySQL 正常运行  
    **When** 执行备份脚本  
    **Then** 生成可识别的全量备份文件
  - **边界条件**
    - 备份文件带时间戳  
    - 保留策略清晰（如 7 天 / 30 天）
  - **异常场景**
    - 连接失败或磁盘路径异常时返回非 0 退出码

#### 任务 D2：编写最小恢复脚本并完成一次演练
- **任务名称与简要说明**  
  将“有备份”提升为“可恢复”。
- **输入/输出**  
  - 输入：任务 D1 的备份文件  
  - 输出：恢复脚本 + 恢复演练记录
- **涉及的文件建议**  
  - ✨新增 `scripts/mysql-restore.ps1` 或 `scripts/mysql-restore.sh`  
  - 🔨修改 `docs/10-部署运维手册.md`
- **复杂度**：P1
- **依赖**：任务 D1
- **验收标准**
  - **Given** 最近一次全量备份  
    **When** 恢复到测试库  
    **Then** 数据库可连通并通过最小健康校验
  - **异常场景**
    - 恢复失败时不影响原生产实例

---

## 二、改动文件清单与计划

- 🔨修改 `.github/workflows/ci.yml`  
  调整 JaCoCo 风险控制策略，增加 SQL Safety 扫描步骤，确保 CI 从“脆弱阻塞”转为“可控门禁”。

- 🔨修改 `backend/pom.xml`  
  如 JaCoCo check 位于 Maven 侧，则同步调整阈值或 profile；同时补 Prometheus registry 依赖（如当前仍不完整）。

- 🔨修改 `docs/未来开发计划.md`  
  将当前基线、缓释策略与阶段目标统一，避免文档与实际运行状态脱节。

- 🔨修改 `scripts/check-sql-safety.ps1`  
  标准化扫描结果输出、失败返回码以及豁免标记约定，便于 CI 直接消费。

- 🔨修改 `docs/11-安全规范.md`  
  加入 SQL 安全门禁说明、误报处理、豁免规则。

- 🔨修改 `backend/src/main/resources/application-dev.yml`  
  打开或调整 dev 环境的 Actuator/Prometheus 暴露配置。

- 🔨修改 `backend/src/main/resources/application-prod.yml`  
  加入 prod 环境 Prometheus 相关配置，并明确暴露边界。

- ✨新增 `deploy/monitoring/prometheus.yml`  
  定义 Prometheus 抓取 backend 指标的最小配置。

- 🔨修改 `deploy/docker-compose*.yml`  
  如需将 Prometheus 纳入现有 compose，则补服务编排和端口映射。

- ✨新增 `scripts/mysql-backup.ps1` / `scripts/mysql-backup.sh`  
  实现 MySQL 全量备份与 binlog 增量归档的最小脚本。

- ✨新增 `scripts/mysql-restore.ps1` / `scripts/mysql-restore.sh`  
  实现恢复到测试库的最小安全脚本。

- 🔨修改 `deploy/.env.example`  
  增加备份路径、MySQL 连接、binlog 相关环境变量说明。

- 🔨修改 `docs/10-部署运维手册.md`  
  增加 Prometheus 部署、指标校验、备份恢复和演练流程。

- ✨新增 `docs/superpowers/plans/2026-06-24-ci-risk-and-ops-foundation-plan.md`  
  保存本计划，作为后续执行与交接基线。

---

## 三、数据流变化

### 1. JaCoCo / CI
开发者提交代码  
→ GitHub Actions 触发  
→ backend test / jacoco check 运行  
→ 若低于当前阶段阈值则 fail  
→ 若达标则进入后续 SQL Safety / build / deploy 流程

### 2. SQL CI
开发者提交包含 SQL / migration / mapper 改动  
→ CI 调用 `check-sql-safety.ps1`  
→ 扫描危险模式  
→ 命中则失败并提示  
→ 未命中则继续

### 3. Prometheus
后端启动  
→ `/actuator/prometheus` 输出指标  
→ Prometheus 定时抓取  
→ 指标进入时序数据库  
→ 运维后续可在 Dashboard/告警中消费

### 4. 备份
定时任务 / 手工触发  
→ 执行 MySQL 全量备份  
→ 同步归档 binlog 增量  
→ 写入备份目录/对象存储  
→ 恢复脚本从备份源恢复到测试库完成演练

---

## 四、影响范围与回归测试建议

### 1. GitHub Actions / CI 主链
- **影响范围**：所有 push / PR 流程  
- **回归建议**：创建一个仅改文档的 PR 和一个改后端代码的 PR，确认 workflow 路径和门禁行为都符合预期

### 2. SQL 相关开发流程
- **影响范围**：Mapper、Flyway migration、脚本目录  
- **回归建议**：构造一个故意命中的 SQL 风险样例，确认 SQL Safety job 能正确 fail

### 3. Spring Boot Actuator / 运维暴露面
- **影响范围**：后端运行时配置、反代、监控接入  
- **回归建议**：验证 `/actuator/health`、`/actuator/info`、`/actuator/prometheus` 同时可用，且不影响业务 API

### 4. 部署编排
- **影响范围**：`deploy/` 下 Docker Compose 和环境变量  
- **回归建议**：在开发环境 compose 中拉起 Prometheus，确认 backend target 能抓取且不破坏原 5 服务编排

### 5. 数据库运维链路
- **影响范围**：备份文件、恢复脚本、运维手册  
- **回归建议**：至少执行一次“全量备份 → 恢复到新库 → 健康检查”闭环验证

---

## 五、冒烟测试方案

1. 运行 `mvn verify`，确认 backend 构建、测试、JaCoCo 检查链路通过。  
2. 触发 CI，确认 JaCoCo 不再因不合理阈值阻塞当前阶段合并。  
3. 在分支中构造 SQL 风险样例，确认 SQL Safety 步骤明确失败。  
4. 启动后端后访问 `/actuator/prometheus`，确认指标文本可见。  
5. 启动 Prometheus 并查看 targets，确认 backend target 为 `UP`。  
6. 执行一次 MySQL 全量备份脚本，确认生成带时间戳的备份文件。  
7. 执行一次恢复脚本到测试库，确认可连接并通过最小校验 SQL。  
8. 验证原有 frontend/backend/E2E 主要 CI 路径未因新增步骤出现异常超时或误失败。  

---

## 六、计划书写入

- **路径**：`D:\projects-test\cgc-pms\docs\superpowers\plans`
- **文件名**：`2026-06-24-ci-risk-and-ops-foundation-plan.md`
