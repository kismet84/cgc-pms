# 10A `contract:list` 第四个点状包收口归档（2026-07-02）

## 结论

建议关闭该点状包；通过，不阻塞 10A 后续逐码裁决。

## 通过依据

1. live MySQL 只读准入已确认：`sys_menu.id=301.perms = contract:list`，且 `menu_id=301` 的 `non_admin_bindings = 0`。
2. live MySQL 只读准入已确认：`sys_menu.id=804.perms = contract:query`，且 `menu_id=804` 的 `non_admin_bindings = 0`。
3. 静态复核已确认：后端真实读权限是 `contract:query`，审批 `workflowDisplay/registry` 使用的也是 `contract:query`，未发现前端真实消费 `contract:list`。

## 冻结边界

1. 仅处理 `sys_menu.id=301` 上的 DB-only 旧码 `contract:list`。
2. 不改 `sys_role_menu`。
3. 不改 `contract:query`。
4. 不把 `contract:list` 替换成 `contract:query`。
5. 不动合同子按钮。
6. 不外推到其他历史权限码，不代表 10A 全量完成。

## V115 范围

1. MySQL/H2 各新增一个 V115 migration。
2. SQL 仅执行：

```sql
UPDATE sys_menu
SET perms = NULL
WHERE id = 301
  AND perms = 'contract:list';
```

3. `MigrationIntegrityTest` 新增最小静态约束：要求存在 V115，且仅允许上述点状清理；不得触达 `contract:query` 或 `sys_role_menu`。

## 剩余风险

1. 当前仅完成 `contract:list` 这个点状样本，不代表 10A 全量完成。
2. 最终验收仍依赖 verifier 跑定向测试 / 检查。
3. 其他 DB-only 历史权限码仍需逐码裁决，禁止批量外推。
