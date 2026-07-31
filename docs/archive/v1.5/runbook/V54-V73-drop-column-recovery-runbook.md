# V54/V73 DROP COLUMN 回滚补救 Runbook

适用范围：已应用 `V54__refactor_contract_party_fields.sql` 或 `V73__drop_contract_warranty_columns.sql` 的环境。旧 Flyway migration 不再修改；补救只能依赖备份、恢复演练和字段语义确认。

## 字段语义

| Migration | 被删除字段 | 原语义 | 新口径 |
| --- | --- | --- | --- |
| V54 | `ct_contract.partner_id` | 合同关联合作方 | 改为甲乙方合作方 ID，不再使用单一 partner |
| V54 | `ct_contract.party_a` | 甲方文本名称 | `party_a_id` 关联 `md_partner` 后通过 JOIN 展示名称 |
| V54 | `ct_contract.party_b` | 乙方文本名称 | `party_b_id` 关联 `md_partner` 后通过 JOIN 展示名称 |
| V73 | `ct_contract.warranty_rate` | 质保比例 | 当前合同主表不再保留该字段，需要从备份或业务归档恢复 |
| V73 | `ct_contract.warranty_amount` | 质保金额 | 当前合同主表不再保留该字段，需要从备份或业务归档恢复 |

## 备份要求

1. 应用 V54/V73 前必须有全库一致性备份，并记录备份文件、数据库版本、应用版本、Flyway 当前版本。
2. MySQL 示例：

```bash
mysqldump --single-transaction --routines --triggers --events \
  -h <host> -u <user> -p <database> > backup-before-v54-v73.sql
```

3. 备份完成后先在隔离库恢复一次，确认 `ct_contract` 中上述字段可查询。

## 恢复策略

优先级从高到低：

1. **整库回滚**：上线窗口内发现严重问题，停止应用，恢复 V54/V73 前全库备份，再回退应用版本。
2. **字段级补救**：如果只缺少被删除字段的数据，在隔离库恢复备份，导出 `ct_contract.id` 与旧字段值，经业务确认后用新增补救脚本回填到新的业务字段或归档表。
3. **人工业务补录**：备份不可用时，必须由业务负责人确认字段来源，不允许用空值或猜测值自动补齐。

## 演练步骤

1. 在预发或临时数据库恢复 V54/V73 前备份。
2. 校验旧字段存在：

```sql
SELECT partner_id, party_a, party_b, warranty_rate, warranty_amount
FROM ct_contract
LIMIT 10;
```

3. 导出字段级恢复样本：

```sql
SELECT id, partner_id, party_a, party_b, warranty_rate, warranty_amount
FROM ct_contract
WHERE partner_id IS NOT NULL
   OR party_a IS NOT NULL
   OR party_b IS NOT NULL
   OR warranty_rate IS NOT NULL
   OR warranty_amount IS NOT NULL;
```

4. 在当前版本数据库只读核对新字段：

```sql
SELECT id, party_a_id, party_b_id
FROM ct_contract
LIMIT 10;
```

5. 形成演练记录：备份文件、恢复耗时、旧字段非空数量、字段级补救决策、负责人签字。

## 禁止事项

1. 禁止修改已经应用的 V54/V73 migration。
2. 禁止新增“反向 DROP/ADD”迁移来假装回滚；恢复旧数据必须来自备份或业务确认。
3. 禁止在未确认字段语义时把旧文本名称直接写入 `party_a_id` / `party_b_id`。
