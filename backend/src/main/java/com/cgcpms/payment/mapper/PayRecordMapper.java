package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.payment.entity.PayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecord>, DeletedCodeSource {
    String COLUMNS = "id, tenant_id, project_id, pay_application_id, contract_id, partner_id, record_code, "
            + "pay_amount, pay_date, fund_account_id, paid_at, pay_method, voucher_no, pay_status, "
            + "external_txn_no, failure_reason, reversed_record_id, reversed_at, reversal_type, version, "
            + "created_by, created_at, updated_by, updated_at, deleted_flag, remark";

    @Select("SELECT " + COLUMNS + " FROM pay_record WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE") // SQL-SAFETY: fixed-sql-fragment
    PayRecord selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT " + COLUMNS + " FROM pay_record " // SQL-SAFETY: fixed-sql-fragment
            + "WHERE tenant_id = #{tenantId} AND external_txn_no = #{externalTxnNo} "
            + "AND deleted_flag = 0 FOR UPDATE")
    PayRecord selectByExternalTxnNoForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("externalTxnNo") String externalTxnNo);

    @Select("SELECT " + COLUMNS + " FROM pay_record " // SQL-SAFETY: fixed-sql-fragment
            + "WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} "
            + "AND pay_status = 'SUCCESS' AND deleted_flag = 0 FOR UPDATE")
    List<PayRecord> selectSuccessByContractForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("contractId") Long contractId);

    @Select("SELECT " + COLUMNS + " FROM pay_record " // SQL-SAFETY: fixed-sql-fragment
            + "WHERE tenant_id = #{tenantId} AND pay_application_id = #{applicationId} "
            + "AND pay_status = 'SUCCESS' AND deleted_flag = 0 FOR UPDATE")
    List<PayRecord> selectSuccessByApplicationForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("applicationId") Long applicationId);

    @Insert("INSERT INTO payment_code_scope (tenant_id) VALUES (#{tenantId}) "
            + "ON DUPLICATE KEY UPDATE tenant_id = tenant_id")
    int ensureTenantPaymentCodeScope(@Param("tenantId") Long tenantId);

    @Select("SELECT tenant_id FROM payment_code_scope WHERE tenant_id = #{tenantId} FOR UPDATE")
    Long lockTenantPaymentCodeScope(@Param("tenantId") Long tenantId);

    @Override
    @Select("SELECT record_code FROM pay_record WHERE record_code LIKE CONCAT(#{prefix}, '%') "
            + "AND tenant_id = #{tenantId} "
            + "ORDER BY CHAR_LENGTH(record_code) DESC, record_code DESC LIMIT 1 FOR UPDATE")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
