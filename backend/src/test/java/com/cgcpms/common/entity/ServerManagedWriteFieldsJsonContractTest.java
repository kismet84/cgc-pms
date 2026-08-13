package com.cgcpms.common.entity;

import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ServerManagedWriteFieldsJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest(name = "{0}")
    @MethodSource("managedTypes")
    void clientCannotBindServerManagedFieldsButServerCanSerializeThem(
            Class<?> type, List<ManagedField> fields) throws Exception {
        ObjectNode attack = objectMapper.createObjectNode();
        fields.forEach(field -> attack.set(field.name(), objectMapper.valueToTree(field.value())));

        BeanWrapper request = new BeanWrapperImpl(objectMapper.treeToValue(attack, type));
        assertAll(fields.stream()
                .map(field -> (Executable) () -> assertNull(request.getPropertyValue(field.name()),
                        () -> type.getSimpleName() + "." + field.name() + " accepted a client value"))
                .toList());

        Object responseEntity = type.getDeclaredConstructor().newInstance();
        BeanWrapper server = new BeanWrapperImpl(responseEntity);
        fields.forEach(field -> server.setPropertyValue(field.name(), field.value()));
        JsonNode response = objectMapper.valueToTree(responseEntity);
        assertAll(fields.stream()
                .map(field -> (Executable) () -> assertTrue(response.hasNonNull(field.name()),
                        () -> type.getSimpleName() + "." + field.name() + " is no longer response-readable"))
                .toList());
    }

    static Stream<Arguments> managedTypes() {
        return Stream.of(
                arguments(CtContractChange.class, fields(
                        lng("id"), lng("tenantId"), text("changeCode"), text("approvalStatus"),
                        integer("effectiveFlag"), integer("costGeneratedFlag"))),
                arguments(OrgCompany.class, fields(lng("id"), lng("tenantId"))),
                arguments(OrgDepartment.class, fields(lng("id"), lng("tenantId"))),
                arguments(OrgPosition.class, fields(lng("id"), lng("tenantId"))),
                arguments(MatPurchaseRequest.class, fields(
                        lng("id"), lng("tenantId"), text("requestCode"),
                        text("approvalStatus"), text("status"))),
                arguments(MatPurchaseRequestItem.class, fields(
                        lng("id"), lng("tenantId"), lng("requestId"),
                        decimal("approvedQuantity"), integer("approvalVersion"))),
                arguments(CostTarget.class, fields(
                        lng("id"), lng("tenantId"), integer("isActive"),
                        text("approvalStatus"), text("status"))),
                arguments(CostTargetItem.class, fields(
                        lng("id"), lng("tenantId"), lng("targetId"), lng("projectId"))),
                arguments(MatWarehouse.class, fields(
                        lng("id"), lng("tenantId"), text("warehouseCode"))));
    }

    private static List<ManagedField> fields(ManagedField... fields) {
        return List.of(fields);
    }

    private static ManagedField lng(String name) {
        return new ManagedField(name, 999L);
    }

    private static ManagedField integer(String name) {
        return new ManagedField(name, 1);
    }

    private static ManagedField decimal(String name) {
        return new ManagedField(name, new BigDecimal("999.00"));
    }

    private static ManagedField text(String name) {
        return new ManagedField(name, "CLIENT_VALUE");
    }

    private record ManagedField(String name, Object value) {
    }
}
