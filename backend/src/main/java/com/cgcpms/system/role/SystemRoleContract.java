package com.cgcpms.system.role;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class SystemRoleContract {

    public static final String COMPANY_OWNER = "COMPANY_OWNER";
    public static final String COMPANY_FINANCE = "COMPANY_FINANCE";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String PROJECT_ACCOUNTANT = "PROJECT_ACCOUNTANT";
    public static final String TECHNICAL_LEAD = "TECHNICAL_LEAD";
    public static final String SAFETY_LEAD = "SAFETY_LEAD";
    public static final String CONSTRUCTION_LEAD = "CONSTRUCTION_LEAD";
    public static final String PROCUREMENT_LEAD = "PROCUREMENT_LEAD";
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String HIDDEN_SUPER_ADMIN = "SUPER_ADMIN";

    public static final List<String> VISIBLE_ROLE_CODES = List.of(
            COMPANY_OWNER,
            COMPANY_FINANCE,
            PROJECT_MANAGER,
            PROJECT_ACCOUNTANT,
            TECHNICAL_LEAD,
            SAFETY_LEAD,
            CONSTRUCTION_LEAD,
            PROCUREMENT_LEAD,
            EMPLOYEE);

    private static final Set<String> VISIBLE_ROLE_CODE_SET = Set.copyOf(VISIBLE_ROLE_CODES);
    public static final Set<String> PROJECT_SCOPED_ROLE_CODES = Set.of(
            PROJECT_MANAGER, PROJECT_ACCOUNTANT, TECHNICAL_LEAD, SAFETY_LEAD,
            CONSTRUCTION_LEAD, PROCUREMENT_LEAD, EMPLOYEE);
    public static final Set<String> LEGACY_PROJECT_ROLE_CODES = Set.of(
            "PM", "CM", "CSTM", "MAT", "SUBC", "FIN", "OTH");

    private SystemRoleContract() {
    }

    public static boolean isVisible(String roleCode) {
        return roleCode != null && VISIBLE_ROLE_CODE_SET.contains(roleCode);
    }

    public static boolean isFinanceAdministrator(Collection<String> roleCodes) {
        return roleCodes != null
                && roleCodes.contains(COMPANY_FINANCE)
                && roleCodes.contains(HIDDEN_SUPER_ADMIN);
    }

    public static String canonicalRoleCode(String roleCode) {
        if (roleCode == null) return null;
        return switch (roleCode) {
            case "FINANCE" -> COMPANY_FINANCE;
            case "GENERAL_MANAGER", "MANAGEMENT", "MANAGEMENT_EXECUTIVE" -> COMPANY_OWNER;
            case "COST_MANAGER", "COMMERCIAL_MANAGER", "DEPARTMENT_MANAGER", "CSTM", "CM", "FIN" -> PROJECT_ACCOUNTANT;
            case "CHIEF_ENGINEER" -> TECHNICAL_LEAD;
            case "PRODUCTION_MANAGER", "SUBC" -> CONSTRUCTION_LEAD;
            case "PURCHASE_MANAGER", "MATERIAL_CLERK", "MAT" -> PROCUREMENT_LEAD;
            case "COMMON_USER", "OTH" -> EMPLOYEE;
            case "PM" -> PROJECT_MANAGER;
            default -> roleCode;
        };
    }
}
