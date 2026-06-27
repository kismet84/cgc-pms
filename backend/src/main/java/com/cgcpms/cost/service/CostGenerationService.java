package com.cgcpms.cost.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.strategy.CostGenerationStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cost generation service using strategy pattern.
 * Dispatches cost generation to appropriate strategy based on source_type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostGenerationService {

    /** Source type marking the cost as originating from a contract. */
    public static final String SOURCE_TYPE_CONTRACT = "CT_CONTRACT";

    /** Default cost type for contract-locked costs (no per-item classification yet). */
    public static final String DEFAULT_COST_TYPE = "CONTRACT_LOCKED";

    /** Cost status once locked from an approved contract. */
    public static final String COST_STATUS_CONFIRMED = "CONFIRMED";

    private final List<CostGenerationStrategy> strategies;
    private Map<String, CostGenerationStrategy> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        CostGenerationStrategy::supportSourceType,
                        Function.identity()
                ));
        log.info("成本生成策略完成初始化");
    }

    /**
     * Generate cost records for the given source.
     *
     * @param sourceType the source type (e.g., "CT_CONTRACT", "MAT_RECEIPT")
     * @param sourceId the source entity ID
     */
    public void generateCost(String sourceType, Long sourceId) {
        CostGenerationStrategy strategy = strategyMap.get(sourceType);
        if (strategy == null) {
            log.error("未找到成本生成策略: sourceType={}", sourceType);
            throw new BusinessException("COST_STRATEGY_NOT_FOUND", "未找到成本生成策略: " + sourceType);
        }
        strategy.generateCost(sourceId);
    }

    /**
     * Generate locked cost records for every line item of the given contract.
     * Convenience method for backward compatibility.
     * Idempotent — re-running on the same contract skips already-generated rows.
     *
     * @param contractId the approved contract id
     */
    public void generateLockedCost(Long contractId) {
        generateCost(SOURCE_TYPE_CONTRACT, contractId);
    }
}
