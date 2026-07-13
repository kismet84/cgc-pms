# 10A `partner:list` 第三个点状包收口归档（2026-07-02）

## 结论

建议关闭该点状包；通过，不阻塞 10A 后续逐码裁决。

## 通过依据

1. live MySQL 只读准入已确认：`sys_menu.id=401.perms = partner:list`，且 `menu_id=401` 的 `non_admin_bound_roles = 0`。
2. live MySQL 只读准入已确认：`sys_menu.id=805.perms = partner:query`，且 `menu_id=805` 的 `non_admin_bound_roles = 0`。
3. 静态复核已确认：后端 Partner 控制器真实读权限为 `partner:query`，`partner:list` 不是后端真实消费权限。

## 冻结边界

1. 仅处理 `sys_menu.id=401` 上的 DB-only 旧码 `partner:list`。
2. 不改 `sys_role_menu`。
3. 不改 `partner:query`。
4. 不把 `partner:list` 替换成 `partner:query`。
5. 不外推到其他历史权限码。

## V114 范围

1. MySQL/H2 各新增一个 V114 migration。
2. SQL 仅执行：

```sql
UPDATE sys_menu
SET perms = NULL
WHERE id = 401
  AND perms = 'partner:list';
```

3. `MigrationIntegrityTest` 新增最小静态约束，限定只允许上述点状清理，不允许触达 `partner:query` 或 `sys_role_menu`。

## 剩余风险

1. 当前仅完成 `partner:list` 这个点状样本，不代表 10A 全量完成。
2. 最终验收仍依赖 verifier 跑定向测试 / 检查。
3. 其他 DB-only 历史权限码仍需逐码裁决，禁止批量外推。
