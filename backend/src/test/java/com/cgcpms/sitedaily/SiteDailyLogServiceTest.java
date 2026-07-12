package com.cgcpms.sitedaily;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.service.SiteDailyLogService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.cgcpms.common.exception.BusinessException;

class SiteDailyLogServiceTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void detailChecksProjectAccess() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = service(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setId(31L); log.setTenantId(11L); log.setProjectId(21L);
        log.setReportDate(LocalDate.of(2099, 1, 1));
        log.setConstructionContent("施工内容");
        log.setWeatherSummary("晴，午后有风");
        log.setOnSiteHeadcount(0);
        log.setStatus("DRAFT");
        when(mapper.selectById(31L)).thenReturn(log);

        var detail = service.getById(31L);

        verify(checker).checkAccess(21L, "访问现场日报");
        assertEquals("晴，午后有风", detail.getWeatherSummary());
        assertEquals(0, detail.getOnSiteHeadcount());
        assertTrue(detail.getDeliveries().isEmpty());
    }

    @Test
    void crossTenantDetailIsHiddenBeforeProjectCheck() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = service(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setId(32L); log.setTenantId(12L); log.setProjectId(22L);
        when(mapper.selectById(32L)).thenReturn(log);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getById(32L));

        assertEquals("SITE_DAILY_LOG_NOT_FOUND", error.getCode());
        verifyNoInteractions(checker);
    }

    @Test
    void createRequiresProjectAccess() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = service(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setProjectId(21L); log.setReportDate(LocalDate.of(2099, 1, 2));
        log.setConstructionContent("施工内容");
        doAnswer(invocation -> { log.setId(33L); return 1; }).when(mapper).insert(log);

        service.create(log);

        verify(checker).checkAccess(21L, "创建现场日报");
        assertEquals(11L, log.getTenantId());
        assertEquals("DRAFT", log.getStatus());
    }

    @Test
    void detailIncludesOnlyApprovedDeliveriesForSameTenantProjectAndDate() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        MatReceiptMapper receiptMapper = mock(MatReceiptMapper.class);
        MatReceiptItemMapper itemMapper = mock(MatReceiptItemMapper.class);
        MdMaterialMapper materialMapper = mock(MdMaterialMapper.class);
        MdPartnerMapper partnerMapper = mock(MdPartnerMapper.class);
        SiteDailyLogService service = new SiteDailyLogService(
                mapper, projectMapper, checker, receiptMapper, itemMapper, materialMapper, partnerMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        SiteDailyLog log = new SiteDailyLog();
        log.setId(31L); log.setTenantId(11L); log.setProjectId(21L);
        log.setReportDate(LocalDate.of(2099, 1, 1)); log.setConstructionContent("施工内容");
        log.setStatus("DRAFT");
        when(mapper.selectById(31L)).thenReturn(log);

        MatReceipt receipt = new MatReceipt();
        receipt.setId(41L); receipt.setTenantId(11L); receipt.setProjectId(21L);
        receipt.setPartnerId(51L); receipt.setReceiptCode("MR-20990101-001");
        receipt.setReceiptDate(LocalDate.of(2099, 1, 1)); receipt.setApprovalStatus("APPROVED");
        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt));

        MatReceiptItem item = new MatReceiptItem();
        item.setId(71L); item.setReceiptId(41L); item.setMaterialId(61L);
        item.setActualQuantity(new BigDecimal("12.3400"));
        item.setQualifiedQuantity(new BigDecimal("12.0000"));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        MdMaterial material = new MdMaterial(); material.setId(61L); material.setMaterialName("钢筋");
        MdPartner partner = new MdPartner(); partner.setId(51L); partner.setPartnerName("供应商甲");
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        when(partnerMapper.selectList(any())).thenReturn(List.of(partner));

        var detail = service.getById(31L);

        assertEquals(1, detail.getDeliveries().size());
        var delivery = detail.getDeliveries().get(0);
        assertEquals("MR-20990101-001", delivery.getReceiptCode());
        assertEquals("供应商甲", delivery.getPartnerName());
        assertEquals("钢筋", delivery.getMaterialName());
        assertEquals("12.3400", delivery.getActualQuantity());
        assertEquals("12.0000", delivery.getQualifiedQuantity());
        verify(checker).checkAccess(21L, "访问现场日报");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatReceipt>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(receiptMapper).selectList(query.capture());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MatReceipt.class);
        assertTrue(query.getValue().getSqlSegment().contains("tenant_id"));
        assertTrue(query.getValue().getSqlSegment().contains("project_id"));
        assertTrue(query.getValue().getSqlSegment().contains("receipt_date"));
        assertTrue(query.getValue().getSqlSegment().contains("approval_status"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(11L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(21L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2099, 1, 1)));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("APPROVED"));
    }

    private SiteDailyLogService service(SiteDailyLogMapper mapper, PmProjectMapper projectMapper,
                                        ProjectAccessChecker checker) {
        return new SiteDailyLogService(mapper, projectMapper, checker,
                mock(MatReceiptMapper.class), mock(MatReceiptItemMapper.class),
                mock(MdMaterialMapper.class), mock(MdPartnerMapper.class));
    }
}
