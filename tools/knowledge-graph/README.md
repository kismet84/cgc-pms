# cgc-pms 项目知识图谱

本工具将项目正式文档、backlog、迭代记录、插件正式产物和最近 Git 提交增量写入本机 Neo4j，并以 MCP 工具提供给 Codex。

## 边界

- Neo4j 是关联索引，仓库文件和 Git 仍是事实源。
- 默认不读取 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/` 和 `archive/v1.0/private/`。
- `docs/archive/v1.0/` 是 Git 跟踪的正式历史资料，允许只读采集，但统一标记 `historical=true`、`versionScope=v1.0`；默认搜索不返回历史结果。
- Markdown 显式链接若指向仓库内的非文档文件或目录，只建立 `referenceOnly` 轻量路径节点和 `REFERENCES` 边，不读取目标正文；不存在、越界或命中禁止区的目标仍保持未解析。
- 不把原始日志写入图谱；使用 `kg_record_episode(kind="log-summary")` 记录摘要、来源和时间。
- 不扫描 Codex 私有会话目录；任务收口时使用 `kg_record_episode(kind="conversation")` 写入结构化摘要。
- `kg_query` 只接受受限只读 Cypher；写入仅通过固定字段的 `kg_record_episode`。
- 当前问题以 `docs/backlog/current-issues.json` 为机器可读唯一快照；采集器确定性校验后生成 `Issue` 节点，不从任意报告文字猜测状态。

## 本地命令

```powershell
cd tools/knowledge-graph
npm install
npm run schema
npm run collect
npm run status
npm test
```

采集采用事件驱动与定时对账组合：任务收口可调用 `kg_record_episode` 后执行
`scripts/collect.ps1 -Trigger closeout`；Windows 定时对账安装器默认只预演：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-schedule.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-schedule.ps1 -Install
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/uninstall-schedule.ps1 -Confirm
```

定时任务每 30 分钟运行一次，只补充本地图谱，不启动业务任务、不修改仓库、不提交或 push。

默认从 `%USERPROFILE%\.cgc-pms-secrets\neo4j.env` 读取 Neo4j 凭据，可使用 `CGC_KG_*` 环境变量覆盖。

## Codex MCP

```powershell
codex mcp add cgc-pms-knowledge-graph -- node D:\projects-test\cgc-pms\tools\knowledge-graph\src\server.js
```

注册后新建 Codex 任务，使 MCP 工具列表刷新。

`kg_search` 支持 `scope=current|historical|all`，默认 `current`。历史资料只用于回溯和分类，不能替代当前代码、配置、backlog 与新鲜验证证据。

查询当前问题优先使用 `kg_list_issues`，不要用宽泛全文检索重建台账：

```json
{ "view": "summary" }
```

需要明细时使用 `{ "view": "list", "parentIssueKey": "A-01", "limit": 100 }`；可按 `status`、`classification`、`priority`、`blocking` 和关键字继续过滤。默认只返回 `current=true` 的问题，摘要查询不展开文档正文或历史版本。

## Schema

核心节点：`Project`、`Artifact`、`ArtifactVersion`、`Section`、`Issue`、`GitCommit`、`Episode`、`Evidence`、`Decision`、`Source`、`SourceCursor`、`CollectionRun`、`Entity`。

核心关系：`IN_PROJECT`、`CONTAINS`、`CURRENT_VERSION`、`VERSION_OF`、`DEFINED_IN`、`SUPPORTED_BY`、`PART_OF`、`REFERENCES`、`CHANGES`、`DERIVED_FROM`、`COLLECTED_IN`、`USED_CURSOR`；后续受控抽取可扩展 `RELATES_TO`、`VERIFIES`、`IMPLEMENTS`、`BLOCKS`、`DEPENDS_ON` 和 `SUPERSEDES`，但不得在缺乏来源证据时自动创建事实关系。
