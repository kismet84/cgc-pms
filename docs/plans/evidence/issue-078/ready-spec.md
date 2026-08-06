# ISSUE-078-001 ReadySpec

## 权威分母

- 动态业务类型来源：当前租户 `wf_template` 中 `enabled=1 AND deleted_flag=0` 的去重 `business_type`，不是前端枚举，也不是 Handler 常量全集。
- 本地 tenant 0 当前分母为 28。`COST_SUBJECT_MAPPING`、投标成本转入/冲销、财务费用分摊/冲销共 5 类没有业务回调 Handler，但审批模板有效且已有业务消费，不能从分母删除；`CONTRACT_REVENUE` 有 Handler 但当前租户未启用，不计入本地分母。
- `WorkflowBusinessHandlerRegistry` 仅决定状态回调，不是启用审批目录。
- `PAYMENT` 是历史文档类型，不能隐式冒充审批类型 `PAY_REQUEST`；兼容迁移必须显式、可测试。

## Provider 契约

每个启用类型必须唯一提供：

1. `businessType`、展示名、`schemaVersion`；
2. 显式语义字段目录及集合上下文；
3. 同契约的结构化示例数据；
4. 复用领域详情 Service 的真实对象读取与权限边界；
5. 不含租户、删除标记、内部外键、审批记录或流程字段的快照；
6. 至少一个目录/快照一致性与越权拒绝测试；
7. 默认模板或明确 `NONE` 登记。

目录接口必须返回全部启用类型及 `providerReady`。缺 Provider 必须 fail-close；G1 和 G5 要求本地分母覆盖率 100%。

## 画布与版本契约

- 画布唯一持久事实为 `design_schema`；坐标/尺寸统一毫米，A4 仅支持纵向 210×297 与横向 297×210。
- 服务端校验边距、元素、集合表格、字段上下文和越界，再生成受限 HTML 与字段清单；前端不提交权威 HTML/manifest。
- 旧版本 `design_schema=null` 继续走源码兼容路径；发布版本三份事实均不可变。
- 实时 HTML 与 PDF 使用同一 Provider 快照、编译器和模板引擎；前端只在 sandbox iframe 展示服务端 HTML。
- 集合只通过明细表使用；禁止多层循环、脚本、外部 URL、外部字体、任意表达式和模板内金额计算。

## 权限

- 类型/目录：`document:template:query`。
- 画布保存：`document:template:edit`。
- 发布/停用/默认绑定：保留既有权限。
- 真实预览：同时要求模板编辑、文档生成和业务对象读取；拒绝时不回退示例数据。
- Provider 必须经既有领域 Service 或业务对象授权器执行租户、项目、对象检查。

## 验收

- 28/28 启用审批类型 Provider 就绪，目录路径与示例/真实快照一致。
- `SUB_MEASURE` 完整主详情与 `items[]` 作为参考实现，字段不含 `workflow.*`、`approvalRecords.*` 或内部 ID。
- V287 在 MySQL、H2 active 和 H2 legacy 完成升级、回读和旧模板兼容。
- A4 纵横切换、拖拽、缩放、属性编辑、集合表格、越界阻断、保存重开、HTML/PDF 一致性通过。
- 后端/前端最小定向后再跑仓库规定聚合门禁；运行态和浏览器仅在本地验证。
