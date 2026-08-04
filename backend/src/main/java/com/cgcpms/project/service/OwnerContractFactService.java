package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Objects;

/** Single server-side source for the project's authoritative owner contract amount. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerContractFactService {
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final JdbcTemplate jdbc;

    public OwnerContractFact requireApprovedMain(PmProject project) {
        if (project == null || project.getOwnerContractId() == null) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_REQUIRED", "项目尚未指定权威业主主合同");
        }
        CtContract contract = contractMapper.selectById(project.getOwnerContractId());
        if (contract == null
                || !Objects.equals(contract.getTenantId(), project.getTenantId())
                || !Objects.equals(contract.getProjectId(), project.getId())
                || !"MAIN".equals(contract.getContractType())) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_INVALID", "权威业主主合同不存在、跨租户或不属于当前项目");
        }
        if (!ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_NOT_APPROVED", "权威业主主合同必须已审批且履约中");
        }
        if (contract.getCurrentAmount() == null || contract.getCurrentAmount().signum() <= 0) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_AMOUNT_INVALID", "权威业主主合同当前金额必须大于0");
        }
        return fact(contract);
    }

    @Transactional(rollbackFor = Exception.class)
    public OwnerContractFact synchronizeApprovedMainContract(Long contractId, Long tenantId) {
        CtContract contract = contractMapper.selectByIdForUpdate(contractId, tenantId);
        if (contract == null || !"MAIN".equals(contract.getContractType())
                || !ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_INVALID", "仅已审批履约中的MAIN合同可同步项目金额");
        }
        PmProject project = projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, contract.getProjectId())
                .eq(PmProject::getTenantId, tenantId)
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (project == null) {
            throw new BusinessException("CONTRACT_PROJECT_NOT_FOUND", "关联合同项目不存在");
        }
        warnBidAwardDifference(project, contract);

        Long ownerContractId = project.getOwnerContractId();
        if (ownerContractId == null) {
            long candidates = contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                    .eq(CtContract::getTenantId, tenantId)
                    .eq(CtContract::getProjectId, project.getId())
                    .eq(CtContract::getContractType, "MAIN")
                    .eq(CtContract::getApprovalStatus, ContractStatusConstants.APPROVAL_APPROVED)
                    .eq(CtContract::getContractStatus, ContractStatusConstants.STATUS_PERFORMING));
            if (candidates != 1) {
                throw new BusinessException("PROJECT_OWNER_CONTRACT_AMBIGUOUS", "项目存在多份候选MAIN合同，必须人工确认权威合同");
            }
            ownerContractId = contractId;
        } else if (!ownerContractId.equals(contractId)) {
            log.warn("MAIN合同审批通过但不是项目权威合同，不更新项目金额 projectId={}, contractId={}, ownerContractId={}",
                    project.getId(), contractId, ownerContractId);
            return requireApprovedMain(project);
        }

        Long selectedOwnerContractId = ownerContractId;
        int updated = projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, project.getId())
                .eq(PmProject::getTenantId, tenantId)
                .and(w -> w.eq(PmProject::getOwnerContractId, selectedOwnerContractId)
                        .or().isNull(PmProject::getOwnerContractId))
                .set(PmProject::getOwnerContractId, selectedOwnerContractId)
                .set(PmProject::getContractAmount, contract.getCurrentAmount()));
        if (updated != 1) {
            throw new BusinessException("PROJECT_OWNER_CONTRACT_CONFLICT", "项目权威合同已被并发修改，请刷新后重试");
        }
        PmProject reread = projectMapper.selectById(project.getId());
        OwnerContractFact fact = requireApprovedMain(reread);
        if (!fact.contractId().equals(contractId)
                || fact.currentAmount().compareTo(contract.getCurrentAmount()) != 0) {
            throw new BusinessException("PROJECT_CONTRACT_PROJECTION_MISMATCH", "项目合同金额投影写后回读不一致");
        }
        return fact;
    }

    private void warnBidAwardDifference(PmProject project, CtContract contract) {
        if (project.getSourceBidCostId() == null || contract.getCurrentAmount() == null) return;
        java.util.List<BigDecimal> bidPrices = jdbc.queryForList("""
                SELECT final_bid_price FROM bid_cost
                WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, BigDecimal.class, project.getSourceBidCostId(), project.getTenantId());
        if (bidPrices.size() == 1 && bidPrices.getFirst() != null
                && bidPrices.getFirst().compareTo(contract.getCurrentAmount()) != 0) {
            log.warn("中标价与签约价存在差异，仅预警不阻断 projectId={}, bidPrice={}, contractAmount={}",
                    project.getId(), bidPrices.getFirst(), contract.getCurrentAmount());
        }
    }

    private static OwnerContractFact fact(CtContract contract) {
        return new OwnerContractFact(contract.getId(), contract.getProjectId(), contract.getTenantId(),
                contract.getContractCode(), contract.getCurrentAmount());
    }

    public record OwnerContractFact(Long contractId, Long projectId, Long tenantId,
                                    String contractCode, BigDecimal currentAmount) {
    }
}
