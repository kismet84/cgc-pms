package com.cgcpms.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.payment.entity.PayApplicationBasis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayApplicationBasisMapper extends BaseMapper<PayApplicationBasis> {
    void insertBatch(@Param("items") List<PayApplicationBasis> items);
}
