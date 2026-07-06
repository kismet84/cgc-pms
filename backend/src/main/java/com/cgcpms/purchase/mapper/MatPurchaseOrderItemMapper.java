package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MatPurchaseOrderItemMapper extends BaseMapper<MatPurchaseOrderItem> {
    void insertBatch(@Param("items") List<MatPurchaseOrderItem> items);
}
