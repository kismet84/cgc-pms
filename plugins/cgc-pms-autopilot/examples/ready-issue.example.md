## READY-33-M1

- 优先级：P1
- 类型：governance
- 来源：第33条主线 / M1
- 目标：补齐 Owner Skill 与 manifest
- 允许路径：plugins/cgc-pms-autopilot/**
- 禁止路径：scripts/codex-autopilot/**, backend/**, frontend-admin/**
- 验收标准：manifest 可验证，Skill 覆盖边界、A-F、失败分类、收口口径
- 验证命令：python validate_plugin.py plugins/cgc-pms-autopilot
- 报告落点：docs/quality/code-audit-YYYY-MM-DD-autopilot-plugin-m1.md
