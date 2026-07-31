# ISSUE-053-002 Clean-room V2 安全会话与请求核心验收报告

## 结论

- 验收结论：通过。
- 阻塞项：0。
- 上线边界：仅完成本地 Clean-room V2 M1 会话切片；未发布生产、未切换正式入口、未修改 Legacy、后端、数据库或业务事实。
- 后续队列：下一串行 Ready 为 `ISSUE-053-003`；本轮新增后续项 0、关闭后续项 0、后续项净变化 0。

## 实施范围

- 共享契约补齐认证 API、成功码、CSRF 名称、会话状态和纯类型守卫；不共享 Vue、Pinia、DOM、CSS 或 Legacy 请求层。
- V2 新增同源 `fetch` 请求核心：`credentials=same-origin`、不安全方法 CSRF、401 单飞恢复、恢复失败一次退出、错误去重与敏感信息脱敏。
- 登录、当前用户恢复、退出和内存态 Pinia 会话闭环完成；用户、角色和权限仅来自后端 `userinfo`，退出清理注册缓存。
- 路由新增公开登录页和受保护会话页；未登录访问 `/v2/session` 返回 `/v2/login?redirect=/session`，外部重定向和登录循环被拒绝。
- 本地 Vite 代理经用户追加授权纳入本 Issue：移除代理到 Spring 的浏览器 Origin，避免同源代理被误判为 CORS；启用 `xfwd`，避免全部 5174 请求共用容器 IP 并触发整站登录锁定。
- `@cgc-pms/frontend-contracts` 改为 workspace link，确保宿主与 Docker 开发容器读取同一当前契约，生产构建路径不变。

## 安全复核

- token 不写入 `localStorage`、`sessionStorage`、IndexedDB、URL、日志或错误提示；生产源码静态扫描无上述存储和 console 写入。
- V2 主动拒绝认证敏感查询参数、`Authorization` 和 `X-Refresh-Token`；后端若在 JSON 暴露非空 token/refreshToken，前端 fail-close。
- CSRF 仅附加到 POST/PUT/PATCH/DELETE 等不安全方法；GET/HEAD/OPTIONS 不附加。
- 并发 401 单飞测试证明只调用一次 refresh；失败只触发一次会话失效通知，不形成重试风暴。
- ADMIN 和普通项目经理会话均完成登录态恢复、刷新后恢复、CSRF 退出和退出后保护路由拦截；未知身份保持匿名且密码字段清空。
- 实时 E2E 每类身份使用独立保留网段客户端 IP，避免后端 5 次失败锁定造成样本互相污染；不删除或重置限流数据。

## 自动化证据

- Ready lint：通过，内容哈希 `756d53b513871ffaf3503f72994374ba90192648affbcd7f97257ef92f089768`。
- `pnpm test:unit`：7 文件、25 项通过。
- `pnpm type-check:contracts`：通过。
- `pnpm type-check`：通过。
- `pnpm lint:check`：通过，0 error、0 warning。
- `pnpm check:boundary`：通过，扫描 43 个 V2 文件和 4 个契约文件。
- `pnpm build`：通过，70 模块；CSS 19.09 kB、应用 JS 17.92 kB、Vue vendor 93.89 kB。
- 实时本地 Edge 认证专项：5 项通过，覆盖未登录、ADMIN、`demo.manager`、未知身份、桌面与移动响应式。
- 模拟认证专项：2 项通过，覆盖匿名重定向和登录成功链。
- `git diff --check`：通过。

## 浏览器与视觉核对

- 视觉源：用户选定的新版经营驾驶舱截图；未生成新视觉方向。
- 色彩：沿用高明度浅蓝画布、主品牌蓝和深色正文层级。
- 排版：保留大标题、短说明、技术边界和白色任务卡的层级关系。
- 组件：输入框、主按钮、边框、圆角和浮层阴影全部来自 `ISSUE-053-001` 令牌/组件。
- 密度：1440×900 无横向溢出，登录卡和品牌说明保持清晰留白。
- 响应式：390×844 隐藏次要技术说明，保留品牌、主标题、完整表单和触控按钮，无横向溢出。
- 控制台：应用 warning/error 与 pageerror 为 0；匿名 `userinfo` 401 属于预期认证探测。浏览器默认 `/favicon.ico` 404 与本切片功能、安全和视觉目标无关，按无明确价值关闭，不创建 backlog。

## 失败分类与修复

- 初始 403：`runtime_config`，Vite 代理转发 Origin 后被 Spring 视为跨源写请求；本轮修复并复验。
- 后续 429：`test_isolation`，代理未转发客户端 IP且负向样本共用限流键；启用 `xfwd`、隔离实时样本后复验通过。
- Playwright 默认 Chromium/Chrome 不存在：`tool_config`；改用工作站现有 Edge 通道，不下载浏览器。
- Lint 长时间无输出：本轮 Playwright HTML/trace 产物被 ESLint 扫描；终止本轮遗留 ESLint 子进程并移出前端扫描目录，复跑通过。

## 回滚

- 回退 V2 认证契约、请求/会话服务、认证页面、路由、测试及 Vite 本地代理配置即可。
- Legacy、后端、数据库和正式入口未改变，无数据回滚步骤。
