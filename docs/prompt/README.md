# Prompt 文档索引

`docs/prompt/` 存放任务型提示词，不替代主线规范。使用顺序是先读 `AGENTS.override.md`、`AGENTS.md`、相关主线规范，再读对应 prompt。

| 场景 | Prompt | 使用要求 |
|------|--------|----------|
| 自适应执行路由/条件派工 | [subagent-dispatch-template.md](subagent-dispatch-template.md) | 默认主线程直接执行；仅实际派工时先补前置检查，再按完整字段下发 |
| 验收/上线裁决 | [acceptance-closeout-template.md](acceptance-closeout-template.md) | 先写通过/不通过与阻塞结论，再补证据 |
| 周复盘归档 | [weekly-codex-review-template.md](weekly-codex-review-template.md) | 用于周度任务复盘，只沉淀可复用结论，不写具体 run/PR/commit |
| 前端 Docker UI 验收 | [frontend-docker-ui-test-rules.md](frontend-docker-ui-test-rules.md) | 以 Docker 前端 `http://localhost:5173/` 为准 |
| 飞书确认交互 | [lark-confirmation-flow.md](lark-confirmation-flow.md) | 仅在必须用户决策且需要飞书确认时使用 |
