# Classifier Rules

本表只定义 AutoPilot 子分类、建议动作和重试手势。一级分类名称唯一引用 `.agents/skills/cgc-pms-ci-gate-triage/SKILL.md`，不得在本文件另建枚举。

## 二级子分类与建议动作

| category | subcategory | typical evidence | suggestedNextAction | retryPolicy |
| --- | --- | --- | --- | --- |
| `tool_invocation` | `powershell_parser_error` | `ParserError`, `unexpected argument`, `ParameterBinding` | `fix_command_syntax` | `no_retry` |
| `tool_config` | `command_or_entrypoint_missing` | `is not recognized`, `script not found`, `not loaded` | `verify_script_entrypoint` | `no_retry` |
| `environment_prerequisite` | `vite_proxy_stale_backend` | `ECONNREFUSED 172.19.x.x:8080`, `vite proxy` | `refresh_frontend_runtime` | `rerun_after_refresh` |
| `environment_prerequisite` | `dev_login_unreachable` | `dev-login`, browser回退 `/login` | `restore_dev_login_path` | `rerun_after_refresh` |
| `environment_prerequisite` | `docker_not_ready` | `docker`, `WSL`, `dockerDesktopLinuxEngine` | `refresh_runtime_state` | `rerun_after_refresh` |
| `environment_prerequisite` | `backend_not_ready` | `actuator/health`, `localhost:8080`, `service unavailable` | `wait_and_retry_backend_health` | `rerun_after_refresh` |
| `environment_prerequisite` | `frontend_not_ready` | `localhost:5173`, `vite ready`, `frontend not ready` | `wait_and_retry_frontend_health` | `rerun_after_refresh` |
| `ready_issue_config` | `test_selector_missing_or_invalid` | `No tests matching`, `class not found`, `method not found`, `selector` | `fix_ready_selector` | `retry_after_ready_fix` |
| `ready_issue_config` | `ready_issue_verification_config` | `verify command`, `OutputPath is required`, `allowed_files` | `fix_ready_issue_metadata` | `retry_after_ready_fix` |
| `retrieval_gap` | `index_or_graph_coverage_missing` | `not indexed`, `index missing`, `retrieval gap`, `graph coverage` | `use_bounded_fallback` | `no_retry` |
| `quality_or_security` | `maven_test_compile` | `testCompile`, `compilation error`, `cannot find symbol` | `open_repair_request` | `manual_review_required` |
| `quality_or_security` | `maven_surefire` | `surefire`, `There are test failures` | `open_repair_request` | `manual_review_required` |
| `quality_or_security` | `real_test_failure` | `assert`, `expected:`, `but was:` | `open_repair_request` | `manual_review_required` |
| `quality_or_security` | `real_build_failure` | generic non-zero exit with stable code error | `open_repair_request` | `manual_review_required` |
| `quality_or_security` | `real_permission_or_security_failure` | `forbidden`, `unauthorized`, `tenant`, `security` | `mark_blocked` | `manual_review_required` |
| `unknown` | `unknown` | 证据不足或规则冲突 | `collect_more_evidence` | `manual_review_required` |

## 输出约束

1. `confidence` 只允许 `high|medium|low`。
2. `evidence` 只保留短摘要，不保留整段日志。
3. `reason` 保留兼容旧脚本消费者。
4. runner 只能消费建议，不得把分类器结果当最终裁决。
5. 一级分类由 CI Skill 维护；Schema 和脚本只做机器校验与执行，不成为第二份规则正文。
