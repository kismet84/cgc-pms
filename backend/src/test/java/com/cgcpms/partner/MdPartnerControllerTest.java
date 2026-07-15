package com.cgcpms.partner;

import com.cgcpms.partner.entity.MdPartner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdPartnerControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsNullZeroAndUpperBoundIntegerLeadDays() throws Exception {
        for (String value : new String[]{"null", "0", "3650"}) {
            MdPartner partner = objectMapper.readValue(json(value), MdPartner.class);
            assertTrue(validator.validate(partner).isEmpty(), "合法默认提前期应通过: " + value);
        }
    }

    @Test
    void rejectsFractionNegativeAndAboveUpperBoundLeadDays() throws Exception {
        for (String value : new String[]{"1.5", "-1", "3651"}) {
            MdPartner partner = objectMapper.readValue(json(value), MdPartner.class);
            assertEquals(1, validator.validate(partner).stream()
                    .filter(v -> "defaultLeadDays".equals(v.getPropertyPath().toString()))
                    .count(), "非法默认提前期应被 Bean Validation 拒绝: " + value);
        }
    }

    private String json(String defaultLeadDays) {
        return "{\"partnerName\":\"供应商\",\"partnerType\":\"SUPPLIER\",\"defaultLeadDays\":"
                + defaultLeadDays + "}";
    }
}
