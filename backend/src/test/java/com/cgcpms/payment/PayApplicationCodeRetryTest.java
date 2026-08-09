package com.cgcpms.payment;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentApplicationIntegrityService;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @SuppressWarnings("unchecked")
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
        SysDictDataService dictDataService = mock(SysDictDataService.class);
        when(dictDataService.requireEnabledValue(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        PayApplicationService service = newService(mapper, contractMapper, dictDataService);

        String prefix = "PAY-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        when(mapper.selectLastCodeByPrefix(any(), any())).thenReturn(prefix + "001");
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
        app.setPayType("PROGRESS");
        Long id = service.create(app);

        assertEquals(42L, id);
        assertEquals(prefix + "003", app.getApplyCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitRejectsPayTypeDisabledAfterDraftCreation() {
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
        SysDictDataService dictDataService = mock(SysDictDataService.class);
        when(dictDataService.requireEnabledValue(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        PayApplicationService service = newService(mapper, contractMapper, dictDataService);

        when(mapper.selectLastCodeByPrefix(any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            PayApplication app = invocation.getArgument(0);
            app.setId(43L);
            return 1;
        }).when(mapper).insert(any(PayApplication.class));

        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPayType("PROGRESS");
        assertEquals(43L, service.create(app));
        when(mapper.selectContractId(43L, 0L)).thenReturn(30001L);
        when(contractMapper.selectByIdForUpdate(30001L, 0L)).thenReturn(contract);
        when(mapper.selectByIdForUpdate(43L, 0L)).thenReturn(app);
        when(dictDataService.requireEnabledValue(any(), any(), any(), any()))
                .thenThrow(new BusinessException("PAY_TYPE_INVALID", "付款类型不合法"));

        assertEquals("PAY_TYPE_INVALID",
                assertThrows(BusinessException.class, () -> service.submitForApproval(43L)).getCode());
    }

    private PayApplicationService newService(PayApplicationMapper mapper,
                                             CtContractMapper contractMapper,
                                             SysDictDataService dictDataService) {
        return new PayApplicationService(
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
                mock(PaymentApplicationIntegrityService.class),
                mock(PaymentApplicationSourceService.class),
                dictDataService,
                mock(FileLifecycleGateway.class),
                new CodeGenerationService(),
                mock(WorkflowEngine.class));
    }
}
