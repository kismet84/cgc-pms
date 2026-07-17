package com.cgcpms.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.quality.entity.QualityPartnerEvaluation;
import com.cgcpms.quality.mapper.QualityPartnerEvaluationMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.supplier.dto.SupplierSourcingModels.*;
import com.cgcpms.supplier.entity.*;
import com.cgcpms.supplier.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierSourcingService {
    private static final Set<String> SOURCING_TYPES = Set.of("INQUIRY", "TENDER");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SourcingEventMapper eventMapper;
    private final SourcingSupplierMapper sourcingSupplierMapper;
    private final SupplierQuoteMapper quoteMapper;
    private final BidEvaluationMapper bidEvaluationMapper;
    private final SupplierPerformanceEvaluationMapper performanceMapper;
    private final SupplierBlacklistRecordMapper blacklistMapper;
    private final SupplierReturnMapper supplierReturnMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final PmProjectMapper projectMapper;
    private final MatPurchaseRequestMapper purchaseRequestMapper;
    private final MatPurchaseOrderMapper purchaseOrderMapper;
    private final MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final MdPartnerMapper partnerMapper;
    private final CtContractMapper contractMapper;
    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final StlSettlementMapper settlementMapper;
    private final QualityPartnerEvaluationMapper qualityPartnerEvaluationMapper;
    private final SysFileMapper fileMapper;

    public List<SourcingEvent> listEvents(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查询供应商招采事件");
        return eventMapper.selectList(new LambdaQueryWrapper<SourcingEvent>()
                .eq(SourcingEvent::getTenantId, tenantId()).eq(SourcingEvent::getProjectId, projectId)
                .orderByDesc(SourcingEvent::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingEvent createEvent(EventCommand command) {
        projectAccessChecker.checkAccess(command.projectId(), "创建供应商招采事件");
        requireActiveProject(command.projectId());
        MatPurchaseRequest request = purchaseRequestMapper.selectById(command.purchaseRequestId());
        if (request == null || !Objects.equals(request.getTenantId(), tenantId()))
            throw new BusinessException("SP_REQUEST_NOT_FOUND", "采购需求不存在");
        if (!Objects.equals(request.getProjectId(), command.projectId()))
            throw new BusinessException("SP_REQUEST_PROJECT_MISMATCH", "采购需求不属于当前项目");
        if (!"APPROVED".equals(request.getApprovalStatus()))
            throw new BusinessException("SP_REQUEST_NOT_APPROVED", "只有审批通过的采购需求才能发起询价或招标");
        String type = upper(command.sourcingType());
        if (!SOURCING_TYPES.contains(type))
            throw new BusinessException("SP_SOURCING_TYPE_INVALID", "招采方式必须为询价或招标");
        if (!command.deadline().isAfter(LocalDateTime.now()))
            throw new BusinessException("SP_DEADLINE_INVALID", "报价截止时间必须晚于当前时间");

        SourcingEvent event = new SourcingEvent();
        event.setTenantId(tenantId());
        event.setProjectId(command.projectId());
        event.setPurchaseRequestId(command.purchaseRequestId());
        event.setSourcingCode(command.sourcingCode().trim());
        event.setSourcingTitle(command.sourcingTitle().trim());
        event.setSourcingType(type);
        event.setDeadline(command.deadline());
        event.setCurrencyCode(upper(command.currencyCode()));
        event.setStatus("DRAFT");
        event.setVersion(0);
        event.setRemark(command.remark());
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_SOURCING_DUPLICATE", "招采编号重复或该采购需求已有招采事件");
        }
        return requireEvent(event.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<SourcingSupplier> addSuppliers(Long eventId, InvitationCommand command) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "维护招采受邀供应商");
        requireStatus(event, "DRAFT", "只有草稿招采事件可以维护受邀供应商");
        LinkedHashSet<Long> partnerIds = new LinkedHashSet<>(command.partnerIds());
        for (Long partnerId : partnerIds) {
            MdPartner partner = requireEligibleSupplier(partnerId);
            SourcingSupplier invitation = new SourcingSupplier();
            invitation.setTenantId(tenantId());
            invitation.setSourcingEventId(eventId);
            invitation.setPartnerId(partner.getId());
            invitation.setInvitationStatus("PENDING");
            try {
                sourcingSupplierMapper.insert(invitation);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("SP_SUPPLIER_DUPLICATE", "同一供应商不能重复加入招采事件");
            }
        }
        return listSuppliers(eventId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingEvent publish(Long eventId) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "发布供应商招采事件");
        requireStatus(event, "DRAFT", "招采事件已发布，禁止重复发布");
        requireActiveProject(event.getProjectId());
        if (!event.getDeadline().isAfter(LocalDateTime.now()))
            throw new BusinessException("SP_DEADLINE_EXPIRED", "报价截止时间已过，不能发布");
        List<SourcingSupplier> suppliers = listSuppliers(eventId);
        if (suppliers.size() < 3)
            throw new BusinessException("SP_SUPPLIERS_INSUFFICIENT", "发布询价或招标至少需要三家合格供应商");
        suppliers.forEach(row -> requireEligibleSupplier(row.getPartnerId()));
        requireFile("SUPPLIER_SOURCING", eventId, "SOURCING_REQUIREMENT", "发布前必须上传采购需求或招标文件");
        LocalDateTime now = LocalDateTime.now();
        int updated = eventMapper.update(null, new LambdaUpdateWrapper<SourcingEvent>()
                .eq(SourcingEvent::getId, eventId).eq(SourcingEvent::getTenantId, tenantId())
                .eq(SourcingEvent::getStatus, "DRAFT").set(SourcingEvent::getStatus, "PUBLISHED")
                .set(SourcingEvent::getPublishedBy, userId()).set(SourcingEvent::getPublishedAt, now));
        if (updated != 1) throw concurrent();
        sourcingSupplierMapper.update(null, new LambdaUpdateWrapper<SourcingSupplier>()
                .eq(SourcingSupplier::getTenantId, tenantId()).eq(SourcingSupplier::getSourcingEventId, eventId)
                .eq(SourcingSupplier::getInvitationStatus, "PENDING")
                .set(SourcingSupplier::getInvitationStatus, "INVITED").set(SourcingSupplier::getInvitedAt, now));
        return requireEvent(eventId);
    }

    public List<SourcingSupplier> listSuppliers(Long eventId) {
        requireEvent(eventId);
        return sourcingSupplierMapper.selectList(new LambdaQueryWrapper<SourcingSupplier>()
                .eq(SourcingSupplier::getTenantId, tenantId()).eq(SourcingSupplier::getSourcingEventId, eventId)
                .orderByAsc(SourcingSupplier::getCreatedAt));
    }

    public List<SupplierQuote> listQuotes(Long eventId) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "查询供应商报价");
        return quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getTenantId, tenantId()).eq(SupplierQuote::getSourcingEventId, eventId)
                .orderByAsc(SupplierQuote::getTotalAmount));
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierQuote createQuote(QuoteCommand command) {
        SourcingEvent event = requireEvent(command.sourcingEventId());
        projectAccessChecker.checkAccess(event.getProjectId(), "登记供应商报价");
        requireStatus(event, "PUBLISHED", "只有已发布且未进入评审的招采事件可以登记报价");
        if (LocalDateTime.now().isAfter(event.getDeadline()))
            throw new BusinessException("SP_QUOTE_DEADLINE_PASSED", "报价截止时间已过");
        requireEligibleSupplier(command.partnerId());
        SourcingSupplier invitation = requireInvitation(event.getId(), command.partnerId());
        if (!Set.of("INVITED", "PENDING").contains(invitation.getInvitationStatus()))
            throw new BusinessException("SP_INVITATION_NOT_OPEN", "该供应商邀请已响应或失效");
        validateQuote(command, event);
        SupplierQuote quote = new SupplierQuote();
        quote.setTenantId(tenantId());
        quote.setSourcingEventId(event.getId());
        quote.setSourcingSupplierId(invitation.getId());
        quote.setPartnerId(command.partnerId());
        quote.setQuoteCode(command.quoteCode().trim());
        quote.setTotalAmount(money(command.totalAmount()));
        quote.setTaxRate(command.taxRate().setScale(4, RoundingMode.HALF_UP));
        quote.setDeliveryDays(command.deliveryDays());
        quote.setValidityDate(command.validityDate());
        quote.setCommercialTerms(command.commercialTerms().trim());
        quote.setStatus("DRAFT");
        quote.setVersion(0);
        quote.setRemark(command.remark());
        try {
            quoteMapper.insert(quote);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_QUOTE_DUPLICATE", "报价编号重复或该供应商已提交报价");
        }
        return requireQuote(quote.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierQuote submitQuote(Long quoteId) {
        SupplierQuote quote = requireQuote(quoteId);
        SourcingEvent event = requireEvent(quote.getSourcingEventId());
        projectAccessChecker.checkAccess(event.getProjectId(), "提交供应商报价");
        requireStatus(event, "PUBLISHED", "招采事件已进入评审或结束，不能提交报价");
        requireStatus(quote, "DRAFT", "报价已提交，禁止重复提交");
        if (LocalDateTime.now().isAfter(event.getDeadline()))
            throw new BusinessException("SP_QUOTE_DEADLINE_PASSED", "报价截止时间已过");
        requireFile("SUPPLIER_QUOTE", quoteId, "QUOTE_ATTACHMENT", "提交报价必须上传报价单附件");
        LocalDateTime now = LocalDateTime.now();
        int updated = quoteMapper.update(null, new LambdaUpdateWrapper<SupplierQuote>()
                .eq(SupplierQuote::getId, quoteId).eq(SupplierQuote::getTenantId, tenantId())
                .eq(SupplierQuote::getStatus, "DRAFT").set(SupplierQuote::getStatus, "SUBMITTED")
                .set(SupplierQuote::getSubmittedBy, userId()).set(SupplierQuote::getSubmittedAt, now));
        if (updated != 1) throw concurrent();
        sourcingSupplierMapper.update(null, new LambdaUpdateWrapper<SourcingSupplier>()
                .eq(SourcingSupplier::getId, quote.getSourcingSupplierId()).eq(SourcingSupplier::getTenantId, tenantId())
                .in(SourcingSupplier::getInvitationStatus, List.of("PENDING", "INVITED"))
                .set(SourcingSupplier::getInvitationStatus, "QUOTED").set(SourcingSupplier::getRespondedAt, now));
        return requireQuote(quoteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingSupplier decline(Long eventId, Long partnerId, DeclineCommand command) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "登记供应商放弃报价");
        requireStatus(event, "PUBLISHED", "招采事件已进入评审或结束");
        SourcingSupplier invitation = requireInvitation(eventId, partnerId);
        int updated = sourcingSupplierMapper.update(null, new LambdaUpdateWrapper<SourcingSupplier>()
                .eq(SourcingSupplier::getId, invitation.getId()).eq(SourcingSupplier::getTenantId, tenantId())
                .in(SourcingSupplier::getInvitationStatus, List.of("PENDING", "INVITED"))
                .set(SourcingSupplier::getInvitationStatus, "DECLINED")
                .set(SourcingSupplier::getDisqualificationReason, command.reason().trim())
                .set(SourcingSupplier::getRespondedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        return requireInvitation(eventId, partnerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingEvent startEvaluation(Long eventId) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "启动比价评审");
        requireStatus(event, "PUBLISHED", "只有已发布招采事件可以启动评审");
        List<SupplierQuote> submitted = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getTenantId, tenantId()).eq(SupplierQuote::getSourcingEventId, eventId)
                .eq(SupplierQuote::getStatus, "SUBMITTED"));
        if (submitted.size() < 2)
            throw new BusinessException("SP_QUOTES_INSUFFICIENT", "至少需要两份有效报价才能启动比价评审");
        boolean allResponded = listSuppliers(eventId).stream()
                .allMatch(row -> Set.of("QUOTED", "DECLINED", "DISQUALIFIED").contains(row.getInvitationStatus()));
        if (LocalDateTime.now().isBefore(event.getDeadline()) && !allResponded)
            throw new BusinessException("SP_QUOTE_WINDOW_OPEN", "报价期尚未结束且仍有供应商未响应");
        int updated = eventMapper.update(null, new LambdaUpdateWrapper<SourcingEvent>()
                .eq(SourcingEvent::getId, eventId).eq(SourcingEvent::getTenantId, tenantId())
                .eq(SourcingEvent::getStatus, "PUBLISHED").set(SourcingEvent::getStatus, "EVALUATING"));
        if (updated != 1) throw concurrent();
        return requireEvent(eventId);
    }

    @Transactional(rollbackFor = Exception.class)
    public BidEvaluation evaluate(EvaluationCommand command) {
        SupplierQuote quote = requireQuote(command.quoteId());
        SourcingEvent event = requireEvent(quote.getSourcingEventId());
        projectAccessChecker.checkAccess(event.getProjectId(), "执行供应商比价评审");
        requireStatus(event, "EVALUATING", "招采事件不在评审阶段");
        requireStatus(quote, "SUBMITTED", "只有有效已提交报价可以评审");
        validateScore(command.commercialScore());
        validateScore(command.technicalScore());
        validateScore(command.deliveryScore());
        validateScore(command.qualityScore());
        BigDecimal total = weighted(command.commercialScore(), "0.40")
                .add(weighted(command.technicalScore(), "0.25"))
                .add(weighted(command.deliveryScore(), "0.20"))
                .add(weighted(command.qualityScore(), "0.15"))
                .setScale(2, RoundingMode.HALF_UP);
        BidEvaluation evaluation = new BidEvaluation();
        evaluation.setTenantId(tenantId());
        evaluation.setSourcingEventId(event.getId());
        evaluation.setQuoteId(quote.getId());
        evaluation.setPartnerId(quote.getPartnerId());
        evaluation.setCommercialScore(score(command.commercialScore()));
        evaluation.setTechnicalScore(score(command.technicalScore()));
        evaluation.setDeliveryScore(score(command.deliveryScore()));
        evaluation.setQualityScore(score(command.qualityScore()));
        evaluation.setTotalScore(total);
        evaluation.setEvaluationComment(command.evaluationComment().trim());
        evaluation.setEvaluatedBy(userId());
        evaluation.setEvaluatedAt(LocalDateTime.now());
        try {
            bidEvaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_QUOTE_ALREADY_EVALUATED", "该报价已完成评审，禁止覆盖历史评分");
        }
        return evaluation;
    }

    public List<BidEvaluation> listEvaluations(Long eventId) {
        requireEvent(eventId);
        return bidEvaluationMapper.selectList(new LambdaQueryWrapper<BidEvaluation>()
                .eq(BidEvaluation::getTenantId, tenantId()).eq(BidEvaluation::getSourcingEventId, eventId)
                .orderByDesc(BidEvaluation::getTotalScore));
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingEvent award(Long eventId, AwardCommand command) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "执行供应商定标");
        requireStatus(event, "EVALUATING", "招采事件不在定标阶段");
        SupplierQuote winner = requireQuote(command.quoteId());
        if (!Objects.equals(winner.getSourcingEventId(), eventId) || !"SUBMITTED".equals(winner.getStatus()))
            throw new BusinessException("SP_WINNER_QUOTE_INVALID", "中标报价不属于当前招采事件或已失效");
        List<SupplierQuote> submitted = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getTenantId, tenantId()).eq(SupplierQuote::getSourcingEventId, eventId)
                .eq(SupplierQuote::getStatus, "SUBMITTED"));
        List<BidEvaluation> evaluations = listEvaluations(eventId);
        if (submitted.size() < 2 || evaluations.size() != submitted.size())
            throw new BusinessException("SP_EVALUATION_INCOMPLETE", "所有有效报价完成评审后才能定标");
        Map<Long, BidEvaluation> byQuote = evaluations.stream().collect(Collectors.toMap(BidEvaluation::getQuoteId, Function.identity()));
        BidEvaluation winnerEvaluation = byQuote.get(winner.getId());
        if (winnerEvaluation == null) throw new BusinessException("SP_WINNER_NOT_EVALUATED", "中标报价尚未评审");
        BigDecimal highest = evaluations.stream().map(BidEvaluation::getTotalScore).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (winnerEvaluation.getTotalScore().compareTo(highest) < 0 && command.awardReason().trim().length() < 20)
            throw new BusinessException("SP_NON_TOP_AWARD_REASON_REQUIRED", "非最高综合评分中标时必须填写充分的定标说明");

        int updated = eventMapper.update(null, new LambdaUpdateWrapper<SourcingEvent>()
                .eq(SourcingEvent::getId, eventId).eq(SourcingEvent::getTenantId, tenantId())
                .eq(SourcingEvent::getStatus, "EVALUATING").set(SourcingEvent::getStatus, "AWARDED")
                .set(SourcingEvent::getAwardedQuoteId, winner.getId()).set(SourcingEvent::getAwardedPartnerId, winner.getPartnerId())
                .set(SourcingEvent::getAwardReason, command.awardReason().trim())
                .set(SourcingEvent::getAwardedBy, userId()).set(SourcingEvent::getAwardedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        quoteMapper.update(null, new LambdaUpdateWrapper<SupplierQuote>()
                .eq(SupplierQuote::getTenantId, tenantId()).eq(SupplierQuote::getSourcingEventId, eventId)
                .eq(SupplierQuote::getStatus, "SUBMITTED").set(SupplierQuote::getStatus, "LOST"));
        quoteMapper.update(null, new LambdaUpdateWrapper<SupplierQuote>()
                .eq(SupplierQuote::getId, winner.getId()).eq(SupplierQuote::getTenantId, tenantId())
                .eq(SupplierQuote::getStatus, "LOST").set(SupplierQuote::getStatus, "WINNER"));
        return requireEvent(eventId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourcingEvent linkContract(Long eventId, LinkContractCommand command) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "关联定标采购合同");
        requireStatus(event, "AWARDED", "只有已定标事件可以关联合同");
        CtContract contract = contractMapper.selectById(command.contractId());
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId()))
            throw new BusinessException("SP_CONTRACT_NOT_FOUND", "采购合同不存在");
        if (!"PURCHASE".equals(contract.getContractType()))
            throw new BusinessException("SP_CONTRACT_TYPE_INVALID", "定标结果只能关联采购合同");
        if (!Objects.equals(contract.getProjectId(), event.getProjectId()))
            throw new BusinessException("SP_CONTRACT_PROJECT_MISMATCH", "采购合同不属于招采项目");
        if (!Objects.equals(contract.getPartyBId(), event.getAwardedPartnerId()))
            throw new BusinessException("SP_CONTRACT_PARTNER_MISMATCH", "采购合同乙方必须是中标供应商");
        if (!ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus()))
            throw new BusinessException("SP_CONTRACT_NOT_PERFORMING", "采购合同审批通过并进入履约后才能关联");
        int updated = eventMapper.update(null, new LambdaUpdateWrapper<SourcingEvent>()
                .eq(SourcingEvent::getId, eventId).eq(SourcingEvent::getTenantId, tenantId())
                .eq(SourcingEvent::getStatus, "AWARDED").set(SourcingEvent::getStatus, "CONTRACTED")
                .set(SourcingEvent::getContractId, contract.getId()).set(SourcingEvent::getContractedBy, userId())
                .set(SourcingEvent::getContractedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        return requireEvent(eventId);
    }

    public List<SupplierPerformanceEvaluation> listPerformance(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查询供应商履约评价");
        return performanceMapper.selectList(new LambdaQueryWrapper<SupplierPerformanceEvaluation>()
                .eq(SupplierPerformanceEvaluation::getTenantId, tenantId())
                .eq(SupplierPerformanceEvaluation::getProjectId, projectId)
                .orderByDesc(SupplierPerformanceEvaluation::getPeriodEnd));
    }

    public List<SupplierReturn> listSupplierReturns(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查询供应商退货记录");
        return supplierReturnMapper.selectList(new LambdaQueryWrapper<SupplierReturn>()
                .eq(SupplierReturn::getTenantId, tenantId()).eq(SupplierReturn::getProjectId, projectId)
                .orderByDesc(SupplierReturn::getReturnDate));
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierReturn createSupplierReturn(SupplierReturnCommand command) {
        MatReceipt receipt = receiptMapper.selectById(command.receiptId());
        if (receipt == null || !Objects.equals(receipt.getTenantId(), tenantId()))
            throw new BusinessException("SP_RECEIPT_NOT_FOUND", "验收单不存在");
        projectAccessChecker.checkAccess(receipt.getProjectId(), "登记供应商退货");
        if (!"APPROVED".equals(receipt.getApprovalStatus()) || receipt.getOrderId() == null
                || receipt.getContractId() == null || receipt.getPartnerId() == null)
            throw new BusinessException("SP_RETURN_RECEIPT_INVALID", "只有已审批且绑定订单、合同和供应商的验收单才能登记退货");
        if (receipt.getReceiptDate() != null && command.returnDate().isBefore(receipt.getReceiptDate()))
            throw new BusinessException("SP_RETURN_DATE_INVALID", "退货日期不能早于验收日期");
        SupplierReturn row = new SupplierReturn();
        row.setTenantId(tenantId());
        row.setProjectId(receipt.getProjectId());
        row.setPartnerId(receipt.getPartnerId());
        row.setContractId(receipt.getContractId());
        row.setPurchaseOrderId(receipt.getOrderId());
        row.setReceiptId(receipt.getId());
        row.setReturnCode(command.returnCode().trim());
        row.setReturnDate(command.returnDate());
        row.setReturnQuantity(command.returnQuantity());
        row.setReturnAmount(money(command.returnAmount()));
        row.setReason(command.reason().trim());
        row.setStatus("DRAFT");
        row.setVersion(0);
        try {
            supplierReturnMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_RETURN_DUPLICATE", "供应商退货编号重复");
        }
        return requireSupplierReturn(row.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierReturn confirmSupplierReturn(Long id) {
        SupplierReturn row = requireSupplierReturn(id);
        projectAccessChecker.checkAccess(row.getProjectId(), "确认供应商退货");
        int updated = supplierReturnMapper.update(null, new LambdaUpdateWrapper<SupplierReturn>()
                .eq(SupplierReturn::getId, id).eq(SupplierReturn::getTenantId, tenantId())
                .eq(SupplierReturn::getStatus, "DRAFT")
                .set(SupplierReturn::getStatus, "CONFIRMED")
                .set(SupplierReturn::getConfirmedBy, userId()).set(SupplierReturn::getConfirmedAt, LocalDateTime.now()));
        if (updated != 1) throw new BusinessException("SP_RETURN_IMMUTABLE", "供应商退货已确认，禁止重复确认或修改");
        return requireSupplierReturn(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierPerformanceEvaluation createPerformance(PerformanceCommand command) {
        MatPurchaseOrder order = purchaseOrderMapper.selectById(command.purchaseOrderId());
        if (order == null || !Objects.equals(order.getTenantId(), tenantId()))
            throw new BusinessException("SP_ORDER_NOT_FOUND", "采购订单不存在");
        projectAccessChecker.checkAccess(order.getProjectId(), "生成供应商履约评价");
        if (!"APPROVED".equals(order.getApprovalStatus()) || order.getContractId() == null || order.getPartnerId() == null)
            throw new BusinessException("SP_ORDER_NOT_APPROVED", "只有绑定合同和供应商的已审批采购订单才能评价");
        validateScore(command.serviceScore());
        long contracted = eventMapper.selectCount(new LambdaQueryWrapper<SourcingEvent>()
                .eq(SourcingEvent::getTenantId, tenantId()).eq(SourcingEvent::getContractId, order.getContractId())
                .eq(SourcingEvent::getAwardedPartnerId, order.getPartnerId()).eq(SourcingEvent::getStatus, "CONTRACTED"));
        if (contracted == 0)
            throw new BusinessException("SP_ORDER_NO_SOURCING_SOURCE", "采购订单合同未关联已定标招采事件");

        List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                .eq(MatPurchaseOrderItem::getTenantId, tenantId()).eq(MatPurchaseOrderItem::getOrderId, order.getId()));
        if (orderItems.isEmpty()) throw new BusinessException("SP_ORDER_ITEMS_REQUIRED", "采购订单没有明细，不能评价");
        List<MatReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId()).eq(MatReceipt::getOrderId, order.getId())
                .eq(MatReceipt::getApprovalStatus, "APPROVED").orderByAsc(MatReceipt::getReceiptDate));
        if (receipts.isEmpty()) throw new BusinessException("SP_RECEIPT_REQUIRED", "至少存在一张已审批验收单才能评价");
        ensureDeliveryComplete(orderItems, receipts);
        LocalDate lastReceiptDate = receipts.stream().map(MatReceipt::getReceiptDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElseThrow(() -> new BusinessException("SP_RECEIPT_DATE_REQUIRED", "验收日期缺失"));
        int onTime = order.getDeliveryDate() != null && !lastReceiptDate.isAfter(order.getDeliveryDate()) ? 1 : 0;
        BigDecimal deliveryScore = onTime == 1 ? HUNDRED : new BigDecimal("60");

        BigDecimal receiptQuality = receipts.stream().map(this::receiptQualityScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(receipts.size()), 2, RoundingMode.HALF_UP);
        LocalDate periodStart = order.getOrderDate() == null ? receipts.get(0).getReceiptDate() : order.getOrderDate();
        LocalDate periodEnd = lastReceiptDate;
        List<QualityPartnerEvaluation> qsFacts = qualityPartnerEvaluationMapper.selectList(
                new LambdaQueryWrapper<QualityPartnerEvaluation>()
                        .eq(QualityPartnerEvaluation::getTenantId, tenantId())
                        .eq(QualityPartnerEvaluation::getProjectId, order.getProjectId())
                        .eq(QualityPartnerEvaluation::getPartnerId, order.getPartnerId())
                        .ge(QualityPartnerEvaluation::getEvaluatedAt, periodStart.atStartOfDay())
                        .lt(QualityPartnerEvaluation::getEvaluatedAt, periodEnd.plusDays(1).atStartOfDay()));
        BigDecimal qsAverage = qsFacts.isEmpty() ? null : qsFacts.stream().map(QualityPartnerEvaluation::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(qsFacts.size()), 2, RoundingMode.HALF_UP);
        BigDecimal qualityScore = qsAverage == null ? receiptQuality
                : receiptQuality.multiply(new BigDecimal("0.70")).add(qsAverage.multiply(new BigDecimal("0.30")))
                .setScale(2, RoundingMode.HALF_UP);

        List<StlSettlement> settlements = settlementMapper.selectList(new LambdaQueryWrapper<StlSettlement>()
                .eq(StlSettlement::getTenantId, tenantId()).eq(StlSettlement::getContractId, order.getContractId())
                .eq(StlSettlement::getSettlementStatus, "FINALIZED"));
        if (settlements.isEmpty())
            throw new BusinessException("SP_SETTLEMENT_REQUIRED", "采购合同完成结算后才能确认综合履约评价");
        int returnCount = Math.toIntExact(supplierReturnMapper.selectCount(new LambdaQueryWrapper<SupplierReturn>()
                .eq(SupplierReturn::getTenantId, tenantId()).eq(SupplierReturn::getPurchaseOrderId, order.getId())
                .eq(SupplierReturn::getStatus, "CONFIRMED").between(SupplierReturn::getReturnDate, periodStart, periodEnd)));
        boolean hasDeduction = settlements.stream().map(StlSettlement::getDeductionAmount).filter(Objects::nonNull)
                .anyMatch(amount -> amount.signum() > 0);
        BigDecimal commercialScore = HUNDRED.subtract(BigDecimal.valueOf(Math.min(40, returnCount * 10 + (hasDeduction ? 10 : 0))));
        BigDecimal total = weighted(deliveryScore, "0.30").add(weighted(qualityScore, "0.35"))
                .add(weighted(command.serviceScore(), "0.15")).add(weighted(commercialScore, "0.20"))
                .setScale(2, RoundingMode.HALF_UP);

        SupplierPerformanceEvaluation evaluation = new SupplierPerformanceEvaluation();
        evaluation.setTenantId(tenantId());
        evaluation.setProjectId(order.getProjectId());
        evaluation.setPartnerId(order.getPartnerId());
        evaluation.setContractId(order.getContractId());
        evaluation.setPurchaseOrderId(order.getId());
        evaluation.setEvaluationCode("SPE-" + order.getId());
        evaluation.setPeriodStart(periodStart);
        evaluation.setPeriodEnd(periodEnd);
        evaluation.setDeliveryScore(deliveryScore);
        evaluation.setQualityScore(qualityScore);
        evaluation.setServiceScore(score(command.serviceScore()));
        evaluation.setCommercialScore(commercialScore);
        evaluation.setTotalScore(total);
        evaluation.setGrade(grade(total));
        evaluation.setOnTimeFlag(onTime);
        evaluation.setApprovedReceiptCount(receipts.size());
        evaluation.setUnqualifiedReceiptCount((int) receipts.stream().filter(r -> "UNQUALIFIED".equals(r.getQualityStatus())).count());
        evaluation.setReturnCount(returnCount);
        evaluation.setFinalizedSettlementCount(settlements.size());
        evaluation.setQualitySafetyFactCount(qsFacts.size());
        evaluation.setQualitySafetyAverage(qsAverage);
        evaluation.setEvaluationComment(command.evaluationComment().trim());
        evaluation.setRecommendBlacklist(total.compareTo(new BigDecimal("60")) < 0 || qualityScore.compareTo(new BigDecimal("60")) < 0 ? 1 : 0);
        evaluation.setStatus("DRAFT");
        evaluation.setVersion(0);
        try {
            performanceMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_PERFORMANCE_DUPLICATE", "同一采购订单只能生成一份履约评价");
        }
        return requirePerformance(evaluation.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierPerformanceEvaluation confirmPerformance(Long id) {
        SupplierPerformanceEvaluation evaluation = requirePerformance(id);
        projectAccessChecker.checkAccess(evaluation.getProjectId(), "确认供应商履约评价");
        int updated = performanceMapper.update(null, new LambdaUpdateWrapper<SupplierPerformanceEvaluation>()
                .eq(SupplierPerformanceEvaluation::getId, id).eq(SupplierPerformanceEvaluation::getTenantId, tenantId())
                .eq(SupplierPerformanceEvaluation::getStatus, "DRAFT")
                .set(SupplierPerformanceEvaluation::getStatus, "CONFIRMED")
                .set(SupplierPerformanceEvaluation::getConfirmedBy, userId())
                .set(SupplierPerformanceEvaluation::getConfirmedAt, LocalDateTime.now()));
        if (updated != 1) throw new BusinessException("SP_PERFORMANCE_IMMUTABLE", "履约评价已确认，禁止重复确认或修改");
        return requirePerformance(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierBlacklistRecord createBlacklist(BlacklistCommand command) {
        SupplierPerformanceEvaluation evaluation = requirePerformance(command.performanceEvaluationId());
        projectAccessChecker.checkAccess(evaluation.getProjectId(), "发起供应商黑名单审批");
        if (!"CONFIRMED".equals(evaluation.getStatus()) || !Objects.equals(evaluation.getRecommendBlacklist(), 1))
            throw new BusinessException("SP_BLACKLIST_BASIS_INVALID", "只有已确认且触发黑名单建议的履约评价可以发起审批");
        MdPartner partner = partnerMapper.selectById(evaluation.getPartnerId());
        if (partner != null && Objects.equals(partner.getBlacklistFlag(), 1))
            throw new BusinessException("SP_PARTNER_ALREADY_BLACKLISTED", "供应商已在黑名单中");
        SupplierBlacklistRecord record = new SupplierBlacklistRecord();
        record.setTenantId(tenantId());
        record.setPerformanceEvaluationId(evaluation.getId());
        record.setPartnerId(evaluation.getPartnerId());
        record.setProjectId(evaluation.getProjectId());
        record.setActionType("ADD");
        record.setReason(command.reason().trim());
        record.setStatus("DRAFT");
        record.setVersion(0);
        try {
            blacklistMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SP_BLACKLIST_DUPLICATE", "该履约评价已发起黑名单审批");
        }
        return requireBlacklist(record.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierBlacklistRecord submitBlacklist(Long id) {
        SupplierBlacklistRecord record = requireBlacklist(id);
        projectAccessChecker.checkAccess(record.getProjectId(), "提交供应商黑名单审批");
        int updated = blacklistMapper.update(null, new LambdaUpdateWrapper<SupplierBlacklistRecord>()
                .eq(SupplierBlacklistRecord::getId, id).eq(SupplierBlacklistRecord::getTenantId, tenantId())
                .eq(SupplierBlacklistRecord::getStatus, "DRAFT")
                .set(SupplierBlacklistRecord::getStatus, "SUBMITTED")
                .set(SupplierBlacklistRecord::getSubmittedBy, userId())
                .set(SupplierBlacklistRecord::getSubmittedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        return requireBlacklist(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierBlacklistRecord reviewBlacklist(Long id, ReviewCommand command) {
        SupplierBlacklistRecord record = requireBlacklist(id);
        projectAccessChecker.checkAccess(record.getProjectId(), "审核供应商黑名单");
        if (!"SUBMITTED".equals(record.getStatus()))
            throw new BusinessException("SP_BLACKLIST_NOT_SUBMITTED", "只有已提交记录可以审核");
        if (Objects.equals(record.getSubmittedBy(), userId()))
            throw new BusinessException("SP_BLACKLIST_SELF_REVIEW", "黑名单发起人不能审核本人提交的申请");
        String decision = upper(command.decision());
        if (!Set.of("APPROVE", "REJECT").contains(decision))
            throw new BusinessException("SP_BLACKLIST_DECISION_INVALID", "审核结果必须为通过或驳回");
        String next = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        int updated = blacklistMapper.update(null, new LambdaUpdateWrapper<SupplierBlacklistRecord>()
                .eq(SupplierBlacklistRecord::getId, id).eq(SupplierBlacklistRecord::getTenantId, tenantId())
                .eq(SupplierBlacklistRecord::getStatus, "SUBMITTED")
                .set(SupplierBlacklistRecord::getStatus, next).set(SupplierBlacklistRecord::getReviewedBy, userId())
                .set(SupplierBlacklistRecord::getReviewedAt, LocalDateTime.now())
                .set(SupplierBlacklistRecord::getReviewComment, command.comment().trim()));
        if (updated != 1) throw concurrent();
        if ("APPROVED".equals(next)) {
            int partnerUpdated = partnerMapper.update(null, new LambdaUpdateWrapper<MdPartner>()
                    .eq(MdPartner::getId, record.getPartnerId()).eq(MdPartner::getTenantId, tenantId())
                    .ne(MdPartner::getBlacklistFlag, 1)
                    .set(MdPartner::getBlacklistFlag, 1).set(MdPartner::getRiskLevel, "HIGH"));
            if (partnerUpdated != 1)
                throw new BusinessException("SP_PARTNER_BLACKLIST_CONFLICT", "供应商状态已变化，请刷新后重试");
        }
        return requireBlacklist(id);
    }

    public SourcingTrace trace(Long eventId) {
        SourcingEvent event = requireEvent(eventId);
        projectAccessChecker.checkAccess(event.getProjectId(), "查询供应商招采履约全链路");
        MatPurchaseRequest request = purchaseRequestMapper.selectById(event.getPurchaseRequestId());
        List<SourcingSupplier> suppliers = listSuppliers(eventId);
        List<SupplierQuote> quotes = listQuotes(eventId);
        List<BidEvaluation> bidEvaluations = listEvaluations(eventId);
        CtContract contract = event.getContractId() == null ? null : contractMapper.selectById(event.getContractId());
        LambdaQueryWrapper<MatPurchaseOrder> orderQuery = new LambdaQueryWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getTenantId, tenantId())
                .and(w -> {
                    w.eq(MatPurchaseOrder::getRequestId, event.getPurchaseRequestId());
                    if (event.getContractId() != null) w.or().eq(MatPurchaseOrder::getContractId, event.getContractId());
                });
        List<MatPurchaseOrder> orders = purchaseOrderMapper.selectList(orderQuery.orderByAsc(MatPurchaseOrder::getOrderDate));
        Set<Long> orderIds = orders.stream().map(MatPurchaseOrder::getId).collect(Collectors.toSet());
        List<MatReceipt> receipts = orderIds.isEmpty() ? List.of() : receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId()).in(MatReceipt::getOrderId, orderIds).orderByAsc(MatReceipt::getReceiptDate));
        List<SupplierReturn> supplierReturns = orderIds.isEmpty() ? List.of() : supplierReturnMapper.selectList(
                new LambdaQueryWrapper<SupplierReturn>().eq(SupplierReturn::getTenantId, tenantId())
                        .in(SupplierReturn::getPurchaseOrderId, orderIds).orderByAsc(SupplierReturn::getReturnDate));
        List<StlSettlement> settlements = event.getContractId() == null ? List.of() : settlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>().eq(StlSettlement::getTenantId, tenantId())
                        .eq(StlSettlement::getContractId, event.getContractId()).orderByAsc(StlSettlement::getCreatedAt));
        List<SupplierPerformanceEvaluation> performance = orderIds.isEmpty() ? List.of() : performanceMapper.selectList(
                new LambdaQueryWrapper<SupplierPerformanceEvaluation>().eq(SupplierPerformanceEvaluation::getTenantId, tenantId())
                        .in(SupplierPerformanceEvaluation::getPurchaseOrderId, orderIds).orderByAsc(SupplierPerformanceEvaluation::getPeriodEnd));
        Set<Long> performanceIds = performance.stream().map(SupplierPerformanceEvaluation::getId).collect(Collectors.toSet());
        List<SupplierBlacklistRecord> blacklists = performanceIds.isEmpty() ? List.of() : blacklistMapper.selectList(
                new LambdaQueryWrapper<SupplierBlacklistRecord>().eq(SupplierBlacklistRecord::getTenantId, tenantId())
                        .in(SupplierBlacklistRecord::getPerformanceEvaluationId, performanceIds));
        List<QualityPartnerEvaluation> qualityFacts = event.getAwardedPartnerId() == null ? List.of() : qualityPartnerEvaluationMapper.selectList(
                new LambdaQueryWrapper<QualityPartnerEvaluation>().eq(QualityPartnerEvaluation::getTenantId, tenantId())
                        .eq(QualityPartnerEvaluation::getProjectId, event.getProjectId())
                        .eq(QualityPartnerEvaluation::getPartnerId, event.getAwardedPartnerId())
                        .orderByAsc(QualityPartnerEvaluation::getEvaluatedAt));
        return new SourcingTrace(event, request, suppliers, quotes, bidEvaluations, contract, orders, receipts, supplierReturns,
                settlements, performance, blacklists, qualityFacts);
    }

    private void ensureDeliveryComplete(List<MatPurchaseOrderItem> orderItems, List<MatReceipt> receipts) {
        Set<Long> receiptIds = receipts.stream().map(MatReceipt::getId).collect(Collectors.toSet());
        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getTenantId, tenantId()).in(MatReceiptItem::getReceiptId, receiptIds));
        Map<Long, BigDecimal> received = receiptItems.stream().filter(item -> item.getOrderItemId() != null && item.getActualQuantity() != null)
                .collect(Collectors.toMap(MatReceiptItem::getOrderItemId, MatReceiptItem::getActualQuantity, BigDecimal::add));
        boolean complete = orderItems.stream().allMatch(item -> item.getQuantity() != null
                && received.getOrDefault(item.getId(), BigDecimal.ZERO).compareTo(item.getQuantity()) >= 0);
        if (!complete) throw new BusinessException("SP_DELIVERY_NOT_COMPLETE", "采购订单尚未完成全部验收，不能生成终期履约评价");
    }

    private void validateQuote(QuoteCommand command, SourcingEvent event) {
        if (command.totalAmount().signum() <= 0 || command.deliveryDays() < 0)
            throw new BusinessException("SP_QUOTE_INVALID", "报价金额必须大于零且交付天数不能为负");
        if (command.validityDate().isBefore(event.getDeadline().toLocalDate()))
            throw new BusinessException("SP_QUOTE_VALIDITY_INVALID", "报价有效期不能早于报价截止日期");
    }

    private MdPartner requireEligibleSupplier(Long id) {
        MdPartner partner = partnerMapper.selectById(id);
        if (partner == null || !Objects.equals(partner.getTenantId(), tenantId()))
            throw new BusinessException("SP_SUPPLIER_NOT_FOUND", "供应商不存在");
        if (!"SUPPLIER".equals(upper(partner.getPartnerType())))
            throw new BusinessException("SP_PARTNER_TYPE_INVALID", "受邀合作方必须是供应商");
        if (!"ENABLE".equals(partner.getStatus()))
            throw new BusinessException("SP_SUPPLIER_DISABLED", "供应商已停用");
        if (Objects.equals(partner.getBlacklistFlag(), 1))
            throw new BusinessException("SP_SUPPLIER_BLACKLISTED", "黑名单供应商禁止参与询价、招标或采购履约");
        return partner;
    }

    private void requireActiveProject(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId()))
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!"ACTIVE".equals(project.getStatus()))
            throw new BusinessException("SP_PROJECT_NOT_ACTIVE", "只有进行中的项目可以发起供应商招采");
    }

    private void requireFile(String businessType, Long businessId, String documentType, String message) {
        long count = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId()).eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId).eq(SysFile::getDocumentType, documentType)
                .eq(SysFile::getVirusScanStatus, "CLEAN"));
        if (count == 0) throw new BusinessException("SP_ATTACHMENT_REQUIRED", message);
    }

    private SourcingEvent requireEvent(Long id) {
        SourcingEvent event = eventMapper.selectById(id);
        if (event == null || !Objects.equals(event.getTenantId(), tenantId()))
            throw new BusinessException("SP_SOURCING_NOT_FOUND", "招采事件不存在");
        return event;
    }

    private SourcingSupplier requireInvitation(Long eventId, Long partnerId) {
        SourcingSupplier row = sourcingSupplierMapper.selectOne(new LambdaQueryWrapper<SourcingSupplier>()
                .eq(SourcingSupplier::getTenantId, tenantId()).eq(SourcingSupplier::getSourcingEventId, eventId)
                .eq(SourcingSupplier::getPartnerId, partnerId));
        if (row == null) throw new BusinessException("SP_SUPPLIER_NOT_INVITED", "供应商未被邀请参与当前招采事件");
        return row;
    }

    private SupplierQuote requireQuote(Long id) {
        SupplierQuote quote = quoteMapper.selectById(id);
        if (quote == null || !Objects.equals(quote.getTenantId(), tenantId()))
            throw new BusinessException("SP_QUOTE_NOT_FOUND", "供应商报价不存在");
        return quote;
    }

    private SupplierPerformanceEvaluation requirePerformance(Long id) {
        SupplierPerformanceEvaluation row = performanceMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId()))
            throw new BusinessException("SP_PERFORMANCE_NOT_FOUND", "供应商履约评价不存在");
        return row;
    }

    private SupplierReturn requireSupplierReturn(Long id) {
        SupplierReturn row = supplierReturnMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId()))
            throw new BusinessException("SP_RETURN_NOT_FOUND", "供应商退货记录不存在");
        return row;
    }

    private SupplierBlacklistRecord requireBlacklist(Long id) {
        SupplierBlacklistRecord row = blacklistMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId()))
            throw new BusinessException("SP_BLACKLIST_NOT_FOUND", "供应商黑名单审批记录不存在");
        return row;
    }

    private void requireStatus(SourcingEvent row, String status, String message) {
        if (!status.equals(row.getStatus())) throw new BusinessException("SP_STATUS_INVALID", message);
    }

    private void requireStatus(SupplierQuote row, String status, String message) {
        if (!status.equals(row.getStatus())) throw new BusinessException("SP_STATUS_INVALID", message);
    }

    private BigDecimal receiptQualityScore(MatReceipt receipt) {
        return switch (Objects.toString(receipt.getQualityStatus(), "PENDING")) {
            case "QUALIFIED" -> HUNDRED;
            case "PARTIAL" -> new BigDecimal("70");
            case "UNQUALIFIED" -> BigDecimal.ZERO;
            default -> new BigDecimal("50");
        };
    }

    private String grade(BigDecimal total) {
        if (total.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (total.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (total.compareTo(new BigDecimal("70")) >= 0) return "C";
        if (total.compareTo(new BigDecimal("60")) >= 0) return "D";
        return "E";
    }

    private void validateScore(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(HUNDRED) > 0)
            throw new BusinessException("SP_SCORE_INVALID", "评分必须在0到100之间");
    }

    private BigDecimal weighted(BigDecimal value, String weight) {
        return value.multiply(new BigDecimal(weight));
    }

    private BigDecimal score(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private Long tenantId() { return UserContext.getCurrentTenantId(); }
    private Long userId() { return UserContext.getCurrentUserId(); }
    private BusinessException concurrent() { return new BusinessException("SP_CONCURRENT_UPDATE", "业务状态已变化，请刷新后重试"); }
}
