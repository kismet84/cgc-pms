# ISSUE-037-008 现场日报 dev-login 直达路由验收报告

## 结论

- Decision：通过。
- 阻塞：无。
- 上线边界：仅 dev/local 本地验收；未修改生产 profile、未发布、未 push。

## 实施边界

- `DevAuthController.ALLOWED_REDIRECT_PREFIXES` 仅新增 `/site`。
- 归一化、cookie、登录服务、SecurityConfig 和前端路由均未修改。
- `/site` 仅匹配自身、`/site/...` 或 `/site?...`；不放行 `/siteevil`。

## 验收证据

- Ready lint、独立准入和独立实现安全复核均 PASS。
- `AuthControllerTest` 15/15 通过；新增专项覆盖 `/site/daily-log`、`//evil.example`、`https://evil.example/path` 和 `/site/../system`。
- `git diff --check` 通过。
- 后端重建后稳定等待 180 秒；backend/frontend 均 200。
- 真实 dev-login：原始响应 302、Location `/site/daily-log`；跟随跳转后页面 200 且最终路径不变。
- 真实站外 URL：原始响应 302、Location `/`。

## 风险与回滚

- 剩余风险：无本轮特有阻塞风险；入口仍受 `dev/local` profile 与 `auth.dev-login.enabled` 双重限制。
- 回滚：移除 `/site` 白名单前缀和对应回归用例；无数据迁移。
