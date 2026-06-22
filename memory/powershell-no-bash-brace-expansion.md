---
name: powershell-no-bash-brace-expansion
description: PowerShell 命令中使用 Bash 花括号路径展开导致解析失败的处理记录
metadata:
  type: feedback
tags:
  - powershell
  - tooling
  - shell
---

# PowerShell 不支持 Bash 花括号路径展开

## 现象

在 PowerShell 中执行 `path/{auth,file}/controller` 时出现 `Missing argument in parameter list`，命令未进入 `rg`。

## 根因

`{a,b}` 是 Bash 的 brace expansion 语法，不是 PowerShell 路径语法；PowerShell 将逗号解析为表达式参数分隔符。

## 修复

改用 `Get-ChildItem -Recurse` 枚举文件，再通过 `Where-Object` 筛选目录；需要多个固定路径时使用 PowerShell 数组逐一传入。

## 教训

仓库默认 shell 为 PowerShell。不要复制 Bash 的花括号路径展开；跨目录检索优先使用 `rg` 的单根目录递归或 PowerShell 文件枚举。
