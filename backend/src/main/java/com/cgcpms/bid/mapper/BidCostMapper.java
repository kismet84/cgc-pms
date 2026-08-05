package com.cgcpms.bid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.bid.dto.BidOwnerOption;
import com.cgcpms.bid.dto.BidCostOption;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.common.util.DeletedCodeSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BidCostMapper extends BaseMapper<BidCost>, DeletedCodeSource {

    @Override
    @Select("SELECT bid_code FROM bid_cost WHERE bid_code LIKE CONCAT(#{prefix}, '%') "
            + "AND tenant_id = #{tenantId} ORDER BY bid_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("SELECT id, tenant_id, project_id FROM bid_cost "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE")
    BidCost selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            <script>
            SELECT b.id,
                   MAX(u.real_name) AS owner_name,
                   COALESCE(SUM(CASE
                     WHEN s.account_category='COST' AND j.status IN ('ARCHIVED','REVERSED')
                     THEN CASE WHEN j.direction='OUT' THEN j.amount ELSE -j.amount END
                     ELSE 0 END),0) AS bid_expense
            FROM bid_cost b
            LEFT JOIN sys_user u ON u.tenant_id=b.tenant_id AND u.id=b.owner_id AND u.deleted_flag=0
            LEFT JOIN cash_journal_entry j ON j.tenant_id=b.tenant_id AND j.bid_cost_id=b.id AND j.deleted_flag=0
            LEFT JOIN cost_subject s ON s.tenant_id=j.tenant_id AND s.id=j.cost_subject_id AND s.deleted_flag=0
            WHERE b.tenant_id=#{tenantId} AND b.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY b.id
            </script>
            """)
    List<BidCost> selectListStats(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    @Select("""
            SELECT u.id AS owner_id, u.real_name AS owner_name
            FROM sys_user u
            WHERE u.tenant_id=#{tenantId} AND u.deleted_flag=0 AND u.status IN ('ACTIVE', 'ENABLE')
            ORDER BY u.real_name, u.id
            """)
    List<BidOwnerOption> selectOwnerOptions(@Param("tenantId") Long tenantId);

    @Select("""
            <script>
            SELECT id, bid_code, bid_project_name FROM bid_cost
            WHERE tenant_id=#{tenantId} AND deleted_flag=0
              AND (project_id IS NULL
              <if test="accessibleProjectIds != null and !accessibleProjectIds.isEmpty()">
                OR project_id IN
                <foreach collection="accessibleProjectIds" item="projectId" open="(" separator="," close=")">
                  #{projectId}
                </foreach>
              </if>
              )
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    List<BidCostOption> selectCostOptions(@Param("tenantId") Long tenantId,
                                          @Param("accessibleProjectIds") List<Long> accessibleProjectIds);

    @Select("SELECT COUNT(*) FROM bid_document_version WHERE tenant_id=#{tenantId} AND bid_cost_id=#{bidCostId} AND deleted_flag=0")
    long countDocumentVersions(@Param("tenantId") Long tenantId, @Param("bidCostId") Long bidCostId);

    @Select("SELECT COUNT(*) FROM cash_journal_entry WHERE tenant_id=#{tenantId} AND bid_cost_id=#{bidCostId} AND deleted_flag=0")
    long countCashEntries(@Param("tenantId") Long tenantId, @Param("bidCostId") Long bidCostId);
}
