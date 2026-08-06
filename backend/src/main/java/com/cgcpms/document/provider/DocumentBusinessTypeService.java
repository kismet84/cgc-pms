package com.cgcpms.document.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentBusinessTypeService {
    private final WfTemplateMapper templateMapper;
    private final DocumentDataProviderRegistry providerRegistry;

    public List<BusinessTypeDefinition> listEnabled() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        List<WfTemplate> templates = templateMapper.selectList(new QueryWrapper<WfTemplate>()
                .select("business_type", "template_name")
                .eq("tenant_id", tenantId)
                .eq("enabled", 1)
                .orderByAsc("business_type", "id"));
        Map<String, String> names = new LinkedHashMap<>();
        templates.forEach(template -> names.putIfAbsent(template.getBusinessType(), template.getTemplateName()));
        return names.entrySet().stream().map(entry -> definition(entry.getKey(), entry.getValue())).toList();
    }

    private BusinessTypeDefinition definition(String businessType, String fallbackName) {
        if (!providerRegistry.has(businessType)) {
            return new BusinessTypeDefinition(businessType, fallbackName, null, false, 0);
        }
        DocumentDataProvider provider = providerRegistry.require(businessType);
        return new BusinessTypeDefinition(businessType, provider.displayName(), provider.schemaVersion(), true,
                provider.fieldCatalog().fields().size());
    }

    public record BusinessTypeDefinition(String businessType, String displayName, String schemaVersion,
                                         boolean providerReady, int fieldCount) {
    }
}
