package com.cgcpms.bid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.bid.entity.BidDocumentVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BidDocumentVersionMapper extends BaseMapper<BidDocumentVersion> {

    @Select("""
            SELECT id,tenant_id,bid_cost_id,document_group,document_type,logical_name,version_no,
                   supersedes_id,sys_file_id,status,current_token,content_sha256,source_name,source_url,
                   published_at,received_at,submitted_at,external_receipt_no,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM bid_document_version
            WHERE tenant_id=#{tenantId} AND bid_cost_id=#{bidCostId}
              AND logical_name=#{logicalName} AND current_token=0 AND deleted_flag=0
            FOR UPDATE
            """)
    BidDocumentVersion selectCurrentForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("bidCostId") Long bidCostId,
                                              @Param("logicalName") String logicalName);
}
