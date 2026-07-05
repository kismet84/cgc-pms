package com.cgcpms.requisition.service;

import com.cgcpms.requisition.entity.MatRequisitionItem;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MatRequisitionServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void itemValidationRequiresMaterialId() {
        MatRequisitionItem item = new MatRequisitionItem();
        item.setRequisitionId(970000000000005704L);
        item.setQuantity(new BigDecimal("104.0000"));

        assertTrue(
                validator.validate(item).stream()
                        .anyMatch(v -> "materialId".equals(v.getPropertyPath().toString())),
                "领料明细保存必须要求真实物料ID，避免名称/单位无法回填并阻断后续出库审批");
    }
}
