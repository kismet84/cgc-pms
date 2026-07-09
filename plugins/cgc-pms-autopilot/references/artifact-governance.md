# Artifact Governance

## 插件内允许长期存在

- `skills/`
- `scripts/`
- `templates/`
- `references/`
- `examples/`

## 项目内正式落点

- `docs/plans`
- `docs/quality`
- `docs/iterations`
- `docs/backlog`

## 明确禁止

- 复制项目真实 `ready/done/blocked/quality/iteration` 内容进插件
- 复制 `events.jsonl`、`result.json`、截图、临时日志
- 写入一次性 run id、日志文件名、个人昵称

## 脚本写入边界

- 只允许写调用方显式传入的 `OutputPath`
- 默认不覆盖已有文件，除非显式 `-Force`
- 不提供删除能力
