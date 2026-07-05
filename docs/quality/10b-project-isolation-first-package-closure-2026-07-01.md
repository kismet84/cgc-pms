# 10B 首包关闭归档

日期：2026-07-01

## 结论

第 10B 专项首包关闭结论：通过。

- 阻塞/非阻塞：非阻塞
- 关闭范围：仅覆盖 `ProjectAccessChecker.checkAccess`、`WorkflowQueryService.getInstanceDetail`、`CtContractService.getById`
- 首批目标业务：合同详情

## 通过依据

1. 数据库侧通过
   - 零 migration 成立。
   - 现有 H2 样本足够支撑同租户有/无项目权限正负验证。
   - 不引入 `pm_project_member`、项目角色、新表、新字段、新索引、新 seed。
2. 后端侧通过
   - `WorkflowQueryService.getInstanceDetail` 既有 `projectId -> requireProjectAccess -> ProjectAccessChecker.checkAccess` 链路未回退。
   - `CtContractService.getById` 已补齐 `projectId -> ProjectAccessChecker.checkAccess(projectId, "查看合同详情")`。
   - 定向测试通过：
     - `CtContractServiceTest#testGetByIdFound`
     - `CtContractServiceTest#testGetByIdTenantIsolation`
     - `CtContractServiceTest#testGetByIdSelfProjectOwnerAllowed`
     - `CtContractServiceTest#testGetByIdSelfNonOwnerDenied`
     - `WorkflowQueryServiceTest#getInstanceDetailParticipantWithoutProjectAccessDenied`
     - `WorkflowQueryServiceTest#getInstanceDetailParticipantWithProjectAccessCanView`
3. 安全侧通过
   - 未发现越界到采购、分包、付款、权限字典、入口注册、10C、10D。
   - 未发现把 workflow 可见性误当作合同详情权限的回退。
   - `DEPT/CUSTOM/未知 data_scope` 仍保持 fail-close。
4. 运维侧通过
   - 后端已完成 clean package、重启、等待 3 分钟。
   - 运行态 `cgc-pms-backend-dev` 健康，`/api/actuator/health` 为 `UP`。
   - 新 JAR 已包含更新后的 `CtContractService.class`。
5. 测试侧通过
   - 真实浏览器下，`admin` 可从审批详情进入合同详情。
   - 登录态接口探针证明 workflow 详情与合同详情指向同一 `projectId`：
     - `/api/workflow/instances/980000000000011001`
     - `/api/contracts/980000000000001001`
   - 证据目录：`D:/projects-test/cgc-pms/output/playwright/10b-project-isolation-acceptance-20260701/`

## 冻结边界

1. 本包只落同租户项目边界最小实现，不扩成全业务隔离。
2. 本包只覆盖三段链路：
   - `ProjectAccessChecker.checkAccess`
   - `WorkflowQueryService.getInstanceDetail`
   - `CtContractService.getById`
3. 本包不纳入：
   - 采购申请
   - 分包计量
   - 付款申请
   - 权限字典治理
   - 可编辑权限平台
   - 全站入口注册体系
   - `pm_project_member` / 项目角色授权模型
   - 第二租户完整矩阵
   - 复杂 `data_scope` 矩阵

## 非阻塞观察项

1. dev 运行态缺少“同租户无项目权限人工账号”现成样本；当前由 H2/定向自动化承担该负向主证据。
2. 跨租户合同详情运行态硬证未形成；当前按冻结口径列为观察项，不阻塞本包关闭。
3. `CtContractServiceTest.testCompositeSaveApprovedContractRejected` 仍有既有无关失败，不属于本包改动面，应另立小包处理。

## 主负责人裁决

1. 10B 首包已完成当前冻结范围内全部任务，可关闭。
2. 后续若要增强为更强运行态负向硬证，应另开最小样本/探针小包，不回灌本首包。
3. 后续若有人尝试把采购、分包、付款并入 10B 首包，按越界处理。
