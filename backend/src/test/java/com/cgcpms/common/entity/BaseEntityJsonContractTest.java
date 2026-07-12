package com.cgcpms.common.entity;

import com.cgcpms.material.entity.MdMaterial;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseEntityJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void remarkIsWritableWhileProtectedFieldsRemainReadOnly() throws Exception {
        MdMaterial material = objectMapper.readValue("""
                {
                  "id": 1,
                  "tenantId": 2,
                  "createdBy": 3,
                  "createdAt": "2026-07-12 10:00:00",
                  "updatedBy": 4,
                  "updatedAt": "2026-07-12 11:00:00",
                  "deletedFlag": 1,
                  "remark": "client remark"
                }
                """, MdMaterial.class);

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(material));

        assertAll(
                () -> assertEquals("client remark", material.getRemark()),
                () -> assertEquals("client remark", serialized.get("remark").asText()),
                () -> assertNull(material.getId()),
                () -> assertNull(material.getTenantId()),
                () -> assertNull(material.getCreatedBy()),
                () -> assertNull(material.getCreatedAt()),
                () -> assertNull(material.getUpdatedBy()),
                () -> assertNull(material.getUpdatedAt()),
                () -> assertNull(material.getDeletedFlag())
        );
    }
}
