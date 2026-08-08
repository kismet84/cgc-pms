# Windows 便携 Chromium 桌面入口

## 适用边界

仅用于 Windows 10/11 x64 本地 dev/test/demo。启动器不安装、不启动或停止 Docker，不连接非本地地址，不保存账号、Token、Cookie 或业务数据。

## 使用

1. 先运行 `scripts\start-dev.bat`，确认 `http://127.0.0.1:5173/api/actuator/health` 返回顶层 `status=UP`。
2. 解压完整发行 ZIP，保留 `CGC-PMS.exe`、`chromium/`、许可证和校验文件的相对结构。
3. 双击 `CGC-PMS.exe`。启动器健康检查通过后，以 Chromium 应用模式打开 `http://127.0.0.1:5173/`。
4. 关闭应用窗口即可正常退出。重复启动只提示已有实例，不创建第二套 profile 进程。

发行目录可只读。可写数据只位于：

```text
%LOCALAPPDATA%\CGC-PMS\Desktop\
├─ profiles\chromium-<major>\UserData\
├─ runtime\launcher-state.json
└─ logs\launcher.log[.1-.4]
```

## 构建与校验

需要 Visual Studio Build Tools 2022（MSVC x64、Windows SDK）、PowerShell 7 和 Node.js：

```powershell
pwsh -NoProfile -File desktop-launcher/scripts/build.ps1 -Contract
pwsh -NoProfile -File desktop-launcher/tests/launcher-contract.ps1
pwsh -NoProfile -File desktop-launcher/scripts/build.ps1
pwsh -NoProfile -File desktop-launcher/scripts/package.ps1
pwsh -NoProfile -File desktop-launcher/scripts/verify-package.ps1 -PackagePath desktop-launcher/dist/CGC-PMS-Desktop-1.0.1-Chromium-153
```

`chromium.lock.json` 是版本、来源、归档 SHA-256 和许可证 SHA-256 权威源。默认下载缓存位于 `%LOCALAPPDATA%\CGC-PMS\Desktop\cache`；只允许专属 LocalAppData/TEMP 缓存根，拒绝仓库内或含重解析点的自定义路径。缓存、浏览器二进制、构建目录、ZIP、PDB、profile、状态和日志均不得提交 Git。

打包先在版本化 staging 目录生成并校验目录包，再压缩、解压复验 ZIP，最后发布到全新版本路径。已存在的版本目录或 ZIP 立即 fail-close，脚本不删除、备份或替换 canonical 产物；必须升版后与旧版并存。陈旧 staging 只在其 PID 不存在且路径通过专属边界检查时回收。

## 升级与回滚

- 新版解压到新目录，与旧版并存；禁止覆盖运行中的目录。
- Chromium major 变化会使用新 profile，需要重新登录。
- 验收失败时关闭新窗口，重新运行旧目录的 `CGC-PMS.exe`；不得把新 major profile 降级复用。
- 删除发行目录不删除登录态。需要清理时先关闭应用，预览 `%LOCALAPPDATA%\CGC-PMS\Desktop\profiles\chromium-<major>` 精确目标；删除会清除该 major 的登录态、缓存和未同步本地草稿。

## 故障定位

| 现象 | 处理 |
| --- | --- |
| 服务未就绪 | 运行 `scripts\start-dev.bat`，复验前端代理 health；启动器不会替用户重启服务 |
| 缺少 `chromium\chrome.exe` | 重新解压完整 ZIP并执行包校验，不单独复制 EXE |
| 运行时路径不安全 | 移除 `chromium` 路径中的目录联接/重解析点，重新校验发行目录 |
| 提示上次浏览器仍运行 | 先正常关闭遗留 CGC-PMS Chromium 窗口；启动器不会强杀其他 Chrome/Edge |
| 登录态需清理 | 按上节精确清理对应 major profile，不动其他浏览器或用户目录 |

`--app` 只提供应用窗口外观，不是导航安全沙箱。认证、租户、权限、文件和状态仍由现有 Spring Boot 权威链处理。
