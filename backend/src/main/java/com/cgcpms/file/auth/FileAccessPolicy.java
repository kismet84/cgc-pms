package com.cgcpms.file.auth;

interface FileAccessPolicy {

    FileAccessPolicyRegistry.Group group();

    void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                     Long businessId,
                     String action,
                     boolean write,
                     String documentType);

    default void checkDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                    Long businessId,
                                    String documentType) {
        // Most file types do not have a second-stage document gate.
    }
}
