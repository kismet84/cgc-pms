package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MatPurchaseRequestItemMapper extends BaseMapper<MatPurchaseRequestItem> {
    void insertBatch(@Param("items") List<MatPurchaseRequestItem> items);
}
