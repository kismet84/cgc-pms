package com.cgcpms.file.auth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAccessPolicyRegistryTest {

    @Test
    void completeRegistryResolvesEveryBusinessTypeExactlyOnce() {
        FileAccessPolicyRegistry registry = new FileAccessPolicyRegistry(
                Arrays.stream(FileAccessPolicyRegistry.Group.values())
                        .map(FileAccessPolicyRegistryTest::policy)
                        .toList());

        assertEquals(43, FileAccessPolicyRegistry.knownTypeNames().size());
        for (FileAccessPolicyRegistry.BusinessType businessType
                : FileAccessPolicyRegistry.BusinessType.values()) {
            assertEquals(businessType, registry.require(businessType.name().toLowerCase()));
            assertEquals(businessType.group(), registry.policyFor(businessType).group());
        }
        assertThrows(UnsupportedOperationException.class,
                () -> FileAccessPolicyRegistry.knownTypeNames().add("UNKNOWN"));
    }

    @Test
    void registryRejectsMissingDuplicateAndUnknownPolicyGroupsAtStartup() {
        List<FileAccessPolicy> complete = Arrays.stream(FileAccessPolicyRegistry.Group.values())
                .map(FileAccessPolicyRegistryTest::policy)
                .toList();

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> new FileAccessPolicyRegistry(complete.subList(1, complete.size())));
        assertTrue(missing.getMessage().startsWith("Missing file access policy groups:"));

        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> new FileAccessPolicyRegistry(List.of(
                        complete.getFirst(), complete.getFirst())));
        assertTrue(duplicate.getMessage().startsWith("Duplicate file access policy group:"));

        FileAccessPolicy unknown = policy(null);
        IllegalStateException invalid = assertThrows(IllegalStateException.class,
                () -> new FileAccessPolicyRegistry(List.of(unknown)));
        assertEquals("Unknown file access policy group", invalid.getMessage());
    }

    private static FileAccessPolicy policy(FileAccessPolicyRegistry.Group group) {
        return new FileAccessPolicy() {
            @Override
            public FileAccessPolicyRegistry.Group group() {
                return group;
            }

            @Override
            public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                                    Long businessId,
                                    String action,
                                    boolean write,
                                    String documentType) {
                // Registry contract test only.
            }
        };
    }
}
