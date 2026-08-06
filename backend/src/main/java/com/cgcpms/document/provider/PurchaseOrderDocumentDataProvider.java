package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class PurchaseOrderDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "purchase-order.v2";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PURCHASE_ORDER", SCHEMA,
            field("purchaseOrder.orderCode", "采购订单号", "TEXT", false),
            field("purchaseOrder.orderType", "订单类型", "ENUM", true),
            field("purchaseOrder.orderDate", "订单日期", "DATE", true),
            field("purchaseOrder.deliveryDate", "交付日期", "DATE", true),
            field("purchaseOrder.deliveryTerms", "交付条款", "TEXT", true),
            field("purchaseOrder.exceptionPurchase", "是否例外采购", "BOOLEAN", true),
            field("purchaseOrder.exceptionReason", "例外采购原因", "TEXT", true),
            field("purchaseOrder.totalAmount", "订单总金额", "MONEY", false),
            field("purchaseOrder.approvalStatus", "审批状态", "ENUM", false),
            field("purchaseOrder.orderStatus", "履约状态", "ENUM", false),
            field("purchaseOrder.createdAt", "创建时间", "DATETIME", true),
            field("purchaseOrder.updatedAt", "更新时间", "DATETIME", true),
            field("purchaseOrder.remark", "备注", "TEXT", true),
            field("project.name", "项目名称", "TEXT", true),
            field("request.code", "采购申请编号", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true),
            field("supplier.name", "供应商名称", "TEXT", true),
            item("items.materialName", "材料名称", "TEXT", "items"),
            item("items.specification", "规格型号", "TEXT", "items"),
            item("items.unit", "单位", "TEXT", "items"),
            item("items.quantity", "数量", "NUMBER", "items"),
            item("items.unitPrice", "单价", "MONEY", "items"),
            item("items.taxRate", "税率", "NUMBER", "items"),
            item("items.amount", "含税金额", "MONEY", "items"),
            item("items.taxAmount", "税额", "MONEY", "items"),
            item("items.amountWithoutTax", "不含税金额", "MONEY", "items"),
            item("items.receivedQuantity", "已收数量", "NUMBER", "items"),
            item("items.priceSource", "价格来源", "TEXT", "items"),
            item("items.quantityAdjustReason", "数量调整原因", "TEXT", "items"),
            item("items.remark", "明细备注", "TEXT", "items"));

    private final MatPurchaseOrderService service;

    public String businessType() { return "PURCHASE_ORDER"; }
    public String displayName() { return "采购订单"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "purchase:order:query"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentDataSnapshot load(Long businessId) { return createSnapshot(businessId, true); }
    public DocumentDataSnapshot loadPreview(Long businessId) { return createSnapshot(businessId, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        MatPurchaseOrderVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PURCHASE_ORDER_STATE_INVALID", "采购订单文档");
        return snapshot(SCHEMA,
                "purchaseOrder", map("orderCode", text(value.getOrderCode()), "orderType", text(value.getOrderType()),
                        "orderDate", text(value.getOrderDate()), "deliveryDate", text(value.getDeliveryDate()),
                        "deliveryTerms", text(value.getDeliveryTerms()),
                        "exceptionPurchase", Integer.valueOf(1).equals(value.getExceptionPurchaseFlag()),
                        "exceptionReason", text(value.getExceptionReason()), "totalAmount", money(value.getTotalAmount()),
                        "approvalStatus", text(value.getApprovalStatus()), "orderStatus", text(value.getOrderStatus()),
                        "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()),
                        "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())),
                "request", map("code", text(value.getRequestCode())),
                "contract", map("name", text(value.getContractName())),
                "supplier", map("name", text(value.getPartnerName())),
                "items", rows(value.getItems(), this::itemRow));
    }

    private Map<String, Object> itemRow(MatPurchaseOrderItemVO value) {
        return map("materialName", text(value.getMaterialName()), "specification", text(value.getSpecification()),
                "unit", text(value.getUnit()), "quantity", number(value.getQuantity()),
                "unitPrice", money(value.getUnitPrice()), "taxRate", number(value.getTaxRate()),
                "amount", money(value.getAmount()), "taxAmount", money(value.getTaxAmount()),
                "amountWithoutTax", money(value.getAmountWithoutTax()),
                "receivedQuantity", number(value.getReceivedQuantity()), "priceSource", text(value.getPriceSource()),
                "quantityAdjustReason", text(value.getQuantityAdjustReason()), "remark", text(value.getRemark()));
    }
}
