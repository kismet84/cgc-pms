# 建筑工程总包项目全过程管理系统 API 与前后端 JSON 契约设计

| 项目 | 内容 |
|---|---|
| 文档编号 | JGZB-DEV-04 |
| 版本 | V1.0 正式版 |
| 日期 | 2026-06-10 |
| 适用对象 | 前端研发、后端研发、测试团队、接口联调人员 |
| 文档定位 | 明确 REST API、统一响应、字段命名、审批 JSON、动态表单和错误码 |

---

## 正式版基线说明

本文件已按正式开发交付口径整理，并统一以下架构基线：

1. 一期数据库默认采用 **MySQL 8.0**，审批规则、动态表单和节点规则使用 MySQL JSON 字段。
2. 后端审批引擎内部采用**事务内领域服务直接调用**，不通过 HTTP 调用自身接口。
3. PC Web 是一期完整管理后台；移动端以 uni-app 为现场主端，企业微信和微信小程序作为轻入口。
4. 移动端正式业务开发节奏后移：第 6 周启动基础工程，第 8 周左右进入真实业务功能。
5. 所有业务数据以 `project_id` 为根，涉及合同的业务以 `contract_id` 为纲，成本以业务事实归集，付款与成本口径分离。

---
## 19.1 设计目标

当前系统已经完成业务模块、审批流程、数据库表结构设计，但前后端之间还缺少统一 API 契约。为保证合同、采购、材料验收、分包计量、付款申请、结算审批等流程可落地，需要补充以下内容：

1. 统一 API 返回结构；
2. 统一字段命名规范；
3. 统一分页、筛选、排序结构；
4. 统一业务单据提交审批接口；
5. 统一审批模板 JSON 结构；
6. 统一审批实例 JSON 结构；
7. 统一待办任务 JSON 结构；
8. 明确会签、或签、顺序审批、转办、加签的数据结构；
9. 明确审批轮次 `roundNo/currentRound` 的数据规则；
10. 明确动态审批表单 `formSchema` 的字段类型、校验规则和渲染规则；
11. 明确审批记录必须关联节点实例，保证审批轨迹可审计；
12. 明确撤回后的业务状态、任务状态、审批记录保留规则；
13. 明确前端可操作按钮由后端返回，不由前端自行判断；
14. 明确审批动作提交后的状态流转规则。

本章重点解决以下问题：

```text
前端怎么展示审批流程？

前端点击“同意 / 驳回 / 转办 / 加签 / 撤回”时，后端需要什么 JSON？

会签、或签、转办、加签的前后端数据结构怎么定？

驳回后重新提交，如何区分第几轮审批？

撤回后已处理过的审批记录是否保留？
```

---

## 19.2 API 总体规范

### 19.2.1 接口风格

建议采用 RESTful API + JSON。

```text
GET     查询
POST    新建 / 提交动作
PUT     全量修改
PATCH   局部修改 / 状态变更
DELETE  删除 / 作废
```

### 19.2.2 URL 统一前缀

```text
/api/v1
```

示例：

```text
/api/v1/contracts
/api/v1/pay-requests
/api/v1/workflow/instances
/api/v1/workflow/tasks
```

### 19.2.3 JSON 字段命名

前后端 JSON 建议使用 `lowerCamelCase`。

数据库字段仍使用 `snake_case`。

| 数据库字段 | API 字段 |
|---|---|
| `project_id` | `projectId` |
| `contract_id` | `contractId` |
| `business_type` | `businessType` |
| `approval_status` | `approvalStatus` |
| `node_instance_id` | `nodeInstanceId` |
| `current_round` | `currentRound` |
| `round_no` | `roundNo` |
| `created_at` | `createdAt` |

### 19.2.4 ID 字段类型

所有 ID 在 JSON 中统一使用字符串，避免前端 JavaScript 大整数精度丢失。

```json
{
  "projectId": "10001",
  "contractId": "20001",
  "instanceId": "30001",
  "nodeInstanceId": "40001",
  "taskId": "80001"
}
```

### 19.2.5 金额字段类型

金额字段统一使用字符串，后端使用 Decimal 处理，避免浮点精度问题。

```json
{
  "contractAmount": "1256800.35",
  "requestAmount": "86000.00",
  "approvedAmount": "86000.00"
}
```

### 19.2.6 日期时间格式

日期使用：

```text
YYYY-MM-DD
```

日期时间使用 ISO 8601：

```text
YYYY-MM-DDTHH:mm:ss+08:00
```

示例：

```json
{
  "signedDate": "2024-01-15",
  "createdAt": "2024-01-15T09:30:00+08:00"
}
```

---

## 19.3 统一响应结构

### 19.3.1 成功响应

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {}
}
```

### 19.3.2 失败响应

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

### 19.3.3 分页响应

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

---

## 19.4 通用枚举设计

### 19.4.1 业务类型 `businessType`

```text
CONTRACT              合同审批
PURCHASE_REQUEST      采购申请
PURCHASE_ORDER        采购订单
MATERIAL_RECEIPT      材料验收
SUB_MEASURE           分包计量
VAR_ORDER             签证变更
PAY_REQUEST           付款申请
SETTLEMENT            结算审批
COST_TARGET           目标成本
COST_ADJUST           成本调整
```

### 19.4.2 审批实例状态 `instanceStatus`

```text
DRAFT                 草稿
RUNNING               审批中
APPROVED              已通过
REJECTED              已驳回
WITHDRAWN             已撤回
TERMINATED            已终止
VOIDED                已作废
```

### 19.4.3 审批节点状态 `nodeStatus`

```text
WAITING               未开始
RUNNING               处理中
PASSED                已通过
REJECTED              已驳回
SKIPPED               已跳过
CANCELED              已取消
```

### 19.4.4 审批任务状态 `taskStatus`

```text
WAITING               等待中，顺序审批中尚未轮到
PENDING               待处理
PENDING_HOLD          挂起中，通常用于前加签
APPROVED              已同意
REJECTED              已驳回
TRANSFERRED           已转办
CANCELED              已取消
WITHDRAWN             已撤回
TIMEOUT               已超时
```

### 19.4.5 审批模式 `approveMode`

```text
SEQUENTIAL            顺序审批
OR_SIGN               或签
COUNTERSIGN           会签
```

### 19.4.6 审批动作 `actionType`

```text
SUBMIT                提交审批
RESUBMIT              重新提交
APPROVE               同意
REJECT                驳回
RETURN_PREVIOUS       退回上一步
RETURN_START          退回发起人
WITHDRAW              撤回
TRANSFER              转办
ADD_SIGN_BEFORE       前加签
ADD_SIGN_AFTER        后加签
ADD_SIGN_PARALLEL     并行加签
CC                    抄送
AUTO_CANCEL           系统自动取消
VOID                  作废
```

### 19.4.7 记录状态 `recordStatus`

```text
EFFECTIVE             有效记录
HISTORY               历史记录，仅用于留痕
SYSTEM                系统自动生成记录
```

说明：

```text
审批记录原则上不物理删除。
流程撤回、驳回、重新提交后，旧轮次审批记录仍保留。
旧记录是否参与当前审批结果，由 instanceStatus、roundNo、recordStatus 共同判断。
```

---

## 19.5 前后端核心约定

### 19.5.1 前端不直接判断权限

前端不应根据角色、状态、节点自行判断是否显示“同意、驳回、转办、加签”。

所有可操作按钮由后端统一返回：

```json
{
  "availableActions": [
    {
      "actionType": "APPROVE",
      "actionName": "同意",
      "enabled": true
    },
    {
      "actionType": "REJECT",
      "actionName": "驳回",
      "enabled": true,
      "requireComment": true
    },
    {
      "actionType": "TRANSFER",
      "actionName": "转办",
      "enabled": true,
      "requireTargetUser": true,
      "requireComment": true
    },
    {
      "actionType": "ADD_SIGN_BEFORE",
      "actionName": "前加签",
      "enabled": true,
      "requireTargetUser": true,
      "requireComment": true
    }
  ]
}
```

前端只负责渲染后端返回的按钮。

### 19.5.2 后端负责所有业务校验

提交审批或处理审批时，后端必须校验：

```text
当前用户是否有权限；
当前任务是否待处理；
审批实例是否仍在审批中；
业务单据是否允许审批；
合同金额是否超限；
付款比例是否超限；
材料是否已验收；
分包是否已计量；
质保金是否到期；
当前审批轮次是否匹配；
是否存在并发处理；
是否重复提交。
```

### 19.5.3 所有审批动作必须带 `taskVersion`

为防止多人同时处理同一个任务，审批任务需要乐观锁字段。

```json
{
  "taskId": "80001",
  "taskVersion": 3,
  "actionType": "APPROVE"
}
```

后端处理前校验：

```text
前端传入 taskVersion 必须等于数据库当前 version。
```

如果不一致，返回：

```json
{
  "code": "WF_TASK_VERSION_CONFLICT",
  "message": "当前任务已发生变化，请刷新后重试"
}
```

### 19.5.4 所有提交动作必须支持幂等

前端提交审批动作时必须传 `idempotencyKey`。

```json
{
  "idempotencyKey": "approve-80001-20240115093000123"
}
```

后端保证同一个用户、同一个任务、同一个 `idempotencyKey` 不重复执行。

---

# 20. 审批模板 JSON 契约

## 20.1 审批模板结构

审批模板用于定义“这个业务应该怎么流转”。

接口：

```text
POST /api/v1/workflow/templates
PUT  /api/v1/workflow/templates/{templateId}
GET  /api/v1/workflow/templates/{templateId}
```

### 20.1.1 创建审批模板请求

```json
{
  "templateCode": "PAY_REQUEST_L3",
  "templateName": "付款申请审批-L3",
  "businessType": "PAY_REQUEST",
  "enabled": true,
  "amountMin": "500000.00",
  "amountMax": "2000000.00",
  "conditionRule": {
    "projectTypes": ["房建", "市政"],
    "extraConditions": [
      {
        "field": "overPaymentRatio",
        "operator": "=",
        "value": false
      }
    ]
  },
  "nodes": [
    {
      "nodeCode": "PROJECT_MANAGER",
      "nodeName": "项目经理审核",
      "nodeOrder": 1,
      "nodeType": "APPROVAL",
      "approveMode": "SEQUENTIAL",
      "approverConfig": {
        "approverType": "PROJECT_ROLE",
        "roleCode": "PROJECT_MANAGER"
      },
      "allowTransfer": true,
      "allowAddSign": true,
      "timeoutHours": 24
    },
    {
      "nodeCode": "BUSINESS_COST_COUNTERSIGN",
      "nodeName": "商务成本会签",
      "nodeOrder": 2,
      "nodeType": "APPROVAL",
      "approveMode": "COUNTERSIGN",
      "passRule": {
        "type": "ALL"
      },
      "rejectRule": {
        "type": "ANY_REJECT"
      },
      "approverConfig": {
        "approverType": "MULTI_ROLE",
        "roleCodes": ["BUSINESS_MANAGER", "COST_MANAGER"]
      },
      "allowTransfer": true,
      "allowAddSign": true,
      "timeoutHours": 48
    },
    {
      "nodeCode": "FINANCE_OR_SIGN",
      "nodeName": "财务审核",
      "nodeOrder": 3,
      "nodeType": "APPROVAL",
      "approveMode": "OR_SIGN",
      "passRule": {
        "type": "ANY_APPROVE"
      },
      "rejectRule": {
        "type": "ALL_REJECT"
      },
      "approverConfig": {
        "approverType": "ROLE",
        "roleCode": "FINANCE"
      },
      "allowTransfer": true,
      "allowAddSign": false,
      "timeoutHours": 24
    },
    {
      "nodeCode": "GENERAL_MANAGER",
      "nodeName": "总经理审批",
      "nodeOrder": 4,
      "nodeType": "APPROVAL",
      "approveMode": "SEQUENTIAL",
      "approverConfig": {
        "approverType": "ROLE",
        "roleCode": "GENERAL_MANAGER"
      },
      "condition": {
        "field": "requestAmount",
        "operator": ">",
        "value": "2000000.00"
      },
      "allowTransfer": false,
      "allowAddSign": true,
      "timeoutHours": 48
    }
  ]
}
```

---

## 20.2 节点字段说明

| 字段 | 说明 |
|---|---|
| `nodeCode` | 节点编码，模板内唯一 |
| `nodeName` | 节点名称 |
| `nodeOrder` | 节点顺序 |
| `nodeType` | 节点类型，审批或抄送 |
| `approveMode` | 审批模式：顺序、会签、或签 |
| `passRule` | 节点通过规则 |
| `rejectRule` | 节点驳回规则 |
| `approverConfig` | 审批人配置 |
| `condition` | 节点是否需要执行的条件 |
| `allowTransfer` | 是否允许转办 |
| `allowAddSign` | 是否允许加签 |
| `timeoutHours` | 超时时间 |

---

## 20.3 审批人配置 `approverConfig`

### 20.3.1 指定用户

```json
{
  "approverType": "USER",
  "userIds": ["501", "502"]
}
```

### 20.3.2 指定系统角色

```json
{
  "approverType": "ROLE",
  "roleCode": "FINANCE"
}
```

### 20.3.3 指定项目角色

```json
{
  "approverType": "PROJECT_ROLE",
  "roleCode": "PROJECT_MANAGER"
}
```

### 20.3.4 多角色

```json
{
  "approverType": "MULTI_ROLE",
  "roleCodes": ["BUSINESS_MANAGER", "COST_MANAGER"]
}
```

### 20.3.5 发起人的直属上级

```json
{
  "approverType": "INITIATOR_MANAGER"
}
```

### 20.3.6 表达式动态取人

```json
{
  "approverType": "EXPRESSION",
  "expression": "business.projectManagerId"
}
```

---

# 21. 审批实例 JSON 契约

## 21.1 提交审批接口

业务模块提交审批时，不直接创建 `wf_task`，而是调用统一审批提交接口。

接口：

```text
POST /api/v1/workflow/instances/submit
```

### 21.1.1 请求示例：付款申请提交审批

```json
{
  "idempotencyKey": "submit-pay-90001-20240115093000123",
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
    "payType": "MATERIAL",
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

### 21.1.2 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "instanceId": "30001",
    "businessType": "PAY_REQUEST",
    "businessId": "90001",
    "instanceStatus": "RUNNING",
    "currentRound": 1,
    "businessRevision": 1,
    "currentNodes": [
      {
        "nodeInstanceId": "40001",
        "nodeName": "项目经理审核",
        "approveMode": "SEQUENTIAL",
        "roundNo": 1
      }
    ],
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

---

## 21.2 重新提交审批接口

当审批被驳回后，发起人修改业务单据并重新提交审批。

接口：

```text
POST /api/v1/workflow/instances/{instanceId}/resubmit
```

### 21.2.1 请求示例

```json
{
  "idempotencyKey": "resubmit-pay-90001-20240116090000123",
  "businessRevision": 2,
  "comment": "已补充发票资料，重新提交审批",
  "variables": {
    "requestAmount": "86000.00",
    "invoiceMissing": false,
    "overContractAmount": false,
    "overPaymentRatio": false
  },
  "attachments": [
    {
      "fileId": "F10002",
      "fileName": "补充发票.pdf",
      "fileCategory": "INVOICE"
    }
  ]
}
```

### 21.2.2 后端处理规则

```text
1. 只允许 REJECTED 状态的审批实例重新提交；
2. 重新提交后 instanceStatus = RUNNING；
3. currentRound = currentRound + 1；
4. resubmitCount = resubmitCount + 1；
5. 新建本轮 wf_node_instance；
6. 新建本轮 wf_task；
7. 旧轮次节点、任务、记录全部保留；
8. 所有新节点、新任务、新记录写入 roundNo。
```

### 21.2.3 响应示例

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "instanceId": "30001",
    "instanceStatus": "RUNNING",
    "currentRound": 2,
    "resubmitCount": 1,
    "currentNodes": [
      {
        "nodeInstanceId": "40011",
        "nodeName": "项目经理审核",
        "approveMode": "SEQUENTIAL",
        "roundNo": 2
      }
    ],
    "currentTasks": [
      {
        "taskId": "80021",
        "approverId": "601",
        "approverName": "李明",
        "taskStatus": "PENDING",
        "roundNo": 2
      }
    ]
  }
}
```

---

## 21.3 审批实例详情接口

接口：

```text
GET /api/v1/workflow/instances/{instanceId}
```

### 21.3.1 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "instanceId": "30001",
    "templateId": "10001",
    "businessType": "PAY_REQUEST",
    "businessId": "90001",
    "projectId": "10001",
    "contractId": "20001",
    "title": "付款申请审批 - 滨江大道综合体项目 - 钢筋采购合同",
    "amount": "86000.00",
    "instanceStatus": "RUNNING",
    "currentRound": 2,
    "resubmitCount": 1,
    "businessRevision": 2,
    "initiator": {
      "userId": "501",
      "userName": "张三",
      "avatarUrl": null
    },
    "businessSummary": {
      "projectName": "滨江大道综合体项目",
      "contractName": "钢筋采购合同",
      "partnerName": "华东钢材有限公司",
      "requestAmount": "86000.00",
      "paymentRatio": "65.20",
      "payType": "材料款"
    },
    "rounds": [
      {
        "roundNo": 1,
        "roundStatus": "REJECTED",
        "startedAt": "2024-01-15T09:30:00+08:00",
        "endedAt": "2024-01-15T15:30:00+08:00"
      },
      {
        "roundNo": 2,
        "roundStatus": "RUNNING",
        "startedAt": "2024-01-16T09:00:00+08:00",
        "endedAt": null
      }
    ],
    "nodes": [
      {
        "nodeInstanceId": "40001",
        "roundNo": 1,
        "nodeCode": "PROJECT_MANAGER",
        "nodeName": "项目经理审核",
        "nodeOrder": 1,
        "approveMode": "SEQUENTIAL",
        "nodeStatus": "PASSED",
        "tasks": [
          {
            "taskId": "80001",
            "roundNo": 1,
            "approverId": "601",
            "approverName": "李明",
            "taskStatus": "APPROVED",
            "actionType": "APPROVE",
            "comment": "同意",
            "receivedAt": "2024-01-15T09:30:00+08:00",
            "handledAt": "2024-01-15T10:15:00+08:00"
          }
        ]
      },
      {
        "nodeInstanceId": "40002",
        "roundNo": 1,
        "nodeCode": "BUSINESS_COST_COUNTERSIGN",
        "nodeName": "商务成本会签",
        "nodeOrder": 2,
        "approveMode": "COUNTERSIGN",
        "nodeStatus": "REJECTED",
        "tasks": [
          {
            "taskId": "80002",
            "roundNo": 1,
            "approverId": "603",
            "approverName": "赵伟",
            "taskStatus": "REJECTED",
            "actionType": "REJECT",
            "comment": "发票资料缺失，请补充后重新提交",
            "handledAt": "2024-01-15T15:30:00+08:00"
          }
        ]
      },
      {
        "nodeInstanceId": "40011",
        "roundNo": 2,
        "nodeCode": "PROJECT_MANAGER",
        "nodeName": "项目经理审核",
        "nodeOrder": 1,
        "approveMode": "SEQUENTIAL",
        "nodeStatus": "RUNNING",
        "tasks": [
          {
            "taskId": "80021",
            "roundNo": 2,
            "approverId": "601",
            "approverName": "李明",
            "taskStatus": "PENDING",
            "receivedAt": "2024-01-16T09:00:00+08:00"
          }
        ]
      }
    ],
    "records": [
      {
        "recordId": "R001",
        "instanceId": "30001",
        "nodeInstanceId": null,
        "taskId": null,
        "roundNo": 1,
        "actionType": "SUBMIT",
        "actionName": "提交审批",
        "operatorId": "501",
        "operatorName": "张三",
        "comment": "提交付款申请",
        "recordStatus": "EFFECTIVE",
        "createdAt": "2024-01-15T09:30:00+08:00"
      },
      {
        "recordId": "R002",
        "instanceId": "30001",
        "nodeInstanceId": "40001",
        "taskId": "80001",
        "roundNo": 1,
        "nodeCode": "PROJECT_MANAGER",
        "nodeName": "项目经理审核",
        "actionType": "APPROVE",
        "actionName": "同意",
        "operatorId": "601",
        "operatorName": "李明",
        "comment": "同意",
        "recordStatus": "EFFECTIVE",
        "createdAt": "2024-01-15T10:15:00+08:00"
      },
      {
        "recordId": "R003",
        "instanceId": "30001",
        "nodeInstanceId": "40002",
        "taskId": "80002",
        "roundNo": 1,
        "nodeCode": "BUSINESS_COST_COUNTERSIGN",
        "nodeName": "商务成本会签",
        "actionType": "REJECT",
        "actionName": "驳回",
        "operatorId": "603",
        "operatorName": "赵伟",
        "comment": "发票资料缺失，请补充后重新提交",
        "recordStatus": "EFFECTIVE",
        "createdAt": "2024-01-15T15:30:00+08:00"
      },
      {
        "recordId": "R004",
        "instanceId": "30001",
        "nodeInstanceId": null,
        "taskId": null,
        "roundNo": 2,
        "actionType": "RESUBMIT",
        "actionName": "重新提交",
        "operatorId": "501",
        "operatorName": "张三",
        "comment": "已补充发票资料，重新提交审批",
        "recordStatus": "EFFECTIVE",
        "createdAt": "2024-01-16T09:00:00+08:00"
      }
    ]
  }
}
```

---

# 22. 待办任务 JSON 契约

## 22.1 我的待办列表

接口：

```text
GET /api/v1/workflow/tasks/todo
```

查询参数：

```text
businessType
projectId
keyword
pageNo
pageSize
```

### 22.1.1 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 36,
    "records": [
      {
        "taskId": "80003",
        "taskVersion": 2,
        "instanceId": "30001",
        "nodeInstanceId": "40002",
        "roundNo": 1,
        "businessType": "PAY_REQUEST",
        "businessId": "90001",
        "title": "付款申请审批 - 钢筋采购合同",
        "projectId": "10001",
        "projectName": "滨江大道综合体项目",
        "contractId": "20001",
        "contractName": "钢筋采购合同",
        "amount": "86000.00",
        "nodeName": "商务成本会签",
        "approveMode": "COUNTERSIGN",
        "initiatorName": "张三",
        "receivedAt": "2024-01-15T10:15:00+08:00",
        "deadlineAt": "2024-01-17T10:15:00+08:00",
        "urgent": false,
        "availableActions": [
          {
            "actionType": "APPROVE",
            "actionName": "同意",
            "enabled": true
          },
          {
            "actionType": "REJECT",
            "actionName": "驳回",
            "enabled": true,
            "requireComment": true
          },
          {
            "actionType": "TRANSFER",
            "actionName": "转办",
            "enabled": true,
            "requireTargetUser": true,
            "requireComment": true
          },
          {
            "actionType": "ADD_SIGN_BEFORE",
            "actionName": "前加签",
            "enabled": true,
            "requireTargetUser": true,
            "requireComment": true
          },
          {
            "actionType": "ADD_SIGN_AFTER",
            "actionName": "后加签",
            "enabled": true,
            "requireTargetUser": true,
            "requireComment": true
          }
        ]
      }
    ]
  }
}
```

---

## 22.2 任务详情接口

接口：

```text
GET /api/v1/workflow/tasks/{taskId}
```

### 22.2.1 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "taskId": "80003",
    "taskVersion": 2,
    "taskStatus": "PENDING",
    "instanceId": "30001",
    "nodeInstanceId": "40002",
    "roundNo": 1,
    "nodeCode": "BUSINESS_COST_COUNTERSIGN",
    "nodeName": "商务成本会签",
    "approveMode": "COUNTERSIGN",
    "approver": {
      "userId": "603",
      "userName": "赵伟"
    },
    "originalApprover": {
      "userId": "603",
      "userName": "赵伟"
    },
    "businessSummary": {
      "businessType": "PAY_REQUEST",
      "businessId": "90001",
      "projectName": "滨江大道综合体项目",
      "contractName": "钢筋采购合同",
      "partnerName": "华东钢材有限公司",
      "requestAmount": "86000.00",
      "payType": "材料款"
    },
    "availableActions": [
      {
        "actionType": "APPROVE",
        "actionName": "同意",
        "enabled": true,
        "requireComment": false
      },
      {
        "actionType": "REJECT",
        "actionName": "驳回",
        "enabled": true,
        "requireComment": true
      },
      {
        "actionType": "TRANSFER",
        "actionName": "转办",
        "enabled": true,
        "requireTargetUser": true,
        "requireComment": true
      },
      {
        "actionType": "ADD_SIGN_BEFORE",
        "actionName": "前加签",
        "enabled": true,
        "requireTargetUser": true,
        "requireComment": true
      }
    ]
  }
}
```

---

# 23. 审批动作 API 契约

## 23.1 同意审批

接口：

```text
POST /api/v1/workflow/tasks/{taskId}/approve
```

### 23.1.1 请求示例

```json
{
  "idempotencyKey": "approve-80003-20240115110000123",
  "taskVersion": 2,
  "roundNo": 1,
  "comment": "同意，付款依据完整",
  "formData": {
    "approvedAmount": "86000.00",
    "riskConfirmed": true
  },
  "attachments": []
}
```

### 23.1.2 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "taskId": "80003",
    "taskStatus": "APPROVED",
    "instanceId": "30001",
    "instanceStatus": "RUNNING",
    "nodeInstanceId": "40002",
    "nodeStatus": "PASSED",
    "roundNo": 1,
    "nextNodes": [
      {
        "nodeInstanceId": "40003",
        "nodeName": "财务审核",
        "approveMode": "OR_SIGN",
        "roundNo": 1
      }
    ],
    "nextTasks": [
      {
        "taskId": "80004",
        "approverId": "604",
        "approverName": "钱芳",
        "roundNo": 1
      },
      {
        "taskId": "80005",
        "approverId": "605",
        "approverName": "孙敏",
        "roundNo": 1
      }
    ]
  }
}
```

---

## 23.2 驳回审批

接口：

```text
POST /api/v1/workflow/tasks/{taskId}/reject
```

### 23.2.1 请求示例

```json
{
  "idempotencyKey": "reject-80003-20240115110000123",
  "taskVersion": 2,
  "roundNo": 1,
  "comment": "发票资料缺失，请补充后重新提交",
  "rejectTo": "STARTER",
  "attachments": []
}
```

### 23.2.2 `rejectTo` 枚举

```text
STARTER             退回发起人
PREVIOUS_NODE       退回上一节点
SPECIFIC_NODE       退回指定节点
```

如果退回指定节点：

```json
{
  "rejectTo": "SPECIFIC_NODE",
  "targetNodeInstanceId": "40001"
}
```

---

## 23.3 退回上一步

接口：

```text
POST /api/v1/workflow/tasks/{taskId}/return-previous
```

请求：

```json
{
  "idempotencyKey": "return-80003-20240115110000123",
  "taskVersion": 2,
  "roundNo": 1,
  "comment": "请项目经理重新确认付款依据"
}
```

---

## 23.4 撤回审批

撤回由发起人操作，不是审批人任务动作。

接口：

```text
POST /api/v1/workflow/instances/{instanceId}/withdraw
```

### 23.4.1 请求示例

```json
{
  "idempotencyKey": "withdraw-30001-20240115110000123",
  "comment": "付款金额填写有误，撤回修改"
}
```

### 23.4.2 撤回策略

系统建议支持三种撤回策略，可作为企业参数配置：

```text
BEFORE_ANY_APPROVAL           未有任何审批人处理前可撤回
BEFORE_CURRENT_NODE_HANDLED   当前节点未有人处理前可撤回
ANYTIME_RUNNING               审批结束前均可撤回
```

建议默认使用：

```text
BEFORE_CURRENT_NODE_HANDLED
```

### 23.4.3 撤回后端规则

```text
1. 只有发起人可撤回；
2. 审批实例必须处于 RUNNING；
3. 根据企业配置判断是否允许撤回；
4. 已处理任务保持原状态，不删除；
5. 未处理任务标记为 CANCELED；
6. 取消原因 cancelReason = WITHDRAWN_BY_INITIATOR；
7. 新增一条 WITHDRAW 审批记录；
8. 审批实例状态变为 WITHDRAWN；
9. 业务单据审批状态变为 WITHDRAWN；
10. 业务单据业务状态变为 PENDING_SUBMIT 或 EDITING；
11. 已有审批记录全部保留，仅作为历史留痕。
```

### 23.4.4 撤回响应示例

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "instanceId": "30001",
    "instanceStatus": "WITHDRAWN",
    "businessType": "PAY_REQUEST",
    "businessId": "90001",
    "approvalStatus": "WITHDRAWN",
    "businessStatus": "PENDING_SUBMIT",
    "canceledTasks": [
      {
        "taskId": "80003",
        "taskStatus": "CANCELED",
        "cancelReason": "WITHDRAWN_BY_INITIATOR"
      }
    ]
  }
}
```

### 23.4.5 撤回后的记录保留规则

```text
1. 撤回不删除审批记录；
2. 已处理任务保持 APPROVED / REJECTED / TRANSFERRED 等原状态；
3. 未处理任务变为 CANCELED；
4. 新增 WITHDRAW 记录；
5. 前端审批时间轴展示“该流程已撤回”；
6. 旧审批意见仅作为历史，不作为当前审批结果。
```

### 23.4.6 撤回后重新提交

建议撤回后重新提交创建新的审批实例，而不是复用旧实例。

```text
旧实例：WITHDRAWN
新实例：RUNNING
新实例 previousInstanceId = 旧实例 ID
业务单据 businessRevision + 1
```

这样可以保证：

```text
1. 旧审批轨迹完整保留；
2. 新审批流程干净运行；
3. 业务数据版本可追溯；
4. 审计时可区分“撤回前”和“重新提交后”的审批。
```

---

# 24. 会签、或签、顺序审批数据结构

## 24.1 顺序审批 `SEQUENTIAL`

顺序审批表示一个节点内按顺序处理审批人。

示例：项目经理 → 商务经理 → 成本经理。

### 24.1.1 节点模板

```json
{
  "nodeCode": "PROJECT_BUSINESS_COST",
  "nodeName": "项目商务成本顺序审核",
  "approveMode": "SEQUENTIAL",
  "approverConfig": {
    "approverType": "USER",
    "userIds": ["601", "602", "603"]
  }
}
```

### 24.1.2 后端生成任务规则

```text
先生成第一个审批人的待办任务；
后续审批人的任务可预生成 WAITING，也可在上一人同意后生成；
第一个审批人同意后，第二个审批人任务变为 PENDING；
最后一个审批人同意后，节点通过。
```

### 24.1.3 前端展示方式

```json
{
  "nodeInstanceId": "40001",
  "roundNo": 1,
  "approveMode": "SEQUENTIAL",
  "tasks": [
    {
      "approverName": "李明",
      "taskStatus": "APPROVED"
    },
    {
      "approverName": "王强",
      "taskStatus": "PENDING"
    },
    {
      "approverName": "赵伟",
      "taskStatus": "WAITING"
    }
  ]
}
```

---

## 24.2 会签 `COUNTERSIGN`

会签表示一个节点内多个人都要处理。

常见场景：

```text
商务经理 + 成本经理共同审核；
质量员 + 安全员共同确认；
多个部门共同会审。
```

### 24.2.1 会签模板

```json
{
  "nodeCode": "BUSINESS_COST_COUNTERSIGN",
  "nodeName": "商务成本会签",
  "approveMode": "COUNTERSIGN",
  "passRule": {
    "type": "ALL"
  },
  "rejectRule": {
    "type": "ANY_REJECT"
  },
  "approverConfig": {
    "approverType": "MULTI_ROLE",
    "roleCodes": ["BUSINESS_MANAGER", "COST_MANAGER"]
  }
}
```

### 24.2.2 `passRule` 规则

| 规则 | 说明 |
|---|---|
| `ALL` | 全部同意才通过 |
| `RATIO` | 达到指定比例即通过 |
| `COUNT` | 达到指定人数即通过 |

### 24.2.3 全部同意才通过

```json
{
  "passRule": {
    "type": "ALL"
  }
}
```

### 24.2.4 达到比例即通过

```json
{
  "passRule": {
    "type": "RATIO",
    "ratio": 0.67
  }
}
```

### 24.2.5 达到人数即通过

```json
{
  "passRule": {
    "type": "COUNT",
    "count": 2
  }
}
```

### 24.2.6 会签节点实例

```json
{
  "nodeInstanceId": "40002",
  "roundNo": 1,
  "nodeName": "商务成本会签",
  "approveMode": "COUNTERSIGN",
  "nodeStatus": "RUNNING",
  "counterSignStats": {
    "totalCount": 3,
    "requiredCount": 3,
    "approvedCount": 1,
    "rejectedCount": 0,
    "pendingCount": 2
  },
  "tasks": [
    {
      "taskId": "80002",
      "roundNo": 1,
      "approverId": "602",
      "approverName": "王强",
      "taskStatus": "APPROVED",
      "comment": "商务确认无误"
    },
    {
      "taskId": "80003",
      "roundNo": 1,
      "approverId": "603",
      "approverName": "赵伟",
      "taskStatus": "PENDING"
    },
    {
      "taskId": "80004",
      "roundNo": 1,
      "approverId": "604",
      "approverName": "钱芳",
      "taskStatus": "PENDING"
    }
  ]
}
```

### 24.2.7 会签后端处理规则

审批人点击同意后：

```text
1. 更新当前 task_status = APPROVED；
2. 写入 wf_record，必须带 nodeInstanceId 和 roundNo；
3. 重新统计 approvedCount、rejectedCount、pendingCount；
4. 判断是否满足 passRule；
5. 如果满足，则 node_status = PASSED；
6. 取消剩余无需处理任务；
7. 生成下一节点任务。
```

审批人点击驳回后：

```text
1. 更新当前 task_status = REJECTED；
2. 写入 wf_record，必须带 nodeInstanceId 和 roundNo；
3. 根据 rejectRule 判断节点是否立即驳回；
4. 如果 rejectRule = ANY_REJECT，则节点立即驳回；
5. 实例进入 REJECTED 或退回指定节点；
6. 取消同节点其他待办任务。
```

---

## 24.3 或签 `OR_SIGN`

或签表示一个节点内有多个候选审批人，只要其中一个人处理即可。

常见场景：

```text
多个财务人员任意一人审核；
多个合同管理员任意一人处理；
项目部同岗位多人抢办。
```

### 24.3.1 或签模板

```json
{
  "nodeCode": "FINANCE_OR_SIGN",
  "nodeName": "财务审核",
  "approveMode": "OR_SIGN",
  "passRule": {
    "type": "ANY_APPROVE"
  },
  "rejectRule": {
    "type": "ALL_REJECT"
  },
  "approverConfig": {
    "approverType": "ROLE",
    "roleCode": "FINANCE"
  }
}
```

### 24.3.2 或签节点实例

```json
{
  "nodeInstanceId": "40003",
  "roundNo": 1,
  "nodeName": "财务审核",
  "approveMode": "OR_SIGN",
  "nodeStatus": "RUNNING",
  "orSignStats": {
    "totalCount": 3,
    "handledCount": 0,
    "pendingCount": 3
  },
  "tasks": [
    {
      "taskId": "80005",
      "roundNo": 1,
      "approverId": "605",
      "approverName": "孙敏",
      "taskStatus": "PENDING"
    },
    {
      "taskId": "80006",
      "roundNo": 1,
      "approverId": "606",
      "approverName": "周丽",
      "taskStatus": "PENDING"
    },
    {
      "taskId": "80007",
      "roundNo": 1,
      "approverId": "607",
      "approverName": "吴磊",
      "taskStatus": "PENDING"
    }
  ]
}
```

### 24.3.3 或签后端处理规则

任意一个审批人点击同意后：

```text
1. 当前任务 task_status = APPROVED；
2. 当前节点 node_status = PASSED；
3. 同节点其他 PENDING 任务更新为 CANCELED；
4. 写入取消记录，原因是 OR_SIGN_COMPLETED；
5. 生成下一节点任务。
```

任意一个审批人点击驳回时：

```text
如果 rejectRule = FIRST_REJECT：
    当前节点立即驳回；
    取消其他待办任务。

如果 rejectRule = ALL_REJECT：
    当前任务标记为 REJECTED；
    其他任务继续待处理；
    只有全部审批人都驳回，节点才驳回。
```

### 24.3.4 并发处理要求

或签最容易出现多人同时点击的问题。

后端必须使用：

```text
数据库事务；
任务版本号；
节点状态锁；
幂等键；
```

处理前必须校验：

```text
node_status 必须仍为 RUNNING；
task_status 必须仍为 PENDING；
round_no 必须等于实例 current_round。
```

如果其他人已经处理完成，返回：

```json
{
  "code": "WF_OR_SIGN_ALREADY_COMPLETED",
  "message": "该或签节点已由其他审批人处理"
}
```

---

# 25. 转办 JSON 契约

## 25.1 转办定义

转办是当前审批人将自己的待办任务转给另一个人处理。

转办后：

```text
原任务状态 = TRANSFERRED；
新任务状态 = PENDING；
新任务继承原任务所在节点；
新任务继承原任务 roundNo；
审批权转移给新审批人；
审批记录中保留原审批人和新审批人。
```

---

## 25.2 转办接口

接口：

```text
POST /api/v1/workflow/tasks/{taskId}/transfer
```

### 25.2.1 请求示例

```json
{
  "idempotencyKey": "transfer-80003-20240115113000123",
  "taskVersion": 2,
  "roundNo": 1,
  "targetUserId": "608",
  "reason": "本人出差，由同岗位人员代为审批",
  "keepCc": true
}
```

### 25.2.2 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "oldTask": {
      "taskId": "80003",
      "taskStatus": "TRANSFERRED",
      "approverId": "603",
      "approverName": "赵伟",
      "roundNo": 1
    },
    "newTask": {
      "taskId": "80008",
      "taskStatus": "PENDING",
      "approverId": "608",
      "approverName": "陈刚",
      "roundNo": 1
    }
  }
}
```

---

## 25.3 转办记录

转办后写入 `wf_record`：

```json
{
  "instanceId": "30001",
  "nodeInstanceId": "40002",
  "taskId": "80003",
  "roundNo": 1,
  "nodeCode": "BUSINESS_COST_COUNTERSIGN",
  "nodeName": "商务成本会签",
  "actionType": "TRANSFER",
  "operatorId": "603",
  "operatorName": "赵伟",
  "targetUserId": "608",
  "targetUserName": "陈刚",
  "comment": "本人出差，由同岗位人员代为审批",
  "recordStatus": "EFFECTIVE"
}
```

---

## 25.4 转办校验规则

```text
只有 PENDING 任务可以转办；
当前用户必须是任务审批人；
模板节点 allowTransfer = true；
目标用户必须存在；
目标用户不能是当前用户；
目标用户必须满足数据权限；
roundNo 必须等于实例 currentRound；
已完成、已驳回、已取消任务不可转办。
```

---

# 26. 加签 JSON 契约

## 26.1 加签定义

加签是在当前审批节点临时增加审批人。

建议支持三种加签：

```text
前加签：新增审批人先审，审完后回到当前审批人；
后加签：当前审批人审完后，新增审批人再审；
并行加签：新增审批人与当前审批人并行审批。
```

对应枚举：

```text
ADD_SIGN_BEFORE
ADD_SIGN_AFTER
ADD_SIGN_PARALLEL
```

---

## 26.2 前加签

### 26.2.1 业务含义

当前审批人认为需要别人先确认，先加一个人审批。

流程：

```text
当前任务挂起
  ↓
生成加签人任务
  ↓
加签人同意
  ↓
当前任务恢复待处理
```

### 26.2.2 接口

```text
POST /api/v1/workflow/tasks/{taskId}/add-sign
```

### 26.2.3 请求示例

```json
{
  "idempotencyKey": "add-sign-before-80003-20240115114000123",
  "taskVersion": 2,
  "roundNo": 1,
  "addSignType": "BEFORE",
  "approveMode": "SEQUENTIAL",
  "targetUsers": [
    {
      "userId": "609",
      "userName": "刘洋"
    }
  ],
  "reason": "请材料负责人先确认验收资料"
}
```

### 26.2.4 后端处理结果

```text
当前任务 task_status = PENDING_HOLD；
生成加签任务 task_status = PENDING；
加签任务 source_task_id = 当前任务 ID；
加签任务 add_sign_type = BEFORE；
加签任务 round_no = 当前任务 round_no。
```

### 26.2.5 响应示例

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "sourceTask": {
      "taskId": "80003",
      "taskStatus": "PENDING_HOLD",
      "roundNo": 1
    },
    "addSignTasks": [
      {
        "taskId": "80009",
        "approverId": "609",
        "approverName": "刘洋",
        "taskStatus": "PENDING",
        "roundNo": 1
      }
    ]
  }
}
```

---

## 26.3 后加签

### 26.3.1 业务含义

当前审批人先处理，处理通过后再交给新增审批人。

流程：

```text
当前审批人同意
  ↓
生成后加签任务
  ↓
加签人审批
  ↓
当前节点继续判断是否通过
```

### 26.3.2 请求示例

```json
{
  "idempotencyKey": "add-sign-after-80003-20240115114000123",
  "taskVersion": 2,
  "roundNo": 1,
  "addSignType": "AFTER",
  "approveMode": "SEQUENTIAL",
  "targetUsers": [
    {
      "userId": "610",
      "userName": "何敏"
    }
  ],
  "reason": "同意，但需财务主管复核发票"
}
```

### 26.3.3 后端处理规则

```text
记录后加签配置；
当前审批人同意后，不立即结束节点；
生成后加签人的待办任务；
后加签人处理完成后，再判断节点是否通过。
```

---

## 26.4 并行加签

### 26.4.1 业务含义

当前审批人需要其他人同时参与审批。

流程：

```text
当前任务继续保留
  ↓
新增审批人任务
  ↓
当前审批人与加签人并行审批
  ↓
根据会签规则判断节点是否通过
```

### 26.4.2 请求示例

```json
{
  "idempotencyKey": "add-sign-parallel-80003-20240115114000123",
  "taskVersion": 2,
  "roundNo": 1,
  "addSignType": "PARALLEL",
  "approveMode": "COUNTERSIGN",
  "targetUsers": [
    {
      "userId": "611",
      "userName": "马超"
    },
    {
      "userId": "612",
      "userName": "林洁"
    }
  ],
  "passRule": {
    "type": "ALL"
  },
  "reason": "涉及成本和合同条款，需要共同确认"
}
```

---

## 26.5 多人加签模式

当一次加签多个人时，必须声明多人之间的审批模式。

```json
{
  "addSignType": "BEFORE",
  "approveMode": "COUNTERSIGN",
  "targetUsers": [
    {
      "userId": "609",
      "userName": "刘洋"
    },
    {
      "userId": "610",
      "userName": "何敏"
    }
  ],
  "passRule": {
    "type": "ALL"
  },
  "reason": "请两位负责人共同确认"
}
```

| `approveMode` | 说明 |
|---|---|
| `SEQUENTIAL` | 加签人按顺序审批 |
| `COUNTERSIGN` | 加签人全部或按规则审批 |
| `OR_SIGN` | 加签人任意一人审批即可 |

---

## 26.6 加签校验规则

```text
当前任务必须是 PENDING；
当前用户必须是任务审批人；
模板节点 allowAddSign = true；
目标用户不能为空；
目标用户不能包含当前用户；
目标用户不能重复；
目标用户必须有对应项目权限；
roundNo 必须等于实例 currentRound；
当前节点已经完成时不可加签；
或签节点已被其他人处理完成时不可加签。
```

---

# 27. 审批前端页面数据结构

## 27.1 审批详情页建议结构

审批详情页建议分为四个区域：

```text
顶部：业务摘要
中部：业务详情
右侧：审批流时间轴
底部：审批操作区
```

---

## 27.2 前端详情页一次性获取数据

接口：

```text
GET /api/v1/workflow/tasks/{taskId}/approval-page
```

响应：

```json
{
  "code": "0",
  "message": "success",
  "traceId": "f6c1b4c92a8841b6",
  "data": {
    "task": {
      "taskId": "80003",
      "taskVersion": 2,
      "taskStatus": "PENDING",
      "instanceId": "30001",
      "nodeInstanceId": "40002",
      "roundNo": 1,
      "nodeName": "商务成本会签",
      "approveMode": "COUNTERSIGN"
    },
    "business": {
      "businessType": "PAY_REQUEST",
      "businessId": "90001",
      "title": "付款申请审批 - 钢筋采购合同",
      "summary": {
        "projectName": "滨江大道综合体项目",
        "contractName": "钢筋采购合同",
        "partnerName": "华东钢材有限公司",
        "requestAmount": "86000.00",
        "paymentRatio": "65.20"
      },
      "detailApi": "/api/v1/pay-requests/90001"
    },
    "workflow": {
      "instanceId": "30001",
      "instanceStatus": "RUNNING",
      "currentRound": 1,
      "nodes": []
    },
    "availableActions": [
      {
        "actionType": "APPROVE",
        "actionName": "同意",
        "enabled": true
      },
      {
        "actionType": "REJECT",
        "actionName": "驳回",
        "enabled": true,
        "requireComment": true
      },
      {
        "actionType": "TRANSFER",
        "actionName": "转办",
        "enabled": true,
        "requireTargetUser": true
      },
      {
        "actionType": "ADD_SIGN_BEFORE",
        "actionName": "前加签",
        "enabled": true,
        "requireTargetUser": true
      }
    ],
    "formSchema": {
      "schemaVersion": "1.0",
      "layout": {
        "columns": 2,
        "labelWidth": 120
      },
      "fields": [
        {
          "field": "approvedAmount",
          "label": "审批金额",
          "type": "money",
          "required": true,
          "defaultValue": "86000.00",
          "placeholder": "请输入审批金额",
          "validators": [
            {
              "type": "min",
              "value": "0.01",
              "message": "审批金额必须大于0"
            },
            {
              "type": "max",
              "value": "86000.00",
              "message": "审批金额不能超过本次申请金额"
            }
          ]
        },
        {
          "field": "riskConfirmed",
          "label": "风险已确认",
          "type": "boolean",
          "required": true,
          "defaultValue": false
        },
        {
          "field": "expectedPayDate",
          "label": "建议付款日期",
          "type": "date",
          "required": false
        },
        {
          "field": "approvalAttachments",
          "label": "审批附件",
          "type": "fileUpload",
          "required": false,
          "accept": [".pdf", ".doc", ".docx", ".xls", ".xlsx", ".jpg", ".png"],
          "maxCount": 5,
          "maxSizeMB": 50
        }
      ]
    }
  }
}
```

说明：

```text
business.detailApi 用于前端加载具体业务详情；
workflow.nodes 用于展示审批时间轴；
availableActions 用于渲染操作按钮；
formSchema 用于渲染当前节点需要填写的审批字段。
```

---

# 28. formSchema 字段类型约定

## 28.1 字段类型枚举

审批动态表单 `formSchema.fields[].type` 建议统一支持以下类型：

```text
text                 单行文本
textarea             多行文本
number               数字
money                金额
percent              百分比
boolean              布尔开关
select               单选下拉
multiSelect          多选下拉
radio                单选按钮
checkboxGroup        多选框
date                 日期
datetime             日期时间
userPicker           人员选择
orgPicker            组织选择
projectPicker        项目选择
contractPicker       合同选择
partnerPicker        合作方选择
cascader             级联选择
fileUpload           文件上传
table                子表
readonlyText         只读文本
```

---

## 28.2 字段通用属性

| 属性 | 说明 |
|---|---|
| `field` | 字段名 |
| `label` | 前端显示名称 |
| `type` | 字段类型 |
| `required` | 是否必填 |
| `defaultValue` | 默认值 |
| `placeholder` | 占位提示 |
| `readonly` | 是否只读 |
| `hidden` | 是否隐藏 |
| `helpText` | 辅助说明 |
| `validators` | 校验规则 |
| `options` | 静态选项 |
| `optionsApi` | 动态选项接口 |
| `dependsOn` | 依赖字段 |
| `visibleWhen` | 条件显示 |
| `disabledWhen` | 条件禁用 |

---

## 28.3 下拉选择 `select`

```json
{
  "field": "contractType",
  "label": "合同类型",
  "type": "select",
  "required": true,
  "options": [
    {
      "label": "总包合同",
      "value": "MAIN_CONTRACT"
    },
    {
      "label": "分包合同",
      "value": "SUB_CONTRACT"
    },
    {
      "label": "采购合同",
      "value": "PURCHASE_CONTRACT"
    }
  ]
}
```

---

## 28.4 级联选择 `cascader`

```json
{
  "field": "projectContract",
  "label": "项目合同",
  "type": "cascader",
  "required": true,
  "levels": [
    {
      "field": "projectId",
      "label": "项目",
      "api": "/api/v1/projects/options"
    },
    {
      "field": "contractId",
      "label": "合同",
      "api": "/api/v1/contracts/options",
      "dependsOn": "projectId"
    }
  ]
}
```

---

## 28.5 文件上传 `fileUpload`

```json
{
  "field": "approvalAttachments",
  "label": "审批附件",
  "type": "fileUpload",
  "required": false,
  "accept": [".pdf", ".doc", ".docx", ".xls", ".xlsx", ".jpg", ".png"],
  "maxCount": 5,
  "maxSizeMB": 50,
  "businessType": "PAY_REQUEST",
  "fileCategory": "APPROVAL_ATTACHMENT"
}
```

---

## 28.6 子表 `table`

```json
{
  "field": "deductionItems",
  "label": "扣款明细",
  "type": "table",
  "required": false,
  "columns": [
    {
      "field": "deductionReason",
      "label": "扣款原因",
      "type": "text",
      "required": true
    },
    {
      "field": "deductionAmount",
      "label": "扣款金额",
      "type": "money",
      "required": true
    }
  ]
}
```

---

## 28.7 条件显示 `visibleWhen`

```json
{
  "field": "rejectReasonType",
  "label": "驳回原因分类",
  "type": "select",
  "required": true,
  "visibleWhen": {
    "field": "actionType",
    "operator": "=",
    "value": "REJECT"
  },
  "options": [
    {
      "label": "资料缺失",
      "value": "MISSING_FILE"
    },
    {
      "label": "金额错误",
      "value": "AMOUNT_ERROR"
    }
  ]
}
```

---

## 28.8 后端校验原则

`formSchema` 只用于前端渲染，不作为后端信任依据。

后端仍必须重新校验：

```text
审批金额不能超过申请金额；
付款金额不能超过合同可付金额；
付款金额不能超过付款比例；
附件类型和大小必须符合规则；
当前用户必须有权限操作当前字段；
当前任务必须处于待处理状态。
```

---

# 29. 审批候选人接口

## 29.1 获取转办候选人

接口：

```text
GET /api/v1/workflow/tasks/{taskId}/transfer-candidates
```

响应：

```json
{
  "code": "0",
  "message": "success",
  "data": [
    {
      "userId": "608",
      "userName": "陈刚",
      "deptName": "成本管理部",
      "roleNames": ["成本经理"],
      "available": true
    },
    {
      "userId": "609",
      "userName": "刘洋",
      "deptName": "商务管理部",
      "roleNames": ["商务经理"],
      "available": true
    }
  ]
}
```

---

## 29.2 获取加签候选人

接口：

```text
GET /api/v1/workflow/tasks/{taskId}/add-sign-candidates
```

响应：

```json
{
  "code": "0",
  "message": "success",
  "data": [
    {
      "userId": "610",
      "userName": "何敏",
      "deptName": "财务部",
      "roleNames": ["财务主管"],
      "available": true
    }
  ]
}
```

---

# 30. 业务模块与审批模块的关系

## 30.1 业务模块保存草稿

以付款申请为例：

```text
POST /api/v1/pay-requests
```

请求：

```json
{
  "projectId": "10001",
  "contractId": "20001",
  "partnerId": "30001",
  "payType": "MATERIAL",
  "requestAmount": "86000.00",
  "basisList": [
    {
      "basisType": "MAT_RECEIPT",
      "basisId": "70001",
      "basisAmount": "50000.00",
      "requestAmount": "50000.00"
    },
    {
      "basisType": "MAT_RECEIPT",
      "basisId": "70002",
      "basisAmount": "36000.00",
      "requestAmount": "36000.00"
    }
  ],
  "attachments": [
    {
      "fileId": "F10001",
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
    "payRequestId": "90001",
    "approvalStatus": "DRAFT",
    "payStatus": "UNSUBMITTED",
    "businessRevision": 1
  }
}
```

---

## 30.2 业务模块提交审批

```text
POST /api/v1/pay-requests/{payRequestId}/submit-approval
```

请求：

```json
{
  "idempotencyKey": "submit-pay-90001-20240115093000123"
}
```

业务后端负责组装审批提交参数，然后调用工作流服务：

```json
{
  "businessType": "PAY_REQUEST",
  "businessId": "90001",
  "projectId": "10001",
  "contractId": "20001",
  "title": "付款申请审批 - 钢筋采购合同",
  "amount": "86000.00",
  "businessRevision": 1,
  "businessSummary": {},
  "variables": {},
  "attachments": []
}
```

---

## 30.3 审批通过后回调业务模块

审批实例最终通过后，工作流模块触发事件：

```json
{
  "eventType": "WORKFLOW_APPROVED",
  "instanceId": "30001",
  "businessType": "PAY_REQUEST",
  "businessId": "90001",
  "businessRevision": 1,
  "approvedAt": "2024-01-16T10:00:00+08:00"
}
```

付款申请模块收到事件后：

```text
pay_request.approval_status = APPROVED
pay_request.pay_status = WAIT_PAY
生成财务付款待办
```

---

## 30.4 审批撤回后回调业务模块

```json
{
  "eventType": "WORKFLOW_WITHDRAWN",
  "instanceId": "30001",
  "businessType": "PAY_REQUEST",
  "businessId": "90001",
  "businessRevision": 1,
  "withdrawnAt": "2024-01-15T11:00:00+08:00"
}
```

付款申请模块收到事件后：

```text
pay_request.approval_status = WITHDRAWN
pay_request.pay_status = UNSUBMITTED
业务单据允许编辑
```

---

# 31. 建议补充的审批数据库表

现有 `wf_template`、`wf_template_node`、`wf_instance`、`wf_task`、`wf_record` 可以支撑基础流程，但为了清晰支持会签、或签、加签、转办、撤回、重新提交和多轮审批，建议增加或强化以下表。

---

## 31.1 审批实例表 `wf_instance` 字段增强

| 字段 | 类型 | 说明 |
|---|---|---|
| `current_round` | int | 当前审批轮次 |
| `resubmit_count` | int | 重新提交次数 |
| `previous_instance_id` | bigint | 撤回后重新提交时关联上一审批实例 |
| `business_revision` | int | 业务数据版本 |
| `withdrawn_by` | bigint | 撤回人 |
| `withdrawn_at` | datetime | 撤回时间 |
| `withdraw_reason` | varchar | 撤回原因 |

建议 SQL：

```sql
ALTER TABLE wf_instance
ADD COLUMN current_round INT DEFAULT 1 COMMENT '当前审批轮次',
ADD COLUMN resubmit_count INT DEFAULT 0 COMMENT '重新提交次数',
ADD COLUMN previous_instance_id BIGINT NULL COMMENT '上一审批实例ID',
ADD COLUMN business_revision INT DEFAULT 1 COMMENT '业务数据版本',
ADD COLUMN withdrawn_by BIGINT NULL COMMENT '撤回人',
ADD COLUMN withdrawn_at DATETIME NULL COMMENT '撤回时间',
ADD COLUMN withdraw_reason VARCHAR(500) NULL COMMENT '撤回原因';
```

---

## 31.2 节点实例表 `wf_node_instance`

用于记录每一次审批实例中的节点运行状态。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | 节点实例 ID |
| `instance_id` | bigint | 审批实例 ID |
| `template_node_id` | bigint | 模板节点 ID |
| `round_no` | int | 节点所属审批轮次 |
| `previous_node_instance_id` | bigint | 上一轮对应节点实例 ID |
| `node_code` | varchar | 节点编码 |
| `node_name` | varchar | 节点名称 |
| `node_order` | int | 节点顺序 |
| `approve_mode` | varchar | 顺序、会签、或签 |
| `node_status` | varchar | WAITING、RUNNING、PASSED、REJECTED |
| `pass_rule_json` | json | 通过规则 |
| `reject_rule_json` | json | 驳回规则 |
| `total_count` | int | 节点审批人数 |
| `required_count` | int | 需要通过人数 |
| `approved_count` | int | 已同意人数 |
| `rejected_count` | int | 已驳回人数 |
| `pending_count` | int | 待处理人数 |
| `started_at` | datetime | 节点开始时间 |
| `ended_at` | datetime | 节点结束时间 |

建议 SQL：

```sql
CREATE TABLE wf_node_instance (
  id BIGINT PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  template_node_id BIGINT NULL,
  round_no INT DEFAULT 1,
  previous_node_instance_id BIGINT NULL,
  node_code VARCHAR(100),
  node_name VARCHAR(100),
  node_order INT,
  approve_mode VARCHAR(50),
  node_status VARCHAR(50),
  pass_rule_json JSON,
  reject_rule_json JSON,
  total_count INT DEFAULT 0,
  required_count INT DEFAULT 0,
  approved_count INT DEFAULT 0,
  rejected_count INT DEFAULT 0,
  pending_count INT DEFAULT 0,
  started_at DATETIME,
  ended_at DATETIME
);
```

---

## 31.3 审批任务表 `wf_task` 字段增强

| 字段 | 类型 | 说明 |
|---|---|---|
| `node_instance_id` | bigint | 节点实例 ID |
| `round_no` | int | 任务所属审批轮次 |
| `approver_id` | bigint | 当前审批人 |
| `original_approver_id` | bigint | 原审批人，转办时保留 |
| `source_task_id` | bigint | 来源任务，加签或转办使用 |
| `task_type` | varchar | APPROVE、CC、ADD_SIGN |
| `add_sign_type` | varchar | BEFORE、AFTER、PARALLEL |
| `task_status` | varchar | PENDING、APPROVED、REJECTED、TRANSFERRED、CANCELED |
| `version` | int | 乐观锁版本 |
| `idempotency_key` | varchar | 幂等键 |
| `cancel_reason` | varchar | 取消原因 |
| `deadline_at` | datetime | 截止时间 |

建议 SQL：

```sql
ALTER TABLE wf_task
ADD COLUMN node_instance_id BIGINT NULL COMMENT '节点实例ID',
ADD COLUMN round_no INT DEFAULT 1 COMMENT '任务所属审批轮次',
ADD COLUMN original_approver_id BIGINT NULL COMMENT '原审批人',
ADD COLUMN source_task_id BIGINT NULL COMMENT '来源任务',
ADD COLUMN task_type VARCHAR(50) DEFAULT 'APPROVE' COMMENT '任务类型',
ADD COLUMN add_sign_type VARCHAR(50) NULL COMMENT '加签类型',
ADD COLUMN version INT DEFAULT 1 COMMENT '乐观锁版本',
ADD COLUMN idempotency_key VARCHAR(100) NULL COMMENT '幂等键',
ADD COLUMN cancel_reason VARCHAR(100) NULL COMMENT '取消原因',
ADD COLUMN deadline_at DATETIME NULL COMMENT '截止时间';
```

---

## 31.4 审批记录表 `wf_record` 字段增强

`wf_record` 必须记录 `node_instance_id`，否则无法精确还原“这个同意、驳回、转办、加签发生在哪个节点”。

| 字段 | 类型 | 说明 |
|---|---|---|
| `instance_id` | bigint | 审批实例 ID |
| `node_instance_id` | bigint | 节点实例 ID，可为空 |
| `task_id` | bigint | 审批任务 ID，可为空 |
| `round_no` | int | 审批轮次 |
| `node_code` | varchar | 节点编码快照 |
| `node_name` | varchar | 节点名称快照 |
| `action_type` | varchar | APPROVE、REJECT、TRANSFER、ADD_SIGN |
| `operator_id` | bigint | 操作人 |
| `from_user_id` | bigint | 原审批人 |
| `to_user_id` | bigint | 目标审批人 |
| `source_task_id` | bigint | 来源任务 |
| `target_task_id` | bigint | 目标任务 |
| `comment` | text | 审批意见 |
| `attachments_json` | json | 审批附件 |
| `record_status` | varchar | EFFECTIVE、HISTORY、SYSTEM |
| `created_at` | datetime | 操作时间 |

建议 SQL：

```sql
ALTER TABLE wf_record
ADD COLUMN node_instance_id BIGINT NULL COMMENT '节点实例ID',
ADD COLUMN round_no INT DEFAULT 1 COMMENT '审批轮次',
ADD COLUMN node_code VARCHAR(100) NULL COMMENT '节点编码快照',
ADD COLUMN node_name VARCHAR(100) NULL COMMENT '节点名称快照',
ADD COLUMN from_user_id BIGINT NULL COMMENT '原审批人',
ADD COLUMN to_user_id BIGINT NULL COMMENT '目标审批人',
ADD COLUMN source_task_id BIGINT NULL COMMENT '来源任务',
ADD COLUMN target_task_id BIGINT NULL COMMENT '目标任务',
ADD COLUMN attachments_json JSON NULL COMMENT '审批附件',
ADD COLUMN record_status VARCHAR(50) DEFAULT 'EFFECTIVE' COMMENT '记录状态';
```

---

# 32. 状态流转核心算法

## 32.1 审批同意伪代码

```text
approveTask(taskId):

  开启事务

  查询 task
  校验 task.status = PENDING
  校验 task.version
  校验 task.round_no = instance.current_round
  校验当前用户 = task.approver_id

  更新 task.status = APPROVED
  写入 wf_record
      instance_id
      node_instance_id
      task_id
      round_no
      node_code
      node_name
      action_type = APPROVE

  查询 node_instance

  如果 approveMode = SEQUENTIAL:
      如果还有下一个审批人:
          创建或激活下一个审批人 task
      否则:
          node.status = PASSED
          激活下一节点

  如果 approveMode = COUNTERSIGN:
      统计 approved_count / rejected_count / pending_count
      如果满足 passRule:
          node.status = PASSED
          取消不需要继续处理的任务
          激活下一节点

  如果 approveMode = OR_SIGN:
      node.status = PASSED
      取消同节点其他待办任务
      写入 AUTO_CANCEL 记录
      激活下一节点

  如果没有下一节点:
      instance.status = APPROVED
      发布 WORKFLOW_APPROVED 事件

  提交事务
```

---

## 32.2 审批驳回伪代码

```text
rejectTask(taskId):

  开启事务

  查询 task
  校验 task.status = PENDING
  校验 task.version
  校验 task.round_no = instance.current_round
  校验当前用户 = task.approver_id

  更新 task.status = REJECTED
  写入 wf_record
      instance_id
      node_instance_id
      task_id
      round_no
      node_code
      node_name
      action_type = REJECT

  查询 node_instance

  根据 rejectRule 判断：
      立即驳回
      或等待其他审批人
      或退回指定节点

  如果实例驳回:
      instance.status = REJECTED
      业务单据 approval_status = REJECTED
      取消所有待办任务
      发布 WORKFLOW_REJECTED 事件

  提交事务
```

---

## 32.3 重新提交伪代码

```text
resubmit(instanceId):

  开启事务

  查询 instance
  校验 instance.status = REJECTED
  校验当前用户 = initiator

  instance.current_round = instance.current_round + 1
  instance.resubmit_count = instance.resubmit_count + 1
  instance.status = RUNNING

  新建新一轮 node_instance
      round_no = instance.current_round

  新建新一轮 task
      round_no = instance.current_round

  写入 wf_record
      action_type = RESUBMIT
      round_no = instance.current_round

  发布 WORKFLOW_RESUBMITTED 事件

  提交事务
```

---

## 32.4 撤回伪代码

```text
withdraw(instanceId):

  开启事务

  查询 instance
  校验 instance.status = RUNNING
  校验当前用户 = initiator
  根据撤回策略判断是否允许撤回

  instance.status = WITHDRAWN
  instance.withdrawn_by = 当前用户
  instance.withdrawn_at = 当前时间
  instance.withdraw_reason = 撤回原因

  将所有 PENDING / WAITING / PENDING_HOLD 任务改为 CANCELED
  cancel_reason = WITHDRAWN_BY_INITIATOR

  已处理任务保持原状态，不删除

  写入 wf_record
      action_type = WITHDRAW
      round_no = instance.current_round
      record_status = EFFECTIVE

  发布 WORKFLOW_WITHDRAWN 事件

  提交事务
```

---

# 33. 前端实现建议

## 33.1 待办中心

待办中心只调用：

```text
GET /api/v1/workflow/tasks/todo
```

前端列表展示字段：

```text
业务标题
业务类型
项目名称
合同名称
金额
发起人
当前节点
审批模式
审批轮次
接收时间
截止时间
可操作按钮
```

---

## 33.2 审批详情页

审批详情页调用：

```text
GET /api/v1/workflow/tasks/{taskId}/approval-page
```

前端展示：

```text
业务摘要
业务详情
审批流时间轴
审批轮次
当前节点处理人
动态审批表单
审批意见输入框
附件上传
操作按钮
```

---

## 33.3 多轮审批展示

前端审批时间轴按 `roundNo` 分组：

```text
第 1 轮审批
  项目经理：同意
  成本经理：驳回，原因：发票缺失

第 2 轮审批
  项目经理：待处理
```

---

## 33.4 会签展示

会签节点展示为：

```text
商务成本会签
  王强  已同意
  赵伟  待处理
  钱芳  待处理

已同意 1 / 共 3
```

---

## 33.5 或签展示

或签节点展示为：

```text
财务审核
  孙敏  待处理
  周丽  待处理
  吴磊  待处理
```

如果孙敏已处理，则展示：

```text
财务审核
  孙敏  已同意
  周丽  已取消
  吴磊  已取消
```

---

## 33.6 转办展示

```text
赵伟 转办给 陈刚
原因：本人出差，由同岗位人员代为审批
```

---

## 33.7 加签展示

前加签展示：

```text
赵伟 前加签 刘洋
刘洋 已同意
赵伟 待处理
```

后加签展示：

```text
赵伟 已同意
后加签 何敏 待处理
```

并行加签展示：

```text
赵伟 待处理
马超 待处理
林洁 待处理
```

---

## 33.8 撤回展示

撤回后的审批流展示：

```text
该审批流程已由 张三 撤回
撤回原因：付款金额填写有误，撤回修改

已处理审批记录保留，仅作为历史留痕。
```

---

# 34. API 清单汇总

## 34.1 审批模板 API

| 方法 | 地址 | 说明 |
|---|---|---|
| `POST` | `/api/v1/workflow/templates` | 新建审批模板 |
| `PUT` | `/api/v1/workflow/templates/{templateId}` | 修改审批模板 |
| `GET` | `/api/v1/workflow/templates/{templateId}` | 查看模板详情 |
| `GET` | `/api/v1/workflow/templates` | 模板列表 |
| `PATCH` | `/api/v1/workflow/templates/{templateId}/enable` | 启用模板 |
| `PATCH` | `/api/v1/workflow/templates/{templateId}/disable` | 禁用模板 |

---

## 34.2 审批实例 API

| 方法 | 地址 | 说明 |
|---|---|---|
| `POST` | `/api/v1/workflow/instances/preview` | 提交前预览审批流 |
| `POST` | `/api/v1/workflow/instances/submit` | 提交审批 |
| `POST` | `/api/v1/workflow/instances/{instanceId}/resubmit` | 驳回后重新提交 |
| `GET` | `/api/v1/workflow/instances/{instanceId}` | 审批实例详情 |
| `POST` | `/api/v1/workflow/instances/{instanceId}/withdraw` | 撤回审批 |

---

## 34.3 审批任务 API

| 方法 | 地址 | 说明 |
|---|---|---|
| `GET` | `/api/v1/workflow/tasks/todo` | 我的待办 |
| `GET` | `/api/v1/workflow/tasks/done` | 我的已办 |
| `GET` | `/api/v1/workflow/tasks/cc` | 抄送我的 |
| `GET` | `/api/v1/workflow/tasks/{taskId}` | 任务详情 |
| `GET` | `/api/v1/workflow/tasks/{taskId}/approval-page` | 审批页面数据 |
| `POST` | `/api/v1/workflow/tasks/{taskId}/approve` | 同意 |
| `POST` | `/api/v1/workflow/tasks/{taskId}/reject` | 驳回 |
| `POST` | `/api/v1/workflow/tasks/{taskId}/return-previous` | 退回上一步 |
| `POST` | `/api/v1/workflow/tasks/{taskId}/transfer` | 转办 |
| `POST` | `/api/v1/workflow/tasks/{taskId}/add-sign` | 加签 |

---

## 34.4 候选人 API

| 方法 | 地址 | 说明 |
|---|---|---|
| `GET` | `/api/v1/workflow/tasks/{taskId}/transfer-candidates` | 转办候选人 |
| `GET` | `/api/v1/workflow/tasks/{taskId}/add-sign-candidates` | 加签候选人 |
| `GET` | `/api/v1/workflow/approver-candidates` | 通用候选人查询 |

---

# 35. 验收标准

API 与 JSON 契约完成后，应满足以下验收标准：

```text
1. 前端可以通过一个接口获取待办列表；
2. 前端可以通过一个接口获取审批详情页所需全部数据；
3. 前端不需要自行判断审批按钮权限；
4. 会签节点可以正确显示多人审批状态；
5. 或签节点一个人处理后，其他任务自动取消；
6. 转办可以保留原审批人与新审批人的完整记录；
7. 加签可以支持前加签、后加签、并行加签；
8. 所有审批动作支持 taskVersion 防并发；
9. 所有审批动作支持 idempotencyKey 防重复提交；
10. 审批通过后能自动回写业务单据状态；
11. 审批驳回后能正确回退业务单据状态；
12. 驳回后重新提交可以生成新一轮审批；
13. 审批时间轴可以按 roundNo 分组展示；
14. wf_record 可以通过 nodeInstanceId 精确还原节点轨迹；
15. 撤回后已处理审批记录保留，未处理任务取消；
16. formSchema 可以支持文本、金额、布尔、下拉、级联、附件、日期、子表等常见表单类型；
17. 审批记录可以完整还原流程轨迹。
```

---

# 36. 最终建议

审批系统不要让前端理解复杂流程规则。

前端只需要关心：

```text
我要展示什么？
用户能点什么按钮？
用户点击后提交什么 JSON？
```

后端需要负责：

```text
流程模板匹配；
审批人计算；
会签或签判断；
转办加签处理；
审批轮次管理；
动态表单校验；
权限校验；
业务规则校验；
状态流转；
审批记录；
撤回留痕；
业务回调。
```

因此，建议系统落地时坚持以下原则：

```text
前端无流程判断；
后端返回可用动作；
审批动作统一 API；
审批状态统一枚举；
审批记录必须关联节点实例；
驳回重提必须区分审批轮次；
撤回不删除历史记录；
复杂规则后端闭环；
业务模块只关心提交审批和审批结果。
```

这样才能保证合同、采购、材料验收、分包计量、付款申请、结算审批等不同业务模块共用一套审批能力，而不是每个模块各写一套审批逻辑。

---

## V1.2 完整合并版说明

本文件以 V1 正式版全文为正文基底，保留原有详细内容，不删除原有 API 示例、数据库表结构、业务规则、开发计划、测试验收和架构修订内容。

V1.2 仅在 V1 基础上增量合并开工前审计整改项，重点包括：

1. 前端技术栈冻结为 `Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia + VxeTable + ECharts`；
2. 数据库从“设计表结构”升级为“可执行迁移脚本要求”，明确 MySQL 8.0、Flyway、雪花 ID、初始化字典、索引和幂等约束；
3. 审批引擎增加独立 POC、业务回调接口、并发控制、幂等提交、撤回/驳回重提测试要求；
4. 成本自动生成改为“审批回调 + 同事务领域服务 + 幂等唯一索引”，通知与外部系统推送再使用 Outbox；
5. 开发计划按真实阻塞依赖链重排前 4 周任务；
6. 测试验收补充异常路径、并发审批、重复提交、事务回滚、附件失败、弱网恢复等边界场景。


## V1.2 增量：业务回调与审批状态契约

业务模块提交审批时，仍调用统一审批接口；审批完成后由后端内部 `WorkflowBusinessHandler` 回调业务模块。前端不直接调用业务回调接口。

### 业务状态回调数据结构

```json
{
  "instanceId": "30001",
  "businessType": "MATERIAL_RECEIPT",
  "businessId": "70001",
  "projectId": "10001",
  "contractId": "20001",
  "currentRound": 2,
  "actionType": "APPROVE",
  "instanceStatus": "APPROVED",
  "operatorId": "601",
  "operatorName": "李明",
  "handledAt": "2024-01-16T10:30:00+08:00",
  "businessRevision": 3
}
```

### 业务回调状态映射

| 审批实例状态 | 业务单据建议状态 |
|---|---|
| RUNNING | APPROVING / 审批中 |
| APPROVED | EFFECTIVE / 已生效 |
| REJECTED | REJECTED / 已驳回，可编辑 |
| WITHDRAWN | DRAFT 或 WAIT_SUBMIT，按业务规则确定 |
| VOIDED | VOIDED / 已作废 |

## V1.2 增量：availableActions 完整规则

接口返回的 `availableActions` 必须同时考虑用户、任务、流程、业务单据和按钮权限。

```json
{
  "availableActions": [
    {
      "actionType": "APPROVE",
      "actionName": "同意",
      "enabled": true,
      "requireComment": false,
      "requireTargetUser": false,
      "danger": false
    },
    {
      "actionType": "REJECT",
      "actionName": "驳回",
      "enabled": true,
      "requireComment": true,
      "requireTargetUser": false,
      "danger": true
    },
    {
      "actionType": "TRANSFER",
      "actionName": "转办",
      "enabled": true,
      "requireComment": true,
      "requireTargetUser": true,
      "danger": false
    }
  ]
}
```

后端判断维度：

```text
当前用户是否为任务处理人
任务是否 PENDING
流程实例是否 RUNNING
taskVersion 是否匹配
业务单据是否仍允许审批
用户是否拥有对应按钮权限
当前节点是否允许转办 / 加签 / 驳回
```

## V1.2 增量：错误码补充

| 错误码 | 说明 |
|---|---|
| WF_BUSINESS_HANDLER_NOT_FOUND | 未找到业务审批回调处理器 |
| WF_TASK_VERSION_CONFLICT | 审批任务版本冲突，请刷新后重试 |
| WF_IDEMPOTENCY_REPLAY | 重复提交，已按首次请求结果返回 |
| WF_INSTANCE_STATUS_INVALID | 当前流程实例状态不允许此操作 |
| WF_ACTION_NOT_ALLOWED | 当前用户或节点不允许执行此审批动作 |
| COST_SOURCE_DUPLICATED | 来源单据已生成成本，禁止重复生成 |
| BUSINESS_REVISION_CONFLICT | 业务单据版本已变化，请刷新后重试 |

## V1.2 增量：合同台账字段契约补充

合同台账接口应支持列配置。默认返回完整字段，由前端根据列设置决定显示范围。

```json
{
  "contractId": "20001",
  "projectId": "10001",
  "projectName": "滨江大道综合体项目",
  "contractCode": "HT-2024-001",
  "contractName": "钢筋采购合同",
  "contractType": "PURCHASE",
  "partnerName": "华东钢材有限公司",
  "originalAmount": "1000000.00",
  "changeAmount": "50000.00",
  "currentAmount": "1050000.00",
  "paidAmount": "320000.00",
  "paymentRatio": "30.48",
  "signedDate": "2024-01-15",
  "contractStatus": "PERFORMING",
  "approvalStatus": "APPROVED",
  "settlementStatus": "NOT_SETTLED",
  "archiveStatus": "NOT_ARCHIVED",
  "riskLevel": "LOW"
}
```

