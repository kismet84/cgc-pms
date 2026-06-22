---
name: backend-rebuild-jasypt-env-fix-2026-06-22
description: 后端重建脚本在 Windows 下修复 mvnw.cmd 启动方式，并补齐 docker compose 所需的 JASYPT_ENCRYPTOR_PASSWORD 环境变量
metadata:
  type: feedback
tags: [backend, rebuild, windows, docker-compose, jasypt, maven]
---

## 问题

- `python scripts/rebuild.py backend` 在 Windows 上直接调用 `mvnw.cmd`，`subprocess.run` 找不到可执行入口，抛出 `FileNotFoundError: [WinError 2]`
- 修复为 `cmd /c mvnw.cmd ...` 后，Maven 构建成功，但 `docker compose restart backend` 因缺少 `JASYPT_ENCRYPTOR_PASSWORD` 变量而失败

## 修复

- 在 `scripts/rebuild.py` 中检测 Windows + `.cmd` 命令，统一改为 `cmd /c` 启动
- 在 `scripts/rebuild.py` 的命令环境中补上 `JASYPT_ENCRYPTOR_PASSWORD=dev-jasypt-key` 默认值，和仓库 `docker-compose.dev.yml` 的要求对齐

## 经验

- Python 的 `subprocess` 不会像交互 shell 一样自动执行 `.cmd` 文件，Windows 下要显式经过 `cmd /c`
- 容器重启失败时不要只看应用构建，先检查 compose 文件要求的环境变量是否完整注入
- 对开发重建脚本这类入口，最好把外部工具依赖的默认环境变量集中在脚本里，避免本机 shell 配置差异导致不稳定
