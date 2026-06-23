# 质量加固实施方案（审计修订版）

> **基于**: `2026-06-22-quality-hardening.md` + `2026-06-22 审计报告`
> **修订日期**: 2026-06-22
> **修订原因**: 审计发现覆盖率基线未知、CI 门禁形同虚设、工作量估计偏低、部分技术细节需要修正

---

## 审计关键发现 & 对本方案的影响

| 发现 | 影响 | 本方案处理方式 |
|---|---|---|
| 后端 JaCoCo 从未执行（CI 跑 `mvn test` 而非 `mvn verify`） | 覆盖率基线完全未知 | Phase 0 先跑一次 `mvn verify` 获取真实基线 |
| 前端 `@vitest/coverage-v8` 未安装，`test:coverage` 脚本缺失 | 覆盖率阈值 80% 形同虚设 | Phase 1 补齐工具链，不改阈值数值 |
| `SettlementStatusConstants` 已定义但 12 处未引用 | 常量迁移工作量确认 | Task 3 保留，修正技术细节 |
| `StlSettlementService.java` 已是 100 行纯门面 | 架构测试首次即 PASS | 保留作为回归守卫，不期望"先失败" |
| 所有 `${}` 均为 Spring `@Value` 注入 | SQL 注入风险被高估 | Task 10 缩小扫描范围到 mapper 目录 |
| 文件类型白名单收紧丢弃 7 种格式 | 可能影响现有用户 | Task 12 标注为 BREAKING CHANGE |
| 6 个页面全部超标（802~1333 行） | 拆分工作量确认 | Task 6-9 保留，可并行执行 |
| E2E 现有 14 个 spec ~45 个 test() | 50+ 目标已接近 | Task 5 目标修正为 55+ |

---

## Phase 0: 建立真实基线（新增）

> **目标**: 在实施任何变更前，获取真实的覆盖率、测试数量和质量基线数据。
> **预计耗时**: 30 分钟
> **输出**: `docs/quality/baseline.json`

### Step 0.1: 运行后端 JaCoCo 覆盖率

```powershell
$env:JAVA_HOME = 'D:\projects-test\jdk-21\jdk-21.0.11+10'
cd backend
bash ./mvnw verify -Djasypt.encryptor.password=dev-jasypt-key
```

记录 `target/site/jacoco/index.html` 中的 **Instruction %** 和 **Branch %**。

### Step 0.2: 测试前端覆盖率工具链

```powershell
cd frontend-admin
pnpm add -D @vitest/coverage-v8@^4.1.0
```

在 `package.json` 添加:
```json
"test:coverage": "vitest run --coverage"
```

运行 `pnpm test:coverage` 获取当前 lines/functions/branches/statements 四项数值。

### Step 0.3: 创建基线脚本

创建 `scripts/quality-baseline.ps1`（使用原始方案中的逻辑），跑一次生成 `docs/quality/baseline.json`。

### Step 0.4: 根据基线调整后续目标

| 当前覆盖率 | Phase 2 目标调整 |
|---|---|
| < 30% | 目标改为 instruction 50% / branch 40%（Phase 3 再冲刺更高） |
| 30%-50% | 目标改为 instruction 60% / branch 50%（Phase 3 冲刺 72%/60%） |
| 50%-65% | 保持原目标 instruction 72% / branch 60% |
| > 65% | 可直接冲刺 instruction 80% / branch 70% |

**提交**:
```powershell
git add scripts/quality-baseline.ps1 docs/quality/.gitkeep docs/quality/baseline.json frontend-admin/package.json frontend-admin/pnpm-lock.yaml
git commit -m "test: establish quality baseline and coverage toolchain"
```

---

## Phase 1: CI 门禁骨架（修订版 Task 1-2）

> **目标**: 让 CI 真正执行覆盖率检查和类型检查，不提高任何阈值。
> **预计耗时**: 1 小时

### Task 1.1: 完善前端覆盖率配置

**修改 `frontend-admin/vitest.config.ts`** — 补充 coverage provider 和 include/exclude，但**保持 thresholds 80% 不变**:

```ts
coverage: {
  provider: 'v8',
  reporter: ['text', 'json-summary', 'html'],
  include: ['src/**/*.{ts,vue}'],
  exclude: [
    'src/**/*.d.ts',
    'src/main.ts',
    'src/components.d.ts',
    'src/types/**',
  ],
  thresholds: {
    lines: 80,
    functions: 80,
    branches: 80,
    statements: 80,
  },
},
```

> **说明**: vitest 的 `coverage.thresholds` 当前不生效的原因是 `--coverage` 未传递。补充 provider 后运行 `pnpm test:coverage` 即会触发门禁检查。阈值 80% 仅作**目标声明**，在 Phase 5 之前不设为硬失败（通过 `thresholdsAutoUpdate: true` 或暂不启用 `--coverage` 在 CI 中）。

**实际上**: 在 Phase 1，CI 中 `pnpm test:coverage` 先不设硬门禁（用 `|| true`），仅做报告生成。硬门禁在 Phase 5 最终确定。

### Task 1.2: 后端 JaCoCo 配置保持

`backend/pom.xml` 中 JaCoCo check 保持不变（instruction 0.60）。新增 branch 0.50:

```xml
<limit>
    <counter>BRANCH</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.50</minimum>
</limit>
```

> **注意**: Phase 0 会先跑一次 `mvn verify` 验证 60% 门禁是否通过。如不通过，本 Phase 不降低阈值（而是记录为待修复项，由 Phase 2/3 处理）。

### Task 1.3: 修正 CI 配置

`.github/workflows/ci.yml` 修改:

1. **触发分支**: `[master, main]`（增加 master）
2. **后端测试**: `./mvnw test` → `./mvnw verify`
3. **前端安装**: `pnpm install` → `pnpm install --frozen-lockfile`
4. **前端测试**: `pnpm test:unit` → `pnpm test:coverage`
5. **MySQL 测试**: **暂不删除** `continue-on-error: true`（在 Phase 5 E2E 稳定后再改为硬门禁）
6. **新增** coverage artifact 上传（后端 + 前端）
7. **新增** type-check job: `cd frontend-admin && pnpm type-check`

### Task 1.4: 验证

```powershell
cd backend
bash ./mvnw verify -Djasypt.encryptor.password=dev-jasypt-key
cd ..\frontend-admin
pnpm type-check
pnpm test:coverage
```

**预期**: 后端 `mvn verify` 可能失败（覆盖率 < 60%）——**此时只记录不降低阈值**。前端生成 `coverage/coverage-summary.json`。

**提交**:
```powershell
git add backend/pom.xml frontend-admin/vitest.config.ts .github/workflows/ci.yml
git commit -m "ci: establish quality gates in CI"
```

---

## Phase 2: 代码规范化（修订版 Task 3 + 6-9）

> **目标**: 消除结算字面量、拆分 6 个超大页面。这些任务互不依赖可并行。
> **预计耗时**: 4-6 小时

### Task 2A: 结算状态常量迁移

**修正点**:
1. `StlSettlementService.java` 已是 100 行门面，架构测试首次即 PASS（作为回归守卫保留）
2. WriteService 中使用 `"DRAFT"` 涉及三个独立字段，需精确匹配常量：
   - `approvalStatus` → `APPROVAL_DRAFT`
   - `status` → `STATUS_DRAFT`
   - `settlementStatus` → `SETTLEMENT_DRAFT`
3. Handler 中的 `"FINALIZED"` → `SETTLEMENT_FINALIZED`

**步骤**:

1. 在 `StlSettlementServiceTest` 增加状态流转回归测试（使用常量断言）
2. 替换 `SettlementWorkflowHandler.java`（4 处）
3. 替换 `StlSettlementQueryService.java`（2 处）
4. 替换 `StlSettlementWriteService.java`（6 处，**注意三态字段**）
5. 创建 `SettlementArchitectureTest.java`（门面守卫）
6. 运行：`rg -n '"DRAFT"|"FINALIZED"' backend/src/main/java/com/cgcpms/settlement` 只允许常量类命中

**提交**: `refactor: enforce settlement status constants`

### Task 2B: 拆分 org 页面（1333→≤500）

- 抽取 `useOrgTree.ts` composable
- 抽取 `OrgEditorModal.vue`
- 补充测试

**提交**: `refactor: split organization page`

### Task 2C: 拆分 dashboard 页面（1231→≤500）

- 抽取 `useDashboardData.ts` composable
- 抽取 `chartOptions.ts`（纯函数，不访问 store/API）
- 抽取 `DashboardCharts.vue`
- 补充测试

**提交**: `refactor: split dashboard page`

### Task 2D: 拆分 stock 页面（900→≤500） + receipt 页面（807→≤500）

- 抽取 `useStockQuery.ts` / `useReceiptForm.ts`
- 抽取 `StockTransactionModal.vue` / `ReceiptEditorModal.vue`
- 补充测试

**提交**: `refactor: split stock and receipt pages`

### Task 2E: 拆分 invoice 页面（841→≤500） + contract ledger 页面（802→≤500）

- 抽取 `useInvoiceTable.ts` / `useContractLedger.ts`
- 抽取 `InvoiceEditorModal.vue` / `ContractLedgerFilters.vue`
- 补充测试
- **硬性要求**: 不得新增 `any` 类型

**提交**: `refactor: split invoice and contract ledger pages`

---

## Phase 3: 安全加固（修订版 Task 10-13）

> **目标**: SQL 门禁、多维限流、文件上传校验、操作审计。
> **预计耗时**: 6-8 小时

### Task 3A: SQL 注入静态门禁（修订版）

**修订点**: 扫描范围从 `backend/src/main` **缩小到**:
- `backend/src/main/resources/mapper/**`（MyBatis XML）
- `backend/src/main/java/**/mapper/**`（Java Mapper）

排除 `@Value` 注解（Spring 配置注入不是 SQL 风险）。

**脚本逻辑**:

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# 只扫描 Mapper 目录，避免 Spring @Value 假阳性
$mapperDirs = @(
    "$root\backend\src\main\resources\mapper",
    "$root\backend\src\main\java\com\cgcpms"
)

$patterns = @(
    '\$\{',                          # MyBatis ${} 字符串替换（非 #{} 参数绑定）
    '@(Select|Update|Delete|Insert).*\+',  # 注解 SQL 字符串拼接
    '\.apply\(',                     # MyBatis-Plus apply() 动态 SQL
    '\.last\(',                      # MyBatis-Plus last() SQL 片段拼接
    '\.having\(',                    # having() 拼接
    '\bStatement\b'                  # 原生 JDBC Statement
)

$hits = Get-ChildItem $mapperDirs -Recurse -File |
    Select-String -Pattern $patterns |
    Where-Object { $_.Line -notmatch 'SQL-SAFETY: server-side-enum' }

if ($hits) {
    $hits | ForEach-Object { "$($_.Path):$($_.LineNumber): $($_.Line.Trim())" }
    exit 1
}
```

**步骤**:
1. 创建 `scripts/check-sql-safety.ps1`
2. 运行扫描，修复真实命中（值参数→MyBatis `#{}`绑定，排序字段→服务端枚举映射）
3. 创建 `SqlSafetyTest.java` 验证豁免注释机制
4. 接入 CI（backend job 在 `mvn verify` 之前运行）

**提交**: `security: add SQL injection gate`

### Task 3B: 多维限流

**新增存储抽象层**（解决 local profile 无 Redis 的问题）:

```java
// 接口 + Redis 实现 + 本地 fallback
@Component
@ConditionalOnProperty(name = "spring.redis.host")  // Redis 可用时
public class RedisRateLimitCounterStore implements RateLimitCounterStore { ... }

@Component
@ConditionalOnMissingBean(RateLimitCounterStore.class)  // Redis 不可用时
public class FallbackRateLimitCounterStore implements RateLimitCounterStore { ... }
```

**其他步骤与原方案一致**: 增加 `RateLimitKey` 枚举、修改 Aspect、标注敏感端点。

**提交**: `security: apply multidimensional rate limits`

### Task 3C: 文件上传类型校验（修订版）

**修订点**: 文件类型白名单收紧是 **BREAKING CHANGE**，需要在 commit message 中标注。

**允许矩阵**: PDF, JPEG, PNG, GIF, WebP, DOCX, XLSX, PPTX, TXT, CSV

**被移除**: doc, xls, ppt, bmp, zip, rar, 7z

> **BREAKING**: 这些格式不再被接受。如有已上传文件，不影响下载；新的 doc/xls/ppt/zip/rar/7z 上传将被拒绝。bmp 建议转 PNG。

**实现与原方案一致**，补充 `@ConditionalOnProperty` 在无 MinIO 环境下跳过。

**提交**: `security!: validate uploaded file content (BREAKING: restrict allowed file types)`

### Task 3D: 持久化操作审计

**修订点**:
1. **Flyway 版本号**: V91 → V10（V9 之后的下一个版本号）
2. **需要启用 `@EnableAsync`** 以支持 `@Async` 审计写入
3. **H2 迁移**: 复合索引语法与 MySQL 不同，需确认

**步骤与原方案一致**。

**提交**: `feat: persist tenant-scoped operation audits`

---

## Phase 4: E2E 主链路覆盖（修订版 Task 5）

> **目标**: 五条业务主链路的端到端测试覆盖，CI 集成。
> **预计耗时**: 3-4 小时

### 修订点

1. **唯一 ID 生成**: `Date.now() + Math.random()` → `crypto.randomUUID()`（避免 CI 并发冲突）
2. **目标**: 50+ → 55+ tests（当前已有 ~45 个）
3. **CI 依赖**: 保持 `backend-test-mysql.continue-on-error: true` 直到 E2E 稳定

### 步骤

1. 创建 `frontend-admin/e2e/fixtures/business-chain.ts`
2. 为五条链路增加业务断言测试
3. 修改 `playwright.config.ts` 增加 CI 报告配置
4. CI 增加 E2E job（依赖 backend-test-mysql + frontend-build）
5. 本地运行五条链路验证

**提交**: `test: cover five business chains in e2e`

---

## Phase 5: 最终门禁与文档（修订版 Task 14-15）

> **目标**: 根据 Phase 0 基线确定最终阈值，完成文档更新。
> **预计耗时**: 2-3 小时

### Task 5.1: 确定最终覆盖率阈值

**根据 Phase 0 基线决定**:

| 后端基线 | 最终 instruction 目标 | 最终 branch 目标 |
|---|---|---|
| < 30% | 55% | 40% |
| 30%-45% | 65% | 50% |
| 45%-60% | 75% | 60% |
| > 60% | 80% | 70% |

| 前端基线 | 最终四项目标 |
|---|---|
| < 40% | 55% |
| 40%-60% | 65% |
| > 60% | 80% |

> **原则**: 覆盖率目标必须是**可达的**——从当前基线出发，每个 Phase 最多提升 15-20 个百分点。如果基线过低，调整目标而不是删除测试或扩大排除范围。

### Task 5.2: 补充高价值测试

1. 审计失败隔离测试
2. 限流边界测试
3. 文件签名检测负向测试
4. 拆分后 composable 测试
5. 五条 E2E 链路的边界条件

### Task 5.3: 设置最终阈值

更新 `backend/pom.xml` 和 `frontend-admin/vitest.config.ts`，将 CI 中 `pnpm test:coverage` 的 `|| true` 移除。

### Task 5.4: 删除 MySQL CI 的 continue-on-error

在 E2E 和 MySQL 测试均稳定后，将 `continue-on-error: true` 改为 `false`。

### Task 5.5: 运行完整门禁

```powershell
cd backend
bash ./mvnw verify -Djasypt.encryptor.password=dev-jasypt-key
cd ..\frontend-admin
pnpm type-check
pnpm lint
pnpm test:coverage
pnpm build
pnpm exec playwright test
cd ..
powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1
```

**预期**: 全部退出码 0。

**提交**: `test: enforce final coverage thresholds`

### Task 5.6: 更新文档

1. `docs/09-测试规范.md`: 更新最终阈值和门禁流程
2. `docs/11-安全规范.md`: 更新 SQL 扫描、限流维度、上传矩阵、审计权限
3. `docs/未来开发计划.md`: 将已完成项标记为"已验收"，删除旧基线数字
4. 创建 `docs/quality/quality-hardening-acceptance.md`

**提交**: `docs: record quality hardening acceptance`

---

## 执行约束

1. **严格串行**: Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
2. **Phase 内部**: Task 2B-2E 可并行，Task 3A-3D 可并行
3. **每个 Phase 单独提交**（Phase 2 的每个 Task 也单独提交）
4. **后端命令**:
   ```powershell
   $env:JAVA_HOME = 'D:\projects-test\jdk-21\jdk-21.0.11+10'
   cd backend
   bash ./mvnw <goal> -Djasypt.encryptor.password=dev-jasypt-key
   ```
5. **不降低阈值策略**: 如果覆盖率达不到目标，优先补测试而非降低阈值或扩大排除范围
6. **中止条件**: Phase 0 基线出来后，如果后端 < 20% 或前端 < 30%，暂停执行，重新评估工作量

---

## 最终退出条件

- [ ] Phase 0: `docs/quality/baseline.json` 生成，真实覆盖率数据已知
- [ ] Phase 1: CI 中 `mvn verify` + `pnpm test:coverage` 正常运行（可能失败，但工具链就绪）
- [ ] Phase 2: `rg -n '"DRAFT"|"FINALIZED"' backend/.../settlement` 仅常量类命中（架构测试 PASS）；6 个页面均 ≤500 行
- [ ] Phase 3: SQL 扫描零命中；限流边界测试 PASS；文件签名测试 PASS；审计迁移 + 测试 PASS
- [ ] Phase 4: Playwright ≥55 tests，五条主链路全部 PASS，CI E2E job 正常
- [ ] Phase 5: 覆盖率达标（根据基线确定的目标值）；MySQL CI 硬门禁；`docs/quality/quality-hardening-acceptance.md` 存在
