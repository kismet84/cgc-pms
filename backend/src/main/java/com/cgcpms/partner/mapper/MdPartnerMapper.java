package com.cgcpms.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.partner.entity.MdPartner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MdPartnerMapper extends BaseMapper<MdPartner> {
    @Select("SELECT * FROM md_partner WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE")
    MdPartner selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
