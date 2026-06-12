# P3 中期改进实施方案（可执行版）

> 基于 `WorkflowEngineIntegrationTest_H2暂缓问题分析报告_2026-06-12.md` §6.2
> 
> 方案日期：2026-06-12
> 
> 状态：待审查

---

## 前置条件

P1（H2 UNIQUE 约束）和 P2（test15 查询条件 + 清理）已实施完成：
- `db/h2/schema.sql:336-338` — 已添加 UNIQUE + INDEX
- `WorkflowEngineIntegrationTest.java:727-756` — transfer/addSign 查询已收窄
- `WorkflowEngineIntegrationTest.java:847` — 新增 instance ID 通知清理

---

## P3-2: 统一 sys_notification.biz_id 语义（优先级最高，工作量最小）

### 状态分析

经全面搜索确认：

| 操作 | bizId 值 | 源字段 |
|------|---------|--------|
| SUBMIT (line 127) | `instance.getId()` | 实例 ID ✅ |
| APPROVE (line 179) | `instanceForNotify.getId()` | 实例 ID ✅ |
| REJECT (line 275) | `instance.getId()` | 实例 ID ✅ |
| WITHDRAW (line 307) | `instanceId` | 实例 ID ✅ |
| **TRANSFER (line 420)** | `task.getBusinessId()` | 业务 ID ❌ |
| **ADD SIGN (line 499)** | `task.getBusinessId()` | 业务 ID ❌ |
| CC (WfCcService:86) | `instance.getBusinessId()` | 业务 ID（独立语义，不改） |
| ALERT | `alert.getId()` | 预警 ID（独立语义，不改） |

### Blast Radius（已确认）

**生产代码**：零影响。`NotificationService.getPage()` 和 `getUnreadCount()` 均不按 bizId 过滤。
**前端**：零影响。`NotificationBell.vue` 接收 bizId 但不做任何路由/跳转/判断。
**数据库**：`idx_sn_biz (biz_type, biz_id)` 索引仅被测试断言使用。
**CC 通知**：不受影响（bizType 不同，为业务类型如 "CONTRACT_APPROVAL"）。

### 实施方案（2 文件，2 行生产代码 + 4 行测试代码）

#### 步骤 1：修改 WorkflowEngine.java — TRANSFER 通知 bizId

文件：`backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:417-420`

```java
// 改前（line 420）：
notificationService.create(notifyTenantId, targetUserId,
        username + "转办了一个审批给你",
        username + "转办了一个审批给你：" + instance.getTitle(),
        "WORKFLOW", task.getBusinessId());

// 改后：
notificationService.create(notifyTenantId, targetUserId,
        username + "转办了一个审批给你",
        username + "转办了一个审批给你：" + instance.getTitle(),
        "WORKFLOW", instance.getId());  // ← 改为 instance ID
```

`instance` 变量在 line 398 已加载：`WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId())`。

#### 步骤 2：修改 WorkflowEngine.java — ADD SIGN 通知 bizId

文件：`backend/src/main/java/com/cgcpms/workflow/service/WorkflowEngine.java:496-499`

```java
// 改前（line 499）：
notificationService.create(notifyTenantId, auid,
        username + "邀请你加签审批",
        username + "邀请你加签审批：" + notifyTitle,
        "WORKFLOW", task.getBusinessId());

// 改后：
notificationService.create(notifyTenantId, auid,
        username + "邀请你加签审批",
        username + "邀请你加签审批：" + notifyTitle,
        "WORKFLOW", instance.getId());  // ← 改为 instance ID
```

`instance` 变量在 line 448 已加载。

#### 步骤 3：更新 test15 断言 — 转办通知

文件：`WorkflowEngineIntegrationTest.java:729`

```java
// 改前：
.eq(SysNotification::getBizId, RUN_ID + 18)

// 改后：
.eq(SysNotification::getBizId, instance4.getId())
```

#### 步骤 4：更新 test15 断言 — 加签通知

文件：`WorkflowEngineIntegrationTest.java:754`

```java
// 改前：
.eq(SysNotification::getBizId, RUN_ID + 19)

// 改后：
.eq(SysNotification::getBizId, instance5.getId())
```

#### 步骤 5：简化 cleanupTestData 通知清理

统一为 instance ID 后，所有 WORKFLOW 通知的 bizId 均为 instance ID。原来的两行清理可合并：

```java
// 删除这两行：
jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id BETWEEN ? AND ?", startBid, endBid);
jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", startBid, endBid);

// 替换为一行的子查询（覆盖所有工作流通知）：
jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", startBid, endBid);
```

### 验证

- `WorkflowEngineIntegrationTest` 16/16 通过（H2）
- 全量 `./mvnw test -Dspring.profiles.active=local` 无回归

---

## P3-3: 测试数据隔离强化（依赖 P3-2 完成）

### 改进项（保守方案，不改测试结构）

#### 改动 1：清理范围改为常量

```java
// 在 RUN_ID 下方新增：
private static final long BID_FIRST = RUN_ID + 1;
private static final long BID_LAST  = RUN_ID + 21;
```

`cleanupTestData` 中 `startBid`/`endBid` 替换为 `BID_FIRST`/`BID_LAST`。

#### 改动 2：消除幂等键 LIKE 模式清理

当前脆弱的 LIKE 匹配（line 848）：
```java
jdbcTemplate.update("DELETE FROM wf_idempotency WHERE idempotency_key LIKE 'tenant-idem-%' OR idempotency_key LIKE 'test13-%' OR idempotency_key LIKE 'test14-%' OR idempotency_key LIKE 'test15-%'");
```

改为按 `business_id` 范围清理（所有幂等记录的 `business_id` 均在 `BID_FIRST~BID_LAST` 内）：
```java
jdbcTemplate.update("DELETE FROM wf_idempotency WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);
```

**验证前提**：确认所有 16 个测试方法的幂等记录 `business_id` 确实在范围内。

#### 改动 3：添加 business ID 分配注释

在类顶部 `RUN_ID` 声明后添加 Javadoc：
```java
/**
 * Business ID allocation (RUN_ID = System.currentTimeMillis()):
 *   +1  : test01-04 (submit/approve/addSign/reject chain)
 *   +2  : test02 (step in chain)
 *   +3  : test03 (step in chain)
 *   +4  : test04 (step in chain)
 *   +5  : test05 (resubmit fallback)
 *   +6  : test06 (withdraw)
 *   +7  : test07 (transfer)
 *   +8-10: test08 (concurrent approve, 3 instances)
 *   +10 : test09 idempotency (3 instances: +8, +10, +10)
 *   +11 : test10 (availableActions)
 *   +12 : test11 (rejectAndResubmit)
 *   +13 : test12 (tenant in records)
 *   +14 : test13 (addSign after approval rejected)
 *   +15-16: test14 (multi-tenant done records)
 *   +15-19: test15 (lifecycle notifications)
 *   +20-21: test16 (CC records)
 *   RANGE: RUN_ID+1 ~ RUN_ID+21 (21 IDs)
 */
```

### 验证

- `WorkflowEngineIntegrationTest` 16/16 通过
- 手动确认清理后数据库无残留数据

---

## P3-1: H2 Schema 从 Flyway 自动生成（工作量最大，独立周期）

### 现状

| 路径 | 内容 | 表数 |
|------|------|------|
| `db/migration/V1~V40` | MySQL Flyway 迁移（40 文件） | 55 |
| `db/h2/schema.sql` | H2 静态建表（V1-V2 时代） | 18 |
| `db/h2/schema-phase23.sql` | H2 静态建表（V3-V40 时代） | 39 |
| `db/h2/data.sql` | 种子数据 | — |

H2 通过 `spring.sql.init` 加载静态 SQL，Flyway 在 test local profile 中被 **禁用**。

### 阻断点：40 个 MySQL 迁移脚本中的 H2 不兼容语法

| 语法 | 出现次数 | H2 兼容？ |
|------|---------|----------|
| `ENGINE=InnoDB` | 55 | ❌ |
| `DEFAULT CHARSET=utf8mb4 COLLATE=...` | 55 | ❌ |
| `COMMENT '...'` | ~500+ | ⚠️ H2 2.2.224+ 部分支持 |
| `ON UPDATE CURRENT_TIMESTAMP` | ~30 | ❌ |
| `JSON` 列类型 | ~15 | ❌ 需改为 TEXT |
| `TINYINT` | ~20 | ❌ 需改为 SMALLINT |
| `SET NAMES utf8mb4` | 3 | ❌ |
| `SET FOREIGN_KEY_CHECKS = 0/1` | 4 | ❌ |
| `INSERT IGNORE` | ~20 | ⚠️ H2 不支持 IGNORE 关键字 |

### 推荐方案：双路 Flyway

```
Spring Boot test profile=local:
  → Flyway enabled
  → locations: classpath:db/migration-h2/   ← H2 兼容副本
  → spring.sql.init.mode: never             ← 不再需要静态 SQL
```

### 实施步骤

#### 第一步：验证 H2 MySQL 模式兼容性边界（1h）

创建一个测试，对每个 V1~V40 脚本执行 SQL，记录实际阻断的语法。预期：
- `ENGINE=InnoDB` 在 H2 MySQL 模式下可能被自动忽略
- `COMMENT` 在 H2 2.2.224 列级可能被接受
- `ON UPDATE CURRENT_TIMESTAMP`、`JSON`、`SET` 语句一定失败

#### 第二步：自动化转换脚本（0.5d）

编写 Python/Shell 脚本，对 `db/migration/` 中每个 `V*.sql` 执行规则转换：

```
规则表：
  ENGINE=InnoDB                       → 删除整行
  DEFAULT CHARSET=utf8mb4 COLLATE=... → 删除
  COMMENT '...' (列级)                → 删除
  COMMENT='...' (表级)                → 删除
  ON UPDATE CURRENT_TIMESTAMP         → 删除
  JSON                                → TEXT
  TINYINT                             → SMALLINT
  SET NAMES utf8mb4                   → 删除
  SET FOREIGN_KEY_CHECKS = 0/1        → 删除
  INSERT IGNORE INTO                  → INSERT INTO
  `backtick`                          → 保留
```

输出到 `db/migration-h2/V1__...sql` ~ `V40__...sql`。

#### 第三步：人工审查关键迁移（2h）

以下迁移需人工确认：
- **V6（演示数据）**、**V9（审批模板）**：可能包含 JSON_OBJECT 函数调用
- **V31**：已知 JSON→TEXT 改写，需对照检查
- **V3（审批幂等表）**：UNIQUE KEY 语法的 H2 等效写法
- **V37（通知表）**、**V38（抄送表）**：索引语法的 H2 等效写法

#### 第四步：修改 test application-local.yml（5min）

文件：`backend/src/test/resources/application-local.yml`

```yaml
spring:
  flyway:
    enabled: true                          # false → true
    locations: classpath:db/migration-h2   # 指向 H2 副本
    baseline-on-migrate: true
    baseline-version: 0
    clean-disabled: false                  # 测试允许 clean
  sql:
    init:
      mode: never                          # always → never
  # 删除以下三行：
  # schema-locations: classpath:db/h2/schema.sql,classpath:db/h2/schema-phase23.sql
  # data-locations: classpath:db/h2/data.sql
```

#### 第五步：处理种子数据

当前 `db/h2/data.sql` 的种子数据（admin 用户/角色/菜单、演示数据等）：

**推荐**：移入 `db/migration-h2/V41__seed_test_data.sql`（H2 兼容的 INSERT 语句），作为测试 fixture 而非 schema 的一部分。

**替代**：在各测试类的 `@BeforeEach` 中通过 Repository 插入（更灵活但改动面大）。

#### 第六步：删除旧文件

```bash
git rm backend/src/main/resources/db/h2/schema.sql
git rm backend/src/main/resources/db/h2/schema-phase23.sql
git rm backend/src/main/resources/db/h2/data.sql
```

#### 第七步：验证

```bash
# 运行全量测试（H2 local profile）
./mvnw test -Dspring.profiles.active=local

# 预期：162/162 通过
```

#### 第八步：建立 CI 守卫

在 CI pipeline 中添加 `./mvnw test -Dspring.profiles.active=local` 步骤。此后，新增 MySQL migration 若不同步更新 `migration-h2/` 副本，CI 会因表结构不一致而失败 → 倒逼同步维护。

### 风险与缓解

| 风险 | 概率 | 缓解 |
|------|------|------|
| H2 MySQL 模式不完全兼容语法 | 高 | 第一步先行验证，确定实际阻断点后再写转换脚本 |
| 自动转换引入语义错误（如删除了不该删的） | 中 | 第三步人工审查关键迁移，验证阶段跑全量测试 |
| 种子数据（data.sql）迁移失败 | 低 | V41 用纯 INSERT 语句，参照现有 data.sql 改写 |
| schema.sql 中有 Flyway 未覆盖的表 | 低 | 第二步前先交叉比对两份 schema，确认表集一致 |

### 验收标准

- [ ] `db/migration-h2/` 含 V1~V41 的 H2 兼容脚本
- [ ] `db/h2/schema.sql`、`schema-phase23.sql`、`data.sql` 已删除
- [ ] `application-local.yml` 中 `flyway.enabled=true`, `sql.init.mode=never`
- [ ] 全量 162/162 测试在 H2 local profile 下通过
- [ ] CI 包含 H2 测试步骤

---

## 执行顺序与工作量

| 阶段 | 改进项 | 工作量 | 依赖 |
|------|--------|--------|------|
| **第一优先** | P3-2: 统一 bizId 语义 | 0.5h | P1+P2 已完成 |
| **第二优先** | P3-3: 测试数据隔离强化 | 0.5h | P3-2（通知清理依赖 bizId 统一） |
| **第三优先** | P3-1: H2 Flyway 自动生成 | 1-2d | 前两项与 P3-1 独立，可并行 |

**建议今天完成 P3-2 + P3-3**（共 1h），P3-1 独立排期。

---

## 改动文件清单

| 文件 | P3-2 | P3-3 | P3-1 |
|------|------|------|------|
| `WorkflowEngine.java` | 2 行 | — | — |
| `WorkflowEngineIntegrationTest.java` | 4 行 | ~10 行 | — |
| `test/application-local.yml` | — | — | 修改 |
| `db/migration-h2/V1~V41` | — | — | 新增 41 文件 |
| `db/h2/schema*.sql` + `data.sql` | — | — | 删除 3 文件 |
