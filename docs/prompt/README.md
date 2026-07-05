# Prompt 文档索引

`docs/prompt/` 存放任务型提示词，不替代主线规范。使用顺序是先读 `AGENTS.override.md`、`AGENTS.md`、相关主线规范，再读对应 prompt。

| 场景 | Prompt | 使用要求 |
|------|--------|----------|
| 前端 Docker UI 验收 | [frontend-docker-ui-test-rules.md](frontend-docker-ui-test-rules.md) | 以 Docker 前端 `http://localhost:5173/` 为准 |
| 飞书确认交互 | [lark-confirmation-flow.md](lark-confirmation-flow.md) | 仅在必须用户决策且需要飞书确认时使用 |
