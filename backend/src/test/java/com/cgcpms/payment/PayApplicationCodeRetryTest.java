package com.cgcpms.payment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayApplicationCodeRetryTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void createRegeneratesApplyCodeAfterDuplicateKey() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", 0L)
                .build());

        PayApplicationMapper mapper = mock(PayApplicationMapper.class);
        CtContractMapper contractMapper = mock(CtContractMapper.class);
        CtContract contract = new CtContract();
        contract.setTenantId(0L);
        contract.setProjectId(10001L);
        when(contractMapper.selectById(30001L)).thenReturn(contract);
        PayApplicationService service = new PayApplicationService(
                mapper,
                mock(PayApplicationBasisMapper.class),
                mock(PmProjectMapper.class),
                contractMapper,
                mock(MdPartnerMapper.class),
                mock(MatReceiptItemMapper.class),
                mock(SubMeasureItemMapper.class),
                mock(MatReceiptMapper.class),
                mock(SubMeasureMapper.class),
                mock(CtContractPaymentTermMapper.class),
                mock(PayRecordMapper.class),
                mock(ProjectAccessChecker.class),
                mock(WorkflowEngine.class));

        String prefix = "PAY-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        PayApplication last = new PayApplication();
        last.setApplyCode(prefix + "001");
        Page<PayApplication> page = new Page<>(0, 1);
        page.setRecords(List.of(last));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page);
        doThrow(new DuplicateKeyException("dup"))
                .doAnswer(invocation -> {
                    PayApplication app = invocation.getArgument(0);
                    app.setId(42L);
                    return 1;
                })
                .when(mapper).insert(any(PayApplication.class));

        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        Long id = service.create(app);

        assertEquals(42L, id);
        assertEquals(prefix + "003", app.getApplyCode());
    }
}
