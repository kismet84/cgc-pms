## READY-33-1-M3

- failed_check: D 验收中 validate-loop-artifacts 缺少 schema 文件
- evidence: 自检脚本 missing 包含 schemas/loop-state.schema.json
- failure_category: ready_issue_config
- required_change: 补齐缺失 schema 并保持字段与 failure classifier 枚举一致
- allowed_files: plugins/cgc-pms-autopilot/schemas/**, plugins/cgc-pms-autopilot/examples/**
- forbidden_files: scripts/codex-autopilot/**, backend/**, frontend-admin/**
- reverify_command: powershell -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\validate-loop-artifacts.ps1
- stop_condition: 若 schema 已存在但 JSON 仍不可解析，则转 unknown 并补充更强证据
