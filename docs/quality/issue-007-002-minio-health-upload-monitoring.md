# ISSUE-007-002 MinIO 健康指标与文件失败监控回归

完成日期：2026-07-09

## 目标

- 回归 MinIO 健康检查成功、桶缺失、连接失败三类口径。
- 回归文件上传失败分类，区分“文件服务暂不可用”和“普通上传失败”。
- 确认失败路径不泄露 endpoint、accessKey、secretKey 等敏感配置。

## 修改范围

- `backend/src/main/java/com/cgcpms/config/MinioHealthIndicator.java`
  - 失败路径补充稳定分类字段 `category`。
  - 连接失败统一返回 `reason=MinIO connection failed`，不暴露底层异常明文。
- `backend/src/main/java/com/cgcpms/file/service/FileService.java`
  - 上传失败统一在单点分类。
  - 连接类/超时类/域名解析类异常映射为 `FILE_STORAGE_UNAVAILABLE`。
  - 其他上传异常维持 `FILE_UPLOAD_FAILED`。
- `backend/src/test/java/com/cgcpms/config/MinioHealthIndicatorTest.java`
  - 补充桶缺失 `BUCKET_MISSING`、连接失败 `CONNECTION_FAILED` 与敏感信息不泄露断言。
- `backend/src/test/java/com/cgcpms/file/FileServiceTest.java`
  - 补充 MinIO 连接失败分类为 `FILE_STORAGE_UNAVAILABLE` 的断言。
  - 补充普通上传异常仍归类为 `FILE_UPLOAD_FAILED` 的断言。

## 验证记录

- Ready Issue 验证命令预检：`MinioHealthIndicatorTest`、`FileServiceTest` 均存在，无需等价替换。
- TDD 红灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=MinioHealthIndicatorTest,FileServiceTest" test`
  - 结果：失败，新增 3 条断言先红。
  - 失败分类：真实代码质量问题，具体为健康指标缺少稳定分类字段，上传失败未区分存储不可用。
- 绿灯验证：
  - `cd backend; .\mvnw.cmd "-Dtest=MinioHealthIndicatorTest,FileServiceTest" test`
  - 结果：通过，`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `git diff --check`
  - 结果：通过。

## 自审结论

PASS。

依据：
- 健康指标成功/失败路径均有稳定断言，不依赖真实 MinIO。
- 上传失败分类在 `FileService.upload()` 单点收口，避免在控制器或调用方重复判断。
- 新增断言确认异常消息与健康详情不暴露 `http://localhost:9000`、`accessKey`、`secretKey`。

## 结论

通过 / 非阻塞。

剩余风险：
- 当前 `isStorageUnavailable()` 仅覆盖连接失败、超时、域名解析三类通用网络故障；若后续需要细分更多 MinIO SDK 异常，可在同一分类入口继续扩展。
- 本轮未连接真实 MinIO，结论限于 mock 条件下的健康指标与异常分类口径。
