# ISSUE-053-014 M3技术管理图纸与RFI闭环验收报告

## 1. 结论

- 验收结论：通过；无阻塞。
- 本地开发/演示验收通过；未切正式入口，未提交、推送、创建PR或发布生产。
- `/technical-management`为真实Clean-room V2页面；台账`LEGACY_ONLY=69 / V2_ACCEPTED=18 / V2_SOURCE_AVAILABLE=0`。
- 无数据库迁移或结构变更；仅补可幂等重放的dev/demo身份、权限、阶段和附件元数据。

## 2. 实现与边界

- 共享契约、薄服务和页面覆盖方案、图纸/版本、会审、RFI发起/回复/接受、交底、施工依据、验收归档和反向trace。
- 查询与九类动作权限严格分离；查询账号无写按钮且页面访问零写请求，后端403保持最终边界。
- 图纸版本和所有ID按字符串归一化；服务支持AbortSignal，写成功/拒绝均回读overview。
- 业务创建成功但附件上传失败时保留业务ID并锁定恢复对话框；重试仅执行附件上传，不重复业务POST。
- 非目标保持：未迁移项目收尾，未修改后端状态机、数据库、Legacy入口或生产环境。

## 3. 验证证据

| 层级 | 范围 | 结果 |
| --- | --- | --- |
| Ready | `ready-lint.ps1 ... ISSUE-053-014` | 通过 |
| 后端 | `TechnicalManagementClosedLoopIntegrationTest` | 2项通过 |
| Demo | `load.ps1`与`verify.ps1` | 通过；21阶段、10账号、9动作、0越权、10成员、7阶段行、6附件类型 |
| V2专项 | 技术域与路由单测 | 2文件12项通过；技术专项5项 |
| V2全量 | `test:unit` | 25文件124项通过 |
| 类型/静态 | contracts、Vue、Lint、Clean-room、`git diff --check` | 通过；106个V2文件、11个契约文件 |
| 路由/构建 | 台账、build、bundle size | 通过；87路由，技术页面JS 29.12kB，17个JS资产满足预算 |
| Live E2E | `V2_LIVE_TECHNICAL=1 playwright test e2e/m3-technical.spec.ts` | 11项通过；三视口、axe、查询零写、九动作真实账号 |

## 4. 发现与闭环

- demo总成员基线未计入新增技术成员，验真fail-close；更新客观总数17后通过。
- 图纸会审账号缺`RECEIVED`版本；补独立待会审图纸/版本，并把技术阶段验真增至7。
- 页面初版使用错误PageState属性且附件失败重试会重复业务创建；改为`kind`并引入`pendingEvidence`单独重试附件，专项回归通过。
- V2容器刷新一次后加载当前路由/页面模块；未达到同问题3次修复停止条件。

## 5. 回滚与后续

- 最小回滚：单路由恢复`LEGACY_ONLY`，回退技术契约/服务/页面、路由生成器、测试与M3技术demo增量；不删除既有技术业务事实或附件。
- 新增后续项0；关闭后续项0；后续项净变化0。
- `ISSUE-053-015～016`为既定串行计划，不是本轮发现项。
