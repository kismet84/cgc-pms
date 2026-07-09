## 重复环境前置失败被误判为代码缺陷

- root_cause: 在后端或前端未就绪时直接执行验证，没有先看 health gate 和已有分类规则
- fix_action: 首次失败先归类为 environment_prereq，只允许复跑一次，再决定 repair 或 blocked
- next_rule: 出现 ECONNREFUSED、actuator/health 不通、dev-login 不通时先走 runtime refresh
- confidence: high
- scope: cgc-pms 本地 AutoPilot 验收与阻塞解除流程
