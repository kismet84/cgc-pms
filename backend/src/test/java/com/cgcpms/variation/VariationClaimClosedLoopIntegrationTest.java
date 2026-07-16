package com.cgcpms.variation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerReviewLine;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerReviewRequest;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerSubmissionRequest;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("变更、签证与索赔闭环")
class VariationClaimClosedLoopIntegrationTest {
    private static final long PROJECT_ID = 186010L;
    private static final long PARTNER_ID = 186020L;
    private static final long CONTRACT_ID = 186030L;

    @Autowired VarOrderService service;
    @Autowired VarOrderMapper orderMapper;
    @Autowired PmProjectMapper projectMapper;
    @Autowired MdPartnerMapper partnerMapper;
    @Autowired CtContractMapper contractMapper;
    @Autowired CtContractChangeMapper changeMapper;
    @Autowired WfInstanceMapper instanceMapper;
    @Autowired WfTaskMapper taskMapper;
    @Autowired WorkflowEngine workflowEngine;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(0L, 1L);
        ensureAdmin();
        seedMasterData();
    }

    @AfterEach
    void clear() {
        TestUserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("现场证据→内部审批→业主退回重报→部分核定→合同变更生效→成本不重复→全链追溯")
    void closesVariationClaimLoop() {
        Long subjectId = jdbc.queryForObject("SELECT id FROM cost_subject WHERE tenant_id=0 AND deleted_flag=0 LIMIT 1", Long.class);
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("基坑支护设计变更索赔");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("REVENUE");
        order.setEventDate(LocalDate.now().minusDays(2));
        order.setClaimDeadline(LocalDate.now().plusDays(20));
        order.setEventDescription("业主指令增加支护深度");
        order.setCauseCategory("OWNER_INSTRUCTION");
        order.setResponsibleParty("OWNER");
        Long orderId = service.create(order);

        VarOrderItem item = new VarOrderItem();
        item.setItemName("新增支护工程");
        item.setUnit("项");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("900.00"));
        item.setClaimUnitPrice(new BigDecimal("1500.00"));
        item.setCostSubjectId(subjectId);
        service.saveItems(orderId, List.of(item));

        BusinessException noEvidence = assertThrows(BusinessException.class, () -> service.submitForApproval(orderId));
        assertEquals("VARIATION_SITE_EVIDENCE_REQUIRED", noEvidence.getCode());
        addFile(orderId, "SITE_EVIDENCE", "现场指令.pdf");
        service.submitForApproval(orderId);
        approveAll("VAR_ORDER", orderId);

        VarOrder approved = orderMapper.selectById(orderId);
        assertEquals("INCOME", approved.getDirection());
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals("INTERNAL_APPROVED", approved.getOwnerStatus());
        assertEquals(0, new BigDecimal("900.00").compareTo(approved.getEstimatedCostAmount()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(approved.getApprovedAmount()));

        OwnerSubmissionRequest firstRequest = new OwnerSubmissionRequest("CGC-CLM-001", LocalDateTime.now().minusMinutes(5), "首报");
        BusinessException noSubmissionFile = assertThrows(BusinessException.class,
                () -> service.submitToOwner(orderId, firstRequest));
        assertEquals("VARIATION_OWNER_SUBMISSION_ATTACHMENT_REQUIRED", noSubmissionFile.getCode());
        addFile(orderId, "OWNER_SUBMISSION", "索赔申报R1.pdf");
        Map<String, Object> first = service.submitToOwner(orderId, firstRequest);
        Long firstId = ((Number) first.get("id")).longValue();

        addFile(orderId, "OWNER_CONFIRMATION", "业主退回函.pdf");
        service.reviewOwnerSubmission(orderId, firstId,
                new OwnerReviewRequest("RETURNED", "OWNER-RET-001", "补充工程量依据",
                        LocalDateTime.now().minusMinutes(3), List.of()));
        assertEquals("OWNER_RETURNED", orderMapper.selectById(orderId).getOwnerStatus());

        addFile(orderId, "OWNER_SUBMISSION", "索赔申报R2.pdf");
        Map<String, Object> second = service.submitToOwner(orderId,
                new OwnerSubmissionRequest("CGC-CLM-002", LocalDateTime.now().minusMinutes(2), "补充后重报"));
        Long secondId = ((Number) second.get("id")).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshots = (List<Map<String, Object>>) second.get("items");
        Long snapshotId = ((Number) snapshots.get(0).get("id")).longValue();

        addFile(orderId, "OWNER_CONFIRMATION", "业主核定函.pdf");
        service.reviewOwnerSubmission(orderId, secondId,
                new OwnerReviewRequest("CONFIRMED", "OWNER-CFM-001", "核定1200元",
                        LocalDateTime.now(), List.of(new OwnerReviewLine(snapshotId,
                        new BigDecimal("1200.00"), "扣除重复措施费"))));

        VarOrder pending = orderMapper.selectById(orderId);
        assertEquals("CHANGE_PENDING", pending.getOwnerStatus());
        assertNotNull(pending.getGeneratedContractChangeId());
        CtContractChange change = changeMapper.selectById(pending.getGeneratedContractChangeId());
        assertEquals(orderId, change.getSourceVarOrderId());
        assertEquals("APPROVING", change.getApprovalStatus());

        approveAll("CT_CHANGE", change.getId());
        VarOrder effective = orderMapper.selectById(orderId);
        assertEquals("CHANGE_EFFECTIVE", effective.getOwnerStatus());
        assertEquals(1, effective.getOwnerConfirmFlag());
        assertEquals(0, new BigDecimal("1001200.00").compareTo(contractMapper.selectById(CONTRACT_ID).getCurrentAmount()));
        Integer varCosts = jdbc.queryForObject("SELECT COUNT(*) FROM cost_item WHERE tenant_id=0 AND source_type='VAR_ORDER' AND source_id=?", Integer.class, orderId);
        Integer changeCosts = jdbc.queryForObject("SELECT COUNT(*) FROM cost_item WHERE tenant_id=0 AND source_type='CT_CHANGE' AND source_id=?", Integer.class, change.getId());
        assertEquals(1, varCosts);
        assertEquals(0, changeCosts, "自动合同变更不得重复生成签证成本");

        Map<String, Object> trace = service.trace(orderId);
        assertFalse(((List<?>) trace.get("internalApproval")).isEmpty());
        assertEquals(2, ((List<?>) trace.get("ownerSubmissions")).size());
        assertFalse(((List<?>) trace.get("contractChange")).isEmpty());
        BusinessException duplicate = assertThrows(BusinessException.class, () ->
                service.reviewOwnerSubmission(orderId, secondId,
                        new OwnerReviewRequest("CONFIRMED", "DUP", "重复", LocalDateTime.now(),
                                List.of(new OwnerReviewLine(snapshotId, new BigDecimal("1200"), null)))));
        assertEquals("VARIATION_OWNER_REVIEW_DUPLICATE", duplicate.getCode());
    }

    private void approveAll(String businessType, Long businessId) {
        WfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBusinessType, businessType).eq(WfInstance::getBusinessId, businessId));
        assertNotNull(instance);
        for (int round = 0; round < 10; round++) {
            List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getInstanceId, instance.getId())
                    .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
            if (pending.isEmpty()) return;
            for (WfTask task : pending) workflowEngine.approve(task.getId(), task.getApproverId(),
                    "admin", "同意", businessType + "-" + UUID.randomUUID());
        }
        fail("审批未在预期轮次内结束: " + businessType);
    }

    private void addFile(Long orderId, String type, String name) {
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,
                    file_size,content_type,storage_path,bucket_name,created_at,updated_at,deleted_flag)
                VALUES(?,0,'VARIATION',?,?,?, ?,128,'application/pdf',?,'cgc-pms',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, IdWorker.getId(), type, orderId, UUID.randomUUID() + ".pdf", name,
                "VARIATION/" + orderId + "/" + UUID.randomUUID() + ".pdf");
    }

    private void ensureAdmin() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=1", Integer.class);
        if (count != null && count == 0) jdbc.update("""
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES(1,0,'admin','{noop}test','管理员','ENABLE',1,0)
                """);
    }

    private void seedMasterData() {
        if (projectMapper.selectById(PROJECT_ID) == null) {
            PmProject p = new PmProject(); p.setId(PROJECT_ID); p.setProjectCode("P-V186");
            p.setProjectName("变更索赔闭环项目"); p.setStatus("ACTIVE"); p.setApprovalStatus("APPROVED");
            projectMapper.insert(p);
        }
        if (partnerMapper.selectById(PARTNER_ID) == null) {
            MdPartner p = new MdPartner(); p.setId(PARTNER_ID); p.setPartnerCode("O-V186");
            p.setPartnerName("闭环测试业主"); p.setPartnerType("PARTY_A"); p.setBlacklistFlag(0); p.setStatus("ENABLE");
            partnerMapper.insert(p);
        }
        if (contractMapper.selectById(CONTRACT_ID) == null) {
            CtContract c = new CtContract(); c.setId(CONTRACT_ID); c.setProjectId(PROJECT_ID);
            c.setContractCode("MAIN-V186"); c.setContractName("闭环测试主合同"); c.setContractType("MAIN");
            c.setPartyAId(PARTNER_ID); c.setPartyBId(PARTNER_ID); c.setContractAmount(new BigDecimal("1000000.00"));
            c.setCurrentAmount(new BigDecimal("1000000.00")); c.setPaidAmount(BigDecimal.ZERO);
            c.setContractStatus("PERFORMING"); c.setApprovalStatus("APPROVED"); contractMapper.insert(c);
        }
    }
}
