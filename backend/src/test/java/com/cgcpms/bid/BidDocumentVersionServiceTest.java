package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BidDocumentVersionServiceTest {

    @Mock BidDocumentVersionMapper mapper;
    @Mock BidCostMapper bidCostMapper;
    @Mock SysFileMapper sysFileMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock ApplicationEventPublisher eventPublisher;

    private BidDocumentVersionService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidDocumentVersion.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidDocumentVersionServiceTest");
            TableInfoHelper.initTableInfo(assistant, BidDocumentVersion.class);
        }
        service = new BidDocumentVersionService(
                mapper, bidCostMapper, sysFileMapper, projectAccessChecker, eventPublisher);
    }

    @AfterEach
    void clear() {
        TestUserContext.clear();
    }

    @Test
    void finalizedVersionCannotBeFinalizedInPlaceAgain() {
        when(bidCostMapper.selectByIdForUpdate(1L, TestUserContext.TENANT_0))
                .thenReturn(bid(1L, TestUserContext.TENANT_0));
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
    void firstAppendUsesServerHashAndStartsAtVersionOne() {
        when(bidCostMapper.selectByIdForUpdate(4L, TestUserContext.TENANT_0))
                .thenReturn(bid(4L, TestUserContext.TENANT_0));
        when(sysFileMapper.selectById(40L)).thenReturn(cleanFile(40L, 4L));

        BidDocumentVersion version = service.append(4L, request(40L));

        assertEquals(1, version.getVersionNo());
        assertEquals("DRAFT", version.getStatus());
        assertEquals("a".repeat(64), version.getContentSha256());
        assertNull(version.getSupersedesId());
    }

    @Test
    void finalVersionCannotBeReplacedByDraft() {
        when(bidCostMapper.selectByIdForUpdate(5L, TestUserContext.TENANT_0))
                .thenReturn(bid(5L, TestUserContext.TENANT_0));
        when(sysFileMapper.selectById(50L)).thenReturn(cleanFile(50L, 5L));
        BidDocumentVersion previous = new BidDocumentVersion();
        previous.setId(500L);
        previous.setVersionNo(2);
        previous.setStatus("FINAL");
        when(mapper.selectCurrentForUpdate(TestUserContext.TENANT_0, 5L, "中标通知书"))
                .thenReturn(previous);

        BusinessException error = assertThrows(BusinessException.class, () -> service.append(5L, request(50L)));

        assertEquals("BID_DOCUMENT_FINAL_IMMUTABLE", error.getCode());
    }

    @Test
    void currentDraftCanBeFinalizedWithCas() {
        when(bidCostMapper.selectByIdForUpdate(6L, TestUserContext.TENANT_0))
                .thenReturn(bid(6L, TestUserContext.TENANT_0));
        when(mapper.selectById(60L)).thenReturn(version(60L, 6L, "DRAFT", 0L));
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.finalizeVersion(6L, 60L);

        verify(eventPublisher).publishEvent(
                new BidDocumentVersionService.BidDocumentFinalizedEvent(6L));
    }

    @Test
    void currentFinalCannotBeVoidedAfterItMayHaveAdvancedStatus() {
        when(bidCostMapper.selectByIdForUpdate(7L, TestUserContext.TENANT_0))
                .thenReturn(bid(7L, TestUserContext.TENANT_0));
        when(mapper.selectById(70L)).thenReturn(version(70L, 7L, "FINAL", 0L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.voidVersion(7L, 70L, "重复上传"));

        assertEquals("BID_DOCUMENT_IMMUTABLE", error.getCode());
    }

    @Test
    void unsafeFileIsRejectedBeforeVersionCreation() {
        when(bidCostMapper.selectByIdForUpdate(8L, TestUserContext.TENANT_0))
                .thenReturn(bid(8L, TestUserContext.TENANT_0));
        SysFile file = cleanFile(80L, 8L);
        file.setVirusScanStatus("PENDING");
        when(sysFileMapper.selectById(80L)).thenReturn(file);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.append(8L, request(80L)));

        assertEquals("BID_DOCUMENT_FILE_NOT_CLEAN", error.getCode());
    }

    @Test
    void fileWithoutServerHashIsRejected() {
        when(bidCostMapper.selectByIdForUpdate(9L, TestUserContext.TENANT_0))
                .thenReturn(bid(9L, TestUserContext.TENANT_0));
        SysFile file = cleanFile(90L, 9L);
        file.setFileName("award-notice.pdf");
        when(sysFileMapper.selectById(90L)).thenReturn(file);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.append(9L, request(90L)));

        assertEquals("BID_DOCUMENT_HASH_MISSING", error.getCode());
    }

    @Test
    void crossTenantSysFileIsHidden() {
        when(bidCostMapper.selectByIdForUpdate(3L, TestUserContext.TENANT_0))
                .thenReturn(bid(3L, TestUserContext.TENANT_0));
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

    private SysFile cleanFile(Long id, Long bidCostId) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setTenantId(TestUserContext.TENANT_0);
        file.setBusinessType("BID_COST");
        file.setBusinessId(bidCostId);
        file.setVirusScanStatus("CLEAN");
        file.setFileName("a".repeat(64));
        return file;
    }

    private BidDocumentCreateRequest request(Long sysFileId) {
        return new BidDocumentCreateRequest(
                "RESULT", "AWARD_NOTICE", "中标通知书", sysFileId,
                null, null, null, null, null, null);
    }

    private BidDocumentVersion version(Long id, Long bidCostId, String status, Long currentToken) {
        BidDocumentVersion version = new BidDocumentVersion();
        version.setId(id);
        version.setTenantId(TestUserContext.TENANT_0);
        version.setBidCostId(bidCostId);
        version.setStatus(status);
        version.setCurrentToken(currentToken);
        return version;
    }
}
