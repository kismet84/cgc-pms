package com.cgcpms.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import com.cgcpms.materialreturn.mapper.MaterialReturnItemMapper;
import com.cgcpms.materialreturn.mapper.MaterialReturnMapper;
import com.cgcpms.procurement.vo.ProcurementTraceVO;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProcurementTraceService {

    private final MatStockTxnMapper stockTxnMapper;
    private final MaterialReturnMapper materialReturnMapper;
    private final MaterialReturnItemMapper materialReturnItemMapper;
    private final CostItemMapper costItemMapper;
    private final MatReceiptMapper receiptMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final MatPurchaseOrderMapper orderMapper;
    private final MatPurchaseOrderItemMapper orderItemMapper;
    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final WfInstanceMapper workflowInstanceMapper;
    private final WfRecordMapper workflowRecordMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public ProcurementTraceVO byStockTransaction(Long transactionId) {
        MatStockTxn txn = stockTxnMapper.selectById(transactionId);
        requireTenant(txn == null ? null : txn.getTenantId(), "STOCK_TXN_NOT_FOUND", "库存流水不存在");
        ProcurementTraceVO trace;
        if ("MAT_RECEIPT".equals(txn.getSourceType())) {
            trace = byReceipt(txn.getSourceId());
        } else if ("MAT_REQUISITION".equals(txn.getSourceType())) {
            trace = byRequisition(txn.getSourceId());
        } else if ("MATERIAL_RETURN".equals(txn.getSourceType())) {
            trace = byMaterialReturn(txn.getSourceId());
        } else {
            throw new BusinessException("PROCUREMENT_TRACE_UNSUPPORTED", "库存流水缺少受支持的采购业务来源");
        }
        trace.setStockTransactions(List.of(txn));
        return trace;
    }

    public ProcurementTraceVO byCost(Long costId) {
        CostItem cost = costItemMapper.selectById(costId);
        requireTenant(cost == null ? null : cost.getTenantId(), "COST_NOT_FOUND", "成本记录不存在");
        ProcurementTraceVO trace;
        if ("MAT_RECEIPT".equals(cost.getSourceType())) {
            trace = byReceipt(cost.getSourceId());
        } else if ("MAT_REQUISITION".equals(cost.getSourceType())) {
            trace = byRequisition(cost.getSourceId());
        } else if ("MATERIAL_RETURN".equals(cost.getSourceType())) {
            trace = byMaterialReturn(cost.getSourceId());
        } else {
            throw new BusinessException("PROCUREMENT_TRACE_UNSUPPORTED", "成本记录不属于采购材料闭环");
        }
        return trace;
    }

    public ProcurementTraceVO byReceipt(Long receiptId) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatReceipt receipt = receiptMapper.selectById(receiptId);
        requireTenant(receipt == null ? null : receipt.getTenantId(), "RECEIPT_NOT_FOUND", "验收单不存在");
        projectAccessChecker.checkAccess(receipt.getProjectId(), "查看采购验收入库全链路");

        ProcurementTraceVO trace = baseTrace(receipt.getProjectId(), receipt.getContractId());
        trace.setReceipt(receipt);
        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getTenantId, tenantId)
                        .eq(MatReceiptItem::getReceiptId, receiptId));
        trace.setReceiptItems(receiptItems);

        MatPurchaseOrder order = receipt.getOrderId() == null ? null : orderMapper.selectById(receipt.getOrderId());
        populateOrderAndRequest(trace, order);
        trace.setStockTransactions(stockTxnMapper.selectList(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getSourceId, receiptId)
                .orderByAsc(MatStockTxn::getCreatedTime)));
        trace.setCosts(loadCosts("MAT_RECEIPT", receiptId));
        populateWorkflow(trace, List.of(
                new BusinessRef("PURCHASE_ORDER", order == null ? null : order.getId()),
                new BusinessRef("MATERIAL_RECEIPT", receiptId)));
        return trace;
    }

    public ProcurementTraceVO byRequisition(Long requisitionId) {
        Long tenantId = UserContext.getCurrentTenantId();
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        requireTenant(requisition == null ? null : requisition.getTenantId(),
                "REQUISITION_NOT_FOUND", "领料申请不存在");
        projectAccessChecker.checkAccess(requisition.getProjectId(), "查看领料出库全链路");

        ProcurementTraceVO trace = baseTrace(requisition.getProjectId(), requisition.getContractId());
        trace.setRequisition(requisition);
        trace.setRequisitionItems(requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getTenantId, tenantId)
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId)));
        trace.setStockTransactions(stockTxnMapper.selectList(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .eq(MatStockTxn::getSourceType, "MAT_REQUISITION")
                .eq(MatStockTxn::getSourceId, requisitionId)
                .orderByAsc(MatStockTxn::getCreatedTime)));
        // 仅兼容展示历史版本曾生成的领料成本；新流程不会再创建此类成本。
        trace.setCosts(loadCosts("MAT_REQUISITION", requisitionId));
        populateWorkflow(trace, List.of(new BusinessRef("MATERIAL_REQUISITION", requisitionId)));
        return trace;
    }

    public ProcurementTraceVO byMaterialReturn(Long returnId) {
        Long tenantId = UserContext.getCurrentTenantId();
        MaterialReturn materialReturn = materialReturnMapper.selectById(returnId);
        requireTenant(materialReturn == null ? null : materialReturn.getTenantId(),
                "MATERIAL_RETURN_NOT_FOUND", "退料单不存在");
        ProcurementTraceVO trace = byRequisition(materialReturn.getRequisitionId());
        trace.setMaterialReturn(materialReturn);
        List<MaterialReturnItem> items = materialReturnItemMapper.selectList(
                new LambdaQueryWrapper<MaterialReturnItem>()
                        .eq(MaterialReturnItem::getTenantId, tenantId)
                        .eq(MaterialReturnItem::getReturnId, returnId));
        trace.setMaterialReturnItems(items);
        trace.setStockTransactions(stockTxnMapper.selectList(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getTenantId, tenantId)
                .eq(MatStockTxn::getSourceType, "MATERIAL_RETURN")
                .eq(MatStockTxn::getSourceId, returnId)));
        List<CostItem> costs = new ArrayList<>(loadCosts("MATERIAL_RETURN", returnId));
        List<Long> originalCostIds = items.stream().map(MaterialReturnItem::getOriginalCostItemId)
                .filter(Objects::nonNull).distinct().toList();
        if (!originalCostIds.isEmpty()) costs.addAll(costItemMapper.selectByIds(originalCostIds));
        trace.setCosts(costs);
        return trace;
    }

    private ProcurementTraceVO baseTrace(Long projectId, Long contractId) {
        ProcurementTraceVO trace = new ProcurementTraceVO();
        trace.setProject(projectId == null ? null : projectMapper.selectById(projectId));
        trace.setContract(contractId == null ? null : contractMapper.selectById(contractId));
        return trace;
    }

    private void populateOrderAndRequest(ProcurementTraceVO trace, MatPurchaseOrder order) {
        if (order == null) return;
        Long tenantId = UserContext.getCurrentTenantId();
        requireTenant(order.getTenantId(), "ORDER_NOT_FOUND", "采购订单不存在");
        trace.setPurchaseOrder(order);
        List<MatPurchaseOrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getTenantId, tenantId)
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId()));
        trace.setPurchaseOrderItems(orderItems);
        if (order.getRequestId() == null) return;
        trace.setPurchaseRequest(requestMapper.selectById(order.getRequestId()));
        List<Long> requestItemIds = orderItems.stream().map(MatPurchaseOrderItem::getRequestItemId)
                .filter(Objects::nonNull).distinct().toList();
        List<MatPurchaseRequestItem> requestItems = requestItemIds.isEmpty()
                ? requestItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                        .eq(MatPurchaseRequestItem::getRequestId, order.getRequestId()))
                : requestItemMapper.selectByIds(requestItemIds);
        trace.setPurchaseRequestItems(requestItems);
    }

    private List<CostItem> loadCosts(String sourceType, Long sourceId) {
        return costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, UserContext.getCurrentTenantId())
                .eq(CostItem::getSourceType, sourceType)
                .eq(CostItem::getSourceId, sourceId));
    }

    private void populateWorkflow(ProcurementTraceVO trace, List<BusinessRef> refs) {
        Long tenantId = UserContext.getCurrentTenantId();
        List<WfInstance> instances = new ArrayList<>();
        for (BusinessRef ref : refs) {
            if (ref.id() == null) continue;
            instances.addAll(workflowInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getTenantId, tenantId)
                    .eq(WfInstance::getBusinessType, ref.type())
                    .eq(WfInstance::getBusinessId, ref.id())
                    .orderByAsc(WfInstance::getCreatedAt)));
        }
        trace.setApprovalInstances(instances);
        List<Long> instanceIds = instances.stream().map(WfInstance::getId).toList();
        trace.setApprovalRecords(instanceIds.isEmpty() ? List.of()
                : workflowRecordMapper.selectList(new LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getTenantId, tenantId)
                        .in(WfRecord::getInstanceId, instanceIds)
                        .orderByAsc(WfRecord::getCreatedAt)));
    }

    private void requireTenant(Long actualTenantId, String code, String message) {
        if (!Objects.equals(actualTenantId, UserContext.getCurrentTenantId())) {
            throw new BusinessException(code, message);
        }
    }

    private record BusinessRef(String type, Long id) {}
}
