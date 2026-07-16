package com.cgcpms.payment;

import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;

import java.math.BigDecimal;

/** 测试专用的最小付款链路夹具。 */
public final class PaymentTestFixtures {

    private PaymentTestFixtures() {
    }

    public static PayApplication insertApplication(
            PayApplicationMapper mapper,
            Long id,
            Long tenantId,
            Long projectId,
            Long contractId,
            Long partnerId,
            BigDecimal amount) {
        PayApplication existing = mapper.selectById(id);
        if (existing != null) {
            return existing;
        }
        PayApplication application = new PayApplication();
        application.setId(id);
        application.setTenantId(tenantId);
        application.setProjectId(projectId);
        application.setContractId(contractId);
        application.setPartnerId(partnerId);
        application.setApplyCode("TEST-PAY-" + id);
        application.setApplyAmount(amount);
        application.setApprovedAmount(amount);
        application.setActualPayAmount(BigDecimal.ZERO);
        application.setPayType("TEST");
        application.setPayStatus("PENDING");
        application.setApprovalStatus("APPROVED");
        application.setIntegrityVersion("LEGACY_UNVERIFIED");
        application.setVersion(0);
        mapper.insert(application);
        return application;
    }
}
