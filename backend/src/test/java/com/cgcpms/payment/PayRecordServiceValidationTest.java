package com.cgcpms.payment;

import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PayRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PayRecordServiceValidationTest {

    @Mock PayRecordMapper payRecordMapper;
    @Mock PayApplicationMapper payApplicationMapper;
    @Mock CtContractMapper contractMapper;
    @Mock PayApplicationService payApplicationService;
    @Mock CostSummaryService costSummaryService;
    @Mock CashJournalService cashJournalService;
    @InjectMocks PayRecordService payRecordService;

    @Test
    void rejectsNullInputBeforeStorageAccess() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(null));

        assertEquals("PAY_WRITEBACK_REQUIRED", error.getCode());
        verifyNoStorageAccess();
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void rejectsInvalidAmountBeforeStorageAccess(BigDecimal amount) {
        PayRecord input = validInput();
        input.setPayAmount(amount);

        BusinessException error = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(input));

        assertEquals("PAY_AMOUNT_INVALID", error.getCode());
        verifyNoStorageAccess();
    }

    @Test
    void rejectsMissingPayDateBeforeStorageAccess() {
        PayRecord input = validInput();
        input.setPayDate(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(input));

        assertEquals("PAY_DATE_REQUIRED", error.getCode());
        verifyNoStorageAccess();
    }

    private static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(null, BigDecimal.ZERO, new BigDecimal("-0.01"),
                new BigDecimal("1.001"), new BigDecimal("10000000000000000.00"));
    }

    private PayRecord validInput() {
        PayRecord input = new PayRecord();
        input.setPayApplicationId(1L);
        input.setPayAmount(new BigDecimal("1.00"));
        input.setPayDate(LocalDate.of(2026, 7, 10));
        input.setExternalTxnNo("VALIDATION-001");
        return input;
    }

    private void verifyNoStorageAccess() {
        verifyNoInteractions(payRecordMapper, payApplicationMapper, contractMapper,
                payApplicationService, costSummaryService, cashJournalService);
    }
}
