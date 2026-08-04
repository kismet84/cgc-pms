package com.cgcpms.payment;

import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentCurrentReadMapperContractTest {

    @Test
    void financialAmountReadsUseMySqlLockingCurrentRead() throws Exception {
        assertForUpdate(PayApplicationMapper.class.getMethod(
                "selectEffectiveByContractForUpdate", Long.class, Long.class, Long.class));
        assertForUpdate(PayRecordMapper.class.getMethod(
                "selectSuccessByContractForUpdate", Long.class, Long.class));
        assertForUpdate(PayRecordMapper.class.getMethod(
                "selectSuccessByApplicationForUpdate", Long.class, Long.class));
        assertForUpdate(PayRecordMapper.class.getMethod(
                "selectByExternalTxnNoForUpdate", Long.class, String.class));
        assertForUpdate(PayRecordMapper.class.getMethod(
                "lockTenantPaymentCodeScope", Long.class));
        assertForUpdate(CashJournalEntryMapper.class.getMethod(
                "selectByPayRecordForUpdate", Long.class, Long.class));
    }

    private void assertForUpdate(Method method) {
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value())).toUpperCase();
        assertTrue(sql.contains("FOR UPDATE"), method + " must use MySQL locking current read");
    }
}
