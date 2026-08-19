# 阶段 5：安全审计

## 总结

认证、租户隔离、方法权限、Cookie/CSRF/CORS、密码与令牌、文件访问、上传扫描和审计基础较完整。原审计 2 个 P1、1 个 P2 已完成本地整改与负向测试；依赖扫描等待当前 Trivy 数据库证据后单独裁决。

## 已确认控制

- 登录、刷新、当前用户均绑定租户；登录限流与锁定启用。
- Cookie 使用 HttpOnly、SameSite=Strict，prod 要求 Secure；CSRF 默认启用，CORS 拒绝通配来源。
- 84 个 Controller 中，仅本地开发认证和客户端错误入口未使用 `@PreAuthorize`，与其用途一致。
- 文件下载先校验租户、业务对象权限、扫描状态和对象存在性。
- 未发现当前仓库中的真实生产密钥；`deploy/.env` 被 Git 忽略，报告未读取或输出其值。

## 风险

- `SEC-001`：已修复。服务端拒绝事件处理属性；列表预览使用空 sandbox，设计器仅保留 `allow-same-origin` 且不允许脚本。
- `OPS-001`：已修复。Prometheus 使用独立 `MONITORING` Basic 机器身份，prod/monitoring Compose 只挂载只读密码文件；空/错/业务 JWT 均拒绝。
- `API-001`：已修复。对象存储 presign 异常对外固定为 `FILE_URL_ERROR/生成下载链接失败`，底层消息不再进入响应。
- `DEP-001`：固定摘要 Trivy 当前库扫描结果见命令日志；该证据具有时间性，不等于远端同 SHA CI。
