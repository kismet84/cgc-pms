---
name: system-environment-fix-2026-06-21
description: 系统环境4个问题修复记录：JAVA_HOME、Python版本、psmux安装、OMC版本漂移
metadata:
  type: feedback
tags: [environment, java, python, tmux, omc]
---

## 问题与修复

### 1. JAVA_HOME 指向临时目录 → 已修复
- **问题**：`~/.bashrc` 和 `~/.zshrc` 中 `JAVA_HOME` 指向 `/c/Users/L1597/AppData/Local/Temp/opencode/jdk/jdk-21.0.5+11`（临时目录，可能被清理）
- **修复**：改为 `D:\projects-test\jdk-21\jdk-21.0.11+10`（项目 JDK）
- **验证**：`source ~/.bashrc && echo $JAVA_HOME` → 输出正确路径

### 2. Python 版本混乱 → 已修复
- **问题**：TRAE IDE 嵌入的 Python 3.10.11 在 PATH 中排在 Python 3.11 之前，导致 `python` → 3.10 但 `pip` → 3.11
- **修复**：在 `~/.bashrc` 和 `~/.zshrc` 中前置 `Python311` 路径
- **验证**：`python --version` → 3.11.0，`pip --version` → 3.11，版本一致

### 3. psmux 已安装 → 待 shell 重启生效
- **问题**：OMC Native Windows (win32) 无 tmux，team/ultrawork/swarm 等功能不可用
- **修复**：`winget install marlocarlo.psmux` 成功安装 v3.3.5
- **验证**：`psmux.exe --version` → `tmux 3.3.5`
- **注意**：当前 shell 需要重启才能识别 `psmux`/`pmux`/`tmux` 别名

### 4. OMC 版本漂移 → 已修复
- **问题**：系统提示 `CLAUDE.md instructions: unknown (needs migration) (expected 4.14.7)`
- **修复**：`omc update --force` 强制重装
- **结果**：版本 4.14.7 → 4.14.7（已是最新版），插件缓存已同步
- **注意**：CLAUDE.md 中的 `OMC:VERSION:4.14.7` 已一致，漂移警告应在下一会话自动消失

## 修改的文件
- `~/.bashrc` — JAVA_HOME + Python PATH
- `~/.zshrc` — JAVA_HOME + Python PATH
- psmux 通过 winget 系统级安装（PATH 自动添加）

**How to apply:** 下次会话启动时自动生效。如需在当前会话验证 Python/JAVA_HOME，执行 `source ~/.bashrc`。
