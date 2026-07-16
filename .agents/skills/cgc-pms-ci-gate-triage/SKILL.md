---
name: cgc-pms-ci-gate-triage
description: 用于 cgc-pms 项目的 GitHub Actions 与 CI 门禁排障：读取 workflow 或门禁日志、先做失败分类、核对远端 checks 与分支保护、区分工具配置问题、环境前置问题和真实质量安全问题。当用户要求排查 CI 红灯、门禁失败、checks 不通过或上线门禁阻塞时使用。
---

# cgc-pms CI 门禁分诊

通用协议=`docs/standards/codex-task-execution-policy.md`

1. 先读仓库根 `AGENTS.override.md`、`AGENTS.md` 与通用协议，不要把所有红灯直接归因为业务代码失败。
2. 先收集最小事实：
   - 工作流名、job、step
   - 分支名、提交号
   - 失败关键词
   - 本地与远端 checks 是否一致
3. 按通用协议分类：
   - `tool_config`：workflow 触发、脚本入口、凭据、工具版本、规则文件加载
   - `tool_invocation`：参数、schema、转义、PowerShell/Maven 调用格式
   - `environment_prerequisite`：Docker/WSL/数据库/端口/测试数据/等待时间/旧代理
   - `ready_issue_config`：测试选择器、验证入口或 Ready 契约失真
   - `quality_or_security`：测试、构建、类型、契约、权限、安全、数据一致性真实失败
   - `unknown`：证据不足或本地/远端结果冲突
4. 最小处理顺序固定为：先分类 -> 配置修复/调用纠正/环境恢复 -> 一次最小等价复验 -> 仍失败才进入代码整改或阻塞裁决。相同前置和参数下不得原样重试。
5. CI 轮询采用退避节奏；状态未变化时保持静默，只在状态变化、超过预期、确定失败或需要用户决策时播报。
6. 需要核对远端状态时，优先使用绑定当前提交的真实远端 checks、分支保护规则和最新 workflow 结果，不只看本地缓存状态。
7. GitHub 整份 job 日志或 artifact 因 EOF、Schannel 或稳定超时不可得时，不切换 Git SSH、不无界尝试下载端点。优先从已登录的 job 页面定位失败 step 的临时日志入口；确认服务端支持 `Accept-Ranges` 后只读取末段，默认 `256 KB`，若缺少最终测试摘要只允许扩大一次。临时签名 URL 只作会话证据，不写入长期规则或正式报告。
8. 回报时明确：
   - 失败分类
   - 当前是否阻塞
   - 下一步该派给实现型、运维型还是验收型子智能体

## 最小回报骨架

```text
失败任务=
失败步骤=
失败分类=
关键证据=
当前处理=
下一步=
是否阻塞=
复验次数=
```
