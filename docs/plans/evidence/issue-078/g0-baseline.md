# ISSUE-078-001 G0 基线

> 锁定时间：2026-08-06
> 分支：`codex/mainline-78-document-designer`
> 实施前 HEAD：`6caaf895d7996b48b778a503f81ec8031a39ea90`

## 目标与边界

- 目标：完成全部启用审批业务类型的自描述文档 Provider、完整语义字段、A4 可视化画布、服务端编译、HTML/PDF 一致预览和版本治理。
- 复用：既有审批模板、领域详情 Service、`BusinessObjectAuthorizer`、受限模板引擎、OpenHTMLtoPDF、V2 组件和权限体系。
- 非目标：审批意见/节点/签字流、实体或数据库反射、脚本/外部资源、第三方设计器、Office/签章、非本地环境。
- 回滚：关闭画布入口并继续读取 `design_schema=null` 的旧源码模板；不删除模板、默认绑定或生成历史。

## 工作区与代码地图

- 初始工作区仅有未跟踪的第78条计划书；第77条已由 PR #399 合并到当前 `origin/master`。
- 已创建独立任务分支；现有代码地图三件套和计划书均归属本任务。
- `docs/codemap` 已重建并绑定实施前 HEAD；JSON 与 HTML 内嵌数据一致，当前 27 节点、61 边。
- 地图未含文档模板精确调用链；源码补充确认 `Controller -> Template/Generation Service -> Provider -> RestrictedTemplateEngine -> OpenHTMLtoPDF`。本轮改变 API、数据库和主要数据流，G5 前必须补图并再次重建。

## 本地环境

- 仅本地 dev 环境；backend `http://127.0.0.1:8080/api` health=`UP`，frontend `http://127.0.0.1:5173` 可达。
- 实际数据库：MySQL `cgc_pms_demo_ui_20260728`，Flyway 已到 V286；H2/MySQL 源码下一迁移号为 V287。
- 租户：启用审批模板与组织数据均为 `tenant_id=0`；dev-login 用户为 `admin`。
- 文档生成总开关及五个历史类型开关均关闭；G4 刷新后只在本地受控验收期间启用。
- 运行容器代码早于任务分支属于预期基线；G4 使用运行态刷新流程重建，不据此修改产品逻辑。

## G0 裁决

`PASS`。分支、工作区、数据库、租户、URL、开关、迁移号、代码地图与回滚边界均已锁定，可进入 G1。
