# ISSUE-006-004 发票识别重复发票与付款关联回归

完成日期：2026-07-09

## 目标

回归发票登记/更新链路中的重复发票号、金额、日期和付款记录关联一致性，确保发票不会静默关联到不存在的付款记录。

## 修改范围

- `backend/src/main/java/com/cgcpms/invoice/service/InvoiceService.java`
  - 在 `update` 携带新 `payRecordId` 时校验付款记录存在、属于当前租户，并按新付款记录解析项目权限后再更新。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceServiceTest.java`
  - 补齐重复发票号失败后仅保留一条有效发票，且原金额、日期、付款记录关联不变的断言。
  - 新增无效 `payRecordId` 更新被拒绝且原金额、日期、付款记录关联不变的断言。
- `backend/src/test/java/com/cgcpms/invoice/InvoiceControllerTest.java`
  - `register` 端到端断言发票号、金额、日期、付款记录关联可追溯，并断言重复 register 返回 `INVOICE_NO_DUPLICATE`。
  - 修正控制器测试夹具：直接种子化本用例付款记录，避免付款申请审批前置阻断发票控制器测试；删除测试改为自建待核验发票，避免依赖已核验共享发票。

本轮未修改前端、migration、deploy、付款模块生产代码或外部识别服务配置。

## 一致性边界

- 重复发票号：同租户同发票号重复创建/登记返回 `INVOICE_NO_DUPLICATE`，不会新增第二条有效发票。
- 金额与日期：重复创建失败和无效付款记录更新失败后，原发票金额、日期保持不变。
- 付款记录关联：创建/登记必须关联有效付款记录；更新携带 `payRecordId` 时必须指向当前租户下真实付款记录。
- 项目权限：发票更新切换付款记录时，会按新付款记录解析项目并执行项目访问校验。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest#shouldRejectUpdateToInvalidPayRecordAndKeepOriginalFields" test`
  - 首轮按 TDD RED 失败，原因：`InvoiceService.update` 未拒绝不存在的 `payRecordId`，实际写入无效付款记录。
  - 修正后通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceServiceTest,InvoiceControllerTest" test`
  - 首轮失败分类：测试前置配置问题，`InvoiceControllerTest` 通过付款服务 writeback 创建付款记录，但付款申请未审批。
  - 第二轮失败分类：测试编写问题，`@Autowired` 重复标注导致测试编译失败。
  - 第三轮失败分类：测试夹具问题，删除测试复用已核验共享发票导致服务按规则拒绝删除。
  - 修正后通过，`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`：通过。

## 结论

通过 / 非阻塞。

发票重复检测、金额/日期不变性和付款记录追溯边界已由服务层与控制器回归测试覆盖；生产修复限定在发票服务更新路径。

## 剩余风险

- 本轮未跑全量后端测试，结论限于 ISSUE-006-004 指定 invoice 模块和控制器回归范围。
- 本轮未接入真实外部发票识别服务，识别 PDF 解析失败和人工确认口径留给 ISSUE-006-005。
