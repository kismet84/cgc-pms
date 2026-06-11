package com.cgcpms.cost.strategy;

/**
 * Strategy interface for generating cost records from different source types.
 * Each implementation handles a specific source_type (CT_CONTRACT, MAT_RECEIPT, etc.)
 * and knows how to query source items and create corresponding cost_item records.
 */
public interface CostGenerationStrategy {

    /**
     * Returns the source type this strategy supports.
     * @return source type identifier (e.g., "CT_CONTRACT", "MAT_RECEIPT")
     */
    String supportSourceType();

    /**
     * Generate cost records for the given source entity.
     * Queries source items, maps to CostItem entities, and inserts with idempotency.
     * 
     * @param sourceId the source entity ID (contract ID, receipt ID, etc.)
     */
    void generateCost(Long sourceId);
}
