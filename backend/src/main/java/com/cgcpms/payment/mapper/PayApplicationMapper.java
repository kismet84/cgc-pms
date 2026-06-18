package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.payment.entity.PayApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayApplicationMapper extends BaseMapper<PayApplication> {

    @Select("SELECT * FROM pay_application WHERE id = #{id} FOR UPDATE")
    PayApplication selectByIdForUpdate(@Param("id") Long id);
}
