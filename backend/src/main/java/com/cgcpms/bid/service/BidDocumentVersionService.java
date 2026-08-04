package com.cgcpms.bid.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.dto.BidDocumentCreateRequest;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.entity.BidDocumentVersion;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.mapper.BidDocumentVersionMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BidDocumentVersionService {

    private static final long CURRENT = 0L;
    private static final Map<String, Set<String>> TYPES = Map.of(
            "TENDER", Set.of("TENDER_DOCUMENT", "BILL_OF_QUANTITIES", "TENDER_DRAWING"),
            "SUBMISSION", Set.of("BID_PRICE", "TECHNICAL_DOCUMENT", "BID_DRAWING"),
            "RESULT", Set.of("CANDIDATE_NOTICE", "AWARD_NOTICE", "LOSS_NOTICE", "OBJECTION_REPLY", "AWARD_CLARIFICATION", "OTHER_RESULT"));

    private final BidDocumentVersionMapper mapper;
    private final BidCostMapper bidCostMapper;
    private final SysFileMapper sysFileMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final ApplicationEventPublisher eventPublisher;

    public record BidDocumentFinalizedEvent(Long bidCostId) {}

    public List<BidDocumentVersion> list(Long bidCostId) {
        Long tenantId = tenant();
        requireBid(bidCostId, tenantId);
        return mapper.selectList(new LambdaQueryWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getTenantId, tenantId)
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .orderByAsc(BidDocumentVersion::getLogicalName)
                .orderByDesc(BidDocumentVersion::getVersionNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public BidDocumentVersion append(Long bidCostId, BidDocumentCreateRequest request) {
        Long tenantId = tenant();
        requireBidForUpdate(bidCostId, tenantId);
        validateType(request.documentGroup(), request.documentType());
        SysFile file = requireFile(request.sysFileId(), bidCostId, tenantId);

        BidDocumentVersion previous = mapper.selectCurrentForUpdate(tenantId, bidCostId, request.logicalName());
        if (previous != null && "FINAL".equals(previous.getStatus())) {
            throw new BusinessException("BID_DOCUMENT_FINAL_IMMUTABLE", "正式版本不可直接替换，请保留原文件并追加新逻辑文件");
        }
        int versionNo = previous == null ? 1 : previous.getVersionNo() + 1;
        if (previous != null) supersede(previous, tenantId);

        BidDocumentVersion version = new BidDocumentVersion();
        version.setTenantId(tenantId);
        version.setBidCostId(bidCostId);
        version.setDocumentGroup(request.documentGroup());
        version.setDocumentType(request.documentType());
        version.setLogicalName(request.logicalName());
        version.setVersionNo(versionNo);
        version.setSupersedesId(previous == null ? null : previous.getId());
        version.setSysFileId(request.sysFileId());
        version.setStatus("DRAFT");
        version.setContentSha256(contentSha256(file));
        version.setSourceName(request.sourceName());
        version.setSourceUrl(request.sourceUrl());
        version.setPublishedAt(request.publishedAt());
        version.setReceivedAt(request.receivedAt());
        version.setSubmittedAt(request.submittedAt());
        version.setExternalReceiptNo(request.externalReceiptNo());
        try {
            mapper.insert(version);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("BID_DOCUMENT_CONCURRENT_CHANGE", "文件版本已变化，请刷新后重试");
        }
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public void finalizeVersion(Long bidCostId, Long versionId) {
        BidDocumentVersion version = requireVersionForUpdate(bidCostId, versionId);
        if (!"DRAFT".equals(version.getStatus()) || !Objects.equals(CURRENT, version.getCurrentToken())) {
            throw new BusinessException("BID_DOCUMENT_IMMUTABLE", "仅当前草稿版本可定版");
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getId, versionId)
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getStatus, "DRAFT")
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)
                .set(BidDocumentVersion::getStatus, "FINAL"));
        requireCas(updated);
        eventPublisher.publishEvent(new BidDocumentFinalizedEvent(bidCostId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidVersion(Long bidCostId, Long versionId, String reason) {
        BidDocumentVersion version = requireVersionForUpdate(bidCostId, versionId);
        if (!"DRAFT".equals(version.getStatus()) || !Objects.equals(CURRENT, version.getCurrentToken())) {
            throw new BusinessException("BID_DOCUMENT_IMMUTABLE", "仅当前草稿版本可作废");
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getId, versionId)
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getStatus, version.getStatus())
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)
                .set(BidDocumentVersion::getStatus, "VOID")
                .set(BidDocumentVersion::getRemark, reason));
        requireCas(updated);
    }

    public boolean hasCurrentFinal(Long bidCostId, String documentGroup, String documentType) {
        return mapper.selectCount(new LambdaQueryWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getDocumentGroup, documentGroup)
                .eq(BidDocumentVersion::getDocumentType, documentType)
                .eq(BidDocumentVersion::getStatus, "FINAL")
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)) > 0;
    }

    public boolean hasCurrentFinalResult(Long bidCostId) {
        return mapper.selectCount(new LambdaQueryWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getDocumentGroup, "RESULT")
                .eq(BidDocumentVersion::getStatus, "FINAL")
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)) > 0;
    }

    public boolean hasCurrentFinalGroup(Long bidCostId, String documentGroup) {
        return mapper.selectCount(new LambdaQueryWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getDocumentGroup, documentGroup)
                .eq(BidDocumentVersion::getStatus, "FINAL")
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)) > 0;
    }

    public boolean hasSubmittedFinalBid(Long bidCostId) {
        return mapper.selectCount(new LambdaQueryWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getTenantId, tenant())
                .eq(BidDocumentVersion::getBidCostId, bidCostId)
                .eq(BidDocumentVersion::getDocumentGroup, "SUBMISSION")
                .eq(BidDocumentVersion::getStatus, "FINAL")
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)
                .and(w -> w.isNotNull(BidDocumentVersion::getSubmittedAt)
                        .or().isNotNull(BidDocumentVersion::getExternalReceiptNo))) > 0;
    }

    private void supersede(BidDocumentVersion previous, Long tenantId) {
        int updated = mapper.update(null, new LambdaUpdateWrapper<BidDocumentVersion>()
                .eq(BidDocumentVersion::getId, previous.getId())
                .eq(BidDocumentVersion::getTenantId, tenantId)
                .eq(BidDocumentVersion::getStatus, previous.getStatus())
                .eq(BidDocumentVersion::getCurrentToken, CURRENT)
                .set(BidDocumentVersion::getStatus, "SUPERSEDED"));
        requireCas(updated);
    }

    private BidDocumentVersion requireVersion(Long bidCostId, Long versionId) {
        requireBid(bidCostId, tenant());
        return requireVersionRecord(bidCostId, versionId);
    }

    private BidDocumentVersion requireVersionForUpdate(Long bidCostId, Long versionId) {
        requireBidForUpdate(bidCostId, tenant());
        return requireVersionRecord(bidCostId, versionId);
    }

    private BidDocumentVersion requireVersionRecord(Long bidCostId, Long versionId) {
        BidDocumentVersion version = mapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getTenantId(), tenant())
                || !Objects.equals(version.getBidCostId(), bidCostId)) {
            throw new BusinessException("BID_DOCUMENT_NOT_FOUND", "投标文件版本不存在");
        }
        return version;
    }

    private void requireBid(Long bidCostId, Long tenantId) {
        BidCost bid = bidCostMapper.selectById(bidCostId);
        requireVisibleBid(bid, tenantId);
    }

    private void requireBidForUpdate(Long bidCostId, Long tenantId) {
        BidCost bid = bidCostMapper.selectByIdForUpdate(bidCostId, tenantId);
        requireVisibleBid(bid, tenantId);
    }

    private void requireVisibleBid(BidCost bid, Long tenantId) {
        if (bid == null || !Objects.equals(bid.getTenantId(), tenantId)) {
            throw new BusinessException("BID_COST_NOT_FOUND", "投标记录不存在");
        }
        if (bid.getProjectId() != null) {
            projectAccessChecker.checkAccess(bid.getProjectId(), "访问投标文件");
        }
    }

    private SysFile requireFile(Long sysFileId, Long bidCostId, Long tenantId) {
        SysFile file = sysFileMapper.selectByIdForUpdate(sysFileId, tenantId);
        if (file == null || !Objects.equals(file.getTenantId(), tenantId)
                || !"BID_COST".equals(file.getBusinessType())
                || !Objects.equals(file.getBusinessId(), bidCostId)) {
            throw new BusinessException("BID_DOCUMENT_FILE_NOT_FOUND", "投标文件不存在");
        }
        if (!"CLEAN".equals(file.getVirusScanStatus())) {
            throw new BusinessException("BID_DOCUMENT_FILE_NOT_CLEAN", "文件未通过安全扫描");
        }
        return file;
    }

    private String contentSha256(SysFile file) {
        String name = file.getFileName();
        String hash = name == null ? "" : name.substring(0, Math.min(64, name.length())).toLowerCase();
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new BusinessException("BID_DOCUMENT_HASH_MISSING", "文件缺少服务端内容哈希");
        }
        return hash;
    }

    private void validateType(String group, String type) {
        if (!TYPES.getOrDefault(group, Set.of()).contains(type)) {
            throw new BusinessException("BID_DOCUMENT_TYPE_INVALID", "投标文件分类不合法");
        }
    }

    private void requireCas(int updated) {
        if (updated != 1) {
            throw new BusinessException("BID_DOCUMENT_CONCURRENT_CHANGE", "文件版本已变化，请刷新后重试");
        }
    }

    private Long tenant() {
        return UserContext.getCurrentTenantId();
    }
}
