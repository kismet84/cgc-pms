# 06 API 契约规范

## 基本约定

| 项 | 约定 |
|----|------|
| 上下文路径 | `/api`（后端 `server.servlet.context-path`） |
| 前端 baseURL | `/api`（Axios `VITE_API_BASE_URL ?? '/api'`） |
| 数据格式 | JSON |
| 字段命名 | lowerCamelCase（JSON） ↔ snake_case（数据库） |
| 认证方式 | JWT + HttpOnly Cookie（`withCredentials`） |

## HTTP 方法

| 方法 | 用途 |
|------|------|
| `GET` | 列表查询、详情、字典、导出 |
| `POST` | 新建、提交审批、审批动作 |
| `PUT` | 更新完整对象 |
| `PATCH` | 局部状态变更 |
| `DELETE` | 删除（逻辑删除/作废） |

## 统一响应

成功：
```json
{ "code": "0", "message": "success", "traceId": "xxx", "data": {} }
```

失败：
```json
{ "code": "COST_TARGET_NOT_FOUND", "message": "目标成本不存在", "traceId": "xxx", "data": null }
```

前端拦截器：`code !== "0"` 时统一提示并 reject。业务错误码必须稳定。

## 分页契约

请求：
```
GET /api/cost-targets?pageNo=1&pageSize=20&projectId=10001
```

响应：
```json
{
  "pageNo": 1,
  "pageSize": 20,
  "total": 100,
  "records": []
}
```

页码从 1 开始。查询字段使用业务名称，不用数据库字段名。

## ID 契约

所有 `Long` ID 在 JSON 中必须为字符串：

```json
{ "id": "2067078524990394370", "projectId": "10001" }
```

原因：JavaScript `number` 无法安全表示 19 位 Snowflake ID。

规则：
- 前端类型 ID 一律 `string`
- 后端 `@JsonSerialize(using = ToStringSerializer.class)` 或全局配置
- 路由参数保持字符串，后端边界转 `Long`

## 金额契约

JSON 金额统一字符串：

```json
{ "contractAmount": "1000000.00", "paidAmount": "320000.00" }
```

- 后端 `BigDecimal`
- 前端展示格式化，提交转字符串
- 金额计算明确舍入规则

## 日期时间

| 类型 | 格式 |
|------|------|
| 日期 | `yyyy-MM-dd` |
| 日期时间 | `yyyy-MM-dd HH:mm:ss`（以接口 `@JsonFormat` 为准） |

## 状态枚举

审批状态：
`DRAFT` | `APPROVING` | `APPROVED` | `REJECTED` | `WITHDRAWN`

业务状态：
`DRAFT` | `ACTIVE` | `CANCELLED` | `COMPLETED`

规则：
- 使用英文稳定码
- 中文标签由字典接口或类型映射提供
- 不以中文状态做程序判断

## 操作接口规范

| 操作 | 路径示例 |
|------|----------|
| 提交审批 | `POST /cost-targets/{id}/submit` |
| 激活 | `POST /cost-targets/{id}/activate` |
| 同意 | `POST /workflow/tasks/{taskId}/approve` |
| 驳回 | `POST /workflow/tasks/{taskId}/reject` |
| 标记已读 | `POST /notifications/{id}/read` |

操作接口返回统一响应，不返回裸布尔值。

## 错误码

格式：`MODULE_REASON`

| 错误码 | 说明 |
|--------|------|
| `AUTH_TOKEN_INVALID` | Token 无效 |
| `AUTH_FORBIDDEN` | 权限不足 |
| `RESOURCE_NOT_FOUND` | 资源不存在 |
| `VALIDATION_ERROR` | 参数校验失败 |
| `WF_TASK_VERSION_CONFLICT` | 审批任务版本冲突 |
| `SYSTEM_ERROR` | 系统异常 |

错误码一旦被前端或测试使用，不得随意改名。
