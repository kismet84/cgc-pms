package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.BusinessMatterRegistry;
import com.cgcpms.contract.mapper.BusinessMatterRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BusinessMatterRegistryService {
    public static final String SOURCE_CONTRACT_CHANGE = "CT_CHANGE";
    public static final String SOURCE_VARIATION_ORDER = "VAR_ORDER";

    private final BusinessMatterRegistryMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public void register(String sourceType, Long sourceId, Long projectId, Long contractId, String matterKey) {
        String normalized = normalize(matterKey);
        if (normalized == null) return;
        Long tenantId = UserContext.getCurrentTenantId();
        BusinessMatterRegistry existing = mapper.selectActiveForUpdate(tenantId, projectId, normalized);
        if (existing != null) {
            if (Objects.equals(existing.getSourceType(), sourceType) && Objects.equals(existing.getSourceId(), sourceId)) {
                return;
            }
            throw new BusinessException("BUSINESS_MATTER_DUPLICATE",
                    "业务事项 " + normalized + " 已由 " + existing.getSourceType() + " 单据登记，禁止跨域重复录入");
        }
        BusinessMatterRegistry registry = new BusinessMatterRegistry();
        registry.setTenantId(tenantId);
        registry.setProjectId(projectId);
        registry.setContractId(contractId);
        registry.setMatterKey(normalized);
        registry.setSourceType(sourceType);
        registry.setSourceId(sourceId);
        registry.setStatus("ACTIVE");
        registry.setActiveToken(1);
        registry.setVersion(0);
        try {
            mapper.insert(registry);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BUSINESS_MATTER_DUPLICATE", "同一业务事项或来源已登记，禁止重复录入");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(String sourceType, Long sourceId, Long projectId, Long contractId,
                        String oldMatterKey, String newMatterKey) {
        String oldKey = normalize(oldMatterKey);
        String newKey = normalize(newMatterKey);
        if (Objects.equals(oldKey, newKey)) return;
        release(sourceType, sourceId, "草稿修改业务事项键");
        register(sourceType, sourceId, projectId, contractId, newKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(String sourceType, Long sourceId, String note) {
        mapper.update(null, new LambdaUpdateWrapper<BusinessMatterRegistry>()
                .eq(BusinessMatterRegistry::getTenantId, UserContext.getCurrentTenantId())
                .eq(BusinessMatterRegistry::getSourceType, sourceType)
                .eq(BusinessMatterRegistry::getSourceId, sourceId)
                .eq(BusinessMatterRegistry::getActiveToken, 1)
                .set(BusinessMatterRegistry::getStatus, "SUPERSEDED")
                .set(BusinessMatterRegistry::getActiveToken, null)
                .set(BusinessMatterRegistry::getResolvedAt, LocalDateTime.now())
                .set(BusinessMatterRegistry::getResolvedBy, UserContext.getCurrentUserId())
                .set(BusinessMatterRegistry::getResolutionNote, note));
    }

    public String normalize(String matterKey) {
        return StringUtils.hasText(matterKey) ? matterKey.trim().toUpperCase(Locale.ROOT) : null;
    }
}
