package com.cgcpms.bid;

import com.cgcpms.bid.dto.BidDocumentCreateRequest;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.entity.BidDocumentVersion;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.mapper.BidDocumentVersionMapper;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class BidDocumentVersionServiceTest {

    @Mock BidDocumentVersionMapper mapper;
    @Mock BidCostMapper bidCostMapper;
    @Mock SysFileMapper sysFileMapper;
    @Mock ProjectAccessChecker projectAccessChecker;

    private BidDocumentVersionService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        service = new BidDocumentVersionService(mapper, bidCostMapper, sysFileMapper, projectAccessChecker);
    }

    @AfterEach
    void clear() {
        TestUserContext.clear();
    }

    @Test
    void finalizedVersionCannotBeFinalizedInPlaceAgain() {
        when(bidCostMapper.selectById(1L)).thenReturn(bid(1L, TestUserContext.TENANT_0));
        BidDocumentVersion version = new BidDocumentVersion();
        version.setId(10L);
        version.setTenantId(TestUserContext.TENANT_0);
        version.setBidCostId(1L);
        version.setStatus("FINAL");
        version.setCurrentToken(0L);
        when(mapper.selectById(10L)).thenReturn(version);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.finalizeVersion(1L, 10L));

        assertEquals("BID_DOCUMENT_IMMUTABLE", error.getCode());
    }

    @Test
    void crossTenantBidIsHidden() {
        when(bidCostMapper.selectById(2L)).thenReturn(bid(2L, 999L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.list(2L));

        assertEquals("BID_COST_NOT_FOUND", error.getCode());
    }

    @Test
    void boundBidOutsideProjectScopeIsRejected() {
        BidCost bid = bid(21L, TestUserContext.TENANT_0);
        bid.setProjectId(210L);
        when(bidCostMapper.selectById(21L)).thenReturn(bid);
        doThrow(new BusinessException("PROJECT_ACCESS_DENIED", "无权访问该项目"))
                .when(projectAccessChecker).checkAccess(210L, "访问投标文件");

        BusinessException error = assertThrows(BusinessException.class, () -> service.list(21L));

        assertEquals("PROJECT_ACCESS_DENIED", error.getCode());
    }

    @Test
    void crossTenantSysFileIsHidden() {
        when(bidCostMapper.selectById(3L)).thenReturn(bid(3L, TestUserContext.TENANT_0));
        SysFile file = new SysFile();
        file.setId(30L);
        file.setTenantId(999L);
        file.setBusinessType("BID_COST");
        file.setBusinessId(3L);
        file.setVirusScanStatus("CLEAN");
        when(sysFileMapper.selectById(30L)).thenReturn(file);

        BidDocumentCreateRequest request = new BidDocumentCreateRequest(
                "RESULT", "AWARD_NOTICE", "中标通知书", 30L,
                null, null, null, null, null, null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.append(3L, request));

        assertEquals("BID_DOCUMENT_FILE_NOT_FOUND", error.getCode());
    }

    private BidCost bid(Long id, Long tenantId) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setTenantId(tenantId);
        return bid;
    }
}
