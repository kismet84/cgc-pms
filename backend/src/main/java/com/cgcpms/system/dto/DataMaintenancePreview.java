package com.cgcpms.system.dto;

import java.util.List;

public record DataMaintenancePreview(
        String database,
        String policyFingerprint,
        boolean eligible,
        List<String> blockers,
        List<RetainedGroupCount> retainedGroups,
        int clearTableCount,
        long clearRowCount,
        long sysFileCount,
        List<String> ignoredViews
) {
    public record RetainedGroupCount(String code, int tableCount, long rowCount) { }
}
