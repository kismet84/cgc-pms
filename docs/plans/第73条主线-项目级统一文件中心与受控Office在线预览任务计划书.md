# 第73条主线：项目级统一文件中心与受控 Office 在线预览

**Goal:** 在 `master@ac6bd6c37c99888021ca0e7037a8f2a03d465df2` 基线上，新增项目级统一文件管理入口，使用户按项目数据范围查看项目资料库和既有业务附件，并完成“编号预览 → 名称/分类 → 服务端版本 → 提交人/更新时间 → 下载”的最小闭环；资料库文件可新建和追加版本，业务证据继续由原业务模块维护。 **Architecture:** 复用现有 `sys_file`、`FileService`、`BusinessObjectAuthorizer`、`ProjectAccessChecker`、MinIO、ClamAV、下载审计、对象任务、系统字典、投标版本链、V2 导航和附件服务；新增的目录与版本关系只保存逻辑文档元数据及 `sys_file.id` 引用，不保存第二份原文件、不建立第二文件主表或对象桶。PDF/图片沿用原对象短期预览，DOCX/XLSX/PPTX 由无外网、无存储凭据的隔离 LibreOffice 转换容器生成同桶派生 PDF；不建设网盘、Office 编辑、通用文档协作、旧版 Office 支持或外部预览平台。

> 日期：2026-08-05
> 实施基线：`origin/master@b718e50dfda0c0f5d682b85f4df3ec528e50aaf7`
> 状态：`IMPLEMENTED / G0-G5_LOCAL_PASSED / GIT_DELIVERY_IN_PROGRESS`
> 唯一问题源：`ISSUE-073-001`（已关闭；本计划为唯一实施载体）
> 优先级：`P2 / blocking:false`
> 工作区：原67个未提交文件已完整隔离到可恢复stash；73～75在独立任务分支实施
> 本轮授权：实现73～75、完成本地验收与治理收口，并执行受保护Git推送、PR、合并和分支清理
> 未授权：非本地环境测试、生产数据库、生产部署、强推或绕过保护
> 失败分类：migration、权限、对象存储、转换、测试、浏览器或工具失败先按统一失败分类契约归类；未分类不得进入下一阶段或判定业务缺陷

## 1. 产品决策与既有边界重开

本计划冻结以下用户决策：

| 决策 | 已选方案 |
| --- | --- |
| 模块形态 | 项目级统一文件中心：资料库文件与既有项目业务附件统一展示 |
| 维护边界 | 统一查看、分域维护；中心只维护资料库文件，业务附件回原模块维护 |
| 项目视图 | 默认展示全部有权项目，可按项目筛选；新建时必须选择单一项目 |
| 文件编号 | `FILE-{项目编码}-{yyyyMMdd}-{三位序号}`，服务端自动生成 |
| 版本 | `V1、V2、V3...` 整数递增，默认选择最高版本 |
| 分类 | 可维护系统字典；历史附件按业务/文档类型映射，未知归“其他” |
| 在线预览 | PDF、图片直接预览；DOCX、XLSX、PPTX 隔离转换为 PDF |
| 旧 Office | DOC、XLS、PPT 继续禁止上传，不纳入本轮 |

第71条将“独立文件中心”和“Office 全格式预览”列为非目标。本次用户明确改变产品边界，因此第73条只重开以下两项：

1. 允许新增项目维度的统一目录页面和最小逻辑版本关系。
2. 允许为现有白名单内 OOXML 文件生成受控 PDF 预览派生物。

第71条已建立的 `sys_file` 单一文件事实、租户对象键、病毒扫描、业务授权、不可变证据、下载审计、对象补偿和恢复边界全部保留；不得借第73条重建文件底座或绕过原业务状态机。

## 2. 范围与非目标

### 2.1 范围

- V2“项目履约 → 项目管理 → 文件中心”页面、导航、权限和项目范围。
- 文件编号、逻辑名称、分类、版本选择、提交人、更新时间、预览和下载读模型。
- 资料库文件新建、首版上传和新版本追加；服务端生成编号和版本。
- 项目关联的 `sys_file` 历史纳管；投标真实版本链保留，其他附件不猜测合并。
- `project:file:query`、`project:file:manage` 与来源业务权限的交集授权。
- PDF/图片直接预览；DOCX/XLSX/PPTX 隔离转换、缓存、重试和清理。
- MySQL/H2 等义 migration、历史纳管预检、后端/前端测试、真实 MinIO/ClamAV/LibreOffice/浏览器验收。
- 计划、Backlog、项目地图和质量报告在文件所有权锁定后的治理回写。

### 2.2 非目标

- 第二张通用文件主表、第二对象桶、文件微服务、网盘、知识库、电子签章、OCR或全文检索平台。
- Office 在线编辑、多人协作、批注、修订、格式无损承诺或 Microsoft Office 服务集成。
- DOC/XLS/PPT、宏文档、加密/密码文档、压缩包内容预览或任意格式转换。
- 按原文件名自动合并历史版本、猜测项目归属、改写投标版本号或业务终态。
- 在文件中心直接删除、改名、改分类或替换既有业务证据；本轮不提供删除入口。
- 扩大 20MB 上传上限、分片上传、断点续传、CDN或长期公开URL。
- 把转换容器暴露公网、赋予数据库/MinIO凭据或把文件发送给第三方。
- 生产历史纳管、生产存量复扫、生产部署和发布；`REL-FILE-RESCAN`继续独立阻塞。
- 强推、绕过分支保护、生产发布或第72条治理文件改写。

## 3. 页面与交互契约

### 3.1 页面

- 路径：`/project/files`。
- 导航：加入“项目履约 → 项目管理”，与“项目列表”并列。
- 默认范围：当前用户有权访问的全部项目；可切换单个项目。
- 筛选：项目、编号/名称关键词、分类；服务端分页，默认每页20条。
- 新建按钮：仅 `project:file:manage` 可见；未选择单个项目时点击后必须先选择项目。

### 3.2 表格

| 列 | 行为 |
| --- | --- |
| 编号 | 链接按钮；打开当前所选版本的安全预览 |
| 名称 | 资料库逻辑名称；历史普通附件使用原文件名；投标使用既有逻辑名称 |
| 分类 | 显示 `file_category` 字典标签 |
| 版本 | 选择器按版本号降序，默认最高版本；切换不写服务端 |
| 提交人 | 随当前所选版本变化，显示上传人真实姓名 |
| 最后更新日期 | 随当前所选版本变化，取该版本上传时间，不取病毒复扫时间 |
| 下载 | 下载当前所选版本；点击时重新取短期URL |

提交人无法解析时：`created_by`为空显示“历史导入”，用户已删除显示“用户#{id}”。

### 3.3 操作

- “新建文件”：项目、名称、分类、文件必填；成功后服务端回读并显示 V1。
- “上传新版本”：只对 `MANAGED` 资料库文件开放；业务来源行不显示该操作。
- 业务附件：保持只读；页面返回来源类型和可选原业务路由，前端可显示“由原业务模块维护”。
- 非 `CLEAN` 文件：预览和下载均禁用，并显示服务端扫描状态。
- 预览失败：关闭预先打开的空白页，显示稳定错误；不得把签名URL写入store、URL参数或持久缓存。

## 4. 数据与服务端契约

### 4.1 最小目录模型

新增两张逻辑关系表，均不保存原文件字节、bucket或原对象路径：

1. `project_file_catalog`
   - `id、tenant_id、project_id、file_code、display_name、category_code`
   - `source_kind=MANAGED|BUSINESS`
   - `source_business_type/source_business_id`
   - `maintain_mode=MANAGED|READ_ONLY`
   - BaseEntity审计字段
2. `project_file_version_link`
   - `id、tenant_id、catalog_id、version_no、sys_file_id`
   - `source_version_type/source_version_id`
   - `preview_status=PENDING|PROCESSING|READY|FAILED|UNSUPPORTED`
   - `preview_storage_path、preview_error_code、preview_updated_at`
   - BaseEntity审计字段

数据库约束：

- `project_file_catalog`唯一键：`tenant_id + project_id + file_code`。
- 业务索引唯一键：`tenant_id + source_kind + source_business_type + source_business_id + file_code`。
- 版本唯一键：`tenant_id + catalog_id + version_no`。
- `sys_file`唯一引用：同一 `sys_file.id` 只能进入一个中心版本记录。
- `tenant_id + sys_file_id`复合外键复用现有 `sys_file`租户唯一约束。
- 不保存“当前版本ID”；最新版本由服务端按 `version_no DESC`确定，避免双重事实。

### 4.2 编号和版本

- 编号前缀使用项目权威 `project_code`：`FILE-{projectCode}-{yyyyMMdd}-`。
- 复用 `CodeGenerationService` 生成三位序号；数据库唯一键处理并发冲突，最多按现有模式重试三次。
- 单项目单日超过999条时返回稳定错误 `PROJECT_FILE_CODE_EXHAUSTED`，不得扩位后静默改变格式。
- 新版本事务内锁定目录行，读取最大版本号并加一；客户端不提交版本号。
- 首版、版本关系和 `sys_file`写入必须处于同一数据库事务；失败依赖现有对象回滚补偿，不能留下空目录或孤儿对象。

### 4.3 分类

复用系统字典新增 `file_category`，首批代码：

`BID、CONTRACT、DRAWING、TECHNICAL、CONSTRUCTION、QUALITY_SAFETY、PROCUREMENT、FINANCE、APPROVAL、OTHER`。

- 新建资料库文件必须选择当前租户可用分类。
- 字典停用只禁止新建和新版本选择，不改写历史标签事实。
- 既有附件按冻结映射表从 `business_type + document_type`导出；无映射使用 `OTHER`。

### 4.4 API

- `GET /project-files?pageNo&pageSize&projectId&keyword&categoryCode`
  - 返回服务端分页；`projectId`为空表示全部有权项目。
  - 每项包含目录字段、维护模式、来源提示和按版本号降序的轻量版本选项。
- `POST /project-files`
  - `multipart/form-data`：`projectId、name、categoryCode、file`。
  - 原子创建目录、V1和 `sys_file`。
- `POST /project-files/{catalogId}/versions`
  - `multipart/form-data`：`file`。
  - 仅 `MANAGED`目录可调用；服务端生成下一版本。
- `POST /project-files/versions/{versionId}/preview`
  - 幂等返回 `READY/PROCESSING/FAILED/UNSUPPORTED`、短期URL、稳定错误和建议重试秒数。
  - PDF/图片直接签原对象；OOXML就绪后签派生PDF；首次历史OOXML预览可创建持久任务。
- 下载继续调用 `GET /files/{sysFileId}/url`，不新增平行下载实现。
- 分类继续使用现有字典读取接口。

## 5. 权限、租户与业务状态

1. 页面进入、分页和详情必须有 `project:file:query`。
2. 新建和追加资料库版本必须有 `project:file:manage`，并通过 `ProjectAccessChecker`。
3. 全部项目视图只查询当前用户数据范围内项目；项目范围进入SQL或服务端权威查询，禁止分页后过滤。
4. 业务附件展示还必须具备来源业务读取权限；只有项目权限而无合同/付款/发票等权限时，不得在中心看到对应文件元数据。
5. 预览和下载每次重新校验中心权限、项目范围、来源业务权限、租户和 `CLEAN`状态。
6. 跨租户目录、版本、`sys_file`或预览对象统一按不存在处理。
7. 投标定版、付款归档、生成单据及其他不可变证据在中心保持 `READ_ONLY`；中心不得追加、删除或替换。
8. 资料库版本一经上传即不可覆盖；本轮无删除接口，错误版本通过追加新版本纠正。
9. 列表不返回 `bucket_name、storage_path、file_name`或签名URL。

## 6. 历史纳管

历史纳管先预览、后分批应用；生产应用另行授权。

### 6.1 归属解析

- 直接项目：`PROJECT`及业务表直接含 `project_id` 的合同、收料、采购、付款、费用、分包、结算、变更、日报、收入计量、质量安全、供应商、技术和收尾对象。
- 间接项目：发票经付款链、质量问题经检查记录、供应商报价经招采事件、RFI回复经RFI、开工准入经项目。
- 条件项目：`BID_COST`和`CASH_JOURNAL`只有 `project_id`非空时纳管。
- 非项目主数据：`PARTNER、MATERIAL`不进入项目中心。
- 应有项目但无法解析、父对象缺失或租户冲突：跳过并进入异常清单，不硬分配。

### 6.2 版本规则

- `bid_document_version`是唯一现有通用检查中确认的真实业务版本链；按其 `logical_name、version_no、sys_file_id`原样纳管。
- 付款证据和生成PDF按不可变单版本资料纳管；`generation_no`不是文件版本号。
- 其他 `sys_file`每条独立形成一个目录和V1；禁止按原文件名、内容哈希或时间猜测版本关系。
- 历史编号按项目、原创建日期、`created_at + sys_file.id`稳定排序生成；超过999条的项目日期批次整体停止并报告。

### 6.3 对账

- 复用 `FileMaintenanceService` 增加只读目录预览、批次应用和对账能力。
- 对账覆盖：未纳管项目文件、重复 `sys_file`引用、版本断号、错误项目、跨租户、来源关系缺失、预览派生物缺失和孤儿派生物。
- 业务表和 `sys_file`始终是原文件与业务归属权威；中心索引漂移时先修复索引，不改写业务事实。

## 7. Office 受控预览

### 7.1 转换边界

- 转换器为独立 LibreOffice headless 容器，只接收内部网络请求并返回PDF字节。
- 容器非root、只读根文件系统、临时目录独立、禁外网、无数据库/MinIO/用户凭据。
- 后端读取已授权且 `CLEAN` 的原对象，调用转换器，再把PDF写回现有MinIO bucket的租户隔离派生路径。
- 转换容器禁用宏和外部链接执行；网络隔离作为第二道阻断，不信任文档内任何外部引用。
- 后端对输出重新校验PDF格式、计算SHA-256并执行ClamAV扫描；只有输出同样为 `CLEAN` 才写入 `READY`。
- 派生路径包含 `tenantId + sysFileId + 内容标识`；不创建 `sys_file`记录，不被业务附件列表误识别。
- 默认单任务超时60秒、1 CPU、1GB内存、100MB输出上限；字体包、LibreOffice版本和容器digest在G0冻结，全部资源参数在prod缺失时失败关闭。

### 7.2 任务与生命周期

- 复用持久对象任务机制增加幂等 `PREVIEW_CONVERT`任务，不引入消息中间件。
- 新上传OOXML在事务提交后排队；历史OOXML首次预览按需排队。
- 同一 `sys_file + 内容标识`最多一个活动任务；失败记录稳定错误，可人工或受限自动重试。
- PDF/图片不转换；其他格式返回 `UNSUPPORTED`。
- 删除原文件或中心版本失效时，派生PDF进入现有可重试删除链；对象对账必须发现孤儿派生物。
- LibreOffice转换只承诺可读预览，不承诺与Microsoft Office像素级一致；原文件下载始终保留。

### 7.3 浏览器行为

- 点击编号先同步打开 `about:blank`并清除 `opener`，再请求预览，避免弹窗拦截和反向标签劫持。
- `READY`跳转短期URL；`PROCESSING`在原页面轮询最多30秒；超时提示稍后重试。
- `FAILED/UNSUPPORTED`关闭空白页并显示错误；下载按钮保持独立。
- 预览签发同样发布审计事件，事件语义为“授权并签发链接”，不伪称用户已读取全部字节。

## 8. G0～G5门禁、阶段与实施包

| 门禁 | 必须证据 | 未通过动作 |
| --- | --- | --- |
| `G0` 基线锁定 | 分支/HEAD、脏改归属、下一migration、项目/业务类型映射、历史数量、LibreOffice镜像版本/许可证/digest、资源上限、治理文件所有权 | 保持计划状态，不改代码或登记冲突文件 |
| `G1` 契约完整 | 页面字段、编号、分类、版本、来源维护、权限矩阵、API、预览状态、历史映射和错误码冻结 | 不得进入实施 |
| `G2` 数据与迁移 | MySQL/H2等义schema，回填预览，租户外键、并发唯一、异常清单、备份/回滚成立 | 停止写入，修复根因 |
| `G3` 服务端闭环 | 新建/V1/追加、权限交集、下载审计、历史索引、转换任务、清理和对账全部服务端成立 | 回到共享服务修复 |
| `G4` 运行态与浏览器 | 真实MySQL、MinIO、ClamAV、转换容器、全部项目/单项目、正负角色、版本切换、预览/下载、DOM/console | 分类失败，停止扩大 |
| `G5` 正式收口 | 依赖扫描、目标/全量门禁、同HEAD CI、计划/Backlog/地图/质量报告回写、风险/回滚、零悬空 | 保持未完成 |

### M0：G0/G1事实与治理锁定

- 等第72条作者提交或隔离治理改动，再登记 `ISSUE-073-001`、计划索引、当前焦点和项目地图。
- 锁定实施分支、HEAD、数据库、租户、运行URL、对象桶、扫描器和转换镜像完整digest。
- 生成所有现存 `businessType`到项目、来源权限、分类、维护模式的冻结矩阵。
- 对历史文件、投标版本、付款证据、生成文件、项目缺失和租户冲突做只读统计。

### M1：G2目录schema与历史预览

- 新增下一空闲 MySQL/H2 等义migration；不得修改V281或任何已应用migration。
- 建立目录、版本、复合外键和唯一约束；先空表上线。
- 提供历史纳管预览、游标批次、幂等应用和对账；本地dev/test数据先金丝雀。

### M2：G3服务端文件中心

- 新增目录分页、新建、追加版本和预览接口。
- 将 `PROJECT_FILE`资料库类型纳入 `BusinessObjectAuthorizer`，绑定项目和新权限。
- 通用附件写入、投标版本建立、付款/生成关系完成后在同一业务事务维护中心索引。
- 删除/作废路径同步失效索引并清理派生物；原业务不可变规则优先。

### M3：G3 Office转换

- 增加隔离转换容器、内部客户端、持久任务处理、状态回读和同桶派生对象。
- 验证超时、崩溃、恶意/损坏OOXML、输出过大、MinIO失败和重复任务。
- 转换不可用只影响Office预览，不得使原文件下载或PDF/图片预览不可用。

### M4：G4 V2交互

- 新增导航、页面、共享契约和服务调用。
- 完成全部项目默认视图、项目/关键词/分类筛选、服务端分页、版本默认/切换、预览、下载、新建和追加版本。
- 业务附件只读；无来源权限、无项目权限、非CLEAN、转换失败和窄视口均有明确状态。

### M5：G5验证与收口

- 运行风险相称的后端、前端、migration、容器和真实浏览器门禁，同一内容本地全量门禁最多一次。
- 形成 `docs/quality/` 实施验收报告；登记/关闭唯一问题源并统计零悬空。
- 另获Git授权后才执行同HEAD CI、push或PR；本地通过不等于目标环境可发布。

## 9. 验收矩阵

| 场景 | 通过标准 |
| --- | --- |
| 全部项目 | 只返回用户有权项目，服务端分页总数正确，无分页后过滤 |
| 来源业务权限 | 有项目权限但无合同/付款等权限时，对应文件不存在于结果和预览接口 |
| 编号并发 | 同项目同日并发新建编号唯一、连续或有可解释间隙，无重复 |
| 版本并发 | 两个并发追加得到不同整数版本；失败事务不占用可见版本 |
| 默认版本 | 接口和页面均默认最高 `version_no`，不按时间或数组顺序猜测 |
| 版本切换 | 提交人、更新时间、预览和下载目标同步切换且不写服务端 |
| 投标版本 | 逻辑名、版本号、当前态和不可变语义与原投标模块一致 |
| 历史普通附件 | 每条独立V1，不按同名合并；无法解析项目的记录进入异常清单 |
| 分类 | 新建只能选择启用字典项，未知历史映射为OTHER |
| 扫描状态 | 非CLEAN文件无法预览、转换或下载，跨租户按不存在处理 |
| PDF/图片 | 点击编号取得短期inline URL，签发成功/失败均审计 |
| DOCX/XLSX/PPTX | 新文件异步转换、历史首次按需转换、READY后显示PDF，原文件可下载 |
| 转换故障 | 超时、容器不可用、坏文件、输出过大均稳定失败并可重试，无孤儿对象 |
| 不支持格式 | 不生成任务，显示不支持预览，下载仍按原规则 |
| 响应披露 | 列表无bucket、storage path、内部文件名和签名URL |
| 浏览器 | 桌面/窄视口表格可用，最终URL/DOM/console无未处理错误 |

建议验证命令在实施时按实际新增测试类补齐：

```powershell
cd backend
.\mvnw.cmd -Dtest=FileServiceTest,FileControllerAuthorizationTest,BusinessObjectAuthorizerTest,FileMaintenanceServiceTest,*ProjectFile*Test test
.\mvnw.cmd verify

cd ..\frontend-admin-v2
pnpm exec vitest run tests/unit/business-attachment-panel.test.ts tests/unit/router.test.ts tests/unit/navigation.test.ts tests/unit/project-file-center.test.ts
pnpm run type-check
pnpm run lint:check
pnpm run build
pnpm run check:route-ledger

cd ..
pwsh -NoProfile -File scripts/codex-autopilot/test-mainline-owner-flow.ps1 -PlanPath "docs/plans/第73条主线-项目级统一文件中心与受控Office在线预览任务计划书.md" -Profile HighRisk
```

## 10. 金丝雀、风险与回滚

金丝雀顺序：静态契约 → schema空表 → 单项目新资料库V1 → V2追加 → PDF/图片预览 → 单个DOCX转换 → XLSX/PPTX → 单个历史普通附件 → 单条投标版本链 → 全部项目视图 → 多角色负样本 → 小批历史纳管 → 全量回归。任一步首次不通过，停止扩大。

| 风险 | 触发 | 回滚/恢复 | 禁止动作 |
| --- | --- | --- | --- |
| 目录索引漂移 | 业务文件有记录但中心缺失或错误 | 禁用中心写操作，运行只读对账并修复索引 | 改写原业务关系迎合索引 |
| 越权聚合 | 无来源权限用户看到文件元数据 | 关闭文件中心路由/权限，保留原业务下载链 | 前端隐藏代替服务端授权 |
| 版本冲突 | 重复版本号或错误默认版本 | 停止追加，按唯一约束和源关系恢复 | 手工改号覆盖历史 |
| 历史误合并 | 不同文件被并成版本链 | 回滚该批索引并按一文件一V1重建 | 按文件名继续猜测 |
| 转换资源耗尽 | 超时、OOM、队列积压 | 关闭Office预览开关，保留下载；限流后恢复 | 扩大资源或绕过限制继续跑 |
| 派生物泄露 | 跨租户路径或长期URL | 停止签发、删除受影响派生物、审计访问 | 公开bucket或复用他租户缓存 |
| 回滚应用 | 新版本出现业务回归 | 关闭中心和转换开关，回退应用；保留新增表和索引供前滚 | 直接删除schema或原文件 |

## 11. 完成定义与治理登记

完成必须同时满足：

- `ISSUE-073-001`已在唯一问题源登记并通过G0～G5；无重复Backlog。
- 页面字段、默认全部项目、筛选、编号预览、分类、整数版本、提交人、更新时间和下载全部按契约成立。
- `sys_file`、业务关系和MinIO原对象仍是权威；中心无第二文件账、无跨租户披露、无业务状态绕过。
- 历史投标版本保真，其他附件不猜测合并；异常记录有清单和裁决。
- PDF/图片和OOXML真实预览、失败恢复、派生物清理及审计通过。
- MySQL/H2、MinIO、ClamAV、LibreOffice、V2和真实浏览器证据绑定同一HEAD。
- 计划、Backlog、项目地图、质量报告和零悬空统计完成；`REL-FILE-RESCAN`保持独立目标环境门。

实施登记裁决：原冲突文件已隔离；本计划、计划索引、当前焦点、项目地图和质量报告已串行登记。`ISSUE-073-001`由本计划唯一承接，G5收口时关闭，不写入只保存未关闭事项的`current-issues.json`。

实施证据：V282 MySQL/H2、目录/版本/权限/历史投影、真实Office转换和V2页面均已落地；后端全量324份报告、2540项测试、0失败/错误、15跳过、JaCoCo分支60.237%，前端70文件469项测试及完整构建门通过。最新JAR容器`UP/healthy`，本地MySQL已到V284。

零悬空统计：计划周期新增1、关闭1、净变化0；独立复核发现并关闭历史统一排序、部分投标链补投影、删除与转换竞争孤儿清理3项，净变化0；无重复或无载体遗留项。
