package com.cgcpms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public final class DataMaintenanceTablePolicy {
    private static final String RESOURCE = "data-maintenance-table-policy.json";

    private final List<Group> groups;
    private final String fingerprint;

    @Autowired
    public DataMaintenanceTablePolicy(ObjectMapper objectMapper) {
        try {
            byte[] bytes = new ClassPathResource(RESOURCE).getContentAsByteArray();
            PolicyFile file = objectMapper.readValue(bytes, PolicyFile.class);
            validate(file);
            this.groups = List.copyOf(file.groups());
            this.fingerprint = sha256(bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load " + RESOURCE, ex);
        }
    }

    DataMaintenanceTablePolicy(List<Group> groups, String fingerprint) {
        validate(new PolicyFile(1, groups));
        this.groups = List.copyOf(groups);
        this.fingerprint = fingerprint;
    }

    public List<Group> groups() {
        return groups;
    }

    public String fingerprint() {
        return fingerprint;
    }

    private static void validate(PolicyFile file) {
        if (file == null || file.version() != 1 || file.groups() == null || file.groups().isEmpty()) {
            throw new IllegalStateException("Invalid data-maintenance table policy");
        }
        Set<String> seenGroups = new HashSet<>();
        Set<String> seenTables = new HashSet<>();
        for (Group group : file.groups()) {
            if (group == null || group.code() == null || group.code().isBlank()
                    || group.disposition() == null || group.tables() == null || group.tables().isEmpty()) {
                throw new IllegalStateException("Invalid data-maintenance policy group");
            }
            if (!seenGroups.add(group.code())) {
                throw new IllegalStateException("Duplicate policy group: " + group.code());
            }
            for (String table : group.tables()) {
                if (table == null || !table.matches("[A-Za-z0-9_]+")
                        || !seenTables.add(table.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("Invalid or duplicate policy table: " + table);
                }
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record PolicyFile(int version, List<Group> groups) { }

    public record Group(String code, Disposition disposition, List<String> tables) {
        public Group {
            tables = tables == null ? null : List.copyOf(tables);
        }
    }

    public enum Disposition { RETAIN, CLEAR }
}
