---
name: release-skills
description: Universal version-release workflow for version bumps, release notes, annotated Tags, GitHub Releases, historical Release backfill, and project release artifacts. Use only when the user asks for a release/发布, new version/新版本, bump/升版本, Tag, GitHub Release, release notes, or 回填 Release.
---

# Release Skills

根规则由 Codex 自动加载。本 Skill 只处理版本发布；普通提交、推送、PR、合并和分支清理由 `git-publish-and-cleanup` 处理。

## 触发边界

使用：

- 发布版本、升 major/minor/patch。
- 生成 release notes、版本 Tag、GitHub Release。
- 回填已有 Tag 缺失的 GitHub Release。

不使用：

- 单独要求提交、同步远端或 Git 交付。
- 普通功能分支 PR、合并或清理。
- 生产部署；除非版本发布流程另有明确、独立授权。

## 选项

| Flag | 含义 |
| --- | --- |
| `--dry-run` | 只预览，不写文件、commit、Tag 或远端 |
| `--major` / `--minor` / `--patch` | 指定版本增量 |
| `--backfill-releases` | 只回填历史 GitHub Release |

## 按需加载

### 普通版本发布

按阶段到达时依次读取，不提前一次性加载全部：

1. [项目与发布钩子检测](references/01-detect-project.md)
2. [变更分析与版本选择](references/02-analyze-and-version.md)
3. [多语言 changelog](references/03-changelog.md)
4. [模块分组与提交](references/04-module-commits.md)
5. [版本文件、release notes 与确认门](references/05-prepare-and-confirm.md)
6. [release commit、Tag、产物与 GitHub Release](references/06-tag-and-publish.md)

仅仓库存在 `.releaserc.yml` 时读取 [配置格式](references/configuration.md)。`--dry-run` 输出示例见 [examples/dry-run.md](examples/dry-run.md)。

### 历史回填

仅读取 [历史 GitHub Release 回填](references/07-backfill-github-releases.md)；不得升版本、改 changelog 或创建 release commit。

## 写操作门

- 先显示项目类型、当前/目标版本、变更摘要、release notes 来源和将执行的写操作。
- 创建 release commit/Tag 前确认目标版本。
- 远端分支/Tag、GitHub Release 和项目产物发布分别确认；本地版本准备不自动授权远端写入。
- 不强推、不重写公开 Tag、不绕过分支保护；公开轻量 Tag 转 annotated Tag 必须单独确认。
- 发布说明写入 UTF-8 临时文件，通过文件传给 Tag、hook 和 GitHub Release；禁止把多行内容内联进 shell。
- `.releaserc.yml` 存在 hook 时复用 hook，不把项目专用发布细节写回本 Skill。

## 收口

回报目标版本、版本文件/changelog、commit、Tag 类型、产物发布、GitHub Release、远端状态、验证和最终 Git 状态。未授权的远端动作明确标记“未执行”，不得称发布完成。
