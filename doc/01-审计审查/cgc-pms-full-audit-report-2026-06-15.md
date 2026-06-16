# CGC-PMS 全量审计报告 v3.0

**审计日期**: 2026-06-15  
**审计范围**: 全量代码库 (319 Java, 47 Vue, 93 TS, 62,548 LOC)  
**审计类型**: 安全审计 + 代码质量审计 + 基础设施审计  
**审计方法**: 从零开始代码扫描，不参考历史报告

---

## 1. 项目概况

| 维度 | 详情 |
|------|------|
| 后端框架 | Spring Boot 3.3, Java 21, MyBatis-Plus |
| 前端管理台 | Vue 3 + TypeScript + Vite |
| 前端移动端 | UniApp + Vue 3 |
| 数据库 | MySQL 8.0 (Flyway 迁移) |
| 缓存 | Redis |
| 认证 | JWT (HS256, 15min access / 7d refresh) |
| 部署 | Docker, GitHub Actions CI |
| 代码量 | 后端 Java 为主, 前端 Vue/TS 为主 |

---

## 2. 审计结果汇总

| 等级 | 数量 | 说明 |
|------|------|------|
| **P0** | 1 | 安全 - 必须立即修复 |
| **P1** | 5 | 严重 - 建议尽快修复 |
| **P2** | 10 | 中等 - 建议规划修复 |
| **合计** | **16** | |

---

## 3. P0 问题 (安全 - 必须立即修复)

### P0-01: 日志输出敏感数据(用户修改密码)

| 字段 | 内容 |
|------|------|
| **文件** | `backend/src/main/java/com/cgcpms/system/service/ProfileService.java:102` |
| **详情** | `log.info("User {} changed password", user.getUsername());` 记录了用户修改密码操作 |
| **风险** | 日志中包含用户修改密码的敏感事件，可能被未授权读取日志的人员利用 |
| **修复** | 使用脱敏方法: `log.info("User {} changed password", "***");` 或移除该日志 |
| **验收** | 日志中不再出现明文用户名 |
| **状态** | ❌ 未修复 |

---

## 4. P1 问题 (严重 - 建议尽快修复)

### P1-01: 超大类 (6个Service类 > 400行)

| 文件 | 行数 |
|------|------|
| `StlSettlementService.java` | 591 |
| `CostSummaryService.java` | 573 |
| `AlertEvaluationService.java` | 568 |
| `DashboardService.java` | 533 |
| `PayApplicationService.java` | 532 |
| `MatReceiptService.java` | 488 |

**风险**: 类过大，违反单一职责原则，难以维护和测试  
**修复**: 按业务领域拆分Service类，每个类不超过400行  
**验收**: 拆分后每个类行数 ≤ 400，单元测试覆盖率 ≥ 80%  
**状态**: ❌ 未修复

### P1-02: Service类缺少日志组件 (8个)

| 文件 |
|------|
| `CtContractItemService.java` |
| `CtContractPaymentTermService.java` |
| `OrgPositionService.java` |
| `OrgCompanyService.java` |
| `OrgDepartmentService.java` |
| `CostSubjectService.java` |
| `MatStockService.java` |
| `MdMaterialService.java` |

**风险**: 无法记录运行时信息，问题排查困难  
**修复**: 添加@Slf4j注解或Logger实例  
**验收**: 所有Service类都有日志组件  
**状态**: ❌ 未修复

### P1-03: AuthController部分接口无权限控制

| 字段 | 内容 |
|------|------|
| **文件** | `AuthController.java` |
| **详情** | 接口4个，@PreAuthorize仅2个 |
| **风险** | 部分接口无权限控制，任何认证用户均可访问 |
| **修复** | 为所有接口添加@PreAuthorize注解 |
| **验收** | 所有接口都有@PreAuthorize |
| **状态** | ❌ 未修复 |

### P1-04: 前端生产代码console.log残留

| 字段 | 内容 |
|------|------|
| **文件** | `frontend-admin/src/stores/user.ts` |
| **详情** | user.ts中有3处console.log，可能输出用户认证信息 |
| **风险** | 生产环境可能泄露用户认证信息 |
| **修复** | 移除console.log或使用DEBUG环境变量控制 |
| **验收** | 生产环境无console.log输出 |
| **状态** | ❌ 未修复 |

### P1-05: logback无敏感数据掩码配置

| 字段 | 内容 |
|------|------|
| **文件** | 全项目 |
| **详情** | logback配置中无RegexFilter或TurboFilter来脱敏身份证号、银行卡号、手机号等 |
| **风险** | 日志可能输出敏感个人信息，违反《个人信息保护法》 |
| **修复** | 在logback-spring.xml中添加RegexMaskConverter和MDC过滤器 |
| **验收** | 日志中身份证号、银行卡号、手机号自动脱敏为*** |
| **状态** | ❌ 未修复 |

---

## 5. P2 问题 (中等 - 建议规划修复)

### P2-01: permitAll端点配置

| 字段 | 内容 |
|------|------|
| **文件** | `SecurityConfig.java` |
| **详情** | permitAll端点: WHITELIST |
| **风险** | 部分接口无需认证即可访问 |
| **修复** | 确认WHITELIST包含的端点确实不需要认证 |
| **验收** | WHITELIST端点列表合理 |
| **状态** | ❌ 未修复 |

### P2-02: XSS Protection Header已禁用

| 字段 | 内容 |
|------|------|
| **文件** | `SecurityConfig.java` |
| **详情** | SecurityConfig中xssProtection header已禁用 |
| **风险** | 降低浏览器内置XSS防护 |
| **修复** | 改为enable或移除配置使用默认值 |
| **验收** | 响应头中包含X-XSS-Protection: 1; mode=block |
| **状态** | ❌ 未修复 |

### P2-03: JWT access token有效期15分钟

| 字段 | 内容 |
|------|------|
| **文件** | `application-prod.yml` |
| **详情** | JWT access token有效期: 15分钟 (900000ms) |
| **风险** | Token有效期适中，但refresh token 7天较长 |
| **修复** | 考虑缩短access token到5分钟，使用refresh token机制 |
| **验收** | access token有效期 ≤ 5分钟 |
| **状态** | ❌ 未修复 |

### P2-04: 前端router guard已配置

| 字段 | 内容 |
|------|------|
| **文件** | `frontend-admin/src/router/index.ts` |
| **详情** | router.beforeEach已配置 |
| **风险** | INFO: 路由守卫已实现 |
| **修复** | 无需修复 |
| **验收** | 路由守卫正常工作 |
| **状态** | ✅ 已实现 |

### P2-05: 前端axios interceptor已配置

| 字段 | 内容 |
|------|------|
| **文件** | `frontend-admin/src/api/request.ts` |
| **详情** | axios.interceptors已配置 |
| **风险** | INFO: 请求/响应拦截已实现 |
| **修复** | 无需修复 |
| **验收** | 拦截器正常工作 |
| **状态** | ✅ 已实现 |

### P2-06: 全项目无aria-*无障碍属性

| 字段 | 内容 |
|------|------|
| **文件** | 全项目前端 |
| **详情** | 前端无aria-*属性 |
| **风险** | 无障碍访问不达标，影响屏幕阅读器用户 |
| **修复** | 为关键交互元素添加aria-label和role属性 |
| **验收** | 关键表单、按钮、链接都有aria-label |
| **状态** | ❌ 未修复 |

### P2-07: 数据库索引已配置

| 字段 | 内容 |
|------|------|
| **文件** | 全项目 |
| **详情** | 发现15处索引定义 |
| **风险** | INFO: 索引已配置 |
| **修复** | 无需修复 |
| **验收** | 索引正常工作 |
| **状态** | ✅ 已实现 |

### P2-08: Dockerfile无HEALTHCHECK

| 字段 | 内容 |
|------|------|
| **文件** | `backend/Dockerfile`, `frontend-admin/Dockerfile` |
| **详情** | Dockerfile无HEALTHCHECK指令 |
| **风险** | 容器无法检测服务健康状态 |
| **修复** | 添加HEALTHCHECK指令 |
| **验收** | Dockerfile包含HEALTHCHECK指令 |
| **状态** | ❌ 未修复 |

### P2-09: CI无Docker构建和部署

| 字段 | 内容 |
|------|------|
| **文件** | `.github/workflows/ci.yml` |
| **详情** | CI无Docker构建和Deploy阶段 |
| **风险** | 缺少自动化构建和部署 |
| **修复** | 添加Docker Build和Deploy阶段到CI/CD |
| **验收** | CI包含Docker Build和Deploy阶段 |
| **状态** | ❌ 未修复 |

### P2-10: 未发现httpOnly cookie使用

| 字段 | 内容 |
|------|------|
| **文件** | 全项目前端 |
| **详情** | 前端未发现httpOnly cookie使用 |
| **风险** | Token可能存储在localStorage，易受XSS攻击 |
| **修复** | 将Token改为httpOnly cookie存储 |
| **验收** | Token存储在httpOnly cookie中 |
| **状态** | ❌ 未修复 |

### P2-11: JWT secret从环境变量读取

| 字段 | 内容 |
|------|------|
| **文件** | `application-prod.yml` |
| **详情** | JWT secret从${JWT_SECRET}环境变量读取 |
| **风险** | INFO: 配置合理 |
| **修复** | 无需修复 |
| **验收** | JWT secret从环境变量读取 |
| **状态** | ✅ 已实现 |

---

## 6. 安全配置详情

### Spring Security 配置
- **CSRF**: 禁用 (无状态JWT设计，合理)
- **Session**: 无状态 (JWT)
- **XSS Protection**: 已禁用 (建议改为enable)
- **CORS**: 已配置，无通配符 (✅)
- **permitAll 端点**: 1处 (WHITELIST)

### JWT 配置
- **算法**: HS256
- **Access Token 过期**: 15分钟 (900000ms)
- **Refresh Token 过期**: 7天 (604800000ms)
- **Secret**: 从环境变量`${JWT_SECRET}`读取 (✅)

### 认证与授权
- **@PreAuthorize**: 大部分Controller接口都有权限控制
- **AuthController**: 4个接口中2个有@PreAuthorize (⚠️)
- **租户隔离**: 未发现明显问题

---

## 7. 改进建议优先级

| 优先级 | 行动 | 预计工作量 |
|--------|------|-----------|
| **P0** | 修复ProfileService.java日志脱敏 | 1小时 |
| **P1** | 拆分6个超大Service类 | 2-3天 |
| **P1** | 添加8个Service类的日志组件 | 2小时 |
| **P1** | 为AuthController接口添加权限控制 | 1小时 |
| **P1** | 清理user.ts console.log | 30分钟 |
| **P1** | 添加日志敏感数据掩码 | 2小时 |
| **P2** | 确认permitAll端点合理性 | 1小时 |
| **P2** | 启用XSS Protection Header | 30分钟 |
| **P2** | 缩短JWT access token到5分钟 | 1小时 |
| **P2** | 添加前端无障碍支持 | 2天 |
| **P2** | 添加Dockerfile HEALTHCHECK | 1小时 |
| **P2** | 添加CI/CD Docker构建和部署 | 2天 |
| **P2** | 改用httpOnly cookie存储Token | 1天 |

---

## 8. 结论

本次审计从零开始，完全基于最新代码扫描，共发现 **16 项问题**。

**主要改进**:
- ✅ 前端已有router.beforeEach路由守卫
- ✅ 前端已有axios.interceptors请求/响应拦截
- ✅ 数据库索引已配置
- ✅ JWT secret从环境变量读取
- ✅ 未发现SQL注入、硬编码密钥等严重安全问题

**仍需关注**:
- ⚠️ 6个Service类超过400行，需重构
- ⚠️ 8个Service类缺少日志组件
- ⚠️ AuthController部分接口无权限控制
- ⚠️ 日志敏感数据掩码缺失
- ⚠️ 前端无障碍支持缺失

**建议下一步**: 优先修复P0日志脱敏问题，然后规划P1超大类重构。
