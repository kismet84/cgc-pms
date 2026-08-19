# 关键安全发现

## SEC-001：文档模板预览可执行存储型 XSS（已修复）

- 等级/状态：原 P1，2026-08-20 本地整改复验通过。
- 入口：具备 `document:template:edit` 或管理员权限的用户可导入或更新模板内容。
- 服务端根因：`RestrictedTemplateEngine.java:21-39` 的禁止模式拦截脚本标签、外部资源和危险协议，但未拦截 `onerror`、`onload` 等事件属性；`DocumentTemplateService.java:468-476` 仅调用该校验。
- 前端执行面：`DocumentTemplatePage.vue:493` 与 `DocumentFlowDesigner.vue:282` 将内容写入无 `sandbox` 的 `iframe srcdoc`；对照 `DocumentCanvas.vue:764` 已使用 `sandbox=""`。
- 动态复现：Playwright 在本地静态 HTML 中向无 sandbox 的 `srcdoc` 注入 `<img src=x onerror="parent.document.body.dataset.auditXss='confirmed'">`，得到 `unsandboxed-srcdoc-parent-access=confirmed`。
- 影响：恶意模板在编辑者/管理员打开预览时，可在同源父页面上下文执行脚本，读取页面数据并以当前会话发起请求。
- 建议：服务端用 HTML 解析器执行允许列表净化并禁止所有事件属性；两个预览 iframe 强制最小 sandbox；必要时使用独立不带凭据的 origin；保存和渲染两侧都做防御。
- 验收：导入、编辑、设计器、列表预览均覆盖 `onerror`、`onload`、SVG/MathML、编码绕过和协议变体；payload 不执行，合法模板保持可用。
- 回归风险：过度净化可能破坏现有模板样式和占位符，需对全部系统模板做视觉与内容回归。

整改证据：`RestrictedTemplateEngine.java:21-39` 拒绝大小写、空白、下划线和斜杠分隔的事件属性；参数化测试覆盖 `onerror`、`ONCLICK`、SVG 与 MathML。`DocumentTemplatePage.vue:493` 使用 `sandbox=""`；`DocumentFlowDesigner.vue:282` 使用不含 `allow-scripts` 的 `sandbox="allow-same-origin"`，只保留父页面选区联动所需 DOM 访问。前端 644 项单测、类型、构建和浏览器 DOM/console 验真通过。

## OPS-001：Prometheus 抓取认证契约不闭合（已修复）

- 等级/状态：原 P1，2026-08-20 本地整改复验通过。
- 证据：`SecurityConfig` 只匿名放行 health；`application-prod.yml:80-88` 暴露 prometheus；`deploy/monitoring/prometheus.yml:18-26` 无凭据抓取 `/api/actuator/prometheus`。
- 影响：保持安全配置时抓取持续 401/403；为恢复监控而错误匿名放行则扩大指标泄露面。
- 验收：使用可轮换机器身份；无/错凭据和普通用户拒绝；认证抓取持续成功；旧凭据失效；Secret 不入库；Prometheus UI 不公网暴露。

整改证据：独立 `@Order(1)` 安全链只匹配 `/actuator/prometheus` 并要求 `ROLE_MONITORING`；JWT 过滤器跳过该机器端点；生产和本地监控叠加配置从同一只读密码文件读取，生产 profile 禁止内联密码；Prometheus UI 仅绑定 `127.0.0.1:9090`。集成测试证明正确 Basic 为 200，空/错/管理员 JWT 为 401；密码文件读取、缺失 fail-closed、prod 内联口令拒绝均有单测。
