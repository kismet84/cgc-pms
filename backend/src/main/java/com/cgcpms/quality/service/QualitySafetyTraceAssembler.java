package com.cgcpms.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.quality.dto.QualitySafetyModels.Trace;
import com.cgcpms.quality.entity.QualityConsequence;
import com.cgcpms.quality.entity.QualityInspectionPlan;
import com.cgcpms.quality.entity.QualityInspectionRecord;
import com.cgcpms.quality.entity.QualityPartnerEvaluation;
import com.cgcpms.quality.entity.QualityRectification;
import com.cgcpms.quality.entity.QualitySafetyIssue;
import com.cgcpms.quality.mapper.QualityConsequenceMapper;
import com.cgcpms.quality.mapper.QualityPartnerEvaluationMapper;
import com.cgcpms.quality.mapper.QualityRectificationMapper;

import java.util.List;

final class QualitySafetyTraceAssembler {

    private final QualityRectificationMapper rectificationMapper;
    private final QualityConsequenceMapper consequenceMapper;
    private final QualityPartnerEvaluationMapper evaluationMapper;
    private final CostItemMapper costItemMapper;

    QualitySafetyTraceAssembler(QualityRectificationMapper rectificationMapper,
                                QualityConsequenceMapper consequenceMapper,
                                QualityPartnerEvaluationMapper evaluationMapper,
                                CostItemMapper costItemMapper) {
        this.rectificationMapper = rectificationMapper;
        this.consequenceMapper = consequenceMapper;
        this.evaluationMapper = evaluationMapper;
        this.costItemMapper = costItemMapper;
    }

    Trace assemble(Long tenantId,
                   QualityInspectionPlan plan,
                   QualityInspectionRecord inspection,
                   QualitySafetyIssue issue) {
        List<QualityRectification> rectifications = rectificationMapper.selectList(
                new LambdaQueryWrapper<QualityRectification>()
                        .eq(QualityRectification::getTenantId, tenantId)
                        .eq(QualityRectification::getIssueId, issue.getId())
                        .orderByAsc(QualityRectification::getRoundNo));
        QualityConsequence consequence = first(consequenceMapper.selectList(
                new LambdaQueryWrapper<QualityConsequence>()
                        .eq(QualityConsequence::getTenantId, tenantId)
                        .eq(QualityConsequence::getIssueId, issue.getId())));
        QualityPartnerEvaluation evaluation = consequence == null ? null : first(evaluationMapper.selectList(
                new LambdaQueryWrapper<QualityPartnerEvaluation>()
                        .eq(QualityPartnerEvaluation::getTenantId, tenantId)
                        .eq(QualityPartnerEvaluation::getConsequenceId, consequence.getId())
                        .eq(QualityPartnerEvaluation::getDeletedFlag, 0)));
        CostItem costItem = consequence == null || consequence.getCostItemId() == null
                ? null : costItemMapper.selectById(consequence.getCostItemId());
        return new Trace(plan, inspection, issue, rectifications, consequence, evaluation, costItem);
    }

    private <T> T first(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
