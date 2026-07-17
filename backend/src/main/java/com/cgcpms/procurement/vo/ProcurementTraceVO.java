package com.cgcpms.procurement.vo;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.supplierreturn.entity.SupplierReturn;
import com.cgcpms.supplierreturn.entity.SupplierReturnItem;
import lombok.Data;

import java.util.List;

@Data
public class ProcurementTraceVO {
    private PmProject project;
    private CtContract contract;
    private MatPurchaseRequest purchaseRequest;
    private List<MatPurchaseRequestItem> purchaseRequestItems = List.of();
    private MatPurchaseOrder purchaseOrder;
    private List<MatPurchaseOrderItem> purchaseOrderItems = List.of();
    private MatReceipt receipt;
    private List<MatReceiptItem> receiptItems = List.of();
    private MatRequisition requisition;
    private List<MatRequisitionItem> requisitionItems = List.of();
    private List<MatStockTxn> stockTransactions = List.of();
    private List<CostItem> costs = List.of();
    private MaterialReturn materialReturn;
    private List<MaterialReturnItem> materialReturnItems = List.of();
    private SupplierReturn supplierReturn;
    private List<SupplierReturnItem> supplierReturnItems = List.of();
    private List<WfInstance> approvalInstances = List.of();
    private List<WfRecord> approvalRecords = List.of();
}
