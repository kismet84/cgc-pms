# 第40条主线 M0：历史阻塞状态归一化验收报告

## 结论

- 结论：通过。
- 阻塞：`ISSUE-037-021-A/B/C` 三条旧阻塞全部解除。
- 失败分类：原 A/B 为真实质量类，原 C 为工具配置/仓库治理类；均已有后续修复和当前远端证据。
- 是否已上线：否；本报告只裁决 CI 合并门禁与分支保护状态，不代表执行生产发布。

## 复核范围

- PR：[kismet84/cgc-pms#334](https://github.com/kismet84/cgc-pms/pull/334)
- PR head：`b1960ec79d73d6dfbf5601be51cc002fb0eca73c`
- 合并提交：`76ec42a0b4f386a4fd432abc62205a3135a1b65d`
- 合并时间：2026-07-13 09:12:45（Asia/Shanghai）
- CI 证据：[Actions run 29216559829](https://github.com/kismet84/cgc-pms/actions/runs/29216559829)
- 分支保护：GitHub Branch Protection API 当前只读响应。

## 验收证据

| 项目 | 当前证据 | 裁决 |
| --- | --- | --- |
| PR 状态 | `MERGED`；merge commit 为 `76ec42a0` | 通过 |
| Required checks | `backend-test`、`backend-test-mysql`、`backend-dependency-scan`、`frontend-lint`、`type-check`、`frontend-build`、`frontend-test`、`frontend-dependency-audit`、`sql-safety-scan`、`e2e`、`supply-chain-security` 全部 `SUCCESS` | 通过 |
| 其他汇总 | `build-summary` 为 `SUCCESS` | 通过 |
| 严格检查 | `required_status_checks.strict=true`，11 个 check 与 GitHub Actions app 绑定 | 通过 |
| 管理员保护 | `enforce_admins=true` | 原管理员绕过风险已解除 |
| 对话解决 | `required_conversation_resolution=true` | 通过 |
| 强推/删除 | `allow_force_pushes=false`、`allow_deletions=false` | 通过 |

## 三条历史阻塞裁决

1. `ISSUE-037-021-A`：前端 lint/test 红灯已由同一 PR 后续提交修复，目标提交两项 check 成功，判 `VerifiedResolved`。
2. `ISSUE-037-021-B`：付款申请页 E2E 契约已修复，目标提交 e2e check 成功，判 `VerifiedResolved`。
3. `ISSUE-037-021-C`：管理员绕过路径已由 `enforce_admins=true` 关闭，strict checks、对话解决、禁止强推/删除保持有效，判 `VerifiedResolved`。

## 状态载体归一化

- `blocked-issues.md` 当前活动阻塞清空，三条记录迁入“已解除的历史阻塞”。
- Current Focus、项目地图、第40条计划和未来开发计划统一改为 M0 已完成。
- `ISSUE-037-021` 首次失败报告继续保留为历史证据，但不再代表当前门禁状态。

## 剩余风险

- 当前 API 未返回 required pull-request review 和 push restrictions。这不再构成原 `enforce_admins=false` 的直接绕过，但是否要求审批人数或推送主体白名单属于仓库治理决策，状态为“需要确认”。
- PR 合并与 CI 门禁通过不等于生产已发布；正式上线仍需独立授权、发布清单和生产前置核验。
