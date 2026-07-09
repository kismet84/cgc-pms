# Failure Classification

先分类，再判断是否为真实代码问题。

## `tool_config`

适用：

- 缺少工具
- CLI 参数错误
- 规则文件未加载
- 脚本入口错误

建议：

- 修配置
- 修命令
- 补前置

## `environment_prereq`

适用：

- Docker / WSL / DB / 端口未就绪
- 服务未达到稳定等待时间
- 前端仍指向旧 backend
- 测试数据前置缺失

建议：

- 先恢复运行态
- 等稳定时间
- 再复验

## `ready_issue_config`

适用：

- Ready Issue 指向的测试类、方法、脚本不存在
- 允许路径、验证命令或报告路径配置错误

建议：

- 修 Ready 条目
- 记录最小等价替换

## `real_quality_or_security`

适用：

- 构建失败
- 测试失败
- 类型错误
- 权限越权
- 安全命中
- 数据一致性问题

建议：

- 进入实现整改或阻塞裁决

## `unknown`

适用：

- 证据不足
- 分类冲突

建议：

- 人工复核
- 不直接判代码失败
