# P2 中危安全修复批次归档

**项目名称：** CGC-PMS  
**归档日期：** 2026-07-02  
**归档范围：** 仅记录本轮 P2 中危安全修复批次结果，不覆盖全量审计结论。  
**批次结论：** 已关闭，非阻塞，可提交

## 本批已修项

- **VUL-005**：CI 工作流中的明文 `root` 密码已收敛，`ci.yml` 改为通过独立 CI 变量注入 MySQL 账号、密码和 healthcheck 凭据，避免把固定 root 密码直接写进 workflow。
- **VUL-010**：`@Async` 缺省线程池配置已补齐，新增统一的 `AsyncConfig` 与 `AsyncConfigTest`，线程池核心参数、队列容量、线程名前缀和异常处理器均有显式配置与验证。
- **VUL-012**：前端登录页不再硬编码默认管理员用户名，登录表单默认值改为空，避免页面一打开就暴露固定账号。
- **VUL-013**：前端用户信息持久化改为只保存认证所需字段，不再把 `realName`、`phone`、`email`、`avatar` 这类 PII 原样写入 `localStorage`。
- **VUL-015**：个人资料页修改密码前增加了客户端强度校验，至少 8 位且必须同时包含字母和数字，并补了对应回归测试。
- **VUL-016**：审批状态机 TOCTOU 竞态已按前一轮修复口径关闭，当前批次不再保留阻塞项。
- **VUL-023**：仪表盘 N+1 查询已按前一轮修复口径关闭，当前批次不再保留阻塞项。

## 复核结论

- **VUL-014**：复核后判定为误报，不纳入本批代码修复。
- **VUL-009**：SSL 私钥文件是否继续留在仓库属于运维/仓库治理项，不在本批代码修复范围内，已单独列为后续处理事项。

## 剩余风险

- 本批已经收敛的是代码层安全面问题，但 `VUL-009` 仍需要运维侧或仓库治理侧继续处理，不能当作代码修复完成。
- CI 密码收敛后，仍需确认 GitHub Actions 侧 secret 配置与仓库默认变量一致，否则 workflow 运行会受环境缺失影响。
- 前端 PII 不再落盘，但内存态用户信息仍会在页面刷新后由接口补回，需持续确认接口返回字段与页面展示一致。

## 验证命令

- `git grep -n "username: 'admin'\|realName\|phone\|email\|avatar" frontend-admin/src/pages/login frontend-admin/src/stores frontend-admin/src/types`
- `git grep -n "MYSQL_ROOT_PASSWORD\|MYSQL_USER\|MYSQL_PASSWORD\|CI_MYSQL" .github/workflows/ci.yml`
- `.\backend\mvnw.cmd "-Dtest=AsyncConfigTest,ProfileControllerTest" test`
- `cd frontend-admin; pnpm test:unit -- --run src/pages/login/__tests__/index.test.ts src/pages/profile/__tests__/index.test.ts src/stores/__tests__/user.test.ts`

## 提交范围 / 排除项

- **提交范围：** 仅记录本批 P2 中危安全修复结果，并配合当前已完成的代码变更一起提交。
- **排除项：** 不改业务代码，不新增架构抽象，不提交 `output/`、`scripts/__pycache__/` 和样本 PDF。
- **结论边界：** 本文件只作为批次关闭归档，不代表全量审计问题已经全部清零。
