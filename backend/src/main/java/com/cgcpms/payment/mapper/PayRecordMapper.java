package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.payment.entity.PayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecord>, DeletedCodeSource {
    @Select("SELECT * FROM pay_record WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    PayRecord selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Override
    @Select("SELECT record_code FROM pay_record WHERE record_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY record_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
