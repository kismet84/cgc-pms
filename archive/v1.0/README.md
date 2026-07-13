# v1.0 本地私有封存

`private/` 保存 v1.0 及更早的本地智能体目录和运行历史，整体由 Git 忽略，不属于项目运行依赖。

完整性以 `private/manifest-sha256.csv` 的相对路径、字节数和 SHA-256 为准。公开文档不得直接链接其中内容。

本次封存共保存 `8795` 个文件、`245,932,197` 字节；另以 `private/reparse-points.csv` 记录 49 个仓库内 junction，避免复制其目标内容。
