# 第26条主线 M6.1 后端安全整改验收报告

报告日期：2026-07-08  
报告类型：M6.1 定向复核 / 后端安全整改验收  
报告边界：本次仅读取计划书、既有审计/收口报告、M6.1 改动代码与测试，运行后端定向测试与一次后端全量测试复核；未修改业务代码、配置、测试源码、运行环境或 Git 状态。  
报告范围：B1 成本摘要项目级访问控制、B3 Token blacklist/Redis 生产降级语义。

## 1. 总体裁决

- B1：通过。
- B3：通过。
- M6.1 阶段结论：通过 / 非阻塞。
- 是否可据此认定 M6.1 已关闭 B1、B3：可以。

依据：

1. `CostSummaryService` 已在当前用户入口 `refreshSummary(Long)`、`getProjectSummary(Long)`、`getSummaryHistory(Long)` 统一调用 `ProjectAccessChecker.checkAccess(...)`，不再只校验“项目属于当前租户”。
2. `JwtAuthenticationFilter` 已在 `prod` profile 下对 `TokenBlacklistService` 缺失执行拒绝放行；`TokenBlacklistService.isBlacklisted(...)` 在 Redis 检查异常时返回 `true`，形成 fail-close。
3. 派工单要求的定向测试通过，且日志与源码语义一致。

## 2. B1 复核结论

结论：通过。  
阻塞/非阻塞：非阻塞。  
对应阻塞项是否关闭：已关闭。

代码证据：

1. `backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java:77` 在手动刷新入口先执行 `projectAccessChecker.checkAccess(projectId, "刷新成本摘要")`。
2. `backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java:248` 在读取最新摘要入口先执行 `projectAccessChecker.checkAccess(projectId, "查看成本摘要")`。
3. `backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java:445` 在历史摘要入口先执行 `projectAccessChecker.checkAccess(projectId, "查看成本摘要历史")`。
4. `backend/src/main/java/com/cgcpms/project/auth/ProjectAccessChecker.java:39-85` 仍保持原有 fail-close 语义：跨租户返回 `PROJECT_NOT_FOUND`，同租户无项目权限返回 `PROJECT_ACCESS_DENIED`，管理员/超级管理员、项目经理、`ALL` 数据范围用户允许访问。

测试证据：

1. `backend/src/test/java/com/cgcpms/cost/CostSummaryControllerTest.java:96-145` 继续覆盖管理员对 `getLatest`、`refresh`、`history` 三条链路的成功路径。
2. `backend/src/test/java/com/cgcpms/cost/CostSummaryControllerTest.java:148-176` 新增同租户无项目权限用户对 `getLatest`、`refresh`、`history` 的 403 断言。
3. `backend/src/test/java/com/cgcpms/cost/CostSummaryControllerTest.java:178-185` 新增跨租户用户访问被隐藏为 `PROJECT_NOT_FOUND`。
4. `backend/src/test/java/com/cgcpms/cost/CostSummaryControllerTest.java:188-208` 新增有权限普通用户覆盖：`ALL` 数据范围用户允许访问、项目经理允许访问。

判断：

1. 就“当前用户链路是否在 refresh/get/history 调用 `ProjectAccessChecker`”这一核心整改目标，代码层已经闭环。
2. 就“管理员/有权限用户/同租户无权限/跨租户”这一验收矩阵，测试已覆盖四类身份；其中非管理员正向与跨租户场景主要落在 `getLatest`，未把正向矩阵完全铺到 `refresh/history`，但三条链路共用同一服务层检查点，本次不足以否决 B1 关闭。

剩余风险：

1. `refresh/history` 缺少“项目经理”或 `ALL` 数据范围用户的正向断言，属于非阻塞测试缺口；后续若继续加固，可补最小两条正向集成测试。

## 3. B3 复核结论

结论：通过。  
阻塞/非阻塞：非阻塞。  
对应阻塞项是否关闭：已关闭。

代码证据：

1. `backend/src/main/java/com/cgcpms/auth/filter/JwtAuthenticationFilter.java:103-116` 已明确：
   - `TokenBlacklistService` 缺失且 `prod` profile 激活时，直接 `401` 拒绝请求。
   - 非 `prod` 环境才保留“记录告警后继续运行”的本地降级语义。
2. `backend/src/main/java/com/cgcpms/auth/service/TokenBlacklistService.java:59-65` 已明确 Redis 检查异常时返回 `true`，即 fail-close，不放行可疑 token。

测试证据：

1. `backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java:45-80` 新增 `prod` profile 下 blacklist service 缺失返回 `401`，并断言过滤器不继续调用后续 `chain.doFilter(...)`。
2. `backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java:82-116` 保留 `local` profile 下缺失 service 仍可继续的测试，证明测试环境与生产语义已被区分。
3. `backend/src/test/java/com/cgcpms/auth/service/TokenBlacklistServiceTest.java:106-129` 已覆盖 Redis 操作异常时 `isBlacklisted(...)` 返回 `true` 的 fail-close 语义。

判断：

1. 生产环境下“service 缺失”已改为 fail-closed。
2. Redis 检查异常路径由 `TokenBlacklistService` 自身收敛为 fail-closed，进入过滤器时等价于“不放行”。
3. 这次整改已经消除旧审计报告中“Redis/token blacklist 不可用仍继续放行”的生产语义问题。

剩余风险：

1. `AuthController.refresh/logout` 在 `blacklistProvider.getIfAvailable()` 返回 `null` 时仍是“跳过写黑名单”的语义；本次派工重点是认证过滤链路和生产放行语义，未要求同步把刷新/登出链路改成同等级 fail-close。当前不构成 B3 退回，但若后续要把“会话吊销语义”做全链路一致化，仍建议继续收口。

## 4. 测试与命令结果

### 4.1 派工单要求的定向测试

命令：

```powershell
.\mvnw.cmd -q "-Dtest=CostSummaryControllerTest,JwtAuthenticationFilterTest" test "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：通过。  
关键摘要：

1. `com.cgcpms.cost.CostSummaryControllerTest`：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`
2. `com.cgcpms.auth.filter.JwtAuthenticationFilterTest`：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
3. 日志与预期一致：
   - `prod` 缺失 blacklist service 时出现 `BLACKLIST_UNAVAILABLE: prod profile requires TokenBlacklistService，拒绝本次请求`
   - `local` 场景仍出现 `BLACKLIST_UNAVAILABLE: TokenBlacklistService 不可用（Redis 未配置），黑名单保护缺失`
   - 同租户无项目权限用户访问成本摘要历史时返回 `PROJECT_ACCESS_DENIED`
   - 跨租户访问返回 `PROJECT_NOT_FOUND`

### 4.2 补充后端全量测试

命令：

```powershell
.\mvnw.cmd -q test "-Djasypt.encryptor.password=dev-jasypt-key"
```

结果：失败。  
摘要：`Tests run: 250, Failures: 1, Errors: 142, Skipped: 1`

失败特征：

1. 大量既有测试报错为 `Unable to find a @SpringBootConfiguration by searching packages upwards from the test`。
2. 另有少量既有失败，如 `SqlSafetyTest.allPatternsShouldMatchTheirTargets:128 NoClassDefFound ...`。
3. 这些失败横跨审计、合同、成本、看板、采购、库存、系统、工作流等多个模块，不呈现为本次 B1/B3 变更直接引入的单点回归。

验收解释：

1. 全量后端当前不具备“绿色基线”，因此本次不能把“全量失败”归因到 M6.1 整改本身。
2. M6.1 的通过判定以派工单要求的定向测试和代码闭环证据为准；全量红基线应单独进入仓库测试治理，不作为本次 B1/B3 退回依据。

## 5. 是否遵守禁止事项

结论：已遵守。

1. 未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`。
2. 未修改业务代码、配置、测试源码、运行环境或 Git 状态。
3. 未提交 Git。
4. 本次唯一写入为正式验收报告：`D:\projects-test\cgc-pms\docs\quality\mainline-26-m6-1-backend-security-verification-2026-07-08.md`。

## 6. 最终结论

- 报告路径：`D:\projects-test\cgc-pms\docs\quality\mainline-26-m6-1-backend-security-verification-2026-07-08.md`
- B1：通过
- B3：通过
- 阻塞/非阻塞：非阻塞
- 剩余风险：
  1. B1 正向非管理员场景尚未把 `refresh/history` 全补齐，属于非阻塞测试缺口。
  2. B3 已解决过滤链路生产放行语义，但刷新/登出链路的 blacklist service 缺失语义仍可后续继续统一。
  3. 后端全量测试基线当前为红，不应误判为本次整改回归，但需要单独治理。
