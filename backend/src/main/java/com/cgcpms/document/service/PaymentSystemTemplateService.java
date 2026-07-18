package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentDefaultBinding;
import com.cgcpms.document.entity.DocumentTemplate;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.mapper.DocumentDefaultBindingMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.document.mapper.DocumentTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentSystemTemplateService {
    public static final String TEMPLATE_CODE = "SYSTEM_PAYMENT_APPLICATION_V1";

    private static final String TEMPLATE = """
            <html><head><style>
            @page { size: A4; margin: 14mm 12mm; @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; font-family: 'CGC PMS Document Font', sans-serif; } }
            body { color: #17202a; font-size: 10pt; line-height: 1.45; }
            h1 { text-align: center; font-size: 20pt; margin: 0 0 12mm; }
            h2 { font-size: 12pt; margin: 6mm 0 2mm; border-left: 3px solid #1f5a94; padding-left: 2mm; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 4mm; }
            th, td { border: 0.3mm solid #9aa4ae; padding: 1.7mm; vertical-align: top; }
            th { background: #eef3f8; text-align: left; }
            .label { width: 18%; color: #52606d; }
            .amount { text-align: right; white-space: nowrap; }
            .footer { margin-top: 8mm; color: #52606d; font-size: 9pt; }
            </style></head><body>
            <h1>付款申请单</h1>
            <table>
              <tr><th class="label">申请编号</th><td>{{payment.applyCode}}</td><th class="label">审批状态</th><td>{{payment.approvalStatus}}</td></tr>
              <tr><th>项目</th><td>{{project.name}}</td><th>项目编号</th><td>{{project.code}}</td></tr>
              <tr><th>合同</th><td>{{contract.name}}</td><th>合同编号</th><td>{{contract.code}}</td></tr>
              <tr><th>申请金额</th><td class="amount">{{payment.applyAmount}}</td><th>批准金额</th><td class="amount">{{payment.approvedAmount}}</td></tr>
              <tr><th>付款类型</th><td>{{payment.payType}}</td><th>申请时间</th><td>{{payment.createdAt}}</td></tr>
              <tr><th>申请事由</th><td colspan="3">{{payment.applyReason}}</td></tr>
            </table>
            <h2>收款信息</h2>
            <table>
              <tr><th>收款单位</th><td>{{payee.name}}</td><th>开户行</th><td>{{payee.bankName}}</td></tr>
              <tr><th>银行账号</th><td>{{payee.bankAccount}}</td><th>联系电话</th><td>{{payee.contactPhone}}</td></tr>
            </table>
            <h2>付款来源</h2>
            <table><tr><th>类型</th><th>来源ID</th><th>来源金额</th><th>已付金额</th></tr>
            {{#each sources}}<tr><td>{{type}}</td><td>{{referenceId}}</td><td class="amount">{{amount}}</td><td class="amount">{{paidAmount}}</td></tr>{{/each}}
            </table>
            <h2>付款依据</h2>
            <table><tr><th>类型</th><th>依据ID</th><th>金额</th></tr>
            {{#each basis}}<tr><td>{{type}}</td><td>{{referenceId}}</td><td class="amount">{{amount}}</td></tr>{{/each}}
            </table>
            <h2>发票</h2>
            <table><tr><th>发票号</th><th>类型</th><th>日期</th><th>金额</th><th>验真状态</th></tr>
            {{#each invoices}}<tr><td>{{number}}</td><td>{{type}}</td><td>{{date}}</td><td class="amount">{{amount}}</td><td>{{verifyStatus}}</td></tr>{{/each}}
            </table>
            <h2>附件清单</h2>
            <table><tr><th>文件名</th><th>类型</th><th>字节数</th></tr>
            {{#each attachments}}<tr><td>{{name}}</td><td>{{type}}</td><td class="amount">{{size}}</td></tr>{{/each}}
            </table>
            <h2>审批轨迹</h2>
            <table><tr><th>节点</th><th>动作</th><th>操作人</th><th>时间</th><th>意见</th></tr>
            {{#each approvalRecords}}<tr><td>{{node}}</td><td>{{action}}</td><td>{{operator}}</td><td>{{time}}</td><td>{{comment}}</td></tr>{{/each}}
            </table>
            <div class="footer">本文件由 CGC-PMS 依据已审批业务数据生成；金额以系统权威字段为准。</div>
            </body></html>
            """;

    private static final String MANIFEST = """
            ["payment.applyCode","payment.approvalStatus","payment.applyAmount","payment.approvedAmount",
             "payment.payType","payment.createdAt","payment.applyReason","project.name","project.code",
             "contract.name","contract.code","payee.name","payee.bankName","payee.bankAccount","payee.contactPhone",
             "sources.type","sources.referenceId","sources.amount","sources.paidAmount",
             "basis.type","basis.referenceId","basis.amount",
             "invoices.number","invoices.type","invoices.date","invoices.amount","invoices.verifyStatus",
             "attachments.name","attachments.type","attachments.size",
             "approvalRecords.node","approvalRecords.action","approvalRecords.operator","approvalRecords.time","approvalRecords.comment"]
            """;

    private final DocumentTemplateService templateService;
    private final DocumentTemplateMapper templateMapper;
    private final DocumentTemplateVersionMapper versionMapper;
    private final DocumentDefaultBindingMapper bindingMapper;

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion ensureCurrentTenantTemplate() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        DocumentTemplate existing = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, tenantId)
                .eq(DocumentTemplate::getTemplateCode, TEMPLATE_CODE));
        if (existing == null) {
            DocumentTemplateVersion draft = templateService.create(TEMPLATE_CODE, "系统付款申请单", "PAYMENT",
                    new DocumentTemplateService.DraftCommand("payment.v1", TEMPLATE, MANIFEST,
                            "第48条主线M2受控系统模板"));
            DocumentTemplateVersion published = templateService.publish(draft.getId());
            templateService.bindDefault(published.getId(), 0);
            return published;
        }

        DocumentTemplateVersion published = versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, tenantId)
                .eq(DocumentTemplateVersion::getTemplateId, existing.getId())
                .eq(DocumentTemplateVersion::getStatus, "PUBLISHED")
                .orderByDesc(DocumentTemplateVersion::getVersionNo)
                .last("LIMIT 1")); // SQL-SAFETY: fixed-sql-fragment — fixed row limit, no user input
        if (published == null) {
            throw new BusinessException("DOCUMENT_SYSTEM_TEMPLATE_STATE_INVALID", "系统付款模板存在但没有已发布版本");
        }
        if (!TEMPLATE.equals(published.getTemplateContent())) {
            DocumentTemplateVersion draft = templateService.createNextDraft(existing.getId(),
                    new DocumentTemplateService.DraftCommand("payment.v1", TEMPLATE, MANIFEST,
                            "第48条主线M2受控系统模板：页码字体修复"));
            published = templateService.publish(draft.getId());
        }

        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, "PAYMENT"));
        if (binding == null) {
            templateService.bindDefault(published.getId(), 0);
        } else if (Objects.equals(binding.getTemplateId(), existing.getId())
                && !Objects.equals(binding.getTemplateVersionId(), published.getId())) {
            templateService.bindDefault(published.getId(), binding.getLockVersion());
        }
        return published;
    }
}
