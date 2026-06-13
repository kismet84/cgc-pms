package com.cgcpms.invoice.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class InvoiceRecognizeResultVO implements Serializable {

    private String invoiceNo;
    private String invoiceType;
    private String invoiceAmount;
    private String taxRate;
    private String taxAmount;
    private String invoiceDate;
    private String sellerName;
    private String buyerName;
    private String buyerTaxNo;
    private String remark;
}
