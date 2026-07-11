package com.cgcpms.common.aspect;

import com.cgcpms.cashbook.dto.FundAccountCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskingAspectTest {

    record DateBody(LocalDate plannedDate) {}

    @Test
    void maskDtoHandlesLocalDateWithoutReflectingIntoJdkInternals() throws Exception {
        SensitiveDataMaskingAspect aspect = new SensitiveDataMaskingAspect();
        Method maskDto = SensitiveDataMaskingAspect.class.getDeclaredMethod("maskDto", Object.class);
        maskDto.setAccessible(true);

        Object result = assertDoesNotThrow(() -> maskDto.invoke(aspect, new DateBody(LocalDate.of(2026, 6, 28))));

        assertTrue(String.valueOf(result).contains("plannedDate=2026-06-28"));
    }

    @Test
    void maskDtoDoesNotExposeFundAccountBankAccountNumber() throws Exception {
        SensitiveDataMaskingAspect aspect = new SensitiveDataMaskingAspect();
        Method maskDto = SensitiveDataMaskingAspect.class.getDeclaredMethod("maskDto", Object.class);
        maskDto.setAccessible(true);
        FundAccountCommand command = new FundAccountCommand();
        command.setBankAccountNo("6222021234567890");

        String result = String.valueOf(maskDto.invoke(aspect, command));

        assertTrue(result.contains("bankAccountNo=****7890"));
        assertFalse(result.contains("6222021234567890"));
    }
}
