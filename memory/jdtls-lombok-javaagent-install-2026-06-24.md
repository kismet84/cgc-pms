---
name: jdtls-lombok-javaagent-install
description: Windows 本地安装 Eclipse JDT LS 后必须配置 Lombok javaagent，否则 LSP 会误报 getter/setter 不存在
metadata:
  type: toolchain
  feedback: resolved
tags:
  - java
  - lsp
  - jdtls
  - lombok
  - windows
---

# JDT LS + Lombok 安装修复

## 现象

最初运行 Java LSP 诊断时报错：

```text
LSP server 'jdtls' is configured but NOT INSTALLED.
Command not found: jdtls
```

安装 Eclipse JDT LS 后，`jdtls` 可以启动，但 Java 诊断仍出现大量 Lombok 生成方法不存在的误报，例如：

```text
The method setProjectId(long) is undefined for the type ContractRevenue
The blank final field mapper may not have been initialized
log cannot be resolved
```

这些类实际使用了 Lombok `@Data`、`@RequiredArgsConstructor`、`@Slf4j`，Maven 编译和测试均能通过，因此是 LSP 没加载 Lombok 的工具链问题。

## 根因

Eclipse JDT LS 不会自动把 Lombok 注解处理挂成 IDE agent。裸启动 JDT LS 时，LSP 只能看到源码字段，看不到 Lombok 生成的 getter/setter、构造器和 `log` 字段。

此外，诊断工具可能已经启动旧的 JDT LS 进程；修改 wrapper 后如果不终止旧进程，仍会继续使用旧参数。

## 修复步骤

1. 下载官方 JDT LS：

   ```text
   https://download.eclipse.org/jdtls/milestones/1.58.0/jdt-language-server-1.58.0-202604151538.tar.gz
   ```

2. 校验 SHA-256 与官方 `.sha256` 一致。

3. 解压到用户目录：

   ```text
   %LOCALAPPDATA%\jdtls\1.58.0
   ```

4. 下载 Lombok agent：

   ```text
   %LOCALAPPDATA%\jdtls\1.58.0\lombok.jar
   ```

5. 在用户 PATH 目录创建 `jdtls.cmd`：

   ```text
   %USERPROFILE%\AppData\Local\Microsoft\WindowsApps\jdtls.cmd
   ```

   关键是 Java 启动参数必须包含：

   ```text
   -javaagent:"%JDTLS_HOME%\lombok.jar"
   ```

6. 终止旧 JDT LS 进程：

   ```powershell
   Get-CimInstance Win32_Process -Filter "name = 'java.exe'" |
     Where-Object { $_.CommandLine -like '*jdt.ls*' -or $_.CommandLine -like '*EclipseJDTLS*' } |
     ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
   ```

7. 重新运行 `lsp_diagnostics`。

## 验证结果

以下文件诊断均为 0：

- `backend/src/main/java/com/cgcpms/revenue/service/ContractRevenueService.java`
- `backend/src/test/java/com/cgcpms/revenue/ContractRevenueServiceTest.java`
- `backend/src/test/java/com/cgcpms/settlement/StlSettlementQueryServiceTest.java`

## 教训

- Java LSP 能启动不代表项目语义完整；Lombok 项目必须确认 annotation processor/javaagent 生效。
- JDT LS wrapper 修改后，要杀掉旧 `java.exe` 语言服务器进程，否则诊断仍可能来自旧配置。
- `jdtls --version` 不是普通版本命令，会启动 LSP server 等待输入；超时不代表安装失败。
