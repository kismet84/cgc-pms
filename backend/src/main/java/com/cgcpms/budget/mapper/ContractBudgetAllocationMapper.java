package com.cgcpms.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ContractBudgetAllocationMapper extends BaseMapper<ContractBudgetAllocation> {
    @Select("""
            SELECT id, tenant_id, project_id, contract_id, budget_line_id,
                   allocated_amount, reserved_amount, consumed_amount, version,
                   created_by, created_at, updated_by, updated_at, deleted_flag, remark
              FROM contract_budget_allocation
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
             FOR UPDATE
            """)
    ContractBudgetAllocation selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Delete("""
            DELETE FROM contract_budget_allocation
             WHERE contract_id = #{contractId} AND tenant_id = #{tenantId}
               AND reserved_amount = 0 AND consumed_amount = 0
            """)
    int hardDeleteEditable(@Param("contractId") Long contractId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE contract_budget_allocation
               SET reserved_amount = reserved_amount + #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND allocated_amount - reserved_amount - consumed_amount >= #{amount}
            """)
    int reserveIfAvailable(@Param("id") Long id, @Param("tenantId") Long tenantId,
                           @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE contract_budget_allocation
               SET reserved_amount = reserved_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND reserved_amount >= #{amount}
            """)
    int releaseReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                        @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE contract_budget_allocation
               SET reserved_amount = reserved_amount - #{amount},
                   consumed_amount = consumed_amount + #{amount},
                   version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND reserved_amount >= #{amount}
            """)
    int consumeReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                        @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE contract_budget_allocation
               SET consumed_amount = consumed_amount - #{amount},
                   reserved_amount = reserved_amount + #{amount},
                   version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND consumed_amount >= #{amount}
            """)
    int restoreConsumedToReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                                  @Param("amount") BigDecimal amount);
}
