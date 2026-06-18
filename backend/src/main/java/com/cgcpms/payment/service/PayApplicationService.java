package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.vo.PayApplicationBasisVO;
import com.cgcpms.payment.vo.PayApplicationVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
// TODO: 拆分超大文件 (667行) — 拆分为 PayApplicationQueryService + PayApplicationWriteService + PayApplicationAssembler
public class PayApplicationService {

    private final PayApplicationMapper payApplicationMapper;
    private final PayApplicationBasisMapper payApplicationBasisMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final MatReceiptItemMapper matReceiptItemMapper;
    private final SubMeasureItemMapper subMeasureItemMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final CtContractPaymentTermMapper contractPaymentTermMapper;
    private final PayRecordMapper payRecordMapper;
    private final WorkflowEngine workflowEngine;

    public PayApplicationService(
            PayApplicationMapper payApplicationMapper,
            PayApplicationBasisMapper payApplicationBasisMapper,
            PmProjectMapper pmProjectMapper,
            CtContractMapper ctContractMapper,
            MdPartnerMapper mdPartnerMapper,
            MatReceiptItemMapper matReceiptItemMapper,
            SubMeasureItemMapper subMeasureItemMapper,
            MatReceiptMapper matReceiptMapper,
            SubMeasureMapper subMeasureMapper,
            CtContractPaymentTermMapper contractPaymentTermMapper,
            PayRecordMapper payRecordMapper,
            @org.springframework.context.annotation.Lazy WorkflowEngine workflowEngine) {
        this.payApplicationMapper = payApplicationMapper;
        this.payApplicationBasisMapper = payApplicationBasisMapper;
        this.pmProjectMapper = pmProjectMapper;
        this.ctContractMapper = ctContractMapper;
        this.mdPartnerMapper = mdPartnerMapper;
        this.matReceiptItemMapper = matReceiptItemMapper;
        this.subMeasureItemMapper = subMeasureItemMapper;
        this.matReceiptMapper = matReceiptMapper;
        this.subMeasureMapper = subMeasureMapper;
        this.contractPaymentTermMapper = contractPaymentTermMapper;
        this.payRecordMapper = payRecordMapper;
        this.workflowEngine = workflowEngine;
    }

    // ---- Query ----

    public IPage<PayApplicationVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                           Long partnerId, String payStatus, String approvalStatus, String applyCode) {
        LambdaQueryWrapper<PayApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayApplication::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(PayApplication::getProjectId, projectId);
        if (contractId != null) wrapper.eq(PayApplication::getContractId, contractId);
        if (partnerId != null) wrapper.eq(PayApplication::getPartnerId, partnerId);
        if (StringUtils.hasText(payStatus)) wrapper.eq(PayApplication::getPayStatus, payStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(PayApplication::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(applyCode)) wrapper.like(PayApplication::getApplyCode, applyCode);
        wrapper.orderByDesc(PayApplication::getCreatedAt);

        Page<PayApplication> page = payApplicationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/contract/partner names to avoid N+1 queries
        List<PayApplication> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(PayApplication::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(PayApplication::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(PayApplication::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
    }

    public PayApplicationVO getById(Long id) {
        PayApplication app = payApplicationMapper.selectById(id);
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

        // Pre-fetch related name maps to avoid N+1 queries (same pattern as getPage)
        Map<Long, String> projectNames = app.getProjectId() != null
                ? pmProjectMapper.selectBatchIds(Set.of(app.getProjectId())).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a))
                : Map.of();
        Map<Long, String> contractNames = app.getContractId() != null
                ? ctContractMapper.selectBatchIds(Set.of(app.getContractId())).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a))
                : Map.of();
        Map<Long, String> partnerNames = app.getPartnerId() != null
                ? mdPartnerMapper.selectBatchIds(Set.of(app.getPartnerId())).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a))
                : Map.of();

        PayApplicationVO vo = toVO(app, projectNames, contractNames, partnerNames);

        // Load basis items
        List<PayApplicationBasis> basisList = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getPayApplicationId, id));
        vo.setBasis(basisList.stream().map(this::toBasisVO).collect(Collectors.toList()));

        return vo;
    }

    public List<PayApplicationBasisVO> getBasisList(Long applicationId) {
        List<PayApplicationBasis> basisList = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getTenantId, UserContext.getCurrentTenantId())
                        .eq(PayApplicationBasis::getPayApplicationId, applicationId));
        return basisList.stream().map(this::toBasisVO).collect(Collectors.toList());
    }

    // ---- CRUD ----

    @Transactional
    public Long create(PayApplication app) {
        // Auto-generate apply code: PAY-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "PAY-" + today + "-";

        LambdaQueryWrapper<PayApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(PayApplication::getApplyCode, prefix)
                .orderByDesc(PayApplication::getApplyCode);
        Page<PayApplication> page = new Page<>(0, 1);
        Page<PayApplication> result = payApplicationMapper.selectPage(page, wrapper);
        PayApplication last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1;
        if (last != null && last.getApplyCode() != null && last.getApplyCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getApplyCode().substring(last.getApplyCode().lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getApplyCode(), e);
            }
        }
        app.setApplyCode(prefix + String.format("%03d", seq));

        // Default statuses
        if (app.getPayStatus() == null || app.getPayStatus().isBlank()) {
            app.setPayStatus("PENDING");
        }
        if (app.getApprovalStatus() == null || app.getApprovalStatus().isBlank()) {
            app.setApprovalStatus("DRAFT");
        }

        app.setTenantId(UserContext.getCurrentTenantId());
        payApplicationMapper.insert(app);
        return app.getId();
    }

    @Transactional
    public void update(PayApplication app) {
        PayApplication existing = payApplicationMapper.selectById(app.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("PAY_APP_IN_APPROVAL", "付款申请审批中或已审批，不可编辑");

        app.setApprovalStatus(existing.getApprovalStatus());
        app.setPayStatus(existing.getPayStatus());
        payApplicationMapper.updateById(app);
    }

    @Transactional
    public void delete(Long id) {
        PayApplication existing = payApplicationMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("PAY_APP_IN_APPROVAL", "付款申请审批中或已审批，不可删除");

        // Delete basis records first
        payApplicationBasisMapper.delete(new LambdaQueryWrapper<PayApplicationBasis>()
                .eq(PayApplicationBasis::getPayApplicationId, id));

        payApplicationMapper.deleteById(id);
    }

    // ---- Contract balance check (called before payment writeback) ----

    /**
     * Verify that a contract still has enough balance before a payment is written back.
     * Called from PayRecordService.writeback() as an authoritative gate.
     *
     * @param app the pay application being written back
     * @param pendingAmount the amount about to be written (checked against remaining balance)
     */
    public void checkContractBalance(PayApplication app, BigDecimal pendingAmount) {
        Long contractId = app.getContractId();
        if (contractId == null) return;

        CtContract contract = ctContractMapper.selectByIdForUpdate(contractId);
        if (contract == null) return;

        BigDecimal currentAmount = contract.getCurrentAmount() != null
                ? contract.getCurrentAmount() : BigDecimal.ZERO;

        // Sum all SUCCESS pay records across ALL applications for this contract
        List<PayRecord> allPaid = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getContractId, contractId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal totalPaid = allPaid.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Include the pending amount in the total to prevent concurrent overpay
        BigDecimal effectiveTotal = totalPaid.add(pendingAmount != null ? pendingAmount : BigDecimal.ZERO);
        if (effectiveTotal.compareTo(currentAmount) > 0) {
            throw new BusinessException("EXCEED_CONTRACT_BALANCE",
                    "合同(" + contract.getContractName() + ")累计付款(" + totalPaid
                    + ") + 本次付款(" + (pendingAmount != null ? pendingAmount : BigDecimal.ZERO)
                    + ")超过当前合同金额(" + currentAmount + ")");
        }
    }

    // ---- Pay status update (called by PayRecordService) ----

    @Transactional
    public void updatePayStatus(Long applicationId) {
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getPayApplicationId, applicationId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal totalPaid = records.stream()
                .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayApplication app = payApplicationMapper.selectById(applicationId);
        if (app == null) return;

        String newStatus;
        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = "APPROVED";
        } else if (totalPaid.compareTo(app.getApplyAmount()) >= 0) {
            newStatus = "PAID";
        } else {
            newStatus = "PARTIALLY_PAID";
        }

        payApplicationMapper.update(null, new LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, applicationId)
                .set(PayApplication::getPayStatus, newStatus)
                .set(PayApplication::getActualPayAmount, totalPaid));
    }

    // ---- Basis batch save ----

    @Transactional
    public void saveBasis(Long applicationId, List<PayApplicationBasis> basisList) {
        PayApplication app = payApplicationMapper.selectById(applicationId);
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

        if (!"DRAFT".equals(app.getApprovalStatus()))
            throw new BusinessException("PAY_APP_IN_APPROVAL", "付款申请审批中或已审批，不可编辑付款依据");

        // Validate header amount == SUM(basis amount)
        BigDecimal headerAmount = app.getApplyAmount() != null ? app.getApplyAmount() : BigDecimal.ZERO;
        BigDecimal basisTotal = BigDecimal.ZERO;
        if (basisList != null) {
            // M2: Check for duplicate basis items within the batch
            Set<String> basisKeys = new HashSet<>();
            for (PayApplicationBasis basis : basisList) {
                String key = basis.getBasisType() + ":" + basis.getBasisId();
                if (!basisKeys.add(key)) {
                    throw new BusinessException("DUPLICATE_BASIS", "付款依据重复: " + key);
                }
            }
            for (PayApplicationBasis basis : basisList) {
                basisTotal = basisTotal.add(basis.getBasisAmount() != null ? basis.getBasisAmount() : BigDecimal.ZERO);
            }
        }
        if (headerAmount.compareTo(basisTotal) != 0) {
            throw new BusinessException("AMOUNT_MISMATCH", "申请金额与依据金额合计不一致");
        }

        // Delete old basis
        payApplicationBasisMapper.delete(new LambdaQueryWrapper<PayApplicationBasis>()
                .eq(PayApplicationBasis::getPayApplicationId, applicationId));

        // Batch insert new basis
        if (basisList != null) {
            for (PayApplicationBasis basis : basisList) {
                basis.setPayApplicationId(applicationId);
                basis.setTenantId(UserContext.getCurrentTenantId());
                basis.setId(null);
                payApplicationBasisMapper.insert(basis);
            }
        }
    }

    // ---- Approval ----

    /**
     * 提交审批。REPEATABLE_READ 隔离级别防止并发提交时的幻读绕过余额检查。
     * <p>
     * 防超付双重校验：
     * <ol>
     *   <li>validatePaymentAmount（本方法内）：用"已 APPROVED 的申请金额"推算可用余额。
     *       口径：仅含 APPROVED 状态的 PayApplication.applyAmount，不含 SUCCESS 实付金额。</li>
     *   <li>checkContractBalance（writeback 时，PayRecordService 调用）：用"已 SUCCESS 的实付金额"推算余额。
     *       口径：含所有 SUCCESS 状态的 PayRecord.payAmount 累加，不含待付/审批中记录。</li>
     * </ol>
     * 两个方法共同构成双层防线：submit 时预判可支付余额，writeback 时以实付口径二次确认。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void submitForApproval(Long id) {
        PayApplication payApp = payApplicationMapper.selectById(id);
        if (payApp == null || !payApp.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");

        if (!"DRAFT".equals(payApp.getApprovalStatus()))
            throw new BusinessException("INVALID_STATUS", "仅草稿状态的付款申请可提交审批");

        // Pessimistic lock on contract row to prevent concurrent bypass
        if (payApp.getContractId() != null) {
            ctContractMapper.selectByIdForUpdate(payApp.getContractId());
        }

        validatePaymentAmount(payApp);

        // 合同余额双重校验（与 writeback 时 checkContractBalance 互补）
        if (payApp.getContractId() != null) {
            checkContractBalance(payApp, payApp.getApplyAmount() != null ? payApp.getApplyAmount() : BigDecimal.ZERO);
        }

        // Re-validate M1: header amount == sum of basis amounts
        List<PayApplicationBasis> basisList = payApplicationBasisMapper.selectList(
            new LambdaQueryWrapper<PayApplicationBasis>()
                .eq(PayApplicationBasis::getPayApplicationId, payApp.getId()));
        BigDecimal basisTotal = basisList.stream()
            .map(b -> b.getBasisAmount() == null ? BigDecimal.ZERO : b.getBasisAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payApp.getApplyAmount().compareTo(basisTotal) != 0) {
            throw new BusinessException("PAY_AMOUNT_MISMATCH", "申请金额(" + payApp.getApplyAmount() + ")与依据金额合计(" + basisTotal + ")不一致");
        }

        payApp.setApprovalStatus("APPROVING");
        payApplicationMapper.updateById(payApp);

        workflowEngine.submit(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentUsername(),
                UserContext.getCurrentTenantId(),
                "PAY_REQUEST",
                payApp.getId(),
                "付款申请 " + payApp.getApplyCode(),
                payApp.getApplyAmount(),
                payApp.getProjectId(),
                payApp.getContractId(),
                payApp.getApplyReason(),
                null, null);
    }

    /**
     * 防超付校验（提交/审批通过时调用）。
     * <p>
     * 口径：仅含 APPROVED 状态的 PayApplication.applyAmount，不含 SUCCESS 实付金额。
     * 已付款金额推算为 {@code getApprovedSumForContract(contractId, excludeId)}，
     * 合同可用余额 = currentAmount - 已 APPROVED 的申请金额合计。
     *
     * @param payApp 付款申请单
     * @throws BusinessException 若申请金额超过合同可用余额或付款比例上限
     */
    public void validatePaymentAmount(PayApplication payApp) {
        BigDecimal applyAmount = payApp.getApplyAmount() != null ? payApp.getApplyAmount() : BigDecimal.ZERO;
        if (applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "申请金额必须大于0");
        }

        Long contractId = payApp.getContractId();
        if (contractId == null) {
            throw new BusinessException("MISSING_CONTRACT", "付款申请缺少关联合同");
        }

        // Rule 1: 本次申请金额 ≤ 合同可用余额
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "关联合同不存在");
        }
        // M-023: Use currentAmount (post-change-order) instead of contractAmount (original)
        BigDecimal currentAmount = contract.getCurrentAmount() != null ? contract.getCurrentAmount() : BigDecimal.ZERO;

        BigDecimal alreadyApprovedSum = getApprovedSumForContract(contractId, payApp.getId());
        BigDecimal availableBalance = currentAmount.subtract(alreadyApprovedSum);
        if (applyAmount.compareTo(availableBalance) > 0) {
            throw new BusinessException("EXCEED_CONTRACT_BALANCE",
                    "本次申请金额(" + applyAmount + ")超过合同可用余额(" + availableBalance + ")");
        }

        // Rule 2: contract payment ratio constraint
        List<CtContractPaymentTerm> terms = contractPaymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId));
        BigDecimal totalRatio = terms.stream()
                .map(t -> t.getPaymentRatio() != null ? t.getPaymentRatio() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRatio.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxByRatio = currentAmount
                    .multiply(totalRatio.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP))
                    .subtract(alreadyApprovedSum);
            if (applyAmount.compareTo(maxByRatio) > 0) {
                throw new BusinessException("PAY_RATIO_EXCEEDED",
                        "本次申请(" + applyAmount + ")超过合同付款比例允许金额(" + maxByRatio + ")");
            }
        }

        // Rule 3/4 basic: 依据项金额 ≤ 来源项金额
        List<PayApplicationBasis> basisList = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getPayApplicationId, payApp.getId()));
        if (basisList != null && !basisList.isEmpty()) {
            // Batch-load source items to avoid N+1 selects
            Map<Long, MatReceiptItem> receiptItemMap = batchLoadReceiptItems(basisList);
            Map<Long, SubMeasureItem> subMeasureItemMap = batchLoadSubMeasureItems(basisList);
            Map<Long, MatReceipt> receiptMap = batchLoadReceipts(receiptItemMap);
            Map<Long, SubMeasure> subMeasureMap = batchLoadSubMeasures(subMeasureItemMap);

            for (PayApplicationBasis basis : basisList) {
                validateBasisAmount(basis, contractId, receiptItemMap, subMeasureItemMap, receiptMap, subMeasureMap);
            }
        }
    }

    private BigDecimal getApprovedSumForContract(Long contractId, Long excludePayAppId) {
        LambdaQueryWrapper<PayApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayApplication::getContractId, contractId)
                .eq(PayApplication::getApprovalStatus, "APPROVED");
        if (excludePayAppId != null) {
            wrapper.ne(PayApplication::getId, excludePayAppId);
        }
        List<PayApplication> approvedApps = payApplicationMapper.selectList(wrapper);
        return approvedApps.stream()
                .map(a -> a.getApplyAmount() != null ? a.getApplyAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateBasisAmount(PayApplicationBasis basis, Long payContractId,
                                       Map<Long, MatReceiptItem> receiptItemMap,
                                       Map<Long, SubMeasureItem> subMeasureItemMap,
                                       Map<Long, MatReceipt> receiptMap,
                                       Map<Long, SubMeasure> subMeasureMap) {
        BigDecimal basisAmount = basis.getBasisAmount() != null ? basis.getBasisAmount() : BigDecimal.ZERO;
        String basisType = basis.getBasisType();
        Long basisId = basis.getBasisId();

        if (basisId == null) return;

        if ("MAT_RECEIPT".equals(basisType)) {
            MatReceiptItem item = receiptItemMap.get(basisId);
            if (item == null) {
                throw new BusinessException("RECEIPT_ITEM_NOT_FOUND",
                        "依据项(验收明细)不存在: id=" + basisId);
            }
            BigDecimal sourceAmount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            if (basisAmount.compareTo(sourceAmount) > 0) {
                throw new BusinessException("BASIS_EXCEED_SOURCE",
                        "付款依据金额(" + basisAmount + ")超过验收单明细金额(" + sourceAmount + ")");
            }
            // M3: Verify basis item belongs to same contract as payment
            if (item.getReceiptId() != null) {
                MatReceipt receipt = receiptMap.get(item.getReceiptId());
                if (receipt != null && !Objects.equals(receipt.getContractId(), payContractId)) {
                    throw new BusinessException("BASIS_CONTRACT_MISMATCH",
                            "依据单据(" + basisId + ")不属于付款申请合同");
                }
            }
        } else if ("SUB_MEASURE".equals(basisType)) {
            SubMeasureItem item = subMeasureItemMap.get(basisId);
            if (item == null) {
                throw new BusinessException("MEASURE_ITEM_NOT_FOUND",
                        "依据项(计量明细)不存在: id=" + basisId);
            }
            BigDecimal sourceAmount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            if (basisAmount.compareTo(sourceAmount) > 0) {
                throw new BusinessException("BASIS_EXCEED_SOURCE",
                        "付款依据金额(" + basisAmount + ")超过计量单明细金额(" + sourceAmount + ")");
            }
            // M3: Verify basis item belongs to same contract as payment
            if (item.getMeasureId() != null) {
                SubMeasure measure = subMeasureMap.get(item.getMeasureId());
                if (measure != null && !Objects.equals(measure.getContractId(), payContractId)) {
                    throw new BusinessException("BASIS_CONTRACT_MISMATCH",
                            "依据单据(" + basisId + ")不属于付款申请合同");
                }
            }
        }
    }

    private Map<Long, MatReceiptItem> batchLoadReceiptItems(List<PayApplicationBasis> basisList) {
        List<Long> ids = basisList.stream()
                .filter(b -> "MAT_RECEIPT".equals(b.getBasisType()))
                .map(PayApplicationBasis::getBasisId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return matReceiptItemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MatReceiptItem::getId, Function.identity()));
    }

    private Map<Long, SubMeasureItem> batchLoadSubMeasureItems(List<PayApplicationBasis> basisList) {
        List<Long> ids = basisList.stream()
                .filter(b -> "SUB_MEASURE".equals(b.getBasisType()))
                .map(PayApplicationBasis::getBasisId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return subMeasureItemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SubMeasureItem::getId, Function.identity()));
    }

    private Map<Long, MatReceipt> batchLoadReceipts(Map<Long, MatReceiptItem> itemMap) {
        if (itemMap.isEmpty()) return Map.of();
        List<Long> receiptIds = itemMap.values().stream()
                .map(MatReceiptItem::getReceiptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (receiptIds.isEmpty()) return Map.of();
        return matReceiptMapper.selectBatchIds(receiptIds).stream()
                .collect(Collectors.toMap(MatReceipt::getId, Function.identity()));
    }

    private Map<Long, SubMeasure> batchLoadSubMeasures(Map<Long, SubMeasureItem> itemMap) {
        if (itemMap.isEmpty()) return Map.of();
        List<Long> measureIds = itemMap.values().stream()
                .map(SubMeasureItem::getMeasureId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (measureIds.isEmpty()) return Map.of();
        return subMeasureMapper.selectBatchIds(measureIds).stream()
                .collect(Collectors.toMap(SubMeasure::getId, Function.identity()));
    }

    // ---- VO conversion helpers ----

    /**
     * @deprecated 此单参数重载每次调用会产生 N+1 查询（逐个查询 project/contract/partner），
     *             请优先使用 {@link #toVO(PayApplication, Map, Map, Map)} 批量预取版本。
     */
    @Deprecated
    private PayApplicationVO toVO(PayApplication app) {
        PayApplicationVO vo = buildBaseVO(app);
        if (app.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(app.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (app.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(app.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (app.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(app.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        return vo;
    }

    private PayApplicationVO toVO(PayApplication app, Map<Long, String> projectNames,
                                   Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        PayApplicationVO vo = buildBaseVO(app);
        if (app.getProjectId() != null) vo.setProjectName(projectNames.get(app.getProjectId()));
        if (app.getContractId() != null) vo.setContractName(contractNames.get(app.getContractId()));
        if (app.getPartnerId() != null) vo.setPartnerName(partnerNames.get(app.getPartnerId()));
        return vo;
    }

    private PayApplicationVO buildBaseVO(PayApplication app) {
        PayApplicationVO vo = new PayApplicationVO();
        vo.setId(app.getId() != null ? app.getId().toString() : null);
        vo.setTenantId(app.getTenantId() != null ? app.getTenantId().toString() : null);
        vo.setProjectId(app.getProjectId() != null ? app.getProjectId().toString() : null);
        vo.setContractId(app.getContractId() != null ? app.getContractId().toString() : null);
        vo.setPartnerId(app.getPartnerId() != null ? app.getPartnerId().toString() : null);
        vo.setApplyCode(app.getApplyCode());
        vo.setApplyAmount(app.getApplyAmount() != null ? app.getApplyAmount().toPlainString() : null);
        vo.setApprovedAmount(app.getApprovedAmount() != null ? app.getApprovedAmount().toPlainString() : null);
        vo.setActualPayAmount(app.getActualPayAmount() != null ? app.getActualPayAmount().toPlainString() : null);
        vo.setPayType(app.getPayType());
        vo.setPayStatus(app.getPayStatus());
        vo.setApprovalStatus(app.getApprovalStatus());
        vo.setApplyReason(app.getApplyReason());
        vo.setCreatedBy(app.getCreatedBy() != null ? app.getCreatedBy().toString() : null);
        vo.setCreatedAt(app.getCreatedAt() != null ? app.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(app.getUpdatedAt() != null ? app.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(app.getRemark());
        return vo;
    }

    private PayApplicationBasisVO toBasisVO(PayApplicationBasis basis) {
        PayApplicationBasisVO vo = new PayApplicationBasisVO();
        vo.setId(basis.getId() != null ? basis.getId().toString() : null);
        vo.setTenantId(basis.getTenantId() != null ? basis.getTenantId().toString() : null);
        vo.setPayApplicationId(basis.getPayApplicationId() != null ? basis.getPayApplicationId().toString() : null);
        vo.setBasisType(basis.getBasisType());
        vo.setBasisId(basis.getBasisId() != null ? basis.getBasisId().toString() : null);
        vo.setBasisAmount(basis.getBasisAmount() != null ? basis.getBasisAmount().toPlainString() : null);
        vo.setCreatedBy(basis.getCreatedBy() != null ? basis.getCreatedBy().toString() : null);
        vo.setCreatedAt(basis.getCreatedAt() != null ? basis.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(basis.getUpdatedAt() != null ? basis.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(basis.getRemark());
        return vo;
    }
}
