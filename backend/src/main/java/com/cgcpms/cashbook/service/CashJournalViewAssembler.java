package com.cgcpms.cashbook.service;

import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.vo.SysFileVO;

import java.math.BigDecimal;
import java.util.Objects;

import static com.cgcpms.cashbook.service.CashJournalService.tenantId;

final class CashJournalViewAssembler {

    private final CostSubjectMapper costSubjectMapper;

    CashJournalViewAssembler(CostSubjectMapper costSubjectMapper) {
        this.costSubjectMapper = costSubjectMapper;
    }

    CashJournalEntryVO toVO(CashJournalEntry entry) {
        CashJournalEntryVO vo = new CashJournalEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setEntryNo(entry.getEntryNo());
        vo.setAccountId(entry.getAccountId() == null ? null : String.valueOf(entry.getAccountId()));
        vo.setDirection(entry.getDirection());
        vo.setAmount(money(entry.getAmount()));
        vo.setBusinessDate(entry.getBusinessDate());
        vo.setCounterpartyName(entry.getCounterpartyName());
        vo.setSummary(entry.getSummary());
        vo.setProjectId(entry.getProjectId() == null ? null : String.valueOf(entry.getProjectId()));
        vo.setContractId(entry.getContractId() == null ? null : String.valueOf(entry.getContractId()));
        vo.setBidCostId(entry.getBidCostId() == null ? null : String.valueOf(entry.getBidCostId()));
        vo.setCostSubjectId(entry.getCostSubjectId() == null ? null : String.valueOf(entry.getCostSubjectId()));
        vo.setBidDepositId(entry.getBidDepositId() == null ? null : String.valueOf(entry.getBidDepositId()));
        vo.setCostSubjectCode(entry.getCostSubjectCodeSnapshot());
        vo.setCostSubjectName(entry.getCostSubjectNameSnapshot());
        if (entry.getCostSubjectId() != null) {
            CostSubject subject = costSubjectMapper.selectById(entry.getCostSubjectId());
            if (subject != null && Objects.equals(subject.getTenantId(), tenantId())) {
                vo.setCostSubjectAccountCategory(subject.getAccountCategory());
            }
        }
        vo.setSourceType(entry.getSourceType());
        vo.setSourceId(entry.getSourceId() == null ? null : String.valueOf(entry.getSourceId()));
        vo.setStatus(entry.getStatus());
        vo.setClosureDueAt(entry.getClosureDueAt());
        vo.setArchivedBy(entry.getArchivedBy() == null ? null : String.valueOf(entry.getArchivedBy()));
        vo.setArchivedAt(entry.getArchivedAt());
        vo.setReverseOfEntryId(entry.getReverseOfEntryId() == null ? null : String.valueOf(entry.getReverseOfEntryId()));
        vo.setReversalEntryId(entry.getReversalEntryId() == null ? null : String.valueOf(entry.getReversalEntryId()));
        vo.setVersion(entry.getVersion());
        vo.setCreatedAt(entry.getCreatedAt());
        vo.setCreatedBy(entry.getCreatedBy() == null ? null : String.valueOf(entry.getCreatedBy()));
        return vo;
    }

    SysFileVO toFileVO(SysFile file) {
        SysFileVO vo = new SysFileVO();
        vo.setId(String.valueOf(file.getId()));
        vo.setBusinessType(file.getBusinessType());
        vo.setBusinessId(String.valueOf(file.getBusinessId()));
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setCreatedAt(file.getCreatedAt() == null ? null : file.getCreatedAt().toString());
        return vo;
    }

    static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2).toPlainString();
    }

}
