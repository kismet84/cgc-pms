package com.cgcpms.system.dict.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;

import java.util.Locale;

final class DictWriteAuthorizer {

    private DictWriteAuthorizer() {
    }

    static void requireSystemAdmin() {
        boolean admin = UserContext.getCurrentRoles().stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .anyMatch(role -> "ADMIN".equals(role) || "SUPER_ADMIN".equals(role));
        if (!admin) {
            throw new BusinessException("DICT_WRITE_FORBIDDEN", "仅系统管理员可以维护字典分组、类型和字典项");
        }
    }
}
