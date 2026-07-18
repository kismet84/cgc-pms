# 业务单据生成 Provider 与渲染契约

> 状态：M0 Approved / 2026-07-17 默认口径已批准，M1实施中
>
> 适用主线：[第48条主线：业务单据模板与可审计PDF生成](../plans/第48条主线-业务单据模板与可审计PDF生成任务计划书.md)

## 一、目的与边界

本契约冻结付款申请单和结算单进入文档域时的稳定逻辑模型、安全边界与技术证据。它不授权M1，不创建数据库表，不改变付款、结算、审批或文件事实。

MVP只允许后端受限HTML/CSS生成PDF。模板不得查库、重算金额、执行脚本、发起网络请求、读取本地路径或接收客户端传入的 `tenantId`。

## 二、Provider 通用契约

```text
businessType()
schemaVersion()
supportedStatus()
fieldCatalog()
authorize(action, businessId, authenticatedContext)
build(businessId, authenticatedContext)
```

执行顺序固定为：

1. 从认证上下文取得租户、用户和权限；拒绝客户端租户输入。
2. 复用业务查询权限与项目数据范围完成 `authorize`；未授权时不得构建部分模型。
3. 通过现有领域Service读取业务对象及关联数据；Provider不得直接向模板暴露实体或Mapper。
4. 把金额规范化为十进制定点字符串，把日期时间规范化为明确格式，把集合按稳定键排序。
5. 对规范化模型计算 `sourceDigest`；不包含令牌、密钥、原始银行账号或未批准的个人信息。
6. 模板只消费 `fieldCatalog` 已声明字段；缺失必填字段时正式生成失败，不做静默兜底。

## 三、首批模型版本

### 3.1 `PAYMENT_APPLICATION / payment.document.v1`

权威主数据来自现有 `PayApplicationService.getById`、付款来源服务和发票领域服务。不得在Provider重复计算审批金额、实付金额或来源可申请余额。

| 逻辑字段 | 类型 | 可空 | 当前事实源 |
| --- | --- | --- | --- |
| `payment.id`、`payment.code` | string | 否 | `PayApplicationVO.id/applyCode` |
| `payment.type` | string | 否 | `PayApplicationVO.payType` |
| `payment.status`、`payment.approvalStatus` | string | 否 | `PayApplicationVO.payStatus/approvalStatus` |
| `payment.amount.apply` | decimal-string | 否 | `PayApplicationVO.applyAmount` |
| `payment.amount.approved` | decimal-string | 是 | `PayApplicationVO.approvedAmount` |
| `payment.amount.actualPaid` | decimal-string | 是 | `PayApplicationVO.actualPayAmount` |
| `payment.reason`、`payment.remark` | string | 是 | 现有付款VO |
| `payment.integrityVersion` | string | 否 | `PayApplicationVO.integrityVersion` |
| `project.id/name` | string | 否 | 付款详情既有项目范围与名称 |
| `contract.id/name` | string | 是 | 付款详情既有合同范围与名称 |
| `partner.id/name` | string | 是 | 付款详情既有往来单位范围与名称 |
| `payment.basis[]` | object[] | 是 | `PayApplicationBasisVO`，按ID稳定排序 |
| `payment.sources[]` | object[] | 是 | `PaymentApplicationSourceService.list` |
| `payment.invoices[]` | object[] | 是 | 仅纳入同租户、同项目、同付款申请的发票服务结果 |
| `audit.createdBy/createdAt/updatedAt` | string | 是 | 现有付款VO审计字段 |

银行账号、身份证号、手机号不在 `payment.document.v1` 冻结目录内。脱敏口径确认前，不得为满足样式直接扩充这些字段。

### 3.2 `SETTLEMENT / settlement.document.v1`

权威主数据来自现有 `StlSettlementQueryService`。不得在Provider用模板表达式重算合同金额、变更、计量、扣款、已付、未付、质保或最终金额。

| 逻辑字段 | 类型 | 可空 | 当前事实源 |
| --- | --- | --- | --- |
| `settlement.id/code/type` | string | 否 | `StlSettlementVO` |
| `settlement.status`、`settlement.approvalStatus`、`settlement.finalStatus` | string | 否 | `status/approvalStatus/settlementStatus` |
| `settlement.amount.contract/change/measured/deduction/paid/final/unpaid/warranty` | decimal-string | 按现有VO | `StlSettlementVO` |
| `settlement.amountFormulaVersion` | string | 否 | `StlSettlementVO.amountFormulaVersion` |
| `project.id/name`、`contract.id/name`、`partner.id/name` | string | 按现有VO | 结算详情既有授权范围与名称 |
| `settlement.items[]` | object[] | 是 | `StlSettlementVO.items`，按ID稳定排序 |
| `settlement.variations[]` | object[] | 是 | `StlSettlementQueryService.getVariations` |
| `settlement.payments[]` | object[] | 是 | `StlSettlementQueryService.getPayments` |
| `settlement.costs[]` | object[] | 是 | `StlSettlementQueryService.getCosts` |
| `settlement.attachments[]` | object[] | 是 | `StlSettlementQueryService.getAttachments` |
| `settlement.approvalRecords[]` | object[] | 是 | `StlSettlementQueryService.getApprovalRecords` |
| `audit.finalizedAt/createdBy/createdAt/updatedAt` | string | 按现有VO | 现有结算VO审计字段 |

## 四、状态与权限门

以下保守口径已由项目负责人于2026-07-17批准，构成M1实施边界：

- 正式付款PDF：仅 `approvalStatus=APPROVED`。
- 正式结算PDF：仅 `approvalStatus=APPROVED` 且 `settlementStatus=FINALIZED`。
- 审批中只允许同步临时预览，必须带显著“非正式/审批中”水印，不归档且不可进入正式生成记录。
- 生成和普通下载必须先满足当前 `payment:app:query` 或 `settlement:query`、管理员角色规则及项目数据范围，再叠加文档动作权限；文档权限不能扩大业务可见范围。
- 模板维护和发布限 `ADMIN/SUPER_ADMIN`；普通生成、历史和下载仍要求来源业务查询权限及对应文档权限。
- MVP审计下载暂限 `SUPER_ADMIN + document:audit:download`，只用于普通下载关闭后的受控历史审计，不得绕过租户边界。

## 五、归档、幂等与历史证据

- Provider类型 `PAYMENT_APPLICATION` 显式映射现有文件业务类型 `PAYMENT`；`SETTLEMENT` 映射 `SETTLEMENT`。
- 正式生成必须新增不可覆盖的 generation；失败重试新增记录并关联 `retryOfGenerationId`。
- 成功证据由归档PDF、`outputSha256`、不可变模板版本、`sourceDigest`、渲染器/字体包版本和生成审计共同组成。
- 成功生成物不得走普通文件删除；MVP不自动删除成功文件。业务对象删除或归档后普通下载fail-close，受控审计下载继续按独立权限和审计规则执行。
- M0不保存完整业务源快照，不承诺按未来业务数据重建完全相同文件。

## 六、M0 渲染技术证据

当前尖峰组合：

- `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.40`，M1已进入compile scope；纯Java、Java 21可编译，CSS能力以CSS 2.1子集为边界，不支持脚本、Flex或Grid。项目许可证为LGPL 2.1或更高版本，生产发布前必须完成分发义务审查。
- `org.apache.pdfbox:pdfbox:3.0.8`；Apache License 2.0。项目原3.0.4已升级并通过发票识别回归。
- 资源控制在URI解析前执行；尖峰仅允许内嵌 `data:` 图片，明确拒绝HTTP(S)和 `file:` 路径。生产字体必须来自受控随包资源，不得接受模板或请求指定的文件路径。
- M1固定采用 `com.helger.font:ph-fonts-noto-sans-sc:6.1.0` 中的 `NotoSansSC-Regular.ttf`，依赖JAR随Spring Boot交付包分发且包含 `fonts/ttf/NotoSansSC/OFL.txt`；不读取操作系统字体，不接受请求或模板指定字体路径。生产发布仍须复核并携带OFL版权与许可证材料。

可复验命令：

```powershell
cd backend
.\mvnw.cmd '-Dtest=OpenHtmlToPdfRendererSpikeTest' test
.\mvnw.cmd '-Dtest=InvoiceRecognitionTest,InvoiceServiceTest' test
.\mvnw.cmd dependency:tree '-Dincludes=io.github.openhtmltopdf:*,org.apache.pdfbox:*'
```

当前结果：M0尖峰2项和M1汇总106项通过，覆盖中文、120行表格分页、页眉页脚、内嵌图片、PDF文本读取、体积/耗时边界、HTTP/本地文件拒绝、随包字体及既有发票识别。OpenHTMLtoPDF、PDFBox 3.0.8和Noto Sans SC已进入M1实现；生产发布仍必须携带LGPL/OFL许可证材料并完成合规复核。

官方核验入口：

- OpenHTMLtoPDF：<https://github.com/openhtmltopdf/openhtmltopdf>
- Maven Central 1.1.40：<https://central.sonatype.com/artifact/io.github.openhtmltopdf/openhtmltopdf-pdfbox>
- PDFBox下载与版本：<https://pdfbox.apache.org/download.cgi>
- PDFBox安全公告：<https://pdfbox.apache.org/security.html>
- Noto CJK官方许可证：<https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE>
- Noto Sans SC 6.1.0 Maven Central：<https://central.sonatype.com/artifact/com.helger.font/ph-fonts-noto-sans-sc/6.1.0>

## 七、M0 退出决策与M1守卫

| 决策 | 冻结结果 |
| --- | --- |
| 正式状态/预览 | 付款 `APPROVED`；结算 `APPROVED+FINALIZED`；审批中仅水印临时预览 |
| 样式 | A4纵向、文字抬头 `CGC-PMS`、无Logo、页眉页脚与页码；M2真实付款样张前可增量替换 |
| 脱敏 | 银行账号后4位、身份证前3后4、手机号前3后4；原值不进入模板模型 |
| 保留与下载 | MVP不自动删除；普通下载随业务当前权限fail-close；审计下载限SUPER_ADMIN及独立权限 |
| 角色 | ADMIN/SUPER_ADMIN维护发布；业务查询权限与文档动作权限取交集 |
| 技术 | OpenHTMLtoPDF 1.1.40、PDFBox 3.0.8、随包Noto Sans SC 6.1.0；发布包携带LGPL/OFL材料 |
| 金丝雀 | 仅dev/test/demo的单租户、`PAYMENT_APPLICATION`、管理员及真实付款角色；模板管理默认关闭 |
| 迁移 | V211 MySQL/H2镜像对称，真实MySQL V1→V211与H2专项通过；M1使用下一空闲V212 |

M1初始资源守卫：模板正文不超过512 KiB；单集合不超过200项；单张内嵌图片不超过1 MiB、总图片不超过3 MiB；PDF不超过20 MiB、100页；单次渲染15秒；单节点并发2。阈值必须配置化且超限fail-close，不得静默截断；M2代表性付款样本可向下收紧，扩大必须重新评审。

M1发布守卫：`DOCUMENT_GENERATION_ENABLED`、`DOCUMENT_PAYMENT_GENERATION_ENABLED`、`DOCUMENT_SETTLEMENT_GENERATION_ENABLED` 默认均为 `false`。只允许在dev/test/demo按全局→业务类型顺序开启新生成；关闭开关不影响历史查询和既有成功PDF下载。
