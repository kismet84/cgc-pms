# 质量加固 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划必须严格串行执行，不得并行派发任务。

**Goal:** 建立覆盖后端、前端、E2E、代码可维护性和安全控制的自动化质量门禁，使 v1.0.0 发布具备可重复验证的质量证据。

**Architecture:** 先固化测试发现规则和 CI 作业，再逐步增加业务测试、完成局部重构，最后实施 SQL、限流、上传和审计安全控制。共享配置文件由指定任务单点修改；后一任务必须在前一任务验收并提交后才能开始。

**Tech Stack:** Java 21、Spring Boot 3.3、JUnit 5、Mockito、MockMvc、JaCoCo、Vue 3、TypeScript、Vitest 4、Vue Test Utils、Playwright、GitHub Actions、MySQL 8、Redis、Flyway、MinIO。

---

## 执行约束

1. 从 Task 1 开始严格顺序执行；任何任务失败时停止，不启动下一任务。
2. 每个任务单独提交。不得把计划外工作区文件加入提交。
3. 后端命令在 Windows 环境显式设置 JDK 21：

```powershell
$env:JAVA_HOME = 'D:\projects-test\jdk-21\jdk-21.0.11+10'
```

4. 覆盖率只能通过新增有效测试提高；禁止扩大排除范围、删除失败测试或降低业务断言。
5. 共享文件归属：Task 1 修改指标脚本；Task 2 建立 `pom.xml/package.json/vitest.config.ts/ci.yml` 骨架；Task 4 提高后端阶段阈值；Task 5 修改 Playwright 配置；Task 14 只提高最终阈值。

## 文件结构

### 新建

- `scripts/quality-baseline.ps1`：统一生成覆盖率、测试数量和大文件基线。
- `scripts/check-sql-safety.ps1`：扫描 SQL 结构性拼接风险。
- `backend/src/test/java/com/cgcpms/architecture/SettlementArchitectureTest.java`：保护结算服务边界。
- `backend/src/test/java/com/cgcpms/common/aspect/RateLimitAspectTest.java`：限流 key、窗口和降级测试。
- `backend/src/main/java/com/cgcpms/common/annotation/RateLimitKey.java`：限流维度枚举。
- `backend/src/main/java/com/cgcpms/file/service/FileTypeValidator.java`：文件扩展名、MIME 和签名联合校验。
- `backend/src/test/java/com/cgcpms/file/FileTypeValidatorTest.java`：伪装文件负向测试。
- `backend/src/main/java/com/cgcpms/audit/**`：审计注解、事件、实体、Mapper、服务、切面、控制器和 VO。
- `backend/src/test/java/com/cgcpms/audit/**`：审计切面、持久化和租户隔离测试。
- `backend/src/main/resources/db/migration/V91__create_operation_audit_log.sql`：MySQL 审计表。
- `backend/src/main/resources/db/migration-h2/V91__create_operation_audit_log.sql`：H2 审计表。
- `frontend-admin/e2e/fixtures/business-chain.ts`：五条主链路共用 API fixture。
- 六个大页面同目录下的 `components/`、`composables/` 和对应单测文件。

### 修改

- `backend/pom.xml`：JaCoCo 阶段阈值。
- `frontend-admin/package.json`、`frontend-admin/pnpm-lock.yaml`、`frontend-admin/vitest.config.ts`：前端覆盖率。
- `.github/workflows/ci.yml`：master/main、MySQL 硬门禁、coverage artifact、E2E 和安全扫描。
- `frontend-admin/playwright.config.ts`：CI 报告和服务启动策略。
- 结算 Handler/QueryService/WriteService：状态常量迁移。
- 六个超过 800 行的 Vue 页面：仅做等价拆分。
- `RateLimit.java`、`RateLimitAspect.java` 及敏感 Controller：多维限流。
- `FileService.java`：调用联合文件类型校验。
- `docs/09-测试规范.md`、`docs/11-安全规范.md`、`docs/未来开发计划.md`：更新最终基线与完成状态。

---

### Task 1: 建立可重复的质量基线

**Files:**
- Create: `scripts/quality-baseline.ps1`
- Create: `docs/quality/.gitkeep`

- [ ] **Step 1: 编写基线脚本的失败验收命令**

Run:

```powershell
Test-Path scripts\quality-baseline.ps1
```

Expected: `False`。

- [ ] **Step 2: 创建基线脚本**

`scripts/quality-baseline.ps1` 使用以下完整逻辑：

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$qualityDir = Join-Path $root 'docs\quality'
New-Item -ItemType Directory -Force $qualityDir | Out-Null

$backendTests = (Get-ChildItem "$root\backend\src\test" -Recurse -Filter *.java).Count
$frontendTests = (Get-ChildItem "$root\frontend-admin\src" -Recurse -File |
  Where-Object { $_.Name -match '\.(test|spec)\.ts$' }).Count
$e2eCases = (Select-String -Path "$root\frontend-admin\e2e\*.spec.ts" -Pattern '\btest\(' -AllMatches |
  ForEach-Object { $_.Matches.Count } | Measure-Object -Sum).Sum
$largeVue = Get-ChildItem "$root\frontend-admin\src" -Recurse -Filter *.vue |
  ForEach-Object { [pscustomobject]@{ path = $_.FullName.Replace($root + '\', ''); lines = (Get-Content $_.FullName).Count } } |
  Where-Object { $_.lines -gt 800 } | Sort-Object lines -Descending

[ordered]@{
  generatedAt = (Get-Date).ToString('s')
  backendTestFiles = $backendTests
  frontendTestFiles = $frontendTests
  e2eCases = $e2eCases
  vueFilesOver800Lines = @($largeVue)
} | ConvertTo-Json -Depth 4 | Set-Content "$qualityDir\baseline.json" -Encoding utf8
```

- [ ] **Step 3: 运行脚本并验证 JSON**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\quality-baseline.ps1
Get-Content docs\quality\baseline.json | ConvertFrom-Json | Select-Object backendTestFiles,frontendTestFiles,e2eCases
```

Expected: 三个数值均大于 0，`vueFilesOver800Lines` 包含 6 个已识别页面。

- [ ] **Step 4: 提交基线工具**

```powershell
git add scripts/quality-baseline.ps1 docs/quality/.gitkeep docs/quality/baseline.json
git commit -m "test: add repeatable quality baseline"
```

---

### Task 2: 固化测试配置与 CI 骨架

**Files:**
- Modify: `backend/pom.xml`
- Modify: `frontend-admin/package.json`
- Modify: `frontend-admin/pnpm-lock.yaml`
- Modify: `frontend-admin/vitest.config.ts`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 添加前端 coverage provider 并锁定脚本**

Run:

```powershell
cd frontend-admin
pnpm add -D @vitest/coverage-v8@4.1.8
```

在 `package.json` 的 scripts 增加：

```json
"test:coverage": "vitest run --coverage"
```

- [ ] **Step 2: 设置可解释的前端覆盖范围**

将 `vitest.config.ts` 的 `coverage` 设置为：

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
    lines: 60,
    functions: 60,
    branches: 50,
    statements: 60,
  },
},
```

说明：Task 14 才提高最终阈值；本任务不伪造当前已达 80%。

- [ ] **Step 3: 保持后端当前门禁并增加 branch 基线**

在 `backend/pom.xml` 的 JaCoCo `limits` 中保持 instruction `0.60`，并新增 branch `0.50`：

```xml
<limit>
    <counter>INSTRUCTION</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.60</minimum>
</limit>
<limit>
    <counter>BRANCH</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.50</minimum>
</limit>
```

- [ ] **Step 4: 修正 CI 触发和硬门禁**

`.github/workflows/ci.yml` 必须满足：

```yaml
on:
  push:
    branches: [master, main]
  pull_request:
    branches: [master, main]
```

删除 `backend-test-mysql.continue-on-error`；后端命令改为 `./mvnw verify`；前端安装改为 `pnpm install --frozen-lockfile`；单测命令改为 `pnpm test:coverage`。两个 coverage job 各追加 artifact：

```yaml
- name: Upload coverage
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: frontend-coverage
    path: frontend-admin/coverage
```

后端对应 `name: backend-coverage`、`path: backend/target/site/jacoco`。

- [ ] **Step 5: 验证配置**

Run:

```powershell
cd backend
./mvnw.cmd verify -Djasypt.encryptor.password=dev-jasypt-key
cd ..\frontend-admin
pnpm type-check
pnpm test:coverage
```

Expected: 后端保持现有门禁通过；前端生成 `coverage/coverage-summary.json`。本任务只固化测试发现和报告规则，不提前提高阈值。

- [ ] **Step 6: 提交门禁骨架**

```powershell
git add backend/pom.xml frontend-admin/package.json frontend-admin/pnpm-lock.yaml frontend-admin/vitest.config.ts .github/workflows/ci.yml
git commit -m "ci: establish quality gates"
```

---

### Task 3: 完成结算状态常量迁移并保护服务边界

**Files:**
- Modify: `backend/src/main/java/com/cgcpms/settlement/handler/SettlementWorkflowHandler.java`
- Modify: `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementQueryService.java`
- Modify: `backend/src/main/java/com/cgcpms/settlement/service/StlSettlementWriteService.java`
- Modify: `backend/src/test/java/com/cgcpms/settlement/StlSettlementServiceTest.java`
- Create: `backend/src/test/java/com/cgcpms/architecture/SettlementArchitectureTest.java`

- [ ] **Step 1: 增加状态流转回归测试**

在 `StlSettlementServiceTest` 增加三个测试：创建默认值断言 `SettlementStatusConstants.SETTLEMENT_DRAFT`；审批后断言 `SETTLEMENT_FINALIZED`；撤回后断言恢复 `SETTLEMENT_DRAFT`。测试中禁止直接写状态字符串。

```java
assertEquals(SettlementStatusConstants.SETTLEMENT_DRAFT, settlement.getSettlementStatus());
assertEquals(SettlementStatusConstants.SETTLEMENT_FINALIZED, updated.getSettlementStatus());
```

- [ ] **Step 2: 验证源码规则当前失败**

Run:

```powershell
rg -n '"DRAFT"|"FINALIZED"' backend/src/main/java/com/cgcpms/settlement
```

Expected: Handler、QueryService、WriteService 有命中。

- [ ] **Step 3: 替换全部业务字面量**

增加静态导入：

```java
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_DRAFT;
import static com.cgcpms.settlement.constant.SettlementStatusConstants.SETTLEMENT_FINALIZED;
```

将比较、赋值和 Wrapper 条件中的字符串替换为以上常量。

- [ ] **Step 4: 增加架构防回退测试**

`SettlementArchitectureTest` 读取 `StlSettlementService.java` 并断言文件不超过 150 行，且不包含 `BaseMapper`、`LambdaQueryWrapper` 或 `@Transactional`：

```java
@Test
void facadeMustRemainThin() throws IOException {
    String source = Files.readString(Path.of("src/main/java/com/cgcpms/settlement/service/StlSettlementService.java"));
    assertTrue(source.lines().count() <= 150);
    assertFalse(source.contains("BaseMapper"));
    assertFalse(source.contains("LambdaQueryWrapper"));
    assertFalse(source.contains("@Transactional"));
}
```

- [ ] **Step 5: 运行测试和字面量扫描**

Run:

```powershell
cd backend
./mvnw.cmd -Dtest=StlSettlementServiceTest,SettlementArchitectureTest test -Djasypt.encryptor.password=dev-jasypt-key
cd ..
rg -n '"DRAFT"|"FINALIZED"' backend/src/main/java/com/cgcpms/settlement
```

Expected: 测试 PASS；扫描只允许常量类自身定义命中。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/main/java/com/cgcpms/settlement backend/src/test/java/com/cgcpms/settlement backend/src/test/java/com/cgcpms/architecture
git commit -m "refactor: enforce settlement status constants"
```

---

### Task 4: 将后端覆盖率稳定提高到 72%

**Files:**
- Modify: `backend/src/test/java/com/cgcpms/workflow/WorkflowCoreServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/workflow/WorkflowConcurrencyTest.java`
- Modify: `backend/src/test/java/com/cgcpms/receipt/MatReceiptServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/inventory/MatStockServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/subcontract/SubMeasureControllerMockMvcTest.java`
- Modify: `backend/src/test/java/com/cgcpms/cost/service/CostLedgerServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/payment/PayApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/payment/PaymentWritebackTest.java`
- Modify: `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`
- Modify: `backend/src/test/java/com/cgcpms/settlement/StlSettlementServiceTest.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: 生成 JaCoCo 未覆盖清单**

Run:

```powershell
cd backend
./mvnw.cmd verify -Djasypt.encryptor.password=dev-jasypt-key
```

Expected: `target/site/jacoco/index.html` 存在。按 branch missed 数排序，选择审批、库存、付款、租户隔离范围内的前 10 个类。

- [ ] **Step 2: 先补到 68% instruction、55% branch**

为审批、库存、付款、租户隔离范围内的高风险类增加正常、失败、幂等和隔离测试。实际覆盖率达到该线后，将 `pom.xml` 更新为 instruction `0.68`、branch `0.55` 并运行 `verify`。

- [ ] **Step 3: 为剩余高风险类补齐同一测试矩阵**

每个目标 Service 至少增加：正常状态流转、非法前置状态、重复调用幂等、跨租户拒绝。金额类额外断言边界值和超额拒绝。示例断言形态：

```java
assertDoesNotThrow(() -> service.approve(id));
BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(id));
assertEquals("INVALID_STATUS", ex.getCode());
verify(costMapper, times(1)).insert(any());
```

- [ ] **Step 4: 分模块运行新增测试**

Run:

```powershell
./mvnw.cmd -Dtest='com.cgcpms.workflow.**,com.cgcpms.receipt.**,com.cgcpms.inventory.**,com.cgcpms.subcontract.**,com.cgcpms.cost.**,com.cgcpms.payment.**,com.cgcpms.invoice.**' test -Djasypt.encryptor.password=dev-jasypt-key
```

Expected: PASS，且无测试依赖执行顺序。

- [ ] **Step 5: 提高 JaCoCo 阶段门禁**

将 `backend/pom.xml` 改为 instruction `0.72`、branch `0.60`，运行 `mvnw.cmd verify`。

Expected: BUILD SUCCESS；若失败，回到 Step 2 补业务测试。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/test backend/pom.xml
git commit -m "test: raise backend coverage gate to 72 percent"
```

---

### Task 5: 补齐五条主链路 E2E 并接入 CI

**Files:**
- Create: `frontend-admin/e2e/fixtures/business-chain.ts`
- Modify: `frontend-admin/e2e/contract.spec.ts`
- Modify: `frontend-admin/e2e/procurement.spec.ts`
- Modify: `frontend-admin/e2e/inventory.spec.ts`
- Create: `frontend-admin/e2e/subcontract-cost.spec.ts`
- Create: `frontend-admin/e2e/payment-invoice.spec.ts`
- Modify: `frontend-admin/e2e/settlement.spec.ts`
- Modify: `frontend-admin/playwright.config.ts`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 创建唯一测试数据 fixture**

`business-chain.ts` 导出唯一编码和 API 创建助手：

```ts
export const runId = `${Date.now()}-${Math.random().toString(16).slice(2)}`
export const code = (prefix: string) => `${prefix}-${runId}`

export async function expectApiOk(response: APIResponse) {
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe('00000')
  return body.data
}
```

- [ ] **Step 2: 为五条链路增加业务断言**

每条链路创建独立 `test()`，不依赖历史数据：

- 合同链：审批后状态和列表回显一致。
- 采购链：验收审批后库存增加，流水 `sourceType=MAT_RECEIPT` 且 `sourceId` 等于验收单 ID。
- 分包链：计量审批后只生成一条来源成本，重复回调不重复插入。
- 付款链：支付后合同/申请回写，发票关联付款记录，超付返回业务错误。
- 结算链：条件不足时归档失败；满足守卫后归档成功。

核心断言使用：

```ts
expect(transaction.sourceType).toBe('MAT_RECEIPT')
expect(transaction.sourceId).toBe(receipt.id)
expect(costs.filter((item) => item.sourceId === measure.id)).toHaveLength(1)
expect(overpay.code).not.toBe('00000')
```

- [ ] **Step 3: 验证发现 50+ 测试**

Run:

```powershell
cd frontend-admin
pnpm exec playwright test --list
```

Expected: 输出 `Total: 50 tests` 或更多，并能看到五个主链路名称。

- [ ] **Step 4: 在 CI 增加串行 E2E job**

在 `.github/workflows/ci.yml` 增加依赖 `backend-test-mysql`、`frontend-build` 的 `e2e` job，启动 `deploy/docker-compose.dev.yml`，等待 `/api/actuator/health` 返回 UP，执行：

```yaml
- run: cd frontend-admin && pnpm exec playwright install --with-deps chromium
- run: cd frontend-admin && pnpm exec playwright test
- uses: actions/upload-artifact@v4
  if: always()
  with:
    name: playwright-report
    path: frontend-admin/playwright-report
```

- [ ] **Step 5: 本地运行五条链路**

Run:

```powershell
pnpm exec playwright test e2e/contract.spec.ts e2e/procurement.spec.ts e2e/inventory.spec.ts e2e/subcontract-cost.spec.ts e2e/payment-invoice.spec.ts e2e/settlement.spec.ts
```

Expected: 全部 PASS；失败时必须保留 trace 并修复测试数据隔离。

- [ ] **Step 6: 提交**

```powershell
git add frontend-admin/e2e frontend-admin/playwright.config.ts .github/workflows/ci.yml
git commit -m "test: cover five business chains in e2e"
```

---

### Task 6: 拆分组织管理页面

**Files:**
- Modify: `frontend-admin/src/pages/org/index.vue`
- Create: `frontend-admin/src/pages/org/composables/useOrgTree.ts`
- Create: `frontend-admin/src/pages/org/components/OrgEditorModal.vue`
- Modify: `frontend-admin/src/pages/org/__tests__/index.test.ts`
- Create: `frontend-admin/src/pages/org/__tests__/useOrgTree.test.ts`

- [ ] **Step 1: 补充页面行为保护测试**

断言首次加载、节点选择、创建/编辑提交、删除确认和接口失败提示；测试通过 mock API 验证调用参数，不断言组件内部实现。

- [ ] **Step 2: 抽取树状态 composable**

`useOrgTree.ts` 对外只暴露：

```ts
export interface OrgTreeState {
  loading: Ref<boolean>
  treeData: Ref<OrgNode[]>
  selectedId: Ref<string | undefined>
  loadTree: () => Promise<void>
  selectNode: (id: string) => void
}

export function useOrgTree(): OrgTreeState
```

将现有加载和选择逻辑原样迁移；页面通过该接口调用。

- [ ] **Step 3: 抽取编辑弹窗并验证**

`OrgEditorModal.vue` 使用 `open/modelValue`、`record` 输入，发出 `saved` 和 `update:open`；保存 API 仍由组件调用，成功后发出 `saved`。运行：

```powershell
pnpm test:unit -- src/pages/org/__tests__
```

Expected: PASS，`org/index.vue` 不超过 500 行。

- [ ] **Step 4: 提交**

```powershell
git add frontend-admin/src/pages/org
git commit -m "refactor: split organization page"
```

---

### Task 7: 拆分驾驶舱页面

**Files:**
- Modify: `frontend-admin/src/pages/dashboard/index.vue`
- Create: `frontend-admin/src/pages/dashboard/composables/useDashboardData.ts`
- Create: `frontend-admin/src/pages/dashboard/chartOptions.ts`
- Create: `frontend-admin/src/pages/dashboard/components/DashboardCharts.vue`
- Modify: `frontend-admin/src/pages/dashboard/__tests__/DashboardDataLoading.test.ts`

- [ ] **Step 1: 锁定加载与聚合行为**

测试未选项目时请求全项目聚合、选定项目时携带 projectId、部分接口失败时其他卡片仍渲染。

- [ ] **Step 2: 抽取数据流和纯图表配置**

`useDashboardData.ts` 暴露 `loading/error/data/load(projectId?)`；`chartOptions.ts` 的每个函数只接收 DTO 并返回 ECharts option，不访问 store 或 API。

```ts
export const buildCostOption = (data: CostTrendPoint[]): EChartsOption => ({
  xAxis: { type: 'category', data: data.map((item) => item.period) },
  yAxis: { type: 'value' },
  series: [{ type: 'line', data: data.map((item) => item.amount) }],
})
```

- [ ] **Step 3: 运行测试和构建**

```powershell
pnpm test:unit -- src/pages/dashboard/__tests__
pnpm build
```

Expected: PASS，页面不超过 500 行。

- [ ] **Step 4: 提交**

```powershell
git add frontend-admin/src/pages/dashboard
git commit -m "refactor: split dashboard page"
```

---

### Task 8: 拆分库存和验收页面

**Files:**
- Modify: `frontend-admin/src/pages/inventory/stock.vue`
- Create: `frontend-admin/src/pages/inventory/composables/useStockQuery.ts`
- Create: `frontend-admin/src/pages/inventory/components/StockTransactionModal.vue`
- Create: `frontend-admin/src/pages/inventory/__tests__/stock.test.ts`
- Modify: `frontend-admin/src/pages/receipt/index.vue`
- Create: `frontend-admin/src/pages/receipt/composables/useReceiptForm.ts`
- Create: `frontend-admin/src/pages/receipt/components/ReceiptEditorModal.vue`
- Modify: `frontend-admin/src/pages/receipt/__tests__/index.test.ts`

- [ ] **Step 1: 增加库存来源与验收仓库回归测试**

库存测试断言筛选参数、来源字段展示和出入库弹窗提交；验收测试断言启用仓库加载、warehouseId 必填及审批后刷新。

- [ ] **Step 2: 抽取 composable 和弹窗**

`useStockQuery` 管理分页筛选和 `loadStocks`；`useReceiptForm` 管理仓库/物料选项和表单校验。弹窗通过 `saved` 事件通知页面刷新，不直接操作页面表格 ref。

- [ ] **Step 3: 验证行为与行数**

```powershell
pnpm test:unit -- src/pages/inventory/__tests__/stock.test.ts src/pages/receipt/__tests__/index.test.ts
pnpm build
```

Expected: PASS；两个页面均不超过 500 行。

- [ ] **Step 4: 提交**

```powershell
git add frontend-admin/src/pages/inventory frontend-admin/src/pages/receipt
git commit -m "refactor: split stock and receipt pages"
```

---

### Task 9: 拆分发票和合同台账页面

**Files:**
- Modify: `frontend-admin/src/pages/invoice/index.vue`
- Create: `frontend-admin/src/pages/invoice/composables/useInvoiceTable.ts`
- Create: `frontend-admin/src/pages/invoice/components/InvoiceEditorModal.vue`
- Create: `frontend-admin/src/pages/invoice/__tests__/index.test.ts`
- Modify: `frontend-admin/src/pages/contract/ContractLedgerPage.vue`
- Create: `frontend-admin/src/pages/contract/composables/useContractLedger.ts`
- Create: `frontend-admin/src/pages/contract/components/ContractLedgerFilters.vue`
- Modify: `frontend-admin/src/pages/contract/__tests__/ContractLedgerPage.test.ts`

- [ ] **Step 1: 增加筛选、分页、编辑和错误提示测试**

发票测试覆盖付款记录关联、PDF 操作和保存失败；合同台账覆盖 project/year 筛选、分页和重置。

- [ ] **Step 2: 抽取状态和展示组件**

composable 负责请求参数和加载；筛选器只发出 `search/reset`；编辑弹窗只发出 `saved`。API 类型继续复用 `src/types`，不得新增 `any`。

- [ ] **Step 3: 验证**

```powershell
pnpm test:unit -- src/pages/invoice/__tests__ src/pages/contract/__tests__/ContractLedgerPage.test.ts
pnpm build
```

Expected: PASS；两个页面均不超过 500 行；`rg -n '\bany\b'` 无新增命中。

- [ ] **Step 4: 提交**

```powershell
git add frontend-admin/src/pages/invoice frontend-admin/src/pages/contract
git commit -m "refactor: split invoice and contract ledger pages"
```

---

### Task 10: 建立 SQL 注入静态门禁

**Files:**
- Create: `scripts/check-sql-safety.ps1`
- Create: `backend/src/test/java/com/cgcpms/security/SqlSafetyTest.java`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 写静态扫描脚本**

脚本扫描 `backend/src/main` 中 `${`、注解 SQL 字符串拼接、`.apply(`、`.last(`、`.having(`、`Statement`，命中时打印 `path:line` 并退出 1。允许项使用同一行注释 `SQL-SAFETY: server-side-enum`，且 `SqlSafetyTest` 验证该行不读取 Controller 参数。

```powershell
$patterns = @('\$\{', '@(Select|Update|Delete|Insert).*\+', '\.apply\(', '\.last\(', '\.having\(', '\bStatement\b')
$hits = Get-ChildItem backend\src\main -Recurse -File | Select-String -Pattern $patterns |
  Where-Object { $_.Line -notmatch 'SQL-SAFETY: server-side-enum' }
if ($hits) { $hits | ForEach-Object { "$($_.Path):$($_.LineNumber): $($_.Line.Trim())" }; exit 1 }
```

- [ ] **Step 2: 运行扫描并修复真实命中**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1
```

Expected: 未解释命中时 FAIL；值参数改为 MyBatis 绑定，排序字段改为服务端枚举映射后 PASS。

- [ ] **Step 3: 接入 CI 并提交**

CI 的 backend job 在测试前运行该脚本。提交：

```powershell
git add scripts/check-sql-safety.ps1 backend/src/test/java/com/cgcpms/security .github/workflows/ci.yml
git commit -m "security: add SQL injection gate"
```

---

### Task 11: 将限流扩展为多维策略

**Files:**
- Create: `backend/src/main/java/com/cgcpms/common/annotation/RateLimitKey.java`
- Create: `backend/src/main/java/com/cgcpms/common/ratelimit/RateLimitCounterStore.java`
- Create: `backend/src/main/java/com/cgcpms/common/ratelimit/RedisRateLimitCounterStore.java`
- Create: `backend/src/main/java/com/cgcpms/common/ratelimit/FallbackRateLimitCounterStore.java`
- Modify: `backend/src/main/java/com/cgcpms/common/annotation/RateLimit.java`
- Modify: `backend/src/main/java/com/cgcpms/common/aspect/RateLimitAspect.java`
- Create: `backend/src/test/java/com/cgcpms/common/aspect/RateLimitAspectTest.java`
- Modify: `backend/src/main/java/com/cgcpms/file/controller/FileController.java`
- Modify: `backend/src/main/java/com/cgcpms/dashboard/controller/DashboardController.java`

- [ ] **Step 1: 编写失败测试**

覆盖同 IP 不同用户隔离、同租户高成本查询共享配额、超过阈值抛出异常、窗口后恢复、伪造 `X-Forwarded-For` 不被默认信任，以及 Redis 异常时回退本地有界计数器并记录告警。

- [ ] **Step 2: 增加 key 枚举和注解字段**

```java
public enum RateLimitKey { IP, USER, TENANT, IP_AND_ACCOUNT }
```

```java
RateLimitKey key() default RateLimitKey.IP;
```

Aspect 生成 `endpoint:key-dimension`，用户和租户从 `UserContext` 获取；仅当请求来自配置的可信代理时读取转发头，否则使用 `getRemoteAddr()`。

`RateLimitCounterStore.increment(key, windowSeconds)` 返回当前计数。Redis 实现使用 `INCR`，首次计数时设置 TTL；`FallbackRateLimitCounterStore` 捕获 Redis 异常、记录限频告警并调用现有 Guava 有界缓存。降级期间仍执行限流，不无限制放行。

- [ ] **Step 3: 标注敏感端点**

- 上传：`@RateLimit(maxRequests = 20, windowSeconds = 60, key = RateLimitKey.USER)`。
- 驾驶舱高成本查询：`@RateLimit(maxRequests = 60, windowSeconds = 60, key = RateLimitKey.TENANT)`。
- 登录保留 IP 限流；刷新令牌使用 USER，不可识别用户时回退 IP。

- [ ] **Step 4: 运行测试并提交**

```powershell
cd backend
./mvnw.cmd -Dtest=RateLimitAspectTest test -Djasypt.encryptor.password=dev-jasypt-key
git add src/main/java/com/cgcpms/common src/main/java/com/cgcpms/file/controller/FileController.java src/main/java/com/cgcpms/dashboard/controller/DashboardController.java src/test/java/com/cgcpms/common/aspect
git commit -m "security: apply multidimensional rate limits"
```

Expected: PASS，超限异常由全局处理器映射为 HTTP 429。

---

### Task 12: 加固文件上传类型校验

**Files:**
- Create: `backend/src/main/java/com/cgcpms/file/service/FileTypeValidator.java`
- Create: `backend/src/test/java/com/cgcpms/file/FileTypeValidatorTest.java`
- Modify: `backend/src/main/java/com/cgcpms/file/service/FileService.java`
- Modify: `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`

- [ ] **Step 1: 增加伪装文件失败测试**

测试空文件、超限、`evil.exe.pdf`、声明 PDF 但头部为 PE、PNG/JPEG/PDF 合法签名、ZIP 容器伪装、含控制字符文件名。

```java
assertThrows(BusinessException.class,
    () -> validator.validate("evil.exe.pdf", "application/pdf", new byte[]{'M','Z'}));
```

- [ ] **Step 2: 实现允许矩阵和签名检测**

`FileTypeValidator.ValidationResult` 返回规范化原名、扩展名和检测 MIME。允许矩阵固定为 PDF、JPEG、PNG、GIF、WebP、DOCX、XLSX、PPTX、TXT、CSV；本轮明确拒绝 DOC/XLS/PPT、ZIP/RAR/7Z 和其他未列出格式。

```java
public record ValidationResult(String sanitizedName, String extension, String detectedMime) {}
```

PDF 检查 `%PDF-`，JPEG 检查 `FF D8 FF`，PNG 检查标准 8 字节签名，ZIP Office 同时检查 ZIP 签名和 `[Content_Types].xml`。

- [ ] **Step 3: 在上传前调用校验器**

FileService 在权限校验前完成大小和类型校验，MinIO `contentType()` 与数据库 `contentType` 使用 `detectedMime`，不使用客户端值。

- [ ] **Step 4: 运行测试并提交**

```powershell
cd backend
./mvnw.cmd -Dtest=FileTypeValidatorTest,FileServiceTest test -Djasypt.encryptor.password=dev-jasypt-key
git add src/main/java/com/cgcpms/file src/test/java/com/cgcpms/file
git commit -m "security: validate uploaded file content"
```

---

### Task 13: 建立持久化操作审计

**Files:**
- Create: `backend/src/main/resources/db/migration/V91__create_operation_audit_log.sql`
- Create: `backend/src/main/resources/db/migration-h2/V91__create_operation_audit_log.sql`
- Create: `backend/src/main/java/com/cgcpms/audit/annotation/AuditedOperation.java`
- Create: `backend/src/main/java/com/cgcpms/audit/entity/OperationAuditLog.java`
- Create: `backend/src/main/java/com/cgcpms/audit/mapper/OperationAuditLogMapper.java`
- Create: `backend/src/main/java/com/cgcpms/audit/event/OperationAuditEvent.java`
- Create: `backend/src/main/java/com/cgcpms/audit/service/OperationAuditService.java`
- Create: `backend/src/main/java/com/cgcpms/audit/aspect/OperationAuditAspect.java`
- Create: `backend/src/main/java/com/cgcpms/audit/controller/OperationAuditController.java`
- Create: `backend/src/main/java/com/cgcpms/audit/vo/OperationAuditLogVO.java`
- Create: `backend/src/test/java/com/cgcpms/audit/OperationAuditAspectTest.java`
- Create: `backend/src/test/java/com/cgcpms/audit/OperationAuditServiceTest.java`

- [ ] **Step 1: 创建审计表迁移**

两种数据库均创建 `sys_operation_audit_log`，字段为 `id, tenant_id, user_id, operation_type, business_type, business_id, http_method, request_path, success_flag, error_code, source_ip, duration_ms, created_at`；建立 `(tenant_id, created_at)`、`(tenant_id, business_type, business_id)` 索引。表不含请求体、响应体、Token 或 Cookie 字段。

- [ ] **Step 2: 编写失败测试**

Aspect 测试断言成功和异常均发布事件；Service 测试断言当前租户写入、敏感字段不可持久化、Mapper 异常不抛回业务线程、查询强制 tenantId。

- [ ] **Step 3: 实现注解、事件和失败隔离**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {
    String type();
    String businessType();
    String businessIdExpression() default "";
}
```

`OperationAuditAspect` 在 finally 中发布不可变 `OperationAuditEvent`；`OperationAuditService` 使用 `@Async` 与 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，捕获并记录 Mapper 异常。

- [ ] **Step 4: 标注关键操作并增加只读查询接口**

覆盖登录/退出、增删改、提交审批、审批处理、上传/下载/删除。查询接口要求 `@PreAuthorize("hasAuthority('audit:query')")`，只接受时间、用户、业务类型和业务 ID 参数，Service 强制当前 tenantId。

- [ ] **Step 5: 运行迁移和测试**

```powershell
cd backend
./mvnw.cmd -Dtest=OperationAuditAspectTest,OperationAuditServiceTest,MigrationIntegrityTest test -Djasypt.encryptor.password=dev-jasypt-key
```

Expected: PASS；审计 Mapper 故障测试中业务方法仍成功。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/main/resources/db backend/src/main/java/com/cgcpms/audit backend/src/test/java/com/cgcpms/audit
git commit -m "feat: persist tenant-scoped operation audits"
```

---

### Task 14: 达成最终覆盖率和发布门禁

**Files:**
- Modify: `backend/pom.xml`
- Modify: `frontend-admin/vitest.config.ts`
- Modify/Create: 覆盖率报告定位出的就近测试文件

- [ ] **Step 1: 运行全量覆盖率并记录缺口**

```powershell
cd backend
./mvnw.cmd verify -Djasypt.encryptor.password=dev-jasypt-key
cd ..\frontend-admin
pnpm test:coverage
```

Expected: 生成后端 JaCoCo 和前端 V8 报告。

- [ ] **Step 2: 只补高价值缺口直到实际值超过最终线**

优先补审计失败隔离、限流边界、文件签名、五条链路 Service 分支和拆分后的 composable；每个新增测试必须含结果或副作用断言，禁止只断言“不抛异常”。

- [ ] **Step 3: 设置最终阈值**

后端：instruction `0.80`、branch `0.70`。前端：lines/functions/statements `80`、branches `70`。

- [ ] **Step 4: 运行完整门禁**

```powershell
cd backend
./mvnw.cmd verify -Djasypt.encryptor.password=dev-jasypt-key
cd ..\frontend-admin
pnpm type-check
pnpm lint
pnpm test:coverage
pnpm build
pnpm exec playwright test
cd ..
powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1
```

Expected: 全部退出码 0；Playwright 50+ tests；无 SQL 风险命中。

- [ ] **Step 5: 提交最终门禁**

```powershell
git add backend/pom.xml backend/src/test frontend-admin/vitest.config.ts frontend-admin/src
git commit -m "test: enforce final coverage thresholds"
```

---

### Task 15: 更新质量文档并形成发布证据

**Files:**
- Modify: `docs/09-测试规范.md`
- Modify: `docs/11-安全规范.md`
- Modify: `docs/未来开发计划.md`
- Create: `docs/quality/quality-hardening-acceptance.md`

- [ ] **Step 1: 更新规范**

测试规范写入最终阈值、`mvn verify`、`pnpm test:coverage`、50+ E2E 和 artifact 位置；安全规范写入 SQL 扫描、限流维度、上传允许矩阵和审计字段/权限。

- [ ] **Step 2: 修正未来计划状态**

将已完成的结算拆分标记为“已验收”；更新实际测试基线；将第四条各项改为完成状态并链接验收报告。不得保留旧的 36 E2E、154 case 等失效数字。

- [ ] **Step 3: 生成验收报告**

报告必须列出：提交范围、后端 instruction/branch、前端四项覆盖率、Playwright 数量与结果、六个页面最终行数、SQL 扫描结果、限流端点清单、上传负向用例、审计查询样例和 CI run URL。

- [ ] **Step 4: 自检并提交**

```powershell
rg -n '待补充|未完成|36 case|154 case' docs/09-测试规范.md docs/11-安全规范.md docs/未来开发计划.md docs/quality/quality-hardening-acceptance.md
git diff --check
git add docs/09-测试规范.md docs/11-安全规范.md docs/未来开发计划.md docs/quality/quality-hardening-acceptance.md
git commit -m "docs: record quality hardening acceptance"
```

Expected: 占位符和旧基线无命中；提交成功。

---

## 最终退出条件

- 后端 `mvn verify`：instruction ≥80%，branch ≥70%。
- 前端 coverage：lines/functions/statements ≥80%，branches ≥70%。
- Playwright：50+ tests，五条业务主链路全部通过。
- MySQL 集成测试和 E2E 在 CI 中均为硬门禁。
- 结算业务代码不再散落状态字面量，门面边界受测试保护。
- 六个原超限页面均≤500行，或存在文档化且批准的例外；本计划默认不设置例外。
- SQL 安全扫描零未解释命中。
- 限流、上传和审计的正向、负向与故障隔离测试全部通过。
- `docs/quality/quality-hardening-acceptance.md` 包含可追溯的发布证据。
