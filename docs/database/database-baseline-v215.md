# 数据库最终基线 B215 与平台初始化

状态：本地实施完成，冻结版本 `N=215`。适用新建 dev/test/demo 环境；既有数据库继续使用 legacy 迁移链。生产重建、clean、删除 `flyway_schema_history` 和 `repair` 均不在授权范围。

## 迁移布局

| 方言  | 新环境/后续迁移                                          | 历史兼容链                                         |
| ----- | -------------------------------------------------------- | -------------------------------------------------- |
| MySQL | `db/migration/B215__cgc_pms_baseline.sql`，未来 V216+    | `db/migration-legacy/V1—V215`                      |
| H2    | `db/migration-h2/B215__cgc_pms_baseline.sql`，未来 V216+ | `db/migration-h2-legacy/V1—V215` 与 Java migration |

dev/prod 同时扫描 MySQL active + legacy；local/test 同时扫描 H2 active + legacy + Java。空库选择 B215 并忽略低版本；已有 V180/V215 库忽略 B215，继续验证和升级历史链。

测试边界：专用基线测试直接扫描 H2 active + legacy + Java，证明空库选择 B215、V180 继续 legacy。历史全量业务回归的测试 classpath 只扫描冻结的 H2 legacy + Java fixture 链，以保留 V90 等既有测试专用数据；该测试夹具不代表新环境默认数据，也不能作为 B215 空库证据。

历史完整性清单：[flyway-v215-manifest.csv](flyway-v215-manifest.csv)，共 425 行：MySQL SQL 212、H2 SQL 204、H2 Java 9。清单保存冻结前 SHA-256；移动只改变路径，不改变内容。

## Schema 生成与等价证据

1. 在本地 MySQL 8.0 dev 容器创建独立参考库，执行原历史链到 V214，再执行只补字段注释的 V215。
2. 从参考库导出最终 schema；不导出业务数据和 `flyway_schema_history`。
3. 仅按下表白名单导出确定性系统数据，并新增 `sys_bootstrap_state/PLATFORM_ADMIN` 控制行。
4. H2 基线由 `scripts/database/build-baseline-h2.ps1` 机械转换，并把外键延后到全表创建后恢复。
5. MySQL 基线新库与参考库逐列、索引、表选项和约束对账；排除 `flyway_schema_history`，允许新增 `sys_bootstrap_state`。

当前本地对账：

| 维度                                     |                    参考库缺失于 B215 | B215 多余于参考库 |
| ---------------------------------------- | -----------------------------------: | ----------------: |
| 表（排除 Flyway，允许 bootstrap 状态表） |                                    0 |                 0 |
| 列定义、默认值、生成列、注释             |                                    0 |                 0 |
| 索引与表达式                             |                                    0 |                 0 |
| PK/UK/FK/CHECK                           |                                    0 |                 0 |
| 视图                                     | 2 个定义同构，仅数据库限定名前缀不同 |                 0 |

## 系统数据白名单

责任人统一为平台/数据库负责人。未列出的表默认不导入数据。

| 表/类别                | 业务键                               | B215 记录数 | 主要来源            | 必要性                   |
| ---------------------- | ------------------------------------ | ----------: | ------------------- | ------------------------ |
| `sys_role`             | `(tenant_id, role_code)`             |           9 | V1、V84、V97、V99   | 正式角色；排除 `*_DEMO`  |
| `sys_menu`             | `(tenant_id, id/perms)`              |         252 | V1—V215 权限迁移    | 菜单和按钮权限           |
| `sys_role_menu`        | `(tenant_id, role_id, menu_id)`      |         489 | 权限迁移            | 仅正式角色绑定           |
| `sys_dict_type`        | `(tenant_id, dict_code)`             |          20 | V5、V125、V132      | tenant=0 正式字典        |
| `sys_dict_data`        | `(tenant_id, dict_code, dict_value)` |          84 | 字典迁移            | tenant=0 正式枚举        |
| `cost_subject`         | `(tenant_id, subject_code)`          |         104 | V4、V78、V213、V214 | 成本科目 V2 最终树       |
| `md_material_category` | `(tenant_id, category_code)`         |           1 | V205、V209          | 默认未分类               |
| `sys_type_registry`    | `(type_domain, type_code)`           |          36 | V207                | 工作流/成本类型注册      |
| `wf_template`          | `(tenant_id, template_code)`         |          27 | V9—V213             | 仅启用正式模板           |
| `wf_template_node`     | `(template_id, node_order)`          |          71 | 模板迁移            | 正式模板节点             |
| `sys_bootstrap_state`  | `bootstrap_key`                      |           1 | B215                | `PLATFORM_ADMIN/PENDING` |

## 演示数据黑名单

B215 明确断言以下事实为零：`sys_user`、`pm_project`、`md_material`、`md_partner`、`mat_warehouse`、`mat_stock`、采购/验收/领料/退料/调拨、合同、预算、目标成本、付款、收入、回款、工作流实例/任务/记录/抄送、通知、预警、质量安全、进度、技术、财务和会计业务事实。`*_DEMO` 角色、tenant=1 演示字典和禁用旧默认审批模板不进入白名单。

## 平台 bootstrap

默认关闭。配置模板位于 `deploy/config/bootstrap.example.yml` 和 `deploy/.env.example`。首次初始化：

1. 通过外部 Secret 注入强临时密码，设置 `CGCPMS_BOOTSTRAP_ENABLED=true`。
2. 应用在 Flyway 后锁定 `PLATFORM_ADMIN` 状态行；事务内创建/复用根公司、根部门和管理员，绑定 `SUPER_ADMIN`。
3. 同名普通用户、停用角色、组织编码冲突或弱密码全部 fail-close；失败整事务回滚。
4. 完成后重启不改密码；首次登录立即改密，删除/轮换 Secret，并恢复 `enabled=false`。

dev-login 默认读取 `CGCPMS_DEV_LOGIN_USERNAME`，回退到 bootstrap 用户名；用户不存在时返回失败，不再自动创建或提权。

兼容边界：`sys_bootstrap_state` 只属于 B215 新环境，不回写 V1—V215 历史迁移。既有数据库继续按 legacy 链校验和升级，且 bootstrap 默认关闭；如需把既有数据库改造成可 bootstrap 环境，必须另立增量迁移和授权，不能修改历史脚本或手工伪造 Flyway 记录。

## 显式演示项目

数据包位于 `scripts/demo/complete-project-v1/`。加载只允许本地 dev/test/demo，默认不执行，不提供自动清理：

```powershell
pwsh scripts/demo/complete-project-v1/load.ps1 -Environment demo -Database cgc_pms_demo
pwsh scripts/demo/complete-project-v1/verify.ps1 -Environment demo -Database cgc_pms_demo
```

package 使用 V215 当前结构专用的四阶段受控 SQL：`CORE`、`PROCUREMENT`、`COMMERCIAL`、`GOVERNANCE`。`sys_bootstrap_state` 保存阶段 checkpoint；每阶段独立事务，失败从首个未完成阶段恢复，重复执行不新增事实。验收脚本核对 43 项指标，包括 3 个合作方、3 份合同、采购申请/订单/验收各 100、领用 20、库存 80、质量成本 5000、实际成本 85000，以及数量、库存和金额守恒。
