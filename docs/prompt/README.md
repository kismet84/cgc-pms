# Prompt 文档索引

`docs/prompt/` 存放任务型提示词，不替代自动加载的 `AGENTS.md` 或按需专项 Skill；仅在任务命中时读取对应 prompt。

| 场景 | Prompt | 使用要求 |
|------|--------|----------|
| 验收/上线裁决 | [acceptance-closeout-template.md](acceptance-closeout-template.md) | 先写通过/不通过与阻塞结论，再补证据 |
| 周复盘归档 | [weekly-codex-review-template.md](weekly-codex-review-template.md) | 用于周度任务复盘，只沉淀可复用结论，不写具体 run/PR/commit |
| 前端 Docker UI 验收 | [frontend-docker-ui-test-rules.md](frontend-docker-ui-test-rules.md) | 以 Docker 前端 `http://localhost:5173/` 为准 |
| 飞书确认交互 | [lark-confirmation-flow.md](lark-confirmation-flow.md) | 仅在必须用户决策且需要飞书确认时使用 |
