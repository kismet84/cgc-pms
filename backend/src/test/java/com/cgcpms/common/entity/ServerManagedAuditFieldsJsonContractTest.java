package com.cgcpms.common.entity;

import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ServerManagedAuditFieldsJsonContractTest {

    private static final List<String> BASE_AND_SHADOW_AUDIT_FIELDS = List.of(
            "createdBy", "createdTime", "createdAt",
            "updatedBy", "updatedTime", "updatedAt", "deletedFlag");
    private static final List<String> BASE_AUDIT_FIELDS = List.of(
            "createdBy", "createdAt", "updatedBy", "updatedAt", "deletedFlag");
    private static final List<String> STANDALONE_AUDIT_FIELDS = List.of(
            "createdBy", "createdTime", "updatedBy", "updatedTime", "deletedFlag");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest(name = "{0}")
    @MethodSource("auditTypes")
    void clientCannotBindServerManagedAuditFieldsButServerCanSerializeThem(
            Class<?> type, List<String> auditFields) throws Exception {
        ObjectNode attack = objectMapper.createObjectNode();
        for (String field : auditFields) {
            putAttackValue(attack, field);
        }

        Object requestBound = objectMapper.treeToValue(attack, type);
        BeanWrapper requestBoundFields = new BeanWrapperImpl(requestBound);
        assertAll(auditFields.stream()
                .map(field -> (Executable) () -> assertNull(requestBoundFields.getPropertyValue(field),
                        () -> type.getSimpleName() + "." + field + " accepted a client value"))
                .toList());

        Object serverOwned = type.getDeclaredConstructor().newInstance();
        BeanWrapper serverOwnedFields = new BeanWrapperImpl(serverOwned);
        for (String field : auditFields) {
            serverOwnedFields.setPropertyValue(field, serverValue(field));
        }
        JsonNode response = objectMapper.valueToTree(serverOwned);
        assertAll(auditFields.stream()
                .map(field -> (Executable) () -> assertTrue(response.hasNonNull(field),
                        () -> type.getSimpleName() + "." + field + " is no longer response-readable"))
                .toList());
    }

    static Stream<Arguments> auditTypes() {
        return Stream.of(
                arguments(PmProjectMember.class, BASE_AUDIT_FIELDS),
                arguments(CtContractChange.class, STANDALONE_AUDIT_FIELDS),
                arguments(OrgCompany.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(OrgDepartment.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(OrgPosition.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(MatPurchaseRequest.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(MatPurchaseRequestItem.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(CostTarget.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(CostTargetItem.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(MatWarehouse.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(MatRequisition.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(MatRequisitionItem.class, BASE_AND_SHADOW_AUDIT_FIELDS),
                arguments(PayInvoice.class, BASE_AND_SHADOW_AUDIT_FIELDS));
    }

    private static void putAttackValue(ObjectNode attack, String field) {
        if (field.endsWith("Time")) {
            attack.put(field, "1999-01-01 00:00:00");
        } else if (field.endsWith("At")) {
            attack.put(field, "1999-01-01T00:00:00");
        } else if ("deletedFlag".equals(field)) {
            attack.put(field, 1);
        } else {
            attack.put(field, 999L);
        }
    }

    private static Object serverValue(String field) {
        if (isTimestamp(field)) return LocalDateTime.of(2026, 8, 13, 9, 30);
        if ("deletedFlag".equals(field)) return 0;
        return 7L;
    }

    private static boolean isTimestamp(String field) {
        return field.endsWith("At") || field.endsWith("Time");
    }
}
