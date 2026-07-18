package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.vo.PaymentSourceOptionVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PaymentApplicationSourceMapper extends BaseMapper<PaymentApplicationSource> {

    @Select("""
            SELECT 'SUB_MEASURE' AS source_type,
                   CONCAT('', sm.id) AS source_ref_id,
                   sm.measure_code AS document_code,
                   sm.net_amount AS source_total_amount,
                   COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) AS committed_amount,
                   sm.net_amount - COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) AS available_amount
              FROM sub_measure sm
              LEFT JOIN payment_application_source s
                ON s.tenant_id = sm.tenant_id
               AND s.source_type = 'SUB_MEASURE'
               AND s.sub_measure_id = sm.id
               AND s.deleted_flag = 0
              LEFT JOIN pay_application p
                ON p.id = s.pay_application_id
               AND p.tenant_id = sm.tenant_id
               AND p.deleted_flag = 0
               AND p.approval_status IN ('APPROVING', 'APPROVED')
             WHERE sm.tenant_id = #{tenantId}
               AND sm.project_id = #{projectId}
               AND sm.contract_id = #{contractId}
               AND sm.partner_id = #{partnerId}
               AND sm.approval_status = 'APPROVED'
               AND sm.status = 'CONFIRMED'
               AND sm.deleted_flag = 0
               AND NOT EXISTS (
                   SELECT 1
                     FROM settlement_sub_measure ssm
                     JOIN stl_settlement stl
                       ON stl.id = ssm.settlement_id
                      AND stl.tenant_id = ssm.tenant_id
                      AND stl.deleted_flag = 0
                    WHERE ssm.tenant_id = sm.tenant_id
                      AND ssm.sub_measure_id = sm.id
                      AND stl.approval_status IN ('APPROVING', 'APPROVED')
               )
             GROUP BY sm.id, sm.measure_code, sm.measure_date, sm.net_amount
            HAVING sm.net_amount - COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) > 0
             ORDER BY sm.measure_date DESC, sm.id DESC
             LIMIT 200
            """)
    List<PaymentSourceOptionVO> selectSubMeasureOptions(
            @Param("tenantId") Long tenantId,
            @Param("projectId") Long projectId,
            @Param("contractId") Long contractId,
            @Param("partnerId") Long partnerId);

    @Select("""
            SELECT 'SETTLEMENT' AS source_type,
                   CONCAT('', stl.id) AS source_ref_id,
                   stl.settlement_code AS document_code,
                   stl.final_amount AS source_total_amount,
                   COALESCE(stl.paid_amount, 0)
                     + COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) AS committed_amount,
                   stl.final_amount - COALESCE(stl.paid_amount, 0)
                     - COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) AS available_amount
              FROM stl_settlement stl
              LEFT JOIN payment_application_source s
                ON s.tenant_id = stl.tenant_id
               AND s.source_type = 'SETTLEMENT'
               AND s.source_ref_id = stl.id
               AND s.deleted_flag = 0
              LEFT JOIN pay_application p
                ON p.id = s.pay_application_id
               AND p.tenant_id = stl.tenant_id
               AND p.deleted_flag = 0
               AND p.approval_status IN ('APPROVING', 'APPROVED')
             WHERE stl.tenant_id = #{tenantId}
               AND stl.project_id = #{projectId}
               AND stl.contract_id = #{contractId}
               AND stl.partner_id = #{partnerId}
               AND stl.approval_status = 'APPROVED'
               AND stl.settlement_status = 'FINALIZED'
               AND stl.deleted_flag = 0
             GROUP BY stl.id, stl.settlement_code, stl.final_amount, stl.paid_amount, stl.finalized_at
            HAVING stl.final_amount - COALESCE(stl.paid_amount, 0)
                     - COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN s.source_amount ELSE 0 END), 0) > 0
             ORDER BY stl.finalized_at DESC, stl.id DESC
             LIMIT 200
            """)
    List<PaymentSourceOptionVO> selectSettlementOptions(
            @Param("tenantId") Long tenantId,
            @Param("projectId") Long projectId,
            @Param("contractId") Long contractId,
            @Param("partnerId") Long partnerId);

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

    @Select("""
            SELECT COALESCE(SUM(s.source_amount), 0)
              FROM payment_application_source s
              JOIN pay_application p ON p.id = s.pay_application_id
             WHERE s.tenant_id = #{tenantId}
               AND s.source_type = 'SUB_MEASURE'
               AND s.sub_measure_id = #{subMeasureId}
               AND s.deleted_flag = 0 AND p.deleted_flag = 0
               AND p.approval_status IN ('APPROVING', 'APPROVED')
               AND p.id <> #{excludeApplicationId}
            """)
    BigDecimal sumCommittedSubMeasure(@Param("tenantId") Long tenantId,
                                      @Param("subMeasureId") Long subMeasureId,
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
