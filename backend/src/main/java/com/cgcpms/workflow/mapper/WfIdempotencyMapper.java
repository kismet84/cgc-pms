package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfIdempotency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfIdempotencyMapper extends BaseMapper<WfIdempotency> {
}
