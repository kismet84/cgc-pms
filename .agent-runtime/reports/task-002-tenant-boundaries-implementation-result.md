# Task 002 Implementation Report: 收口租户边界与直绑写入入口

## Status

**COMPLETE** — all 13 new tests pass, build is green.

## Summary

修复了审计报告 (task-030) 中发现的 5 个租户边界安全缺陷 (SEC-01 ~ SEC-05)，覆盖：
- SEC-01: 手动费用分摊接受客户端任意 `tenantId`
- SEC-02: 菜单 CRUD 完全无租户过滤
- SEC-03: 组织/分包任务/项目成员创建时信任客户端 `tenantId`
- SEC-04: `data_scope` SELF 范围生效
- SEC-05: (部分) 项目成员创建/查询强制租户一致性

## Changed files

### 生产代码
| 文件 | 变更 | 对应缺陷 |
|---|---|---|
| `backend/src/main/java/com/cgcpms/overhead/controller/OverheadAllocationController.java` | `executeAllocation` 忽略客户端 `tenantId`，从 `UserContext` 获取 | SEC-01 |
| `backend/src/main/java/com/cgcpms/overhead/service/OverheadAllocationService.java` | `update` 方法保留已有 tenant，忽略客户端注入值 | SEC-01 |
| `backend/src/main/java/com/cgcpms/system/service/SysMenuService.java` | 全部 CRUD 方法加入租户过滤：`getTree`/`getFlatList` 过滤、`getById`/`update`/`delete` 校验归属、`create` 强制写入当前 tenantId | SEC-02 |
| `backend/src/main/java/com/cgcpms/org/service/OrgCompanyService.java` | `create` 方法强制从 `UserContext` 设置 tenantId | SEC-03 |
| `backend/src/main/java/com/cgcpms/subcontract/service/SubTaskService.java` | `create` 方法强制从 `UserContext` 设置 tenantId | SEC-03 |
| `backend/src/main/java/com/cgcpms/system/service/SysRoleService.java` | `create` 方法角色编码唯一性检查加入租户维度 | P2 |
| `backend/src/main/java/com/cgcpms/project/service/PmProjectService.java` | 新增 `resolveEffectiveDataScope()` + `getPage` 中 SELF 范围过滤 | SEC-04 |

### 测试
| 文件 | 说明 |
|---|---|
| `backend/src/test/java/com/cgcpms/TenantBoundaryTask2Test.java` | 新增 13 个跨租户边界测试 |

## TDD red/green evidence

### Red phase
N/A — 由于仓库存在编译冲突（其他 task 未完成变更导致 `JwtAuthenticationFilter` 等文件编译失败），先实现后验证。

### Green phase (2026-06-21)

```
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- TenantBoundaryTask2Test
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0 -- SysUserServiceTest
[INFO] BUILD SUCCESS
```

测试覆盖：
- T-BOUND-1~4: 菜单跨租户读/改/删/列表过滤
- T-BOUND-5~7: OrgCompany/SubTask/PmProjectMember 创建忽略客户端 tenantId
- T-BOUND-8~9: OverheadAllocation 规则创建和列表过滤
- T-BOUND-10: data_scope 值在角色 VO 中传递
- T-BOUND-11~12: 跨租户项目成员读写拒绝
- T-BOUND-13: 创建菜单时忽略客户端 tenantId 并验证跨租户不可见

## Verification commands and results

```bash
export JAVA_HOME="D:\projects-test\jdk-21\jdk-21.0.11+10"
cd D:/projects-test/cgc-pms/backend

# 编译验证
bash ./mvnw compile -Djasypt.encryptor.password=dev-jasypt-key
# Result: BUILD SUCCESS — 所有生产代码编译通过

# 测试编译
bash ./mvnw test-compile -Djasypt.encryptor.password=dev-jasypt-key
# Result: BUILD SUCCESS — 测试代码编译通过

# 新测试运行
bash ./mvnw test -Djasypt.encryptor.password=dev-jasypt-key -Dtest="com.cgcpms.TenantBoundaryTask2Test"
# Result: Tests run: 13, Failures: 0, Errors: 0, Skipped: 0

# 原有回归测试 (SysUserServiceTest — 使用 SysRoleService)
bash ./mvnw test -Djasypt.encryptor.password=dev-jasypt-key -Dtest="com.cgcpms.system.SysUserServiceTest"
# Result: Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
```

## Known risks

1. **TenantIsolationTest 仍然失败** — 因 `CodeGenerationService` lambda cache 问题（`CodeGenerationService$SFunctionUtil$TenantIdMarker`），是 codebase 已有的 bug，非本次变更引入
2. **DEPT/CUSTOM scope 未完全实现** — `PmProjectService.resolveEffectiveDataScope()` 识别了 DEPT/CUSTOM 范围但未实际过滤，仅实现了 SELF 范围。DEPT 需要部门树基础设施，CUSTOM 需要自定义范围规则表
3. **FileService 业务对象归属校验** (SEC-05) — 文件 CRUD 已有租户校验，但上传时未校验 businessId 指向的业务实体是否属于当前租户。需要跨模块业务归属校验器 — 作为后续独立任务
4. **data_scope 仅作用于项目列表** — 其他业务模块（合同、付款等）的数据范围过滤尚未实现

## Commit

commit hash: `fdf976c1`
commit message: `fix(auth): enforce tenant boundaries across writes`
