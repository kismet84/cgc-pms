package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.payment.entity.PayApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PayApplicationMapper extends BaseMapper<PayApplication>, DeletedCodeSource {

    String COLUMNS = "id, tenant_id, project_id, contract_id, partner_id, cost_subject_id, budget_line_id, "
            + "contract_budget_allocation_id, expense_category, approval_instance_id, integrity_version, "
            + "apply_code, apply_amount, approved_amount, actual_pay_amount, pay_type, pay_status, "
            + "approval_status, apply_reason, version, created_by, created_at, updated_by, updated_at, "
            + "deleted_flag, remark";

    @Select("SELECT apply_code FROM pay_application WHERE apply_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(apply_code) DESC, apply_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("SELECT " + COLUMNS + " FROM pay_application WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE") // SQL-SAFETY: fixed-sql-fragment
    PayApplication selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT contract_id FROM pay_application WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0")
    Long selectContractId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT " + COLUMNS + " FROM pay_application " // SQL-SAFETY: fixed-sql-fragment
            + "WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} AND deleted_flag = 0 "
            + "AND approval_status IN ('APPROVING', 'APPROVED') "
            + "AND (#{excludeId} IS NULL OR id <> #{excludeId}) FOR UPDATE")
    List<PayApplication> selectEffectiveByContractForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("contractId") Long contractId,
                                                            @Param("excludeId") Long excludeId);
}
