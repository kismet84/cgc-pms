package com.cgcpms.file.scan;

public interface VirusScanner {

    ScanResult scan(byte[] content);

    record ScanResult(Status status, String detail) {
        public enum Status {
            CLEAN,
            INFECTED,
            UNAVAILABLE
        }

        public static ScanResult clean() {
            return new ScanResult(Status.CLEAN, null);
        }

        public static ScanResult infected(String threatName) {
            return new ScanResult(Status.INFECTED, threatName);
        }

        public static ScanResult unavailable(String detail) {
            return new ScanResult(Status.UNAVAILABLE, detail);
        }
    }
}
