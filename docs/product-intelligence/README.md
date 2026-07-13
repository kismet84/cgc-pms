# CGC-PMS 产品情报

本目录回答三个问题：当前产品真实具备什么能力、外部同类产品提供什么能力、下一轮最值得做什么。

## 当前入口

- [项目地图](project-map.md)
- [竞品分析](competitor-analysis.md)
- [迭代决策](evolution-decision.md)

## 边界

- 当前事实以 `develop/1.5` 的代码、配置、现行规范和本轮验证为准。
- `docs/archive/v1.0/` 只用于历史回溯，不替代当前验证。
- 禁止读取、扫描或依赖 `archive/v1.0/private/`。
- 竞品能力不是需求；只有通过迭代决策门的方向才能进入 Ad-hoc Candidate。
- Candidate 不是 Ready，不能直接实施。

## 状态词典

| 状态 | 定义 |
| --- | --- |
| `Implemented` | 真实链路存在，并有当前验证证据 |
| `Partial` | 已有部分链路，但当前验证、数据、权限或关键闭环不完整 |
| `Documented` | 只在现行规范中定义 |
| `Planned` | 已进入 Candidate 或 Ready，尚未完成 |
| `Frozen` | 已明确冻结，并记录了解冻条件 |
| `Unknown` | 当前证据不足，不能判断 |

## 证据等级

| 等级 | 外部来源 |
| --- | --- |
| A | 官方产品文档、官方帮助中心、官方发布说明 |
| B | 官方产品介绍、官方案例或官方新闻 |
| C | 一手开源仓库及其正式文档 |

搜索摘要、第三方转载和模型记忆不能作为唯一事实依据。

## 更新规则

项目地图在主线完成、核心架构变化、地图落后当前分支 30 天或发现事实冲突时刷新。

竞品分析在 Ready/Candidate 耗尽、候选需要外部证据、来源超过 90 天未复核或产品出现重大变化时刷新。

迭代决策在项目事实、外部证据、用户目标、依赖或实施风险变化时重新裁决。

## 输出流向

```text
project-map.md
      +
competitor-analysis.md
      ↓
evolution-decision.md
      ↓
docs/backlog/ad-hoc-plan.md
      ↓
docs/backlog/ready-issues.md
      ↓
现有 AutoPilot / 正常开发流程
```

