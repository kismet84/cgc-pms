package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.payment.entity.PayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecord> {
    @Select("SELECT * FROM pay_record WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    PayRecord selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
