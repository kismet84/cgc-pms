package com.cgcpms.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayInvoiceMapper extends BaseMapper<PayInvoice> {

    @Select("""
            SELECT COUNT(DISTINCT i.id)
              FROM pay_invoice i
              LEFT JOIN invoice_payment_allocation a
                ON a.tenant_id = i.tenant_id AND a.invoice_id = i.id
             WHERE i.tenant_id = #{tenantId}
               AND i.deleted_flag = 0
               AND i.verify_status = 'VERIFIED'
               AND (i.pay_record_id = #{payRecordId} OR a.pay_record_id = #{payRecordId})
            """)
    long countVerifiedByPayRecord(@Param("tenantId") Long tenantId,
                                  @Param("payRecordId") Long payRecordId);
}
