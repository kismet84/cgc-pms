# Loop Budget Policy

本文件只定义预算作用域，不复制动态数值。

## 唯一事实入口

- run 总时长读取 `scripts/codex-autopilot/codex-autopilot.config.json` 的 `maxRunMinutes`。
- Issue 补修次数与超时读取配置的 `repair` 段。
- command 重试读取分类结果 `retryPolicy`、`rerun-policy.md` 与对应 Schema。
- validation/review 的命令集合与超时读取各自配置、Schema 和 Ready 契约。

command、Issue 与 run 是不同作用域，不得用一个默认分钟数或重试数覆盖其他作用域。

## 稳定规则

1. 首次失败先分类；只有策略允许时做一次有界复验，同一失败重复出现转 repair 或 blocked。
2. 高风险权限、安全和数据一致性问题可提高证据强度，不能突破配置与授权边界。
3. 预算触边写入 checkpoint；恢复时读取当前配置和既有证据，不新建第二套计数账本。
4. 用户提高验收标准只改变证据范围，不静默扩大 Git、生产、数据库或外部写入授权。
