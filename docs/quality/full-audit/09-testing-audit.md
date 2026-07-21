# 全量审计：测试

## 结论

**自动化门禁通过，覆盖深度仍有缺口。评分 82/100。**

## 本地结果

| 范围 | 结果 |
| --- | --- |
| 后端 `mvnw.cmd -C verify` | 2049 tests；0 failure；0 error；3 skipped |
| 后端 JaCoCo instruction | 80.13% |
| 后端 JaCoCo branch | 58.89% |
| Legacy unit | 131 files / 732 tests，通过 |
| V2 unit | 18 files / 83 tests，通过 |
| 两前端 lint/type/build/bundle | 通过 |

## 远端结果

- PR #358 首轮同 SHA push/PR CI 13 项全绿。
- 当前 master 合并提交 `e38737a0...` post-merge workflow 成功。
- MySQL 最小权限 Flyway、基线 smoke、测试顺序、安全扫描、V2 门禁与 E2E 均包含在远端通过证据中。

## 风险

- `TEST-001`（P2）：branch coverage 58.89%，明显低于 instruction 80.13%；需优先补租户拒绝、金额边界、状态机异常、回滚与补偿分支。
- 本轮未重复执行本地浏览器 E2E；采用同代码链的远端成功证据和当前运行态健康检查。生产环境验收仍由 `REL-001` 承担。
