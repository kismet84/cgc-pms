# P0/P1 安全修复批次归档

**项目名称：** CGC-PMS
**归档日期：** 2026-07-02
**归档范围：** 仅记录本轮 P0/P1 安全修复批次结果，不覆盖全量 41 项审计结论。
**最终 verifier 结论：** 通过 / 非阻塞 / 可提交

## 本轮修复项

- VUL-004：`application-local.yml` 的 JWT 硬编码回退已移除，改为环境变量注入。
- VUL-030：仓库内 Jasypt 默认密文/解密 key 共存已移除；dev/test 配置不再保留可用默认凭据；CI 改用 Secret。
- VUL-006：新增全局写接口限流，`GET`、`OPTIONS`、`/auth/login`、`/auth/refresh`、`/actuator/health` 跳过，保留 `@RateLimit` 独立生效。
- VUL-007：发票识别/创建的 INFO 敏感日志已移除；失败对外消息固定。
- VUL-008：`externalTxnNo` 空白拒绝；重复非空保持幂等；V76 唯一约束完整性测试通过。
- VUL-011：`HttpMessageNotReadable` 对外固定为“请求数据格式错误”。

## 降级 / 运维项

- VUL-003：当前仓库未跟踪真实 `.env`；模板无真实凭据；真实凭据轮换仍为运维项，不记为代码完成。
- VUL-001 / VUL-002：复核为误报或降级安全硬化项，本轮不纳入代码修复。

## 验证命令摘要

- 敏感字串扫描：`git grep -n -I -E "dev-jasypt-key|ENC\\(|cgc123|minioadmin" -- backend/src/main/resources README.md scripts .github/workflows`
- 定向测试组合：`.\mvnw.cmd "-Dtest=GlobalWriteRateLimitFilterTest,RateLimitAspectTest,JwtAuthenticationFilterTest,CorsConfigTest,PaymentWritebackTest,InvoiceControllerTest,InvoiceRecognitionTest,InvoiceServiceTest,JwtPropertiesTest" test`
- 迁移完整性检查：`.\mvnw.cmd "-Dtest=MigrationIntegrityTest#payRecordExternalTxnNoUniqueConstraintRemainsTenantScopedForMysqlAndH2" test`
- 附加确认：`sample-invoice.pdf` 无不应有改动，扫描结果为 `NO_MATCH`

## 剩余风险

- 真实生产凭据轮换仍依赖运维动作，不属于当前代码批次可关闭项。
- 若后续扩大验收口径到测试资产或示例文档中的说明文本，需要另行补扫，但本轮 verifier 已按当前口径通过。

## 提交范围 / 排除项

- 提交范围：仅审计归档文档更新。
- 排除项：不改业务代码，不改 `backend/`、`frontend/`、`deploy/`、`scripts/`，不提交、不推送。
- 结论边界：本文件只记录本轮 P0/P1 安全修复批次结果，不宣称全量 41 项已全部修复。
