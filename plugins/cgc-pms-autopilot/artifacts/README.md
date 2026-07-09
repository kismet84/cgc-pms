# Plugin Artifacts

本目录只存放 `plugins/cgc-pms-autopilot` 自身的正式产出物，不存放项目真实业务事实源。

## 默认落点

- `plans/`：插件自有计划书与阶段计划
- `quality/`：插件自有质量报告、验收收口、审计结论
- `iterations/`：插件自有迭代摘要
- `runs/`：插件自有 run summary、loop preview、受控运行摘要

这些目录只服务于插件自身封装、验证、治理和收口，不替代项目正式业务文档目录。

## 边界

- 项目真实 `docs/backlog`、业务 `docs/plans`、业务 `docs/quality`、业务 `docs/iterations` 继续留在仓库根 `docs/**`
- 独立项目业务任务产出的计划书、质量报告、迭代摘要和 backlog 更新，应继续写入项目 `docs/**` 对应目录，不写入本目录
- 插件运行时可以读取项目 `docs/**` 作为事实源和参考，但不应把这些内容复制成插件资产
- 插件脚本仍以显式 `OutputPath` 为准；如需默认值，优先从本目录下选择对应子目录
- 不写临时日志、截图、缓存、一次性 run id 或个人昵称
