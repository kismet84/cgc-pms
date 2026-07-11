# ISSUE-032-006 构建兼容性与运行提示治理报告

报告日期：2026-07-09  
Issue：ISSUE-032-006：Mockito 动态 agent 与 Spring Boot generated password 提示治理  
结论：通过  
阻塞：否

## 1. 治理范围

本轮仅处理 Ready Issue 声明的两类提示：

1. Mockito 动态 agent 未来兼容性告警。
2. Spring Boot generated password 开发/测试运行提示。

未修改 Flyway migration、生产发布配置、生产凭据、生产数据库连接，也未重构 JWT / dev-login 鉴权链路。

## 2. 变更摘要

1. `backend/pom.xml`
   - 在 Maven Surefire 现有 `argLine` 中显式加入 Mockito javaagent：
     `-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar`
   - 保留原 JaCoCo `@{argLine}`、`--add-opens` 与内存参数。

2. `backend/src/main/resources/application-dev.yml`
   - 为 dev profile 配置 `spring.security.user.name/password`，避免 Spring Security 自动生成随机密码提示。

3. `backend/src/main/resources/application-local.yml`
   - 为 local profile 配置 `spring.security.user.name/password`，覆盖本地开发与多数测试启动场景。

4. `backend/src/test/resources/application.yml`
   - 为测试 classpath 默认配置 `spring.security.user.name/password`，避免 Spring Boot Test 启动时输出 generated password 提示。

## 3. 提示分类

| 提示 | 最终分类 | 是否阻塞 | 处理结论 |
| --- | --- | --- | --- |
| Mockito 动态 agent 告警 | 构建兼容性技术债 | 否 | 已通过 Surefire 显式 javaagent 做最小治理 |
| Spring Boot generated password 提示 | 开发/测试运行日志噪音 | 否 | 已限定在 dev/local/test 配置中提供固定默认 security user，未触碰 JWT / dev-login 链路 |

## 4. 验收证据

执行命令：

```powershell
cd backend
.\mvnw.cmd "-Dtest=AuthControllerTest,AuthServiceDevLoginTest,WorkflowCoreServiceTest" test
```

结果：

- BUILD SUCCESS
- Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
- 输出中未再出现 Mockito 动态 agent 自附加告警。
- 输出中未再出现 Spring Boot generated password 提示。

执行命令：

```powershell
git diff --check
```

结果：

- 退出码 0。
- 仅输出既有工作区脏文件的 CRLF/LF 换行归一化 warning，未发现 whitespace error。

## 5. 剩余风险

1. `org.springframework.boot.test.mock.mockito.MockBean` 过时 warning 仍存在，属于后续 Spring Boot 测试 API 迁移技术债，不属于本 Issue 的 Mockito 动态 agent 治理范围。
2. 测试结束仍有 `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended`，这是 javaagent/boot classpath 组合下的 JVM 提示，不影响测试结果；若后续门禁要求零 warning，可单独拆分治理。
3. 本轮未修改 prod profile；generated password 治理限定在 dev/local/test，避免引入生产默认账号或生产启动环境变量新要求。

## 6. 最终裁决

ISSUE-032-006 已完成最小治理。两类提示均维持非阻塞，当前验证通过，可进入主线程验收。
