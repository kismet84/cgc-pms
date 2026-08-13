package com.cgcpms.contract.service;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.CtContractService.ContractProjectOption;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.ContractPerformanceReportVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class CtContractQueryOperations {

    private final CtContractMapper ctContractMapper;
    private final CtContractChangeMapper ctContractChangeMapper;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final PayRecordMapper payRecordMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfRecordMapper wfRecordMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final CtContractViewAssembler assembler;

    CtContractQueryOperations(CtContractMapper ctContractMapper,
                              CtContractChangeMapper ctContractChangeMapper,
                              ProjectBudgetMapper projectBudgetMapper,
                              PayRecordMapper payRecordMapper,
                              PmProjectMapper pmProjectMapper,
                              MdPartnerMapper mdPartnerMapper,
                              WfInstanceMapper wfInstanceMapper,
                              WfRecordMapper wfRecordMapper,
                              ProjectAccessChecker projectAccessChecker) {
        this.ctContractMapper = ctContractMapper;
        this.ctContractChangeMapper = ctContractChangeMapper;
        this.projectBudgetMapper = projectBudgetMapper;
        this.payRecordMapper = payRecordMapper;
        this.pmProjectMapper = pmProjectMapper;
        this.mdPartnerMapper = mdPartnerMapper;
        this.wfInstanceMapper = wfInstanceMapper;
        this.wfRecordMapper = wfRecordMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.assembler = new CtContractViewAssembler(pmProjectMapper, mdPartnerMapper);
    }

    List<ContractProjectOption> getProjectOptions() {
        List<PmProject> projects = projectAccessChecker.accessibleProjects();
        if (projects.isEmpty()) return List.of();
        Set<Long> projectIds = projects.stream().map(PmProject::getId).collect(Collectors.toSet());
        Set<Long> activeBudgetProjectIds = projectBudgetMapper.selectList(
                        new LambdaQueryWrapper<ProjectBudget>()
                                .eq(ProjectBudget::getTenantId, UserContext.getCurrentTenantId())
                                .in(ProjectBudget::getProjectId, projectIds)
                                .eq(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE)
                                .eq(ProjectBudget::getActiveFlag, 1))
                .stream().map(ProjectBudget::getProjectId).collect(Collectors.toSet());
        return projects.stream().map(project -> new ContractProjectOption(
                String.valueOf(project.getId()), project.getProjectCode(), project.getProjectName(),
                project.getStatus(),
                "APPROVED".equals(project.getApprovalStatus())
                        && Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE)
                        .contains(project.getStatus()),
                ProjectStatusConstants.ACTIVE.equals(project.getStatus())
                        && activeBudgetProjectIds.contains(project.getId())))
                .toList();
    }

    IPage<CtContractVO> getPage(long pageNo, long pageSize, String keyword,
                                       String contractCode, String contractName,
                                       String contractType, String contractStatus, String approvalStatus,
                                       Long projectId, Long partyAId, Long partyBId,
                                       LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        applyProjectScope(wrapper, CtContract::getProjectId, projectId, "查看合同台账");
        // keyword 全局搜索：匹配合同编号、合同名称、合同类型、甲方名称、乙方名称等字段
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w ->
                w.like(CtContract::getContractCode, keyword)
                    .or().like(CtContract::getContractName, keyword)
                    .or().like(CtContract::getContractType, keyword)
            );
        }
        if (StringUtils.hasText(contractCode)) wrapper.like(CtContract::getContractCode, contractCode);
        if (StringUtils.hasText(contractName)) wrapper.like(CtContract::getContractName, contractName);
        if (StringUtils.hasText(contractType)) wrapper.eq(CtContract::getContractType, contractType);
        if (StringUtils.hasText(contractStatus)) wrapper.eq(CtContract::getContractStatus, contractStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContract::getApprovalStatus, approvalStatus);
        if (partyAId != null) wrapper.eq(CtContract::getPartyAId, partyAId);
        if (partyBId != null) wrapper.eq(CtContract::getPartyBId, partyBId);
        if (startDate != null) wrapper.ge(CtContract::getSignedDate, startDate);
        if (endDate != null) wrapper.le(CtContract::getSignedDate, endDate);
        wrapper.eq(CtContract::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(CtContract::getCreatedAt);

        Page<CtContract> page = ctContractMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/partner names to avoid N+1 queries.
        List<CtContract> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(CtContract::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
                Set<Long> partyAIds = records.stream()
                .map(CtContract::getPartyAId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partyBIds = records.stream()
                .map(CtContract::getPartyBId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allPartyIds = new java.util.HashSet<>(partyAIds);
        allPartyIds.addAll(partyBIds);

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> partyNames = allPartyIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectByIds(allPartyIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(c -> assembler.toVO(c, projectNames, partyNames));
    }

    Map<String, Object> getKpi(String contractCode, String contractName,
                                      String contractType, String contractStatus, String approvalStatus,
                                      Long projectId, Long partyAId, Long partyBId,
                                      LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        applyProjectScope(wrapper, CtContract::getProjectId, projectId, "查看合同台账");
        if (StringUtils.hasText(contractCode)) wrapper.like(CtContract::getContractCode, contractCode);
        if (StringUtils.hasText(contractName)) wrapper.like(CtContract::getContractName, contractName);
        if (StringUtils.hasText(contractType)) wrapper.eq(CtContract::getContractType, contractType);
        if (StringUtils.hasText(contractStatus)) wrapper.eq(CtContract::getContractStatus, contractStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContract::getApprovalStatus, approvalStatus);
        if (partyAId != null) wrapper.eq(CtContract::getPartyAId, partyAId);
        if (partyBId != null) wrapper.eq(CtContract::getPartyBId, partyBId);
        if (startDate != null) wrapper.ge(CtContract::getSignedDate, startDate);
        if (endDate != null) wrapper.le(CtContract::getSignedDate, endDate);
        wrapper.eq(CtContract::getTenantId, UserContext.getCurrentTenantId());

        List<CtContract> contracts = ctContractMapper.selectList(wrapper);
        BigDecimal totalAmount = contracts.stream()
                .map(contract -> {
                    BigDecimal current = contract.getCurrentAmount();
                    // currentAmount 默认值为 0（非 null），无法区分"未设"和"真的为 0"
                    // 当 currentAmount 为 0 或 null 时回退到 contractAmount
                    if (current != null && current.compareTo(BigDecimal.ZERO) != 0) {
                        return current;
                    }
                    return nvl(contract.getContractAmount());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = contracts.stream()
                .map(contract -> nvl(contract.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdueCount = contracts.stream()
                .filter(contract -> contract.getEndDate() != null && contract.getEndDate().isBefore(LocalDate.now()))
                .filter(contract -> !"SETTLED".equals(contract.getContractStatus()))
                .count();

        return Map.of(
                "totalCount", (long) contracts.size(),
                "totalAmount", totalAmount.toPlainString(),
                "paidAmount", paidAmount.toPlainString(),
                "unpaidAmount", totalAmount.subtract(paidAmount).toPlainString(),
                "overdueCount", overdueCount);
    }

    ContractPerformanceReportVO getPerformanceReport(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId);
        applyProjectScope(wrapper, CtContract::getProjectId, projectId, "查看合同履约报表");
        List<CtContract> contracts = ctContractMapper.selectList(wrapper);
        List<Long> contractIds = contracts.stream().map(CtContract::getId).toList();

        Map<Long, BigDecimal> changeByContract = contractIds.isEmpty() ? Map.of()
                : ctContractChangeMapper.selectList(new LambdaQueryWrapper<CtContractChange>()
                        .eq(CtContractChange::getTenantId, tenantId)
                        .in(CtContractChange::getContractId, contractIds)
                        .eq(CtContractChange::getApprovalStatus, "APPROVED"))
                .stream()
                .collect(Collectors.groupingBy(CtContractChange::getContractId,
                        Collectors.mapping(c -> nvl(c.getChangeAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<Long, BigDecimal> paidByContract = contractIds.isEmpty() ? Map.of()
                : payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .in(PayRecord::getContractId, contractIds)
                        .eq(PayRecord::getPayStatus, "SUCCESS"))
                .stream()
                .collect(Collectors.groupingBy(PayRecord::getContractId,
                        Collectors.mapping(p -> nvl(p.getPayAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        ContractPerformanceReportVO report = new ContractPerformanceReportVO();
        List<ContractPerformanceReportVO.Row> rows = contracts.stream()
                .map(contract -> assembler.toPerformanceRow(contract, changeByContract, paidByContract))
                .toList();
        report.setRows(rows);
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> nvl(c.getContractAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalChangeAmount = changeByContract.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaidAmount = paidByContract.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        report.setTotalContractAmount(totalContractAmount.toPlainString());
        report.setTotalChangeAmount(totalChangeAmount.toPlainString());
        report.setTotalPaidAmount(totalPaidAmount.toPlainString());
        report.setPaymentProgress(CtContractViewAssembler.formatRatio(totalPaidAmount, totalContractAmount.add(totalChangeAmount)));
        return report;
    }

    CtContractVO getById(Long id) {
        CtContract c = ctContractMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        if (c.getProjectId() != null) {
            projectAccessChecker.checkAccess(c.getProjectId(), "查看合同详情");
        }
        return assembler.toVO(c);
    }

    List<ContractApprovalRecordVO> getApprovalRecords(Long contractId) {
        Long tenantId = UserContext.getCurrentTenantId();
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        if (contract.getProjectId() != null) {
            projectAccessChecker.checkAccess(contract.getProjectId(), "查看合同审批记录");
        }
        // 1. 查 wf_instance WHERE businessType=CONTRACT_APPROVAL AND businessId=contractId AND tenantId=?
        LambdaQueryWrapper<WfInstance> instQw = new LambdaQueryWrapper<>();
        instQw.eq(WfInstance::getBusinessType, ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL)
                .eq(WfInstance::getBusinessId, contractId)
                .eq(WfInstance::getTenantId, tenantId);
        WfInstance instance = wfInstanceMapper.selectOne(instQw);
        if (instance == null) return List.of();

        // 2. 查 wf_record WHERE instanceId=instance.id AND tenantId=? ORDER BY createdAt ASC
        LambdaQueryWrapper<WfRecord> recQw = new LambdaQueryWrapper<>();
        recQw.eq(WfRecord::getInstanceId, instance.getId())
                .eq(WfRecord::getTenantId, tenantId)
                .orderByAsc(WfRecord::getCreatedAt);
        List<WfRecord> records = wfRecordMapper.selectList(recQw);

        // 3. 转 VO
        return records.stream().map(r -> {
            ContractApprovalRecordVO vo = new ContractApprovalRecordVO();
            vo.setId(r.getId() != null ? r.getId().toString() : null);
            vo.setNodeName(r.getNodeName());
            vo.setOperatorName(r.getOperatorName());
            vo.setActionType(r.getActionType());
            vo.setActionName(r.getActionName());
            vo.setComment(r.getComment());
            vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DateTimeUtils.DTF) : null);
            return vo;
        }).toList();
    }

    private <T> void applyProjectScope(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> projectField,
                                       Long projectId, String action) {
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, action);
            wrapper.eq(projectField, projectId);
            return;
        }
        List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
        if (accessibleProjectIds.isEmpty()) {
            wrapper.eq(projectField, -1L);
            return;
        }
        wrapper.in(projectField, accessibleProjectIds);
    }
}
