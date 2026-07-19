# 第53条主线 M0 无 UI 契约基线

## 边界

- 唯一来源：当前 `frontend-admin` 路由、登录 API、驾驶舱 API 与权限守卫。
- V2 可共享：DTO、权限码、API 相对路径、无副作用常量。
- V2 禁止共享：Vue 组件、布局、CSS、Pinia Store、Axios 拦截器、消息提示、浏览器状态写入。
- 本基线不改变后端接口、Cookie 会话、CSRF、租户、项目范围或权限语义。

## 登录 API

| 用途 | 方法 | 路径 | M0 行为 |
|---|---|---|---|
| 登录 | POST | `/api/auth/login` | 只冻结契约，不在 V2 M0 调用 |
| 恢复用户信息 | GET | `/api/auth/userinfo` | 只冻结契约，不在 V2 M0 调用 |
| 刷新会话 | POST | `/api/auth/refresh` | 只冻结契约，不在 V2 M0 调用 |
| 登出 | POST | `/api/auth/logout` | 只冻结契约，不在 V2 M0 调用 |
| 健康探针 | GET | `/api/actuator/health` | V2 M0 唯一运行时 API；只读 |

认证态继续由 HttpOnly Cookie 承载；V2 不把令牌写入 localStorage。

## 驾驶舱权限与 API

| 角色键 | 权限 | GET 路径 |
|---|---|---|
| pm | `dashboard:project-manager:view` | `/api/dashboard/project-manager` |
| bm | `dashboard:business-manager:view` | `/api/dashboard/business-manager` |
| cost | `dashboard:cost-manager:view` | `/api/dashboard/cost-manager` |
| purchase | `dashboard:purchase-manager:view` | `/api/dashboard/purchase-manager` |
| production | `dashboard:production-manager:view` | `/api/dashboard/production-manager` |
| chiefEngineer | `dashboard:chief-engineer:view` | `/api/dashboard/chief-engineer` |
| finance | `dashboard:finance:view` | `/api/dashboard/finance` |
| mgmt | `dashboard:management:view` | `/api/dashboard/management` |

`ADMIN`、`SUPER_ADMIN`、`*`、`dashboard:view` 与细分 `dashboard:*:view` 的路由准入语义保持不变；M1 才实现 V2 守卫，M2 才迁移真实驾驶舱。

## M0 运行面

- Legacy：`http://localhost:5173/`
- V2：`http://localhost:5174/v2/health`
- V2 仅有技术健康页，无登录、导航或业务写操作。
- 完整命名路由、视图、permission、adminOnly、迁移状态见自动生成迁移台账。
