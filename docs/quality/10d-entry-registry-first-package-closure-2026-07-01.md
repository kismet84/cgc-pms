# 10D 首批实现包质量归档

日期：2026-07-01

## 结论

10D 首批实现包归档结论：通过。

- 阻塞/非阻塞：非阻塞
- 是否建议关闭本包：建议关闭
- 收口方式：零代码收口

## 通过依据

1. 前端侧通过
   - 已确认 `frontend-admin/src/pages/approval/workflowDisplay.ts` 现有 `workflowBusinessEntryRegistry` 已满足首批 registry/helper 最小字段集：
     - `businessType`
     - `displayName`
     - `permissionCode`
     - `targetRoute`
     - `openMode`
     - `forbiddenPolicy`
   - 当前代码侧仅接入三类审批入口：
     - `CONTRACT` / `CONTRACT_APPROVAL` -> 合同审批
     - `PURCHASE_REQUEST` -> 采购申请
     - `SUB_MEASURE` -> 分包计量
   - `PAY_APPLICATION`、`PAY_REQUEST` 当前仅保留在 `workflowBusinessTypeLabels` 作为业务类型标签，不在 `workflowBusinessEntryRegistry` 中，不代表已接入入口 registry。
   - 前端最终结论为零代码收口通过，符合计划书“若 `workflowDisplay.ts` 已满足本包目标，则允许零代码实现 + 补充验证/归档收口”的冻结口径。
2. 后端侧通过
   - 零新增接口成立。
   - 零新增 workflow 授权聚合成立。
   - 零新增目标业务权限预判接口成立。
   - registry 仍仅用于前端导航展示元数据，不提升为后端授权边界。
3. 数据库侧通过
   - 零 DB、零 migration、零入口注册表成立。
   - 未引入数据库化 registry，避免形成代码 registry / router / `sys_menu` 三源并存风险。
4. 安全侧通过
   - 正式复核通过。
   - registry 未被当作授权边界使用，目标业务权限仍由目标业务模块自身接口鉴权承担。
   - 未新增目标业务详情字段聚合，未引入权限预判返回能力。
5. 运维侧通过
   - 本包为零代码收口，不需要运行态刷新。
   - 未触发前端重启、后端重启或 Docker 等待链路。
6. 测试侧通过
   - 最小运行态验收通过。
   - 证据目录：`D:/projects-test/cgc-pms/output/playwright/10d-entry-registry-acceptance-20260701/`
   - 结合前置回传，可确认首批三类入口与第 8 / 第 9 条审批入口基线未回退。

## 冻结边界核对

1. 首批仅覆盖审批中心三类入口：
   - 合同审批
   - 采购申请
   - 分包计量
2. registry 仅承载导航展示元数据，不是授权边界。
3. 不新增入口注册表。
4. 不新增插件框架。
5. 不新增后端授权聚合。
6. 不新增目标业务权限预判接口。
7. 不接付款申请或更多业务类型。
8. 不改第 8 / 第 9 条审批入口安全基线。

## 代码事实

`frontend-admin/src/pages/approval/workflowDisplay.ts` 当前已形成单点入口口径：

1. `workflowBusinessEntryRegistry`
   - 仅包含合同审批、采购申请、分包计量三类入口。
2. `getWorkflowBusinessEntry`
   - 统一按 `businessType` 读取入口元数据。
3. `getWorkflowBusinessEntryPath`
   - 统一按 registry 计算目标路由。
4. `getWorkflowBusinessEntryPermission`
   - 统一按 registry 读取权限码。
5. `canAccessWorkflowBusinessEntry`
   - 仅据现有角色/权限做前端可达性判断；未引入后端预判接口。

其中：

- `PAY_APPLICATION: '付款申请'`
- `PAY_REQUEST: '付款申请'`

仍仅存在于 `workflowBusinessTypeLabels` 标签映射中，未出现在 `workflowBusinessEntryRegistry`，因此不构成已接入入口 registry 的事实。

## 第 8 / 第 9 条基线回归结论

1. 第 8 条账号矩阵基线未回退。
2. 第 9 条审批入口 registry/helper 收口基线未回退。
3. 管理员三类正向入口能力保持。
4. `workflow-only` 三类负向口径保持。
5. `cc-readonly` 只读口径保持。
6. `non-participant` 不可见或 `data=null` 口径保持。

## 非阻塞观察项

1. 本包为零代码收口，因此没有新增定向单测或 `build` 产物；当前通过依据来自代码只读核对、前置专业回传与最小运行态验收，不构成阻塞。
2. `PAY_APPLICATION/PAY_REQUEST` 仍有业务类型标签，但未接入 registry；后续若主负责人要求接入，必须另开新包，不得回灌本首包。
3. 当前工作区仍存在非 10D 范围内的既有改动与 `output/` 残留，不属于本归档处理范围。

## 主负责人裁决建议

1. 10D 首批实现包已按冻结边界完成，可关闭。
2. 后续若推进付款申请或更多业务类型，应按新实现包重新冻结业务范围、账号矩阵与负向验收，不得沿用本包“已通过”结论直接外扩。
3. 后续若有人尝试把 registry 扩成入口注册表、插件框架、后端授权聚合或目标业务权限预判接口，按越界处理。
