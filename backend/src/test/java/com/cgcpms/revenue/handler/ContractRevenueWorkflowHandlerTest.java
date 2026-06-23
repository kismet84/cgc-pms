package com.cgcpms.revenue.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("ContractRevenueWorkflowHandler — approval lifecycle tests")
class ContractRevenueWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired private ContractRevenueWorkflowHandler handler;
    @Autowired private ContractRevenueMapper revenueMapper;

    @BeforeEach void setupContext() {
        UserContext.set(Jwts.claims().add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_0).add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach void clearContext() { UserContext.clear(); }

    @Test @DisplayName("supportBusinessType -> CONTRACT_REVENUE")
    void testSupportBusinessType() { assertEquals("CONTRACT_REVENUE", handler.supportBusinessType()); }
    @Test @DisplayName("isCritical -> true")
    void testIsCritical() { assertTrue(handler.isCritical()); }

    @Test @Transactional @DisplayName("onApproved -> service.onApproved called")
    void testOnApproved() {
        ContractRevenue rev = new ContractRevenue();
        rev.setContractId(30001L); rev.setProjectId(10001L); rev.setRevenueCode("RV-HDLR-" + System.nanoTime());
        rev.setRevenueAmount(new BigDecimal("10000.00")); rev.setRevenueDate(LocalDate.now());
        rev.setProgressPercent(new BigDecimal("50.00"));
        rev.setApprovalStatus("APPROVING"); rev.setTenantId(0L);
        revenueMapper.insert(rev);

        WfInstance instance = new WfInstance(); instance.setBusinessId(rev.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onApproved(ctx);
        ContractRevenue updated = revenueMapper.selectById(rev.getId());
        assertNotNull(updated);
        // Handler delegates to service.onApproved, which changes status via @Transactional
        assertNotNull(updated.getApprovalStatus());
    }

    @Test @Transactional @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance(); instance.setId(9201L);
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);
        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx));
    }

    @Test @Transactional @DisplayName("onRejected -> service.onRejected called")
    void testOnRejected() {
        ContractRevenue rev = new ContractRevenue();
        rev.setContractId(30001L); rev.setProjectId(10001L); rev.setRevenueCode("RV-HDLR-REJ-" + System.nanoTime());
        rev.setRevenueAmount(new BigDecimal("5000.00")); rev.setRevenueDate(LocalDate.now());
        rev.setApprovalStatus("APPROVING"); rev.setTenantId(0L);
        revenueMapper.insert(rev);

        WfInstance instance = new WfInstance(); instance.setBusinessId(rev.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onRejected(ctx);
        assertNotNull(revenueMapper.selectById(rev.getId()).getApprovalStatus());
    }
}
