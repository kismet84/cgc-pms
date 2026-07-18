package com.cgcpms.bootstrap.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Getter
@Setter
@ConfigurationProperties(prefix = "cgc-pms.bootstrap")
public class PlatformBootstrapProperties {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.@-]{3,64}");
    private static final Pattern STRONG_PASSWORD = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}$");

    private boolean enabled;
    private int version = 1;
    private long tenantId;
    private Organization organization = new Organization();
    private Administrator administrator = new Administrator();

    public void validateForExecution() {
        if (!enabled) {
            return;
        }
        require(version == 1, "BOOTSTRAP_VERSION_UNSUPPORTED");
        require(tenantId == 0, "BOOTSTRAP_TENANT_UNSUPPORTED");
        requireCode(organization.companyCode, "BOOTSTRAP_COMPANY_CODE_INVALID");
        requireText(organization.companyName, "BOOTSTRAP_COMPANY_NAME_REQUIRED");
        requireCode(organization.departmentCode, "BOOTSTRAP_DEPARTMENT_CODE_INVALID");
        requireText(organization.departmentName, "BOOTSTRAP_DEPARTMENT_NAME_REQUIRED");
        require(USERNAME.matcher(value(administrator.username)).matches(), "BOOTSTRAP_USERNAME_INVALID");
        requireText(administrator.realName, "BOOTSTRAP_REAL_NAME_REQUIRED");
        require("SUPER_ADMIN".equals(administrator.roleCode), "BOOTSTRAP_ROLE_UNSUPPORTED");
        require(STRONG_PASSWORD.matcher(value(administrator.password)).matches(), "BOOTSTRAP_PASSWORD_WEAK");
        if (StringUtils.hasText(administrator.email)) {
            require(administrator.email.contains("@") && administrator.email.length() <= 128,
                    "BOOTSTRAP_EMAIL_INVALID");
        }
    }

    private static void requireCode(String value, String code) {
        require(CODE.matcher(value(value)).matches(), code);
    }

    private static void requireText(String value, String code) {
        require(StringUtils.hasText(value), code);
    }

    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return "PlatformBootstrapProperties{enabled=" + enabled + ", version=" + version
                + ", tenantId=" + tenantId + ", organization=" + organization
                + ", administrator=" + administrator.safeDescription() + '}';
    }

    @Getter
    @Setter
    public static class Organization {
        private String companyCode = "ROOT";
        private String companyName = "总公司";
        private String departmentCode = "ROOT_DEPT";
        private String departmentName = "总部";

        @Override
        public String toString() {
            return "Organization{companyCode='" + companyCode + "', departmentCode='" + departmentCode + "'}";
        }
    }

    @Getter
    @Setter
    public static class Administrator {
        private String username = "admin";
        private String realName = "平台管理员";
        private String email;
        private String password;
        private String roleCode = "SUPER_ADMIN";

        private String safeDescription() {
            return "Administrator{username='" + username + "', roleCode='" + roleCode + "', password='***'}";
        }

        @Override
        public String toString() {
            return safeDescription();
        }
    }
}
