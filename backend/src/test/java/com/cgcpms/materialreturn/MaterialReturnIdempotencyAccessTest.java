package com.cgcpms.materialreturn;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostFactLineageResolver;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.materialreturn.dto.MaterialReturnRequest;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import com.cgcpms.materialreturn.mapper.MaterialReturnItemMapper;
import com.cgcpms.materialreturn.mapper.MaterialReturnMapper;
import com.cgcpms.materialreturn.service.MaterialReturnService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaterialReturnIdempotencyAccessTest {

    private final MaterialReturnMapper returnMapper = mock(MaterialReturnMapper.class);
    private final MaterialReturnItemMapper returnItemMapper = mock(MaterialReturnItemMapper.class);
    private final ProjectAccessChecker projectAccessChecker = mock(ProjectAccessChecker.class);
    private final MaterialReturnService service = new MaterialReturnService(
            returnMapper,
            returnItemMapper,
            mock(MatRequisitionMapper.class),
            mock(MatRequisitionItemMapper.class),
            mock(MatStockTxnMapper.class),
            mock(CostItemMapper.class),
            mock(CostFactLineageResolver.class),
            mock(MatStockService.class),
            projectAccessChecker,
            mock(AccountingPeriodGuard.class));
    private final MaterialReturnRequest request = new MaterialReturnRequest(
            201L, 301L, new BigDecimal("2.0000"),
            LocalDate.of(2026, 7, 20), "退回余料", "RETURN-KEY-1");

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims()
                .add("userId", 77L)
                .add("username", "outsider")
                .add("tenantId", 7L)
                .add("roleCodes", List.of())
                .build());
        MaterialReturn existing = new MaterialReturn();
        existing.setId(401L);
        existing.setTenantId(7L);
        existing.setProjectId(12L);
        existing.setReturnDate(request.returnDate());
        existing.setReason(request.reason());
        when(returnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void idempotencyHitDoesNotLeakReturnIdAcrossProjectBoundary() {
        doThrow(new BusinessException("PROJECT_ACCESS_DENIED", "无权访问项目"))
                .when(projectAccessChecker).checkAccess(12L, "确认材料退料");

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirm(request));

        assertEquals("PROJECT_ACCESS_DENIED", error.getCode());
        verifyNoInteractions(returnItemMapper);
    }

    @Test
    void idempotencyHitRejectsDifferentRequestFacts() {
        MaterialReturnItem item = new MaterialReturnItem();
        item.setRequisitionItemId(request.requisitionItemId());
        item.setOriginalStockTxnId(request.originalStockTxnId());
        item.setQuantity(new BigDecimal("1.0000"));
        when(returnItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(item);

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirm(request));

        assertEquals("MATERIAL_RETURN_IDEMPOTENCY_CONFLICT", error.getCode());
    }

    @Test
    void idempotencyHitReturnsExistingIdForSameRequestFacts() {
        MaterialReturnItem item = new MaterialReturnItem();
        item.setRequisitionItemId(request.requisitionItemId());
        item.setOriginalStockTxnId(request.originalStockTxnId());
        item.setQuantity(request.quantity());
        when(returnItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(item);

        assertEquals(401L, service.confirm(request));
    }
}
