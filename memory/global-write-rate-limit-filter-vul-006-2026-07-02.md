---
name: 全局写接口限流修复 VUL-006 2026-07-02
description: 统一写接口限流应挂在 JWT 认证后过滤器层，复用 RateLimitCounterStore，避免给各 Controller 批量补注解
metadata:
  type: feedback
tags:
  - backend
  - security
  - ratelimit
  - filter
  - redis
---

## 背景

VUL-006 需要为普通写接口补统一限流，但不能误伤已有 `@RateLimit` 的登录、刷新、上传等更严格规则。

## 结论

- 优先在 Spring Security 过滤链增加 `OncePerRequestFilter`，并挂在 `JwtAuthenticationFilter` 之后。
- 只拦 `POST` / `PUT` / `PATCH` / `DELETE`，跳过 `GET` / `HEAD` / `OPTIONS`。
- 复用现有 `RateLimitCounterStore`，已认证请求按 `userId` 计数，兜底按 IP 计数。
- 跳过 `SecurityConfig` 里的认证白名单、文档白名单和健康检查白名单，避免影响 `/auth/login`、`/auth/refresh`、`/actuator/health/**`。
- 不要用“给所有写接口批量补 `@RateLimit`”的方式修这个漏洞；那不是全局入口，而且维护成本高。

## 验证

- 新增安全链集成测试时，用 test-scope `@RestController` + `MockMvc` 全链路验证。
- 全局阈值要调小到 2 之类的测试值，同时把“已有 `@RateLimit` 仍独立生效”的测试端点阈值设得更严格，才能证明两者叠加时不是全局限流把注解逻辑覆盖掉。
