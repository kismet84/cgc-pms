# cgc-pms 项目知识图谱

本工具将项目正式文档、backlog、迭代记录、插件正式产物和最近 Git 提交增量写入本机 Neo4j，并以 MCP 工具提供给 Codex。

## 边界

- Neo4j 是关联索引，仓库文件和 Git 仍是事实源。
- 默认不读取 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`、`archive/v1.0/private/` 和历史 `docs/archive/v1.0/`。
- 不把原始日志写入图谱；使用 `kg_record_episode(kind="log-summary")` 记录摘要、来源和时间。
- 不扫描 Codex 私有会话目录；任务收口时使用 `kg_record_episode(kind="conversation")` 写入结构化摘要。
- `kg_query` 只接受受限只读 Cypher；写入仅通过固定字段的 `kg_record_episode`。

## 本地命令

```powershell
cd tools/knowledge-graph
npm install
npm run schema
npm run collect
npm run status
npm test
```

默认从 `%USERPROFILE%\.cgc-pms-secrets\neo4j.env` 读取 Neo4j 凭据，可使用 `CGC_KG_*` 环境变量覆盖。

## Codex MCP

```powershell
codex mcp add cgc-pms-knowledge-graph -- node D:\projects-test\cgc-pms\tools\knowledge-graph\src\server.js
```

注册后新建 Codex 任务，使 MCP 工具列表刷新。

## Schema

核心节点：`Project`、`Artifact`、`Section`、`GitCommit`、`Episode`、`Entity`。

核心关系：`IN_PROJECT`、`CONTAINS`、`REFERENCES`；后续受控抽取可扩展 `RELATES_TO`、`VERIFIES`、`IMPLEMENTS`、`BLOCKS`、`DEPENDS_ON`、`SUPERSEDES` 和 `DERIVED_FROM`，但不得在缺乏来源证据时自动创建事实关系。
