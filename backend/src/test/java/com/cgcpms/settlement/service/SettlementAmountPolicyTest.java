package com.cgcpms.settlement.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementAmountPolicyTest {

    @Test
    void calculatesTheAuthoritativeSettlementSnapshot() {
        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                new BigDecimal("300.00"));

        assertEquals(new BigDecimal("1250.00"), snapshot.finalAmount());
        assertEquals(new BigDecimal("62.50"), snapshot.warrantyAmount());
        assertEquals(new BigDecimal("887.50"), snapshot.unpaidAmount());
        assertEquals(SettlementAmountPolicy.FORMULA_VERSION, snapshot.formulaVersion());
    }

    @Test
    void normalizesNullsAndRoundsOnlyAtTheMoneyBoundary() {
        SettlementAmountSnapshot snapshot = SettlementAmountPolicy.calculate(
                new BigDecimal("10.005"), null, null, null, null);

        assertEquals(new BigDecimal("10.01"), snapshot.effectiveContractAmount());
        assertEquals(new BigDecimal("10.01"), snapshot.finalAmount());
        assertEquals(new BigDecimal("0.50"), snapshot.warrantyAmount());
        assertEquals(new BigDecimal("9.51"), snapshot.unpaidAmount());
    }
}
