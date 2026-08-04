package com.cgcpms.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.file.entity.SysFile;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id,tenant_id,business_type,document_type,business_id,file_name,original_name,
                   file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scan_detail,
                   virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM sys_file WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE
            """)
    SysFile selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id FROM sys_file
            WHERE bucket_name = #{bucketName}
              AND storage_path = #{storagePath}
              AND deleted_flag = 0
            ORDER BY id FOR UPDATE
            """)
    List<Long> lockActiveByObjectPath(@Param("bucketName") String bucketName,
                                      @Param("storagePath") String storagePath);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*) FROM sys_file
            WHERE tenant_id = #{tenantId}
              AND business_type = #{businessType}
              AND business_id = #{businessId}
              AND deleted_flag = 0
            """)
    long countActiveByBusiness(@Param("tenantId") Long tenantId,
                               @Param("businessType") String businessType,
                               @Param("businessId") Long businessId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*) FROM sys_file
            WHERE bucket_name = #{bucketName}
              AND storage_path = #{storagePath}
              AND deleted_flag = 0
            """)
    long countActiveByObjectPath(@Param("bucketName") String bucketName,
                                 @Param("storagePath") String storagePath);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM bid_document_version
                 WHERE tenant_id = #{tenantId} AND sys_file_id = #{fileId} AND deleted_flag = 0)
              + (SELECT COUNT(*) FROM payment_document_link
                 WHERE tenant_id = #{tenantId} AND file_id = #{fileId})
            """)
    long countImmutableReferences(@Param("tenantId") Long tenantId,
                                  @Param("fileId") Long fileId);
}
