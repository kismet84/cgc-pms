package com.cgcpms.file.auth;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;

import java.util.Set;

final class FilePolicySupport {

    private FilePolicySupport() {}

    static void checkProjectAccess(ProjectAccessChecker projectAccessChecker,
                                   Long projectId,
                                   String action) {
        if (projectId == null) {
            throw new BusinessException("FILE_ACCESS_DENIED", "业务对象缺少项目关系，拒绝访问文件");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    static boolean isEditableDocumentStatus(String approvalStatus) {
        return "DRAFT".equals(approvalStatus) || "REJECTED".equals(approvalStatus);
    }

    static void requireInvoiceDocumentType(String documentType) {
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        if (!Set.of("ELECTRONIC_INVOICE", "SCANNED_INVOICE").contains(type)) {
            throw new BusinessException("INVOICE_DOCUMENT_TYPE_INVALID",
                    "发票附件仅支持电子发票或扫描件");
        }
    }

    static String value(Object value) {
        return value == null ? null : value.toString();
    }
}
