package com.cgcpms.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ExpenseApplicationMapper extends BaseMapper<ExpenseApplication> {
    @Select("SELECT * FROM expense_application WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    ExpenseApplication selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE expense_application
               SET converted_amount = converted_amount + #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND approval_status = 'APPROVED'
               AND amount - converted_amount >= #{amount}
            """)
    int allocateToPayment(@Param("id") Long id, @Param("tenantId") Long tenantId,
                          @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE expense_application
               SET converted_amount = converted_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND converted_amount >= #{amount}
            """)
    int releasePaymentAllocation(@Param("id") Long id, @Param("tenantId") Long tenantId,
                                 @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE expense_application
               SET paid_amount = paid_amount + #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND converted_amount - paid_amount >= #{amount}
            """)
    int consumePayment(@Param("id") Long id, @Param("tenantId") Long tenantId,
                       @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE expense_application
               SET paid_amount = paid_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND paid_amount >= #{amount}
            """)
    int reversePayment(@Param("id") Long id, @Param("tenantId") Long tenantId,
                       @Param("amount") BigDecimal amount);
}
