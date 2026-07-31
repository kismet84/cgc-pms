# 外部输入资料清单

## 1. 文档定位

本文件用于整理“进入当前确认闭环前，外部至少需要提供哪些资料”。

使用边界：
- 本文件是资料收集与执行准备文档，不是已收集结果，也不是已执行结果。
- 本文件不填写真实账号、真实密码、真实审批模板名、真实执行结论。
- 本文件只定义需要收什么、谁提供、何时使用、如何进入现有确认流程。

## 2. 使用方式

建议在真实现场执行前先按本清单补齐资料，再进入以下闭环：
1. 外部资料到位后，先更新或对照 `pilot-account-and-approval-checklist.md`。
2. 现场执行时，将已拿到的资料作为 `pilot-confirmation-record-seeded.md` 的输入证据。
3. 现场完成后，再进入 `pending-items-signoff-seeded.md` 形成签收结论。
4. 只有签收为“通过”的事项，才允许按 `confirmation-writeback-sop.md` 进入正式回写。

## 3. 必收资料项

| 资料项 | 需要收集什么 | 提供方 | 什么时候用 | 进入哪个确认文件 | 缺失时的影响 |
|------|------|------|------|------|------|
| 试点租户信息 | 试点使用的租户范围、环境归属、是否唯一租户、现场核对方式 | 实施负责人 / 系统管理员 | 确认范围前、现场核对时 | `pilot-account-and-approval-checklist.md`、`pilot-confirmation-record-seeded.md` | 无法确认本次试点到底针对哪个环境或租户，后续记录和签收范围会失焦 |
| 账号清单 | 需开通的账号范围、角色归属、是否一人一号、发放对象 | 系统管理员 | 现场账号核对前 | `pilot-account-and-approval-checklist.md`、`pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 无法判断账号是否齐全，账号相关事项不能签收 |
| 角色映射资料 | 账号与角色的对应关系、冻结角色是否排除、角色分配依据 | 系统管理员 | 现场权限核对时 | `pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 无法确认培训对象和权限边界，角色归属项应保留待确认 |
| 密码发放口径 | 初始密码由谁发、通过什么渠道发、是否需要签收留痕 | 系统管理员 / 实施负责人 | 现场培训前、账号交付前 | `pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 培训现场无法统一登录口径，终端用户手册不能安全回写 |
| 密码重置口径 | 谁负责重置、触发场景、响应时点、首次登录是否改密 | 系统管理员 | 现场答疑、培训说明时 | `pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 现场只能口头解释，不能形成稳定操作口径 |
| 审批模板命名资料 | 模板列表、命名规则说明、是否已有统一命名约定 | 流程管理员 | 审批配置核对时 | `pilot-account-and-approval-checklist.md`、`pilot-confirmation-record-seeded.md` | 无法确认模板名称是否可培训、可回写，审批模板项不能通过 |
| 审批人映射资料 | 审批人按角色、岗位、组织、项目或手工指定的实际映射口径 | 流程管理员 / 系统管理员 | 审批流演示和现场核对时 | `pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 审批路径只能停留在口头层面，审批人映射项不能签收 |
| 生产经理能力演示证据 | 现场演示截图、演示路径、最小操作步骤、业务代表确认意见 | 培训讲师 / 业务代表 / 实施负责人 | 生产经理能力边界确认时 | `pilot-confirmation-record-seeded.md`、`pending-items-signoff-seeded.md` | 生产经理能力边界只能继续保留待确认，不得回写正式手册 |

## 4. 收集完成判定

只有满足以下条件，才可视为“资料已具备进入现场确认”：
- 每项资料都已明确提供方；
- 每项资料都能对应到现场记录或签收文件；
- 敏感信息不直接写入模板文档，只保留核对方式与责任角色；
- 缺失项已被显式标记，且现场知道哪些事项只能保留待确认。

## 5. 使用提醒

- 本清单只是外部输入准备清单，不代表外部资料已经到位。
- 资料未补齐时，可以组织现场，但缺失项应直接按“待确认”留痕，不得口头视为默认通过。
