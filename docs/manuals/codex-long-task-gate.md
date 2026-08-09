# Codex 长任务完成门禁

## 定位

`long-task-gate` 是仓库级显式金丝雀：原生 `/goal` 负责长时间持续执行；本门禁只在 Stop 阶段运行确定性 Completion Contract，并在通过后发送一次飞书终态通知。普通任务不建立 Contract，但每个主线程终态 Stop 仍发送一次 best-effort 飞书通知，并附最新助手消息首个非空文本行生成的一句话汇报；通知失败只警告，不阻断任务结束。

Codex 会从仓库 `.codex/hooks.json` 加载 Hook。首次使用或 Hook 定义变化后，在 `/hooks` 审查并信任当前 hash；不要用本仓库配置覆盖用户全局 Hook 或 `notify`。

## 激活与登记

用户提示必须包含独立标记 `$long-task-gate`。普通“长任务”文字不会激活。

1. 按 [Completion Contract](../../.agents/skills/long-task-gate/references/completion-contract.md) 创建仓库相对 JSON，放在已忽略的任务临时目录。
2. 登记：

   ```powershell
   node .agents/skills/long-task-gate/scripts/long-task-gate.mjs arm --contract <repo-relative-json>
   ```

3. 查询或取消：

   ```powershell
   node .agents/skills/long-task-gate/scripts/long-task-gate.mjs status
   node .agents/skills/long-task-gate/scripts/long-task-gate.mjs cancel
   ```

   进程崩溃遗留陈旧锁时，普通命令 fail-close。先查看 `status`，再显式执行 `node .agents/skills/long-task-gate/scripts/long-task-gate.mjs recover-lock`；存活进程的锁不可恢复。

4. Stop 时检查失败会生成 continuation。相同失败连续 3 次进入 `BLOCKED_GATE`，不无限续跑。

## 飞书目标与重试

V1 只使用已认证 Bot。普通终态通知从 `LTG_FEISHU_CHAT_ID` 读取 `oc_` 群聊目标；显式契约可使用 `chat-id` 或 `user-id`，但只保存私有环境变量名，例如 `LTG_FEISHU_USER_ID`。真实收件值不得进入仓库、契约、状态或日志。Windows 用户环境变量变更后必须完全重启 Codex，Hook 子进程才能继承新值。

通知失败不会重跑通过的业务检查，也不会把已通过任务改为阻塞；任务保持 `COMPLETED`，未发送 outbox 可在恢复认证、网络或目标后显式重试：

```powershell
node .agents/skills/long-task-gate/scripts/long-task-gate.mjs notify-retry
```

稳定幂等键由仓库、会话和契约 hash 派生。状态只保存 message id hash，不保存真实 message id 或收件值。

## 状态、安全与回滚

- 状态：`${CODEX_HOME}\long-task-gate`；未设置 `CODEX_HOME` 时使用当前用户默认 Codex 目录。
- 每会话最多一个活动任务；所有变更命令以当前 `CODEX_THREAD_ID`/官方 Hook `session_id` fencing，仓库锁只串行化并发写。
- 写入采用私有目录、原子 replace、state hash 和 generation；陈旧锁禁止自动回收，仅由独占 recovery guard 的显式 `recover-lock` 处理，避免 TOCTOU 双持锁。
- 命令检查总预算 265 秒，为通知和终态落盘保留时间；最后一项的子进程超时会被剪裁到剩余预算。
- 检查只执行 `shell:false` 的精确 executable/args；拒绝 shell executable、路径逃逸和符号链接逃逸。
- 事件不记录 prompt、命令完整输出、绝对用户目录、recipient、Token 或飞书 message id。普通任务汇报不落盘，只取最新助手消息首行、移除 Markdown 前缀、脱敏常见令牌并限制为 160 字。
- 回滚：在 `/hooks` 禁用本仓库 Hook；必要时先预览再清理本工具精确状态目录。不要删除飞书凭据、全局通知或其他 Codex 状态。

官方 Hook 行为与信任边界见 <https://learn.chatgpt.com/docs/hooks>；Skill 结构见 <https://learn.chatgpt.com/docs/build-skills>。
