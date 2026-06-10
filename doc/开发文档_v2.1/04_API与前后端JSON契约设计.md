> 文档版本：V1.1 正式修订版  
> 输出日期：2026-06-10  
> 项目名称：建筑工程总包项目全过程管理系统  
> 架构基线：模块化单体优先、MySQL 8.0、统一审批引擎、统一 API 契约、PC Web 优先、移动端后置启动  

# API 与前后端 JSON 契约设计

## 1. 设计目标

本文档定义前后端统一 API 契约，确保 PC Web、移动端、企业微信、小程序、PWA 均通过同一后端数据契约访问系统。

重点解决：

```text
字段命名如何统一
ID 和金额如何传输
分页、筛选、排序如何约定
审批按钮如何返回
审批动作如何提交
动态表单如何渲染
错误码如何定义
幂等和并发如何处理
```

## 2. API 基础规范

### 2.1 URL 前缀

```text
/api/v1
```

### 2.2 REST 方法

| 方法 | 用途 |
|---|---|
| GET | 查询 |
| POST | 新建 / 提交动作 |
| PUT | 全量修改 |
| PATCH | 局部修改 / 状态变更 |
| DELETE | 删除 / 作废 |

### 2.3 字段命名

| 层级 | 命名风格 | 示例 |
|---|---|---|
| 数据库 | snake_case | `project_id`, `contract_id` |
| Java DTO | lowerCamelCase | `projectId`, `contractId` |
| JSON | lowerCamelCase | `approvalStatus`, `currentRound` |

### 2.4 ID 字段

所有 ID 在 JSON 中统一使用字符串，避免 JavaScript 大整数精度问题。

```json
{
  "projectId": "10001",
  "contractId": "20001",
  "instanceId": "30001",
  "taskId": "80001"
}
```

### 2.5 金额字段

金额字段在 JSON 中统一使用字符串，后端使用 `BigDecimal`。

```json
{
  "contractAmount": "1256800.35",
  "requestAmount": "86000.00"
}
```

### 2.6 日期时间

```text
日期：YYYY-MM-DD
日期时间：YYYY-MM-DDTHH:mm:ss+08:00
```

## 3. 统一响应结构

### 3.1 成功响应

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {}
}
```

### 3.2 失败响应

```json
{
  "code": "WF_TASK_ALREADY_HANDLED",
  "message": "当前审批任务已被处理",
  "traceId": "f6c1b4c92a8841b6",
  "errors": [
    {
      "field": "taskId",
      "message": "任务状态不是待处理"
    }
  ]
}
```

### 3.3 分页响应

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 128,
    "records": []
  }
}
```

## 4. 通用查询参数

```http
GET /api/v1/contracts?pageNo=1&pageSize=20&keyword=钢筋&sort=createdAt,desc
```

复杂筛选使用 POST 查询：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "filters": {
    "projectId": "10001",
    "contractType": ["PURCHASE", "SUBCONTRACT"],
    "approvalStatus": "APPROVED",
    "signedDateRange": ["2026-01-01", "2026-12-31"]
  },
  "sorts": [
    {"field": "createdAt", "order": "desc"}
  ]
}
```

## 5. 枚举规范

### 5.1 业务类型 `businessType`

```text
CONTRACT
PURCHASE_REQUEST
PURCHASE_ORDER
MATERIAL_RECEIPT
SUB_MEASURE
VAR_ORDER
PAY_REQUEST
SETTLEMENT
COST_TARGET
COST_ADJUST
```

### 5.2 审批动作 `actionType`

```text
SUBMIT
RESUBMIT
APPROVE
REJECT
RETURN_PREVIOUS
RETURN_START
WITHDRAW
TRANSFER
ADD_SIGN_BEFORE
ADD_SIGN_AFTER
ADD_SIGN_PARALLEL
CC
AUTO_CANCEL
VOID
```

## 6. 前端按钮契约

前端不根据角色、节点、状态自行判断按钮。所有可操作按钮由后端返回。

```json
{
  "availableActions": [
    {
      "actionType": "APPROVE",
      "actionName": "同意",
      "enabled": true,
      "requireComment": false,
      "requireTargetUser": false
    },
    {
      "actionType": "REJECT",
      "actionName": "驳回",
      "enabled": true,
      "requireComment": true,
      "requireTargetUser": false
    },
    {
      "actionType": "TRANSFER",
      "actionName": "转办",
      "enabled": true,
      "requireComment": true,
      "requireTargetUser": true
    }
  ]
}
```

## 7. 审批提交接口

### 7.1 业务提交审批

```http
POST /api/v1/workflow/instances/submit
```

请求：

```json
{
  "idempotencyKey": "submit-pay-90001-20260610093000123",
  "businessType": "PAY_REQUEST",
  "businessId": "90001",
  "projectId": "10001",
  "contractId": "20001",
  "title": "付款申请审批 - 滨江大道综合体项目 - 钢筋采购合同",
  "amount": "86000.00",
  "initiatorId": "501",
  "businessRevision": 1,
  "businessSummary": {
    "projectName": "滨江大道综合体项目",
    "contractName": "钢筋采购合同",
    "partnerName": "华东钢材有限公司",
    "requestAmount": "86000.00",
    "paidAmount": "320000.00",
    "paymentRatio": "65.20",
    "basisType": "MAT_RECEIPT",
    "basisIds": ["70001", "70002"]
  },
  "variables": {
    "requestAmount": "86000.00",
    "overContractAmount": false,
    "overPaymentRatio": false,
    "invoiceMissing": true,
    "urgent": false
  },
  "attachments": [
    {
      "fileId": "F10001",
      "fileName": "付款申请单.pdf",
      "fileCategory": "PAY_REQUEST"
    }
  ]
}
```

响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "instanceId": "30001",
    "businessType": "PAY_REQUEST",
    "businessId": "90001",
    "instanceStatus": "RUNNING",
    "currentRound": 1,
    "currentTasks": [
      {
        "taskId": "80001",
        "approverId": "601",
        "approverName": "李明",
        "taskStatus": "PENDING",
        "roundNo": 1
      }
    ]
  }
}
```

## 8. 审批动作接口

### 8.1 同意

```http
POST /api/v1/workflow/tasks/{taskId}/approve
```

```json
{
  "idempotencyKey": "approve-80001-20260610103000123",
  "taskVersion": 3,
  "comment": "同意",
  "formData": {
    "approvedAmount": "86000.00"
  }
}
```

### 8.2 驳回

```http
POST /api/v1/workflow/tasks/{taskId}/reject
```

```json
{
  "idempotencyKey": "reject-80001-20260610103000123",
  "taskVersion": 3,
  "rejectType": "RETURN_START",
  "comment": "发票资料缺失，请补充后重新提交"
}
```

### 8.3 转办

```http
POST /api/v1/workflow/tasks/{taskId}/transfer
```

```json
{
  "idempotencyKey": "transfer-80001-20260610103000123",
  "taskVersion": 3,
  "targetUserId": "602",
  "comment": "因出差转办给同部门审批人"
}
```

### 8.4 加签

```http
POST /api/v1/workflow/tasks/{taskId}/add-sign
```

```json
{
  "idempotencyKey": "addsign-80001-20260610103000123",
  "taskVersion": 3,
  "addSignType": "ADD_SIGN_BEFORE",
  "targetUserIds": ["603"],
  "comment": "请成本经理先确认预算"
}
```

## 9. 审批详情接口

```http
GET /api/v1/workflow/instances/{instanceId}
```

响应核心结构：

```json
{
  "instanceId": "30001",
  "businessType": "PAY_REQUEST",
  "businessId": "90001",
  "instanceStatus": "RUNNING",
  "currentRound": 2,
  "businessSummary": {},
  "rounds": [],
  "nodes": [],
  "records": [],
  "availableActions": []
}
```

## 10. 动态表单 formSchema

### 10.1 字段类型

```text
input
textarea
number
money
percent
select
multiSelect
cascader
date
dateTime
switch
radio
checkbox
upload
userSelect
orgSelect
projectSelect
contractSelect
partnerSelect
materialSelect
```

### 10.2 示例

```json
{
  "fields": [
    {
      "field": "approvedAmount",
      "label": "本次审批金额",
      "type": "money",
      "required": true,
      "precision": 2,
      "min": "0.00"
    },
    {
      "field": "invoiceFiles",
      "label": "发票附件",
      "type": "upload",
      "required": false,
      "accept": [".pdf", ".jpg", ".png"],
      "maxCount": 10
    }
  ]
}
```

## 11. 合同台账接口

```http
POST /api/v1/contracts/search
```

请求：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "filters": {
    "projectId": "10001",
    "contractType": "PURCHASE",
    "approvalStatus": "APPROVED",
    "keyword": "钢筋"
  },
  "visibleColumns": [
    "contractCode",
    "contractName",
    "contractType",
    "projectName",
    "partnerName",
    "contractAmount",
    "currentAmount",
    "paidAmount",
    "paymentRatio",
    "contractStatus",
    "approvalStatus"
  ]
}
```

响应记录：

```json
{
  "contractId": "20001",
  "contractCode": "HT-2026-001",
  "contractName": "钢筋采购合同",
  "contractType": "PURCHASE",
  "projectId": "10001",
  "projectName": "滨江大道综合体项目",
  "partnerId": "30001",
  "partnerName": "华东钢材有限公司",
  "contractAmount": "1200000.00",
  "changeAmount": "50000.00",
  "currentAmount": "1250000.00",
  "paidAmount": "320000.00",
  "paymentRatio": "25.60",
  "signedDate": "2026-06-01",
  "contractStatus": "PERFORMING",
  "approvalStatus": "APPROVED",
  "settlementStatus": "NOT_SETTLED",
  "archiveStatus": "NOT_ARCHIVED",
  "riskLevel": "LOW"
}
```

## 12. 错误码规范

| 错误码 | 含义 |
|---|---|
| `COMMON_PARAM_INVALID` | 参数不合法 |
| `AUTH_TOKEN_EXPIRED` | 登录已过期 |
| `AUTH_PERMISSION_DENIED` | 无权限 |
| `WF_TASK_ALREADY_HANDLED` | 审批任务已处理 |
| `WF_TASK_VERSION_CONFLICT` | 审批任务版本冲突 |
| `WF_INSTANCE_NOT_RUNNING` | 审批实例不在运行中 |
| `WF_IDEMPOTENCY_REPLAY` | 重复请求已被幂等处理 |
| `BIZ_CONTRACT_NOT_APPROVED` | 合同未审批通过 |
| `BIZ_OVER_CONTRACT_AMOUNT` | 超合同金额 |
| `BIZ_PAYMENT_RATIO_EXCEEDED` | 超付款比例 |
| `BIZ_MATERIAL_NOT_RECEIVED` | 材料未验收 |
| `BIZ_SUB_MEASURE_NOT_CONFIRMED` | 分包计量未确认 |
| `BIZ_COST_ALREADY_GENERATED` | 成本已生成 |

## 13. 客户端类型请求头

所有终端请求建议携带：

```http
X-Client-Type: PC_WEB | MOBILE_APP | WECHAT_WORK | WECHAT_MINI | PWA
X-Client-Version: 1.0.0
```

后端可根据客户端类型返回不同字段精度、列表列数和移动端轻量摘要。
