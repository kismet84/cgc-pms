package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.bid.service.BidAwardProjectCreator;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidAwardProjectService implements BidAwardProjectCreator {
    private static final int MAX_CODE_RETRIES = 3;

    private final PmProjectMapper projectMapper;

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

        String prefix = "XM-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            PmProject project = new PmProject();
            project.setTenantId(command.tenantId());
            project.setProjectCode(nextProjectCode(command.tenantId(), prefix, attempt));
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

    private String nextProjectCode(Long tenantId, String prefix, int offset) {
        Page<PmProject> page = projectMapper.selectPage(new Page<>(1, 1),
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .likeRight(PmProject::getProjectCode, prefix)
                        .orderByDesc(PmProject::getProjectCode));
        List<PmProject> projects = page.getRecords();
        int sequence = 1 + offset;
        if (!projects.isEmpty()) {
            String code = projects.getFirst().getProjectCode();
            if (code != null && code.length() == prefix.length() + 3) {
                try {
                    sequence = Integer.parseInt(code.substring(prefix.length())) + 1 + offset;
                } catch (NumberFormatException ignored) {
                    // Unique project-code constraint remains the authoritative concurrency guard.
                }
            }
        }
        return prefix + String.format("%03d", sequence);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
