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
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import com.cgcpms.site.controller.SiteDailyLogController;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.service.SiteDailyLogService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
                mapper, projectMapper, checker, receiptMapper, itemMapper, materialMapper, partnerMapper,
                mock(SubTaskMapper.class), mock(OperationAuditLogMapper.class));
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

    @Test
    void detailIncludesPlannedTasksCoveringTheReportDate() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SubTaskMapper taskMapper = mock(SubTaskMapper.class);
        SiteDailyLogService service = new SiteDailyLogService(
                mapper, projectMapper, checker, mock(MatReceiptMapper.class), mock(MatReceiptItemMapper.class),
                mock(MdMaterialMapper.class), mock(MdPartnerMapper.class), taskMapper,
                mock(OperationAuditLogMapper.class));
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        SiteDailyLog log = new SiteDailyLog();
        log.setId(32L); log.setTenantId(11L); log.setProjectId(21L);
        log.setReportDate(LocalDate.of(2099, 2, 10)); log.setConstructionContent("施工内容");
        log.setStatus("DRAFT");
        when(mapper.selectById(32L)).thenReturn(log);

        SubTask task = new SubTask();
        task.setId(81L); task.setTenantId(11L); task.setProjectId(21L);
        task.setTaskCode("SUB-001"); task.setTaskName("主体钢筋施工"); task.setWorkArea("1号楼");
        task.setPlannedStartDate(LocalDate.of(2099, 2, 9));
        task.setPlannedEndDate(LocalDate.of(2099, 2, 10));
        task.setStatus("IN_PROGRESS"); task.setProgressPercent(new BigDecimal("35.00"));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        var detail = service.getById(32L);

        assertEquals(1, detail.getPlannedTasks().size());
        var planned = detail.getPlannedTasks().get(0);
        assertEquals("SUB-001", planned.getTaskCode());
        assertEquals("主体钢筋施工", planned.getTaskName());
        assertEquals("1号楼", planned.getWorkArea());
        assertEquals("2099-02-09", planned.getPlannedStartDate());
        assertEquals("2099-02-10", planned.getPlannedEndDate());
        assertEquals("IN_PROGRESS", planned.getStatus());
        assertEquals("35.00", planned.getProgressPercent());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubTask>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(taskMapper).selectList(query.capture());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SubTask.class);
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("project_id"));
        assertTrue(sql.contains("planned_start_date"));
        assertTrue(sql.contains("planned_end_date"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(11L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(21L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2099, 2, 10)));
    }

    @Test
    void detailIncludesMinimalAuditTrailAndCreateBindsGeneratedId() throws Exception {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        OperationAuditLogMapper auditMapper = mock(OperationAuditLogMapper.class);
        SiteDailyLogService service = new SiteDailyLogService(
                mapper, projectMapper, checker, mock(MatReceiptMapper.class), mock(MatReceiptItemMapper.class),
                mock(MdMaterialMapper.class), mock(MdPartnerMapper.class), mock(SubTaskMapper.class), auditMapper);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());

        SiteDailyLog log = new SiteDailyLog();
        log.setId(33L); log.setTenantId(11L); log.setProjectId(21L);
        log.setReportDate(LocalDate.of(2099, 3, 1)); log.setConstructionContent("施工内容");
        log.setStatus("SUBMITTED");
        when(mapper.selectById(33L)).thenReturn(log);

        OperationAuditLog success = new OperationAuditLog();
        success.setId(91L); success.setTenantId(11L); success.setUserId(7L);
        success.setOperationType("SUBMIT"); success.setBusinessType("SITE_DAILY_LOG");
        success.setBusinessId("33"); success.setSuccessFlag(1);
        success.setCreatedAt(LocalDateTime.of(2099, 3, 1, 18, 0));
        OperationAuditLog failed = new OperationAuditLog();
        failed.setId(92L); failed.setTenantId(11L); failed.setUserId(8L);
        failed.setOperationType("UPDATE"); failed.setBusinessType("SITE_DAILY_LOG");
        failed.setBusinessId("33"); failed.setSuccessFlag(0);
        failed.setCreatedAt(LocalDateTime.of(2099, 3, 1, 19, 0));
        when(auditMapper.selectList(any())).thenReturn(List.of(failed, success));

        var detail = service.getById(33L);

        assertEquals(2, detail.getAuditTrail().size());
        assertEquals("UPDATE", detail.getAuditTrail().get(0).getOperationType());
        assertEquals("8", detail.getAuditTrail().get(0).getUserId());
        assertEquals(false, detail.getAuditTrail().get(0).getSuccess());
        assertEquals("2099-03-01 19:00:00", detail.getAuditTrail().get(0).getCreatedAt());
        assertEquals(true, detail.getAuditTrail().get(1).getSuccess());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationAuditLog>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(auditMapper).selectList(query.capture());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OperationAuditLog.class);
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("business_type"));
        assertTrue(sql.contains("business_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(11L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SITE_DAILY_LOG"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("33"));

        AuditedOperation createAudit = SiteDailyLogController.class
                .getMethod("create", SiteDailyLog.class).getAnnotation(AuditedOperation.class);
        assertEquals("#log.id", createAudit.businessIdExpression());
    }

    private SiteDailyLogService service(SiteDailyLogMapper mapper, PmProjectMapper projectMapper,
                                        ProjectAccessChecker checker) {
        return new SiteDailyLogService(mapper, projectMapper, checker,
                mock(MatReceiptMapper.class), mock(MatReceiptItemMapper.class),
                mock(MdMaterialMapper.class), mock(MdPartnerMapper.class), mock(SubTaskMapper.class),
                mock(OperationAuditLogMapper.class));
    }
}
