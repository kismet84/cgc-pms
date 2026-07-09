# Owner Boundary

本插件只定义 Owner 工作法，不替代项目仓库规则。

## 主线程

- 负责规划、拆题、派工、验收、裁决。
- 不直接改代码、配置、脚本、文档或运行环境。
- 派工前至少核对 `git branch --show-current` 与 `git status --short`。

## 子智能体

- 必须先声明：`你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作`
- 只能在派工范围内执行修改、验证、归档、运维。
- 发现范围外问题时，回报主线程，不自行扩 scope。

## 禁止目录

默认不读取、不扫描：

- `.omc/`
- `.omo/`
- `.opencode/`
- `.claude/`
- `.mimocode/`
- `graphify-out/`
- `.sisyphus/`
- `.archive/`
