# 全量审计：可观测性

## 结论

**后端基础具备，端到端闭环不足。评分 58/100。**

## 已有能力

- Logback 输出 traceId，并对 token、authorization、cookie、手机号、证件、银行卡等字段脱敏。
- Actuator 健康检查存在；本地 `/api/actuator/health` 返回 `UP`。
- Micrometer Prometheus registry 与 `/actuator/prometheus` 控制器存在，端点由认证链保护。
- 公共壳通知不使用伪造 SSE，按权限读取有限摘要，属于正确降级。

## 风险

- `OBS-001`（P2，已确认）：Legacy 前端中央异常上报仍为 TODO，浏览器错误只能本地观察。
- `OBS-002`（P2，已确认）：`deploy/monitoring/prometheus.yml` 抓取 `/api/actuator/prometheus`，但未配置 Authorization；该端点不是白名单，当前捆绑配置无法完成认证抓取。

## 修复方向

1. 为 Prometheus 建立最小权限机器身份/受控网络入口，Secret 不入库；加入 scrape 集成验证。
2. 选择一个前端错误平台，建立 source map、脱敏、环境/版本标签、采样与告警规则；禁止只埋 SDK 不做告警闭环。
