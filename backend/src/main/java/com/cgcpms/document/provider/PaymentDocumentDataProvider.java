package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentTraceService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.catalog;

/** 历史 PAYMENT 模板兼容入口；新审批模板必须使用 PAY_REQUEST。 */
@Component
@Profile("!document-test-provider")
public class PaymentDocumentDataProvider extends PayRequestDocumentDataProvider {
    private static final String SCHEMA = "payment.v2";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PAYMENT", SCHEMA, FIELDS);

    public PaymentDocumentDataProvider(PayApplicationService service, PaymentTraceService traceService) {
        super(service, traceService);
    }

    public String businessType() { return "PAYMENT"; }
    public String displayName() { return "付款申请（历史兼容）"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "payment:app:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
}
