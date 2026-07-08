# ISSUE-007-007 登录失败与文件失败次数指标回归

完成日期：2026-07-09

## 目标

- 回归登录失败次数和文件上传失败次数的本地可观测性。
- 在不接入外部监控平台、不记录敏感数据的前提下，补齐最小 Micrometer Counter 与自动化断言。

## 修改范围

- `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`
  - 在正式登录失败的 `BusinessException` 分支记录 `auth.login.failures{code=...}`。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 在文件上传失败的 `BusinessException` 分支记录 `file.upload.failures{code=...}`。
- `backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`
  - 断言 `AUTH_FAILED` 登录失败会递增 `auth.login.failures`。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 断言 `FILE_UPLOAD_FAILED` 上传失败会递增 `file.upload.failures`，并检查指标标签不包含密码、Token、文件内容、文件名等敏感键。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/iterations/iteration-2026-07-08-report.md`
  - 完成本轮 backlog 与 iteration 收口，并拆出下一轮 Ready Issue。

## 指标边界

本轮新增指标：

- `auth.login.failures`
  - 标签：`code`
  - 覆盖：正式 `/auth/login` 调用中由认证服务抛出的业务失败，例如 `AUTH_FAILED`。
- `file.upload.failures`
  - 标签：`code`
  - 覆盖：`FileService.upload` 中的业务异常失败，包括校验失败、存储不可用、上传失败等。

不记录：

- 密码
- Token
- 文件内容
- 文件名
- MinIO 凭据或外部平台配置

未覆盖：

- Prometheus 抓取、Grafana 面板、外部告警平台。
- dev-login 免密入口失败次数；该入口不属于正式登录失败口径。

## 验证记录

- 测试类存在性预检：
  - `backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`：存在。
  - `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`：存在。
- 指定验证命令：
  - `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest,FileServiceTest" test`
  - 结果：通过，`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 自审结论

PASS。

依据：

- 登录失败和文件上传失败均已有本地 Micrometer Counter。
- 自动化测试覆盖两个指标的递增行为。
- 指标标签只使用错误码，不包含密码、Token、文件内容或文件名。
- 未新增依赖，未接入外部监控平台，未修改前端、migration、deploy 或生产凭据。

## 结论

通过 / 非阻塞。

剩余风险：

- 本轮只覆盖本地指标注册与递增，不覆盖外部采集、告警规则或仪表盘展示。
- `file.upload.failures` 当前按错误码聚合，不按租户、用户、文件类型等维度细分；如后续需要运营分析维度，应另开监控口径 Issue 并重新评估敏感信息边界。
