# Artifact Governance

## 插件内允许长期存在

- `skills/`
- `scripts/`
- `templates/`
- `references/`
- `examples/`
- `artifacts/README.md`
- `artifacts/plans/`
- `artifacts/quality/`
- `artifacts/iterations/`
- `artifacts/runs/`

## 默认正式落点

- 插件自身计划书：`plugins/cgc-pms-autopilot/artifacts/plans/`
- 插件自身质量/收口报告：`plugins/cgc-pms-autopilot/artifacts/quality/`
- 插件自身迭代摘要：`plugins/cgc-pms-autopilot/artifacts/iterations/`
- 插件自身 run summary / loop preview：`plugins/cgc-pms-autopilot/artifacts/runs/`

以上只适用于插件自身治理、封装、验证、收口所产生的正式文档，不自动外溢到项目级 `docs/**`。

## 项目业务任务正式落点

- 独立项目业务任务的计划书：`docs/plans`
- 独立项目业务任务的质量/收口报告：`docs/quality`
- 独立项目业务任务的迭代摘要：`docs/iterations`
- 项目真实 backlog / ready / done / blocked / focus：`docs/backlog`

这些文档属于项目事实源，不因使用本插件而迁入 `plugins/cgc-pms-autopilot/artifacts/**`。

## 插件运行时允许读取

- 允许读取项目真实 `docs/backlog`
- 允许读取项目真实业务 `docs/plans`
- 允许读取项目真实业务 `docs/quality`
- 允许读取项目真实业务 `docs/iterations`

读取目的仅限于编排、验收、收口和引用事实源；不默认复制、镜像或改写为插件资产。

## 明确禁止

- 复制项目真实 `ready/done/blocked/quality/iteration` 内容进插件，伪装成插件资产
- 复制 `events.jsonl`、`result.json`、截图、临时日志
- 写入一次性 run id、日志文件名、个人昵称
- 把插件自有计划书、收口报告继续默认写到仓库级 `docs/plans` 或 `docs/quality`
- 把独立项目业务任务的正式计划书、质量报告、backlog、iteration 错写到 `plugins/cgc-pms-autopilot/artifacts/**`

## 脚本写入边界

- 只允许写调用方显式传入的 `OutputPath`
- 若需要为插件自身产物选默认路径，优先在 `plugins/cgc-pms-autopilot/artifacts/**` 下选对应子目录
- 默认不覆盖已有文件，除非显式 `-Force`
- 不提供删除能力
