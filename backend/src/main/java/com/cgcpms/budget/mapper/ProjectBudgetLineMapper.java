package com.cgcpms.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ProjectBudgetLineMapper extends BaseMapper<ProjectBudgetLine> {
    @Delete("DELETE FROM project_budget_line WHERE budget_id = #{budgetId} AND tenant_id = #{tenantId}")
    int hardDeleteDraftLines(@Param("budgetId") Long budgetId, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM project_budget_line WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    ProjectBudgetLine selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE project_budget_line
               SET reserved_amount = reserved_amount + #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND budget_amount - reserved_amount - consumed_amount >= #{amount}
            """)
    int reserveIfAvailable(@Param("id") Long id, @Param("tenantId") Long tenantId,
                           @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE project_budget_line
               SET reserved_amount = reserved_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND reserved_amount >= #{amount}
            """)
    int releaseReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                        @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE project_budget_line
               SET reserved_amount = reserved_amount - #{amount},
                   consumed_amount = consumed_amount + #{amount},
                   version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND reserved_amount >= #{amount}
            """)
    int consumeReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                        @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE project_budget_line
               SET consumed_amount = consumed_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND consumed_amount >= #{amount}
            """)
    int reverseConsumed(@Param("id") Long id, @Param("tenantId") Long tenantId,
                        @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE project_budget_line
               SET consumed_amount = consumed_amount - #{amount},
                   reserved_amount = reserved_amount + #{amount},
                   version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND consumed_amount >= #{amount}
            """)
    int restoreConsumedToReserved(@Param("id") Long id, @Param("tenantId") Long tenantId,
                                  @Param("amount") BigDecimal amount);
}
