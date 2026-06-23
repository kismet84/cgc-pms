---
name: rebuild-script-maven-wrapper-windows-path-fix-2026-06-23
description: 修复 Windows 下 scripts/rebuild.py 调用 Maven Wrapper 裸命令导致 mvnw.cmd not recognized 的问题
metadata:
  type: feedback
  tags:
    - backend
    - rebuild
    - windows
    - maven
    - docker-compose
---

# 重建脚本 Maven Wrapper Windows 路径修复

## 现象

执行 `python scripts/rebuild.py` 时，后端构建阶段失败：

```text
'mvnw.cmd' is not recognized as an internal or external command,
operable program or batch file.
```

脚本随后只能继续重启前端容器，后端 JAR 未通过一键脚本完成构建。

## 根因

`scripts/rebuild.py` 在 Windows 下返回裸命令 `mvnw.cmd`。当前 PowerShell/子进程环境不会从工作目录解析裸命令，需要使用 `.\mvnw.cmd` 或绝对路径。

## 修复

`resolve_maven_wrapper()` 在 Windows 下返回 Maven Wrapper 的绝对路径：

- `D:\projects-test\cgc-pms\backend\mvnw.cmd`
- 如仅存在 Unix wrapper，则返回对应绝对路径

脚本原有的 `cmd /c` 包装逻辑保留。

## 验证

- `.\mvnw.cmd clean package -DskipTests -q` 在 `backend/` 下手动构建通过。
- 修复后 `python scripts\rebuild.py backend` 构建 JAR 并重启 `cgc-pms-backend-dev` 通过。
- `python scripts\rebuild.py frontend` 重启 `cgc-pms-frontend-dev` 通过。
- `curl.exe -I http://127.0.0.1:5173/` 返回 200。
- `curl.exe -I http://127.0.0.1:8080/api/actuator/health` 返回 200。
