package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.bid.service.BidAwardProjectCreator;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BidAwardProjectService implements BidAwardProjectCreator {
    private static final int MAX_CODE_RETRIES = 3;

    private final PmProjectMapper projectMapper;
    private final CodeGenerationService codeGenerationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrGet(BidAwardProjectCommand command) {
        if (command == null || command.tenantId() == null || command.bidCostId() == null
                || command.projectName() == null || command.projectName().isBlank()
                || command.ownerUnit() == null || command.ownerUnit().isBlank()
                || command.projectAddress() == null || command.projectAddress().isBlank()
                || command.contractAmount() == null || command.contractAmount().signum() <= 0
                || command.plannedStartDate() == null || command.plannedEndDate() == null
                || command.plannedStartDate().isAfter(command.plannedEndDate())) {
            throw new BusinessException("BID_AWARD_PROJECT_INVALID", "中标项目资料不完整");
        }

        PmProject existing = findByBid(command.tenantId(), command.bidCostId());
        if (existing != null) return existing.getId();

        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            PmProject project = new PmProject();
            project.setTenantId(command.tenantId());
            project.setProjectCode(codeGenerationService.nextCode(
                    projectMapper, PmProject::getProjectCode,
                    "XM-", command.tenantId(), true, attempt));
            project.setProjectName(command.projectName().trim());
            project.setOwnerUnit(blankToNull(command.ownerUnit()));
            project.setProjectAddress(blankToNull(command.projectAddress()));
            project.setContractAmount(BigDecimal.ZERO);
            project.setTargetCost(BigDecimal.ZERO);
            project.setPlannedStartDate(command.plannedStartDate());
            project.setPlannedEndDate(command.plannedEndDate());
            project.setSourceBidCostId(command.bidCostId());
            project.setInitiationBasis("BID_AWARD");
            project.setApprovalStatus("APPROVED");
            project.setStatus(ProjectStatusConstants.PREPARING);
            try {
                projectMapper.insert(project);
                return project.getId();
            } catch (DuplicateKeyException conflict) {
                existing = findByBid(command.tenantId(), command.bidCostId());
                if (existing != null) return existing.getId();
            }
        }
        throw new BusinessException("PROJECT_CODE_CONFLICT", "项目编号生成冲突，请重试");
    }

    private PmProject findByBid(Long tenantId, Long bidCostId) {
        return projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getSourceBidCostId, bidCostId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
