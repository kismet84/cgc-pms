# 10A 本轮状态闭环记录（2026-07-02）

## 结论

10A 本轮低风险点状治理已按 `V113/V114/V115` 收口，通过，不阻塞后续逐码裁决。

- 阻塞/非阻塞：非阻塞
- 是否建议关闭本轮批次：建议关闭
- 是否代表 10A 全量完成：不代表 10A 全量完成

## 已关闭点状包

1. `V113`：`notification:list`，已清空 `sys_menu.id=761.perms`。
2. `V114`：`partner:list`，已清空 `sys_menu.id=401.perms`。
3. `V115`：`contract:list`，已清空 `sys_menu.id=301.perms`。

## 未进入实现的候选及原因

1. `invoice:list`
   - live 旧码菜单 `id=751` 仍绑定非管理角色 `FINANCE`。
   - 不能直接清空，不能并入本轮批量 migration。
2. `inventory:transaction:list`
   - live 旧码菜单 `id=733` 仍绑定 `MATERIAL_CLERK`。
   - 测试对菜单 `733` 有既有预期，不能直接清空。
3. `dashboard:view`
   - live 旧码仍绑定 5 个非管理角色。
   - 前端存在无细分 dashboard 权限时回退风险，不能直接清空。
4. `project:list`
   - 旧码本身仅 `SUPER_ADMIN` 绑定。
   - 真实替代权限 `project:query` 涉及项目列表、dashboard 项目选择器和 `data_scope=ALL` 项目边界风险，不作为本轮低风险样本。

## 验证证据

1. `MigrationIntegrityTest` 已覆盖 `V113/V114/V115`。
2. 最近一次定向测试结果为 `14 tests, 0 failures/errors/skipped, BUILD SUCCESS`。
3. 本轮验证口径仅确认 `V113/V114/V115` 关闭，不扩展到其他历史权限码。

## 剩余风险

1. `invoice:list`、`inventory:transaction:list`、`dashboard:view`、`project:list` 仍是单项准入阻塞或观察项。
2. 当前结论只覆盖本轮点状包，不代表 10A 全量完成。
3. 后续若继续推进，只能按单码裁决，不能批量清理其他历史权限码。

## 主负责人建议

1. 将 `V113/V114/V115` 作为本轮提交范围收口。
2. 其余候选继续保留阻塞/观察结论，等待逐码裁决。
3. 后续任何新批次都不要默认沿用本轮收口口径。
