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
public class SettlementSystemTemplateService {
    public static final String TEMPLATE_CODE = "SYSTEM_SETTLEMENT_V1";

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
            <h1>结算单</h1>
            <table>
              <tr><th class="label">结算编号</th><td>{{settlement.code}}</td><th class="label">结算类型</th><td>{{settlement.type}}</td></tr>
              <tr><th>审批状态</th><td>{{settlement.approvalStatus}}</td><th>定案状态</th><td>{{settlement.finalStatus}}</td></tr>
              <tr><th>项目</th><td>{{project.name}}</td><th>合同</th><td>{{contract.name}}</td></tr>
              <tr><th>结算对象</th><td>{{partner.name}}</td><th>定案时间</th><td>{{settlement.finalizedAt}}</td></tr>
            </table>
            <h2>金额基线</h2>
            <table>
              <tr><th>合同金额</th><th>变更金额</th><th>累计计量</th><th>扣款金额</th></tr>
              <tr><td class="amount">{{settlement.contractAmount}}</td><td class="amount">{{settlement.changeAmount}}</td><td class="amount">{{settlement.measuredAmount}}</td><td class="amount">{{settlement.deductionAmount}}</td></tr>
              <tr><th>已付金额</th><th>定案金额</th><th>未付金额</th><th>质保金额</th></tr>
              <tr><td class="amount">{{settlement.paidAmount}}</td><td class="amount">{{settlement.finalAmount}}</td><td class="amount">{{settlement.unpaidAmount}}</td><td class="amount">{{settlement.warrantyAmount}}</td></tr>
            </table>
            <h2>结算明细</h2>
            <table><tr><th>名称</th><th>单位</th><th>数量</th><th>单价</th><th>金额</th><th>来源</th><th>备注</th></tr>
            {{#each items}}<tr><td>{{name}}</td><td>{{unit}}</td><td class="amount">{{quantity}}</td><td class="amount">{{unitPrice}}</td><td class="amount">{{amount}}</td><td>{{sourceType}}</td><td>{{remark}}</td></tr>{{/each}}
            </table>
            <h2>变更签证</h2>
            <table><tr><th>编号</th><th>名称</th><th>类型</th><th>方向</th><th>确认金额</th><th>状态</th></tr>
            {{#each variations}}<tr><td>{{code}}</td><td>{{name}}</td><td>{{type}}</td><td>{{direction}}</td><td class="amount">{{confirmedAmount}}</td><td>{{status}}</td></tr>{{/each}}
            </table>
            <h2>付款关联</h2>
            <table><tr><th>申请编号</th><th>类型</th><th>申请金额</th><th>批准金额</th><th>实付金额</th><th>状态</th><th>日期/凭证</th></tr>
            {{#each payments}}<tr><td>{{applyCode}}</td><td>{{payType}}</td><td class="amount">{{applyAmount}}</td><td class="amount">{{approvedAmount}}</td><td class="amount">{{actualPayAmount}}</td><td>{{status}}</td><td>{{payDate}}/{{voucherNo}}</td></tr>{{/each}}
            </table>
            <h2>成本明细</h2>
            <table><tr><th>成本科目</th><th>类型</th><th>来源</th><th>含税金额</th><th>税额</th><th>不含税金额</th><th>日期</th><th>状态</th></tr>
            {{#each costs}}<tr><td>{{subjectName}}</td><td>{{type}}</td><td>{{sourceType}}</td><td class="amount">{{amount}}</td><td class="amount">{{taxAmount}}</td><td class="amount">{{amountWithoutTax}}</td><td>{{date}}</td><td>{{status}}</td></tr>{{/each}}
            </table>
            <div class="footer">本文件由 CGC-PMS 依据已审批且已定案结算数据生成；金额以结算服务权威字段为准。</div>
            </body></html>
            """;

    private static final String MANIFEST = """
            ["settlement.code","settlement.type","settlement.approvalStatus","settlement.finalStatus",
             "settlement.contractAmount","settlement.changeAmount","settlement.measuredAmount",
             "settlement.deductionAmount","settlement.paidAmount","settlement.finalAmount",
             "settlement.unpaidAmount","settlement.warrantyAmount","settlement.finalizedAt",
             "project.name","contract.name","partner.name",
             "items.name","items.unit","items.quantity","items.unitPrice","items.amount","items.sourceType","items.remark",
             "variations.code","variations.name","variations.type","variations.direction","variations.confirmedAmount","variations.status",
             "payments.applyCode","payments.payType","payments.applyAmount","payments.approvedAmount",
             "payments.actualPayAmount","payments.status","payments.payDate","payments.voucherNo",
             "costs.subjectName","costs.type","costs.sourceType","costs.amount","costs.taxAmount",
             "costs.amountWithoutTax","costs.date","costs.status"]
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
            DocumentTemplateVersion draft = templateService.create(TEMPLATE_CODE, "系统结算单", "SETTLEMENT",
                    new DocumentTemplateService.DraftCommand("settlement.v2", TEMPLATE, MANIFEST,
                            "第78条主线安全业务详情模板"));
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
            throw new BusinessException("DOCUMENT_SYSTEM_TEMPLATE_STATE_INVALID", "系统结算模板存在但没有已发布版本");
        }
        if (!"settlement.v2".equals(published.getSchemaVersion()) || !TEMPLATE.equals(published.getTemplateContent())) {
            DocumentTemplateVersion draft = templateService.createNextDraft(existing.getId(),
                    new DocumentTemplateService.DraftCommand("settlement.v2", TEMPLATE, MANIFEST,
                            "第78条主线安全业务详情模板升级"));
            published = templateService.publish(draft.getId());
        }

        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, "SETTLEMENT"));
        if (binding == null) {
            templateService.bindDefault(published.getId(), 0);
        } else if (Objects.equals(binding.getTemplateId(), existing.getId())
                && !Objects.equals(binding.getTemplateVersionId(), published.getId())) {
            templateService.bindDefault(published.getId(), binding.getLockVersion());
        }
        return published;
    }
}
