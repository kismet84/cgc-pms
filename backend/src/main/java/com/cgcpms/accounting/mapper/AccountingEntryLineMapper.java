package com.cgcpms.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountingEntryLineMapper extends BaseMapper<AccountingEntryLine> {
}
