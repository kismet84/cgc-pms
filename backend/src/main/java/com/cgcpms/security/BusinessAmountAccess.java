package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class BusinessAmountAccess {

    public static final String PERMISSION = "business:amount:view";

    private BusinessAmountAccess() {
    }

    public static boolean canView() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> PERMISSION.equals(authority.getAuthority()));
    }

    public static void requireDownload() {
        if (!canView()) {
            throw new BusinessException("AMOUNT_DOWNLOAD_FORBIDDEN", "当前账号无价格型下载权限");
        }
    }
}
