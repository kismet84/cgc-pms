package com.cgcpms.file.auth;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

final class FileAuthorityPolicy {

    void requireAccess(FileAccessPolicyRegistry.BusinessType businessType,
                       boolean write,
                       String genericAuthority,
                       String cashJournalAuthority,
                       String documentType) {
        switch (businessType.authorityMode()) {
            case GENERIC -> requireAuthority(genericAuthority);
            case CASH_JOURNAL -> requireAuthority(cashJournalAuthority);
            case FIXED -> requireAnyAuthority(
                    businessType.authorities(write), true, denialMessage(businessType));
            case VARIATION -> {
                if (write) requireAuthority(genericAuthority);
                else requireAnyAuthority(businessType.authorities(false), true, "无权执行该文件操作");
            }
            case BID -> requireAnyAuthority(
                    businessType.authorities(write), false, "无权执行投标文件操作");
            case MEASUREMENT -> {
                if (write) requireAuthority(measurementFileAuthority(documentType));
                else requireAnyAuthority(businessType.authorities(false), true, "无权执行该文件操作");
            }
        }
    }

    void requireSourceReadAuthority(FileAccessPolicyRegistry.BusinessType businessType) {
        if (businessType.sourceReadAuthority() != null) {
            requireAuthority(businessType.sourceReadAuthority());
        }
    }

    void requireAuthority(String requiredAuthority) {
        requireAnyAuthority(Set.of(requiredAuthority), true, "无权执行该文件操作");
    }

    String measurementFileAuthority(String documentType) {
        String type = documentType == null ? "" : documentType.toUpperCase();
        if ("OWNER_SUBMISSION".equals(type)) return "measurement:owner:submit";
        if ("MEASUREMENT_GENERAL".equals(type) || type.startsWith("ML_")) return "measurement:submit";
        throw new BusinessException("MEASUREMENT_DOCUMENT_STAGE_INVALID", "不支持的产值计量附件类型");
    }

    private void requireAnyAuthority(Set<String> requiredAuthorities,
                                     boolean allowAdmin,
                                     String deniedMessage) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthorities.contains(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority)
                        || (allowAdmin && "ROLE_ADMIN".equals(authority)));
        if (!allowed) throw new BusinessException("FILE_ACCESS_DENIED", deniedMessage);
    }

    private String denialMessage(FileAccessPolicyRegistry.BusinessType businessType) {
        String name = businessType.name();
        if (name.startsWith("QS_")) return "无权执行该质量安全文件操作";
        if (name.startsWith("SUPPLIER_")) return "无权执行该供应商招采文件操作";
        if (name.startsWith("TECH_")) return "无权执行该技术管理文件操作";
        if (name.startsWith("CLOSEOUT_")) return "无权执行该项目收尾文件操作";
        return "无权执行该文件操作";
    }
}
