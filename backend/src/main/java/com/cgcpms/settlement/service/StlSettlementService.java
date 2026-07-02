package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementSourcesVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.vo.VarOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 结算服务门面 — 委托给 QueryService / WriteService / Assembler。
 *
 * @deprecated 新代码请直接注入 StlSettlementQueryService 和 StlSettlementWriteService。
 *             此门面仅保留用于向后兼容现有测试代码。
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class StlSettlementService {

    private final StlSettlementQueryService queryService;
    private final StlSettlementWriteService writeService;

    // ---- Query delegation ----

    public IPage<StlSettlementVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                          Long partnerId, String settlementCode, String settlementType,
                                          String keyword) {
        return queryService.getPage(pageNo, pageSize, projectId, contractId, partnerId,
                settlementCode, settlementType, keyword);
    }

    public Map<String, Object> getKpi(Long projectId, Long contractId, Long partnerId,
                                      String settlementCode, String settlementType) {
        return queryService.getKpi(projectId, contractId, partnerId, settlementCode, settlementType);
    }

    public StlSettlementVO getById(Long id) {
        return queryService.getById(id);
    }

    public StlSettlementVO computeSettlementAmount(Long contractId) {
        return queryService.computeSettlementAmount(contractId);
    }

    public SettlementSourcesVO getSources(Long settlementId) {
        return queryService.getSources(settlementId);
    }

    public List<VarOrderVO> getVariations(Long settlementId) {
        return queryService.getVariations(settlementId);
    }

    public List<PayRecord> getPayments(Long settlementId) {
        return queryService.getPayments(settlementId);
    }

    public List<SettlementCostItemVO> getCosts(Long settlementId) {
        return queryService.getCosts(settlementId);
    }

    public List<SettlementAttachmentVO> getAttachments(Long settlementId) {
        return queryService.getAttachments(settlementId);
    }

    public List<SettlementApprovalRecordVO> getApprovalRecords(Long settlementId) {
        return queryService.getApprovalRecords(settlementId);
    }

    // ---- Write delegation ----

    public Long create(StlSettlement settlement) {
        return writeService.create(settlement);
    }

    public void update(StlSettlement settlement) {
        writeService.update(settlement);
    }

    public void delete(Long id) {
        writeService.delete(id);
    }

    public void saveItems(Long settlementId, List<StlSettlementItem> items) {
        writeService.saveItems(settlementId, items);
    }

    public void submitForApproval(Long settlementId) {
        writeService.submitForApproval(settlementId);
    }
}
