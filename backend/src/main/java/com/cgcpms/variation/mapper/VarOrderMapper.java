package com.cgcpms.variation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.variation.entity.VarOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VarOrderMapper extends BaseMapper<VarOrder> {

    @Select("""
            SELECT id,tenant_id,project_id,contract_id,partner_id,var_code,var_name,event_date,
                   claim_deadline,event_description,cause_category,responsible_party,business_matter_key,
                   var_type,direction,reported_amount,approved_amount,confirmed_amount,owner_confirm_flag,
                   estimated_cost_amount,owner_status,internal_approval_instance_id,
                   generated_contract_change_id,impact_days,approval_status,cost_generated_flag,version,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM var_order WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE
            """)
    VarOrder selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 查询最新签证编号（含软删除记录，避免编号冲突）
     */
    @Select("SELECT var_code FROM var_order WHERE var_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY var_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
