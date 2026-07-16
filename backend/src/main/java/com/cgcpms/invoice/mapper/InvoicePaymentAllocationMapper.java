package com.cgcpms.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface InvoicePaymentAllocationMapper extends BaseMapper<InvoicePaymentAllocation> {
    @Delete("DELETE FROM invoice_payment_allocation WHERE invoice_id = #{invoiceId} AND tenant_id = #{tenantId}")
    int hardDeletePending(@Param("invoiceId") Long invoiceId, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT COALESCE(SUM(allocated_amount), 0)
              FROM invoice_payment_allocation
             WHERE tenant_id = #{tenantId} AND pay_record_id = #{payRecordId}
               AND invoice_id <> #{excludeInvoiceId}
            """)
    BigDecimal sumAllocatedToRecord(@Param("tenantId") Long tenantId,
                                    @Param("payRecordId") Long payRecordId,
                                    @Param("excludeInvoiceId") Long excludeInvoiceId);
}
