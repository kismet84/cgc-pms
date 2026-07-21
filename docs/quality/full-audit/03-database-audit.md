# 全量审计：数据库

## 结论

**本地与 CI 数据库门禁通过；生产数据证据未完成。评分 82/100。**

## 结构与迁移

- 活跃运行态连接 `cgc_pms_demo_v2`，197 张表，Flyway B215、V216、V217、V218 均成功。
- 兼容库 `cgc_pms` 为 197 张表，当前到 V217；未作为本轮活跃后端数据源。
- MySQL/H2 均存在 B215 与 V216—V218。方言文件 SHA 不同属语法适配，不以字节一致作为正确性标准；空库基线与 MySQL 最小权限迁移由 CI smoke test 验证。
- `application-prod.yml:28-34` 启用 Flyway、同时加载 active + legacy、`validate-on-migrate=true`、`clean-disabled=true`。

## 一致性控制

- 资金账户与现金分录使用 `FOR UPDATE` 和 `@Version`。
- 工作流存在幂等表、租户字段与并发测试；付款写回、外部交易号、间接费执行均有幂等测试。
- 金额、租户、项目范围均有专项或集成测试覆盖。

## 风险

- `DATA-001`（P1，待验证）：生产等价副本及仓库外消费者未确认，禁止直接删除历史间接费结果表。
- `REL-001`（P0）：目标环境 Flyway、备份恢复、真实租户/角色/金额验证未执行。
- `DEV-001`（P3）：本地 MySQL 报告 `/etc/mysql/conf.d/ssl.cnf` 权限过宽并忽略；当前仅为 dev 容器问题，生产配置仍要求 TLS。
