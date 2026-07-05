# 10A 第二版首批最小修复包阻塞归档（2026-07-02）

## 结论

不通过进入实现。

当前状态：阻塞。

阻塞性质：环境阻塞，不是样本语义阻塞。

## 阻塞结论

`notification:list` 作为 10A 第二版低风险样本，已通过后端、安全、代码质量和数据库静态复核，但在进入 migration 前要求的 live MySQL 只读复验未能执行，因此当前不得进入小包 2 migration。

## 已确认通过项

1. 后端通过：
   - `notification:list` 不被 `NotificationController`、后端测试或前端权限 helper 真实消费。
   - 通知真实读权限为 `notification:view`，写权限为 `notification:edit`。
   - 不应把 `notification:list` 替换成 `notification:view`。
2. 安全通过：
   - `notification:list` 属于 DB-only 旧读码残留。
   - 清空 `sys_menu.id=761.perms` 不会扩大权限。
   - 只允许清空，不允许替换为 `notification:view`。
3. 代码质量通过：
   - 第二版样本触达面明显小于 `alert:edit`、`contract:submit`、submit 未绑定和 `system/project` 高危旧码。
   - 若执行，最小 diff 可限定为一对 MySQL/H2 migration。
4. 数据库静态复核有条件通过：
   - `menu_id=761` 来源于 `V39__init_phase4_menu_perms.sql` / H2 对等迁移。
   - 静态迁移链仅确认 SUPER_ADMIN 种子绑定。
   - 最小数据库动作可限定为仅清空 `sys_menu.id=761.perms`，不改 `sys_role_menu`。

## 实际阻塞点

数据库负责人执行 live MySQL 只读复验时命中环境阻塞：

1. Docker API 不可用，无法读取当前 Docker 运行态。
2. `deploy/docker-compose.dev.yml` 中 dev MySQL 映射端口为 `127.0.0.1:3307 -> 3306`，但主机侧 `3307` 未监听。
3. 本机未发现可直接调用的 `mysql` 客户端，无法绕过 Docker/端口映射执行只读查询。

因此以下 3 条 SQL 均未执行：

```sql
SELECT id, parent_id, menu_name, menu_type, path, component, perms, status, visible, tenant_id
FROM sys_menu
WHERE id = 761;

SELECT rm.role_id, r.role_code, r.role_name, r.role_type, r.data_scope
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
WHERE rm.menu_id = 761
ORDER BY rm.role_id;

SELECT COUNT(*) AS non_admin_bind_count
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
WHERE rm.menu_id = 761
  AND COALESCE(r.role_code, '') NOT IN ('SUPER_ADMIN','ADMIN');
```

## 主负责人裁决

1. 当前不得进入小包 2 migration。
2. 当前不得执行任何与第二版样本相关的代码改动、migration、新增测试、运行态刷新或验收。
3. 第二版后续只有两条路：
   - 恢复 dev MySQL 可达性后，重跑上述 3 条只读 SQL；
   - 明确裁定 10A 暂停，不再继续推进第二版样本。

## 后续恢复条件

只有同时满足以下条件，才允许重新打开第二版：

1. dev MySQL 可达。
2. `menu_id=761` 的 live 查询可执行。
3. `non_admin_bind_count = 0`。
4. 未发现运行态真实消费 `notification:list`。

在上述条件满足前，本包保持阻塞。
