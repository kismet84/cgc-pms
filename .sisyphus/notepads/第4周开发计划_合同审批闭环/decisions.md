# T0 决策记录

## 决策 1：状态枚举对齐

**时间**：2026-06-11
**决策者**：Atlas（按计划推荐方向裁定）

### 现状
| 字段 | 前端 (contract.ts) | DB 字典 (V5) |
|---|---|---|
| contractStatus | DRAFT, EXECUTING, COMPLETED, TERMINATED | DRAFT, PERFORMING, SETTLED, TERMINATED |
| approvalStatus | DRAFT, SUBMITTED, APPROVING, APPROVED, REJECTED | DRAFT, APPROVING, APPROVED, REJECTED, WITHDRAWN |

### 决策：前端对齐 DB 字典

**理由**：改动前端比改动 DB 字典 + 后端实体成本低，且不破坏已有数据。

**具体变更**：

1. `ContractStatus`：`EXECUTING` → `PERFORMING`，`COMPLETED` → `SETTLED`
   - 新定义：`'DRAFT' | 'PERFORMING' | 'SETTLED' | 'TERMINATED'`

2. `ApprovalStatus`：去掉 `SUBMITTED`（统一用 `APPROVING`），新增 `WITHDRAWN`
   - 新定义：`'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'`

3. 前端所有引用 `EXECUTING`、`COMPLETED`、`SUBMITTED` 的地方需同步修改（预计涉及 ContractLedgerPage、ContractDetailPage、ContractFormPage）。

### 后端常量

后端新建 `ContractStatusConstants` 类，值与 DB 字典一致：
```
approvalStatus: DRAFT / APPROVING / APPROVED / REJECTED / WITHDRAWN
contractStatus: DRAFT / PERFORMING / SETTLED / TERMINATED
businessType:   CONTRACT_APPROVAL
```

---

## 决策 2：回调事务一致性策略 ✅ 已裁定

**时间**：2026-06-11
**评审者**：Oracle

### Oracle 结论：采用方案 B

在 `WorkflowBusinessHandler` 接口新增 `default boolean isCritical() { return false; }`。

**引擎侧改动**（`WorkflowEngine.notifyHandler()`）：
- 提取现有 switch dispatch 为私有方法 `dispatchToHandler()`
- 在调用前检查 `handler.isCritical()`：
  - `true` → 不包 try-catch，异常向上传播 → `@Transactional` 回滚
  - `false`（默认）→ 保持现有 swallow-and-log 行为

**Handler 侧**：`ContractWorkflowHandler` override `isCritical()` 返回 `true`。`onApproved()` 内合同状态更新 + 成本生成如失败，抛 RuntimeException 自动回滚整个审批事务。

### 为何方案 B 优于方案 A

- 决策权在 Handler（业务层）而非引擎（基础设施层），职责分离
- 默认 false 向后兼容，未来非关键 Handler（通知/日志）不受影响
- 采购订单/材料验收等后续模块接入时无需改引擎，只需 override `isCritical()`
- 零 Handler 存在，无迁移成本；接口加一个 default 方法纯增量

### 代码改动量
- `WorkflowBusinessHandler.java`：+1 default 方法
- `WorkflowEngine.java`：提取 `dispatchToHandler()` + 1 个 if 分支
- 不影响任何现有测试
