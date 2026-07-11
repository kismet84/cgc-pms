# 第32条主线 M4 构建兼容性技术债治理报告

报告日期：2026-07-09
报告类型：构建兼容性技术债归档 / Ready 入口冻结
报告边界：仅基于既有正式报告、`backend/pom.xml`、`backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java`、`backend/src/main/resources/application*.yml` 的只读证据形成裁决；本报告不修改代码、测试、配置、脚本、运行环境或 Git 状态。

## 1. 结论

通过/不通过：通过。
阻塞/非阻塞：非阻塞。
是否满足 M4 验收标准：满足。

裁决依据：

1. 第30主线正式验收已把 Mockito 动态 agent 未来兼容性警告、Spring Boot generated password 提示列为非阻塞剩余风险，而非当前发布阻塞。
2. 第32主线计划书已明确 M4 目标是把两类提示从“报告尾注”提升为独立治理项，本轮无需直接修配置。
3. `backend/pom.xml` 已存在 surefire `argLine` 与 JaCoCo `prepare-agent` 组合，说明测试运行链路对 JVM agent 行为敏感；Mockito 动态 agent 告警属于未来 JDK / 安全策略升级时可能放大的兼容性债。
4. 项目已自带 `SecurityConfig`、JWT 和 `auth.dev-login` 配置；`application-dev.yml`、`application-local.yml` 未声明 `spring.security.user.*`。因此 generated password 更接近默认安全自动配置未完全收敛的开发提示，不构成当前鉴权链路阻塞。

## 2. 提示分类

| 提示 | 当前分类 | 当前是否阻塞 | 依据 |
| --- | --- | --- | --- |
| Mockito 动态 agent 未来兼容性告警 | 构建兼容性技术债 | 否 | 现有正式报告已确认测试退出码可为 `0`；但测试链路依赖 agent 行为，未来 JDK 策略收紧时可能从 warning 升级为门禁失败 |
| Spring Boot generated password 开发提示 | 开发运行提示收敛债 | 否 | 项目实际鉴权链路使用自定义 `SecurityConfig`、JWT 与 `auth.dev-login`；提示会污染开发/验收日志，但当前未被用作真实登录入口 |

## 3. 只读证据

### 3.1 Mockito 动态 agent

1. `docs/quality/mainline-30-core-flow-regression-acceptance-2026-07-08.md` 将该提示列为 `R1` 非阻塞风险。
2. `backend/pom.xml` 中：
   - surefire 显式设置 `argLine`；
   - JaCoCo 执行 `prepare-agent`；
   - 测试运行对 agent 注入顺序和 JVM 兼容性敏感。
3. 本轮不重跑测试，直接复用既有正式报告和同日 triage 结论；当前证据足以支持“先归档、后治理”。

结论：该项不是当前代码失败，但属于应单独治理的构建兼容性入口，优先级高于普通日志噪音。

### 3.2 Spring Boot generated password

1. `docs/quality/mainline-30-core-flow-regression-acceptance-2026-07-08.md` 将该提示列为 `R2` 非阻塞风险。
2. `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java` 显示项目使用自定义 `SecurityFilterChain`、JWT、`PasswordEncoder` 与 `auth.dev-login`。
3. `backend/src/main/resources/application-dev.yml`、`application-local.yml` 已声明 `auth.dev-login.enabled=true` 与默认演示账号，但未声明 `spring.security.user.*`。
4. 在上述前提下，generated password 更像默认安全自动配置产生的开发提示，当前会干扰日志判读，但不足以推翻既有主链路验收结论。

结论：该项应作为开发运行提示收敛任务处理，不应升级为当前发布阻塞，也不应扩大为登录体系重做。

## 4. 最小治理顺序

1. 先处理 Mockito 动态 agent 兼容性。
   目标：把“未来 JDK/安全策略升级可能失败”的风险前置收敛，避免后续测试门禁被动爆雷。
2. 再收敛 Spring Boot generated password 提示。
   目标：让开发/验收日志更干净，避免把默认安全提示误判为真实鉴权入口异常。

本顺序符合第32主线计划书的最小路径：先构建兼容，再运行提示；不扩展为完整安全重构。

## 5. 后续 Ready 入口

新增 Ready Issue：`ISSUE-032-006：Mockito 动态 agent 与 Spring Boot generated password 提示治理`

进入标准：

1. 允许修改 `backend/pom.xml`、`backend/src/main/resources/application*.yml`、必要的测试/安全配置文件和正式归档文档。
2. 不允许修改生产发布配置、生产凭据、生产数据库连接。
3. 不允许把该任务扩展为完整登录体系重构、权限模型改造或大范围测试框架迁移。

验收方向：

1. 明确 Mockito 兼容性告警的最小收敛方案，并留下可复验命令。
2. 明确 generated password 提示的最小收敛方案，并证明不影响现有 JWT / dev-login 鉴权链路。
3. 若治理中发现真实安全边界回退，再单独升级分类；在出现明确反证前，本项维持非阻塞技术债。

## 6. 最终裁决

M4 已完成正式归档并形成独立 Ready 入口。
当前结论维持：非阻塞技术债，不阻塞第32主线继续推进，也不阻塞当前已通过主线的发布/收口裁决。
