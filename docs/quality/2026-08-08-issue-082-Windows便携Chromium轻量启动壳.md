# ISSUE-082 Windows 便携 Chromium 轻量启动壳质量报告

> 日期：2026-08-08
> 状态：`IMPLEMENTED / G0-G5_LOCAL_PASSED / GIT_DELIVERY_PENDING`
> 基线：`fd2668bc13d611e7b76908bd7777655f37563bb6`
> 环境：Windows 10/11 x64 本地 dev/test/demo 与 GitHub Actions 定义

## 裁决

G0～G5 本地通过。新增 Win32 C++17 x64 薄启动器和固定 Chromium 153.0.7998.0 发行链；未改业务前后端、数据库、Compose loopback 或认证语义。Git、同 SHA CI、PR 与合并证据在交付阶段另行绑定，不能由本报告预先替代。

## G0～G5 证据

| 门禁 | 结果 |
| --- | --- |
| G0 | 基线 HEAD 已记录；Codemap 先刷新；Visual Studio Build Tools 2022 17.14.37、MSVC x64、Windows SDK、`cl/link/rc` 可用 |
| G1 | 固定 URL、health、参数、单实例、profile、日志与退出码由 fake Chromium 合同冻结；失败优先测试后实现 |
| G2 | 无 migration、DB、backend/frontend 业务改动；profile/state/log 与 Chromium 缓存位于专属 `%LOCALAPPDATA%`，发行目录保持只读可复制 |
| G3 | `/MT` GUI EXE、Unicode/空格路径、单实例、陈旧状态、健康失败/恢复、禁用危险 flags、包清单和应用图标通过 |
| G4 | 完整包实际启动 Chromium 应用窗口；frontend、API 代理、dashboard、文件中心、下载入口、通讯/SSE 历史补拉可见；控制台 error/warn 为 0；正常关窗后 browser-root 与 launcher 均退出 |
| G5 | Windows launcher CI job、15 项 required verifier、策略/工作流/Codemap/前端静态与文档门禁纳入最终复验 |

## 供应链与产物

- Chromium snapshot revision `1676106`，commit `b63b9e327e1587d61dd78339be39b0b017817326`。
- 归档 SHA-256：`8344DFB088B8E7D844172E0BEEBEDC9900404447784AEAABBFEEE57AA42C539C`。
- pinned LICENSE SHA-256：`368CCA1106BE99D39ECD32A38D8305585D802A475EFFB66380B91FFC9BCF709B`。
- 本地 ZIP：`CGC-PMS-Desktop-1.0.1-Chromium-153.zip`；最终复验 SHA-256 `4DD35C4444877FEB5838FD8336F4D24CA1CCD5C790B4EC021EEDFF81BF6FAC83`。ZIP 和 Chromium 二进制不入 Git。
- EXE 产品版本 `1.0.1.0`，图标资源可提取，发行包校验通过。发布脚本永不删除或替换已存在版本；同版本重建 fail-close，必须升版后与旧目录/ZIP 并存。

## 自动与运行态验证

- launcher contract：PASS；Release build：PASS；staging 目录包、ZIP 解压包和发布后目录三次 package verify：PASS。
- WinHTTP `HINTERNET` 由专用 RAII 调用 `WinHttpCloseHandle`；不再误用 `CloseHandle`。
- 故意中断 staging 打包后，旧 1.0.0 目录和 ZIP 保持可验；1.0.1 成功发布后两版并存。同版本重建被拒绝，前后两版目录/ZIP hash 均未变，`VersionedFailClose=PASS`。
- 仓库内 cache 参数被拒绝，`external dedicated cache boundary: PASS`。
- 前端 health/dev-entry 单测：2 文件、5 测试通过；类型检查、Vite build 通过。
- Compose 当前服务健康，5173/8080 维持 `127.0.0.1`；frontend、backend health 均 200。
- 浏览器证据：dashboard、文件中心文件行/下载与上传新版本入口、站内通讯会话/联系人/消息历史均加载；warn/error 0。
- 下载由页面 Blob 流触发，自动化表面未产生浏览器原生 download event；页面无错误且入口存在，本轮不把该工具事件缺口改写为业务失败。

## 失败分类

- 缺 MSVC/SDK：`environment_prerequisite`；安装受支持 Build Tools 后复验。
- Chromium 大文件单流下载超时/TLS：`environment_prerequisite`；改为可校验分段下载，最终归档 hash 与锁一致。
- 构建参数、Unicode fixture、缺链接库、包复验漏参：`tool_invocation`；修正最小调用后通过。
- 图标缺口：`quality_or_security`；增加确定性 ICO 生成与资源编译，实际 EXE 提取验证。
- 浏览器 performance API 不可用：`tool_config`；停止原样重试，改用 DOM、控制台和既有 Service Worker 静态/单测证据。

## 零悬空

- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 运行产物、缓存、ZIP、profile 与日志均被忽略；无载体外遗留。
