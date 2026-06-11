package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.CtContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CtContractMapper extends BaseMapper<CtContract> {

    @Select("SELECT * FROM ct_contract WHERE id = #{id} FOR UPDATE")
    CtContract selectByIdForUpdate(@Param("id") Long id);
}
