package com.cgcpms.bootstrap.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.bootstrap.config.PlatformBootstrapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformBootstrapService {

    private static final String BOOTSTRAP_KEY = "PLATFORM_ADMIN";
    private static final String ENABLED = "ENABLE";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PlatformBootstrapProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public Result bootstrap() {
        require(properties.isEnabled(), "BOOTSTRAP_DISABLED");
        properties.validateForExecution();
        StateRow state = one(jdbcTemplate.query(
                "SELECT bootstrap_version, status FROM sys_bootstrap_state WHERE bootstrap_key=? FOR UPDATE",
                (rs, rowNum) -> new StateRow(rs.getInt("bootstrap_version"), rs.getString("status")),
                BOOTSTRAP_KEY), "BOOTSTRAP_STATE_MISSING");
        if ("COMPLETED".equals(state.status())) {
            return Result.ALREADY_COMPLETED;
        }
        require("PENDING".equals(state.status()), "BOOTSTRAP_STATE_INVALID");
        require(state.version() == properties.getVersion(), "BOOTSTRAP_STATE_VERSION_MISMATCH");

        long tenantId = properties.getTenantId();
        PlatformBootstrapProperties.Administrator administrator = properties.getAdministrator();
        RoleRow role = one(jdbcTemplate.query(
                "SELECT id, status FROM sys_role WHERE tenant_id=? AND role_code=? AND deleted_flag=0",
                (rs, rowNum) -> new RoleRow(rs.getLong("id"), rs.getString("status")),
                tenantId, administrator.getRoleCode()), "BOOTSTRAP_ROLE_MISSING");
        require(ENABLED.equals(role.status()), "BOOTSTRAP_ROLE_DISABLED");

        long companyId = getOrCreateCompany(tenantId);
        getOrCreateDepartment(tenantId, companyId);

        List<UserRow> users = jdbcTemplate.query(
                "SELECT id, status, is_admin FROM sys_user WHERE tenant_id=? AND username=? AND deleted_flag=0",
                (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("status"), rs.getInt("is_admin")),
                tenantId, administrator.getUsername().trim());

        Result result;
        if (users.isEmpty()) {
            long userId = IdWorker.getId();
            String passwordHash = passwordEncoder.encode(administrator.getPassword());
            jdbcTemplate.update("""
                    INSERT INTO sys_user
                    (id, tenant_id, username, password, real_name, email, org_id, status, is_admin, deleted_flag, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'ENABLE', 1, 0, '平台引导创建')
                    """, userId, tenantId, administrator.getUsername().trim(), passwordHash,
                    administrator.getRealName().trim(), blankToNull(administrator.getEmail()), companyId);
            jdbcTemplate.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                    IdWorker.getId(), tenantId, userId, role.id());
            result = Result.CREATED;
        } else {
            UserRow user = one(users, "BOOTSTRAP_USER_DUPLICATE");
            require(ENABLED.equals(user.status()), "BOOTSTRAP_USER_DISABLED");
            Integer binding = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user_role WHERE tenant_id=? AND user_id=? AND role_id=?",
                    Integer.class, tenantId, user.id(), role.id());
            require(binding != null && binding == 1, "BOOTSTRAP_EXISTING_USER_NOT_SUPER_ADMIN");
            if (user.isAdmin() != 1) {
                jdbcTemplate.update("UPDATE sys_user SET is_admin=1 WHERE tenant_id=? AND id=?", tenantId, user.id());
            }
            result = Result.ADOPTED;
        }

        int updated = jdbcTemplate.update("""
                UPDATE sys_bootstrap_state
                SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP
                WHERE bootstrap_key=? AND bootstrap_version=? AND status='PENDING'
                """, BOOTSTRAP_KEY, properties.getVersion());
        require(updated == 1, "BOOTSTRAP_STATE_UPDATE_FAILED");
        return result;
    }

    private long getOrCreateCompany(long tenantId) {
        PlatformBootstrapProperties.Organization organization = properties.getOrganization();
        List<CompanyRow> companies = jdbcTemplate.query(
                "SELECT id, company_name FROM org_company WHERE tenant_id=? AND company_code=? AND deleted_flag=0",
                (rs, rowNum) -> new CompanyRow(rs.getLong("id"), rs.getString("company_name")),
                tenantId, organization.getCompanyCode().trim());
        if (!companies.isEmpty()) {
            CompanyRow company = one(companies, "BOOTSTRAP_COMPANY_DUPLICATE");
            require(organization.getCompanyName().trim().equals(company.name()), "BOOTSTRAP_COMPANY_CONFLICT");
            return company.id();
        }
        long id = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO org_company
                (id, tenant_id, company_code, company_name, status, deleted_flag, remark)
                VALUES (?, ?, ?, ?, 'ENABLE', 0, '平台引导创建')
                """, id, tenantId, organization.getCompanyCode().trim(), organization.getCompanyName().trim());
        return id;
    }

    private long getOrCreateDepartment(long tenantId, long companyId) {
        PlatformBootstrapProperties.Organization organization = properties.getOrganization();
        List<DepartmentRow> departments = jdbcTemplate.query(
                "SELECT id, company_id, dept_name FROM org_department WHERE tenant_id=? AND dept_code=? AND deleted_flag=0",
                (rs, rowNum) -> new DepartmentRow(rs.getLong("id"), rs.getLong("company_id"),
                        rs.getString("dept_name")), tenantId, organization.getDepartmentCode().trim());
        if (!departments.isEmpty()) {
            DepartmentRow department = one(departments, "BOOTSTRAP_DEPARTMENT_DUPLICATE");
            require(department.companyId() == companyId
                    && organization.getDepartmentName().trim().equals(department.name()),
                    "BOOTSTRAP_DEPARTMENT_CONFLICT");
            return department.id();
        }
        long id = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO org_department
                (id, tenant_id, company_id, parent_id, dept_code, dept_name, order_num, status, deleted_flag, remark)
                VALUES (?, ?, ?, NULL, ?, ?, 0, 'ENABLE', 0, '平台引导创建')
                """, id, tenantId, companyId, organization.getDepartmentCode().trim(),
                organization.getDepartmentName().trim());
        return id;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T one(List<T> rows, String errorCode) {
        require(rows.size() == 1, errorCode);
        return rows.get(0);
    }

    private static void require(boolean condition, String errorCode) {
        if (!condition) {
            throw new IllegalStateException(errorCode);
        }
    }

    public enum Result { CREATED, ADOPTED, ALREADY_COMPLETED }

    private record StateRow(int version, String status) { }
    private record RoleRow(long id, String status) { }
    private record CompanyRow(long id, String name) { }
    private record DepartmentRow(long id, long companyId, String name) { }
    private record UserRow(long id, String status, int isAdmin) { }
}
