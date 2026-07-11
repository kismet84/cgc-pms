# 10A 第二版首批最小修复包质量归档

日期：2026-07-02

## 结论

10A 第二版首批最小修复包归档结论：通过。

- 阻塞/非阻塞：非阻塞
- 是否建议关闭 10A 第二版首包：建议关闭
- 本包性质：最小数据库点状修复收口

## 通过依据

1. 数据库侧通过
   - 本包仅新增一对 MySQL/H2 对等 migration：`V113`。
   - 仅补充最小 `MigrationIntegrityTest` 验证。
   - 修复动作严格限定为清空 `sys_menu.id=761` 的 `perms`，运行态已确认该字段为 `NULL`。
2. 冻结边界通过
   - 未将 `notification:list` 改写为 `notification:view`。
   - 未修改 `sys_role_menu`。
   - 未扩散到 `alert:edit`、`contract:submit`、submit 未绑定项或其他历史权限码。
3. 运维侧通过
   - 已确认 `V113` 进入运行态。
   - 已确认 `sys_menu.id=761.perms = NULL`。
4. 测试侧通过
   - `notification:list` 已从 `/api/auth/userinfo` 权限集消失。
   - `notification:list` 已从 `/api/system/menus/tree` 等价菜单数据中消失。
   - 通知读取接口仍按 `notification:view` 控制，未发生能力回退。
   - 第 9 条审批轻量探针未回退。
5. 安全侧通过
   - 本次修复未扩大通知读取能力。
   - 未放松通知读写接口授权。
   - 未影响第 9 条权限清单与审批入口安全基线。

## 冻结边界核对

1. 本包只清空 `sys_menu.id=761.perms`。
2. 本包不把 `notification:list` 改成 `notification:view`。
3. 本包不改 `sys_role_menu`。
4. 本包不改通知控制器、前端权限 helper、权限清单实现或审批入口 registry。
5. 本包关闭仅代表 10A 第二版首包收口完成，不等于 10A 其余历史权限码治理全部完成。

## 代码与运行态事实

1. `notification:list` 当前按既有复核结论属于 DB-only 历史旧读码残留，不是通知模块真实接口权限。
2. 通知模块既有真实权限语义保持不变：
   - 读：`notification:view`
   - 写：`notification:edit`
3. 本次运行态收口结果是移除登录权限集和菜单权限数据中的旧残留码，不是把旧码替换成新的真实权限码。

## 归档关系说明

1. 本归档对应 10A 第二版首批最小修复包的正式关闭结论。
2. 本归档基于前置阻塞归档 [10a-notification-list-second-package-blocked-2026-07-02.md](D:\projects-test\cgc-pms\docs\quality\10a-notification-list-second-package-blocked-2026-07-02.md) 的后续收口，不改写当时“环境阻塞”判断本身。
3. 当前 `docs/` 目录仍被仓库 `.gitignore` 忽略，归档文件默认不进入版本管理；该点不影响本地质量归档成立。

## 非阻塞剩余风险

1. 10A 其余历史权限码仍待后续逐项裁决，当前不能沿用本包结论直接批量清理。
2. 若后续有人尝试把同类旧码直接改写成现行真实权限码，需要重新经过后端、安全、测试和代码质量联合裁决。
3. 第 9 条基线本轮已确认未回退；后续任何权限治理新包若触达审批入口或权限清单，仍需重新做回归验证。

## 主负责人裁决建议

1. 建议关闭 10A 第二版首批最小修复包。
2. 不建议把本包结论外推为“10A 全部完成”。
3. 后续若继续推进 10A，应继续按“单码点状治理、逐包验收、不得批量替换为真实权限码”的口径串行推进。
