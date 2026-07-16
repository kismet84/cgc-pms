package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface PaymentApplicationSourceMapper extends BaseMapper<PaymentApplicationSource> {
    @Delete("DELETE FROM payment_application_source WHERE pay_application_id = #{applicationId} AND tenant_id = #{tenantId}")
    int hardDeleteDraftSources(@Param("applicationId") Long applicationId, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT COALESCE(SUM(s.source_amount), 0)
              FROM payment_application_source s
              JOIN pay_application p ON p.id = s.pay_application_id
             WHERE s.tenant_id = #{tenantId}
               AND s.source_type = 'SETTLEMENT'
               AND s.settlement_id = #{settlementId}
               AND s.deleted_flag = 0 AND p.deleted_flag = 0
               AND p.approval_status IN ('APPROVING', 'APPROVED')
               AND p.id <> #{excludeApplicationId}
            """)
    BigDecimal sumCommittedSettlement(@Param("tenantId") Long tenantId,
                                      @Param("settlementId") Long settlementId,
                                      @Param("excludeApplicationId") Long excludeApplicationId);

    @Update("""
            UPDATE payment_application_source
               SET paid_amount = paid_amount + #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND source_amount - paid_amount >= #{amount}
            """)
    int consumeForPayment(@Param("id") Long id, @Param("tenantId") Long tenantId,
                          @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE payment_application_source
               SET paid_amount = paid_amount - #{amount}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
               AND paid_amount >= #{amount}
            """)
    int reversePayment(@Param("id") Long id, @Param("tenantId") Long tenantId,
                       @Param("amount") BigDecimal amount);
}
