# 第57条主线：CGC-PMS V1.5开发版本正式收口验收报告

报告日期：2026-07-31
计划日期：2026-07-29
执行键：`V15-REMAINDER-COMPLETION-AND-CLOSEOUT`
结论：**通过。V1.5开发版本正式收口；不构成生产发布、目标环境、正式入口切换或Legacy退役裁决。**

## 1. 验收范围

- 第55条非生产遗留最终裁决及生产门守恒。
- 第56条独立本地数据集、9类角色、27个目标单角色用户、15个项目及全部适用业务链。
- 后端、MySQL/H2、Legacy、Clean-room V2、E2E、权限、审批、金额、库存、文件和审计回归。
- 候选制品、安全供应链、同SHA CI、本地根入口切换与回滚。
- 当前状态源、计划、索引、项目地图、Ready/Done和版本说明统一。

## 2. 候选SHA与证据边界

| 项 | 事实 |
| --- | --- |
| 分支 | `codex/mainline-57-v1.5-closeout-20260731` |
| 编制基线 | `master@f984c162d30f49a076d9411d83e366e7481f1fb9` |
| 代码与安全候选 | `c3cfcf942491f810d1c79b2cd326298524e8a1e7` |
| push CI | 运行`30604473043`；同SHA 13/13成功 |
| PR | [#379](https://github.com/kismet84/cgc-pms/pull/379)，Open、非Draft、未合并 |
| PR CI | 运行`30605238524`；同SHA 13/13成功 |
| Pre-PR verifier | 在干净临时克隆绑定`c3cfcf9...`通过 |

本报告和治理索引属于G4文档收口，不改变已验收业务代码、安全补丁或制品内容。若承载本报告的后续docs-only HEAD出现required check失败、混入业务变更或与PR证据不一致，本结论自动撤销并回到`V1.5_DEVELOPMENT_IN_PROGRESS`。

## 3. 变更统计

相对编制基线`f984c162...`至代码与安全候选`c3cfcf942...`：

- 271个文件变更。
- 新增9056行，删除898行。
- 覆盖第55/56条治理与证据、业务根修复及测试、V2页面与契约、E2E、两套前端非root容器安全补丁。
- 7张`frontend-admin/e2e/screenshots/ui-smoke-*.png`为本地验证产物，明确排除候选，不覆盖、不提交。

## 4. 第55条裁决

- 非生产事项全部取得唯一状态：`0_IMPLEMENTED / 0_READY`。
- A-06由当前能力覆盖；其余产品候选有据冻结或观察，不复制状态源。
- `REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION`继续`RELEASE_GATE / PRODUCTION_BLOCKED`。
- 结论：`MAINLINE_55_NON_PRODUCTION_ITEMS_DECIDED / MAINLINE_55_PRODUCTION_GATES_PRESERVED`。

## 5. 第56条数据与业务链

- 9类目标角色。
- 27个目标单角色用户，逐账号真实登录通过；管理员角色绑定0。
- 15个项目：施工总承包10、专业分包3、劳务分包1、材料采购1。
- 五类基础资料各15项；15项目全部适用链通过真实V2写入、实际审批、服务端回读和D4全量对账。
- 旧ID、跨租户、跨项目、跨链、金额和库存错误均为0。
- 结论：`MAINLINE_56_COMPLETED`。

## 6. 路由与前端

- V2路由终态：`LEGACY_ONLY=0 / V2_ACCEPTED=87 / V2_SOURCE_AVAILABLE=0`。
- Legacy：133个测试文件、743项单测；生产构建4642模块；7项真实页面E2E通过。
- V2：55个测试文件、432项单测；Design System 81项；全量E2E 97通过、80个契约跳过、失败0、flaky 0。
- 表格、操作菜单、文件选择、下拉选择和详情入口统一整改已由单测、E2E及浏览器实测覆盖。
- 正式入口未切换，Legacy未退役。

## 7. 权限与审批

- 认证、会话撤销、RBAC、租户与项目范围、工作流及审批回归通过。
- 27个目标用户保持单角色；跨租户、跨项目和越权写入验证无误。
- 15项目适用审批均通过真实操作和服务端状态回读，不以页面展示替代服务端事实。

## 8. 金额与库存

- 合同、预算、成本、采购、库存、分包、计量、结算、付款、发票、现金及会计链回归通过。
- 金额、数量、余额和状态以服务端返回事实为准；跨链守恒和逆向状态对账通过。
- D4全量对账中金额、库存、旧ID和跨项目错误均为0。

## 9. 数据库与迁移

| 门 | 结果 |
| --- | --- |
| MySQL空库 | `FlywayMySqlSmokeTest`、`BaselineMySqlSmokeTest`共2/2通过 |
| MySQL升级 | V180→V250；248个迁移校验成功；1/1通过 |
| H2 | 全量迁移、约束及升级测试通过 |
| 备份恢复 | 200张表、15项目、349个工作流实例恢复守恒 |
| Flyway清单 | 248个SQL；SHA-256 `230477a42eae9cf33567e199f09751c85fd1a632d74c916a21ca0666948abcac` |

## 10. 自动化与CI

| 域 | 结果 |
| --- | --- |
| 后端`clean verify` | 2352项，失败0、错误0、跳过3；JaCoCo通过 |
| 后端顺序独立 | 178项，失败0、错误0、跳过0 |
| Legacy | lint、类型、743项单测、构建、7项页面E2E通过 |
| V2 | lint、类型、432项单测、81项Design System、构建、97项E2E通过 |
| push CI | `30604473043`，13/13成功 |
| PR CI | `30605238524`，13/13成功 |
| required jobs | backend依赖/测试/MySQL、类型、lint、SQL安全、构建、V2门、前端测试/依赖、E2E、供应链、summary全部成功 |

push CI首次尝试的`backend-test-mysql`在checkout前因Docker Hub拉取MySQL镜像超时失败，分类为`environment_prerequisite`；同一SHA失败job重跑后成功，不属于代码或业务缺陷。

## 11. 安全与供应链

- 后端源码依赖、fat JAR及两套前端运行镜像Trivy High/Critical均为0。
- 两个Dockerfile配置扫描misconfiguration为0。
- 两套运行镜像固定`nginx:1.30-alpine`摘要，以`nginx`/UID 101运行；V2 `/healthz` HTTP 200，Legacy HTTP/HTTPS/重定向通过。
- 两端依赖审计漏洞0；SQL安全扫描通过。
- 作用域化secret扫描命中0；全仓宽扫因超时且未生成报告，保持`unknown`，不冒充通过。
- 同SHA CI生成SBOM和provenance attestation。

## 12. 制品与本地回滚演练

| 制品 | 结果 |
| --- | --- |
| 后端JAR | SHA-256 `02ebb810e917e58c1ca323aea897537c61b0d02158d6d663cc3db2333d4bf984` |
| Legacy dist | 262文件；清单`14c598a9a9f3f1e65957c200039b65ddded6f027c53f00b6f0b8a10d05a0e9ea` |
| V2 dist | 102文件；清单`0c5f7d73b511acac99b073ea519393a6490eeabe5ba37076e0472f148e13d348` |
| 本地切换 | `V2_ROOT → LEGACY_ROLLBACK → V2_RESTORE`三阶段健康、API、深链和静态资源通过 |

演练结束后恢复既有本地运行边界；未切正式入口，未操作生产。

## 13. 缺陷终态

- 本地P0：0。
- 本地P1：0。
- 未分类失败：0。
- 候选容器默认root和旧Nginx基线问题已在`c3cfcf9...`根修并复验。
- CI镜像拉取超时已分类`environment_prerequisite`并在同SHA重跑通过。

## 14. 已知限制

- 全仓宽范围secret扫描结果为`unknown`；作用域化源码、CI、脚本、部署和工具扫描命中0。
- E2E中80项契约跳过为既有受控契约边界，不计作通过用例。
- 本结论仅覆盖本地开发版本和GitHub同SHA CI，不覆盖目标环境、真实生产凭据、生产文件重扫、生产迁移/权限/备份/回滚。
- V2 Docker构建阶段`node:22-alpine`尚未固定摘要；运行阶段已固定摘要、UID 101运行且High/Critical为0。
- PR #379尚未合并；合并、Tag、Release和生产部署需独立授权。

## 15. 后续项裁决

- 本轮新增后续项：0。
- 原三项生产门继续由既有稳定键和载体承接，不复制、不改状态。
- 无证据价值或不可验收建议未制造backlog。

## 16. 后续项统计

| 指标 | 数量 |
| --- | ---: |
| 新增后续项 | 0 |
| 关闭后续项 | 0 |
| 后续项净变化 | 0 |

## 17. 重复与悬空

| 指标 | 数量 |
| --- | ---: |
| 重复项 | 0 |
| 悬空项 | 0 |

`docs/backlog/current-issues.json`继续保存12项现行全局Issue；第57条主线不复制这些状态源。

## 18. 最终结论

以下状态全部成立：

```text
V1.5_DEVELOPMENT_CLOSED
MAINLINE_55_NON_PRODUCTION_ITEMS_DECIDED
MAINLINE_55_PRODUCTION_GATES_PRESERVED
MAINLINE_56_COMPLETED
LOCAL_RC_ACCEPTED
SAME_HEAD_CI_PASSED
ZERO_LOCAL_P0_P1
ZERO_DUPLICATE
ZERO_DANGLING
```

裁决：**第57条主线通过，CGC-PMS V1.5开发版本正式收口。**

边界：**生产发布仍不通过。** 三项生产门保持阻塞；未授权且未执行目标环境操作、正式入口切换、Legacy退役、PR合并、Tag或Release。

回滚条件：候选SHA混入业务变更、required check失败、制品与SHA失配、生产门被误关闭或治理状态不一致时，撤销`V1.5_DEVELOPMENT_CLOSED`并恢复`V1.5_DEVELOPMENT_IN_PROGRESS`。
