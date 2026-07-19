# Loop Budget Policy

本插件只定义默认预算，不接管项目真实事实源。

## 默认预算

- `max_retries_per_command=1`
- `max_reverify_commands=3`
- `max_wall_time_minutes=45`
- `max_repair_rounds=2`
- `stop_on_repeated_blocker=true`

## 执行规则

1. 单轮优先最小闭环；未证明完全无关联时按串行处理。
2. 高风险权限、安全、数据一致性问题可以升档，但不能突破重试和补修轮次上限。
3. 同一命令首次疑似瞬时失败只允许复跑一次；重复失败直接进入 repair 或 blocked。
4. 同一问题累计两轮 repair 仍未收敛，默认停止继续自修，写 blocked 或请求人工裁决。
5. `max_reverify_commands` 用于控制 D 的裁决成本；只复跑本轮结论必需项。

## 使用提示

- 在 loop state 中记录预算是否已触边，不需要新增数据库。
- 若用户明确提高验收标准，先更新预算判断，再调整执行方案或证据强度。
