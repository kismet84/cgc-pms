package com.cgcpms.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountingEntryMapper extends BaseMapper<AccountingEntry> {
}
