package com.cgcpms.file.vo;

public enum FileVirusScanStatus {
    NOT_SCANNED("VIRUS_SCAN_NOT_SCANNED", "文件尚未完成病毒扫描，未标记为安全检查通过", false),
    NOT_CONFIGURED("VIRUS_SCAN_NOT_CONFIGURED", "未接入病毒扫描能力，文件未标记为安全检查通过", false),
    FAILED("VIRUS_SCAN_FAILED", "病毒扫描失败，文件未标记为安全检查通过", false);

    private final String code;
    private final String message;
    private final boolean passed;

    FileVirusScanStatus(String code, String message, boolean passed) {
        this.code = code;
        this.message = message;
        this.passed = passed;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public boolean passed() {
        return passed;
    }
}
