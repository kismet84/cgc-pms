package com.cgcpms.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.partner.entity.MdPartner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MdPartnerMapper extends BaseMapper<MdPartner>, DeletedCodeSource {
    @Select("SELECT partner_code FROM md_partner WHERE partner_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(partner_code) DESC, partner_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id,tenant_id,partner_code,partner_name,partner_type,credit_code,legal_person,
                   contact_name,contact_phone,bank_name,bank_account,qualification_level,blacklist_flag,
                   risk_level,status,default_lead_days,created_by,created_at,updated_by,updated_at,
                   deleted_flag,remark
            FROM md_partner WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE
            """)
    MdPartner selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
