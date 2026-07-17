package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeReorderRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateNodeRequest;
import com.cgcpms.workflow.dto.WorkflowTemplateUpdateRequest;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import com.cgcpms.workflow.mapper.WfTemplateNodeMapper;
import com.cgcpms.workflow.vo.WfTemplateNodeVO;
import com.cgcpms.workflow.vo.WfTemplateVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WfTemplateMapper templateMapper;
    private final WfTemplateNodeMapper nodeMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowVOAssembler voAssembler;

    // ═══════════════════════════════════════════════════════════
    //  Template CRUD
    // ═══════════════════════════════════════════════════════════

    public PageResult<WfTemplateVO> listTemplates(long pageNo, long pageSize, String businessType,
                                                  Integer enabled, String keyword) {
        LambdaQueryWrapper<WfTemplate> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId != null) wrapper.eq(WfTemplate::getTenantId, tenantId);
        if (businessType != null && !businessType.isBlank()) wrapper.eq(WfTemplate::getBusinessType, businessType);
        if (enabled != null) wrapper.eq(WfTemplate::getEnabled, enabled);
        if (keyword != null && !keyword.isBlank())
            wrapper.and(w -> w.like(WfTemplate::getTemplateName, keyword).or().like(WfTemplate::getTemplateCode, keyword));
        wrapper.orderByDesc(WfTemplate::getUpdatedAt).orderByAsc(WfTemplate::getTemplateCode);

        IPage<WfTemplate> page = templateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<WfTemplateVO> records = page.getRecords().stream().map(t -> {
            WfTemplateVO vo = voAssembler.toTemplateVO(t);
            vo.setNodeCount(Math.toIntExact(countNodes(t.getId())));
            return vo;
        }).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    public WfTemplateVO getTemplateDetail(Long templateId) {
        WfTemplate template = getTemplateOrThrow(templateId);
        WfTemplateVO vo = voAssembler.toTemplateVO(template);
        List<WfTemplateNodeVO> nodes = listNodes(templateId).stream().map(voAssembler::toTemplateNodeVO).toList();
        vo.setNodes(nodes);
        vo.setNodeCount(nodes.size());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long templateId, WorkflowTemplateUpdateRequest request) {
        WfTemplate template = getTemplateOrThrow(templateId);
        validateAmountRange(request.getAmountMin(), request.getAmountMax());
        rejectUnsupportedConfig(request.getConditionRule(), "conditionRule");
        rejectUnsupportedConfig(request.getFormSchema(), "formSchema");
        template.setTemplateName(request.getTemplateName());
        template.setEnabled(request.getEnabled() == null ? template.getEnabled() : request.getEnabled());
        template.setAmountMin(request.getAmountMin());
        template.setAmountMax(request.getAmountMax());
        template.setConditionRule(blankToNull(request.getConditionRule()));
        template.setFormSchema(blankToNull(request.getFormSchema()));
        template.setRemark(request.getRemark());
        templateMapper.updateById(template);
    }

    // ═══════════════════════════════════════════════════════════
    //  Node CRUD (创建/更新/删除/排序)
    // ═══════════════════════════════════════════════════════════

    @Transactional(rollbackFor = Exception.class)
    public WfTemplateNodeVO createNode(Long templateId, WorkflowTemplateNodeRequest request) {
        WfTemplate template = getTemplateOrThrow(templateId);
        validateNodeRequest(request);
        List<WfTemplateNode> existingNodes = listNodes(templateId);
        int targetOrder = request.getNodeOrder() == null ? existingNodes.size() + 1 : request.getNodeOrder();
        shiftNodesForInsert(templateId, targetOrder);
        WfTemplateNode node = buildNodeFromRequest(template, templateId, request, targetOrder);
        nodeMapper.insert(node);
        normalizeOrders(templateId);
        return voAssembler.toTemplateNodeVO(nodeMapper.selectById(node.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long templateId, Long nodeId, WorkflowTemplateNodeRequest request) {
        getTemplateOrThrow(templateId);
        validateNodeRequest(request);
        WfTemplateNode node = getNodeOrThrow(templateId, nodeId);
        if (request.getNodeCode() != null && !request.getNodeCode().isBlank()) {
            ensureNodeCodeUnique(templateId, nodeId, request.getNodeCode().trim());
            node.setNodeCode(request.getNodeCode().trim());
        }
        node.setNodeName(request.getNodeName());
        if (request.getNodeOrder() != null) node.setNodeOrder(request.getNodeOrder());
        applyNodeRequest(node, request);
        nodeMapper.updateById(node);
        normalizeOrders(templateId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long templateId, Long nodeId) {
        getTemplateOrThrow(templateId);
        getNodeOrThrow(templateId, nodeId);
        if (countNodes(templateId) <= 1) throw new BusinessException("TEMPLATE_LAST_NODE", "至少保留一个审批节点");
        nodeMapper.deleteById(nodeId);
        normalizeOrders(templateId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderNodes(Long templateId, WorkflowTemplateNodeReorderRequest request) {
        getTemplateOrThrow(templateId);
        List<WfTemplateNode> nodes = listNodes(templateId);
        if (request.getNodeIds().size() != nodes.size())
            throw new BusinessException("TEMPLATE_NODE_REORDER_INVALID", "排序节点数量不一致");
        Set<Long> expected = new HashSet<>(nodes.stream().map(WfTemplateNode::getId).toList());
        if (!expected.equals(new HashSet<>(request.getNodeIds())))
            throw new BusinessException("TEMPLATE_NODE_REORDER_INVALID", "排序节点不属于该模板");
        int order = 1;
        for (Long id : request.getNodeIds()) {
            WfTemplateNode update = new WfTemplateNode();
            update.setId(id);
            update.setNodeOrder(order++);
            nodeMapper.updateById(update);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Lookup / validation
    // ═══════════════════════════════════════════════════════════

    private WfTemplate getTemplateOrThrow(Long templateId) {
        WfTemplate template = templateMapper.selectById(templateId);
        if (template == null) throw new BusinessException("TEMPLATE_NOT_FOUND", "审批模板不存在");
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId != null && !tenantId.equals(template.getTenantId()))
            throw new BusinessException("TEMPLATE_NOT_FOUND", "审批模板不存在");
        return template;
    }

    private WfTemplateNode getNodeOrThrow(Long templateId, Long nodeId) {
        WfTemplateNode node = nodeMapper.selectById(nodeId);
        if (node == null || !templateId.equals(node.getTemplateId()))
            throw new BusinessException("TEMPLATE_NODE_NOT_FOUND", "审批节点不存在");
        return node;
    }

    private List<WfTemplateNode> listNodes(Long templateId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<WfTemplateNode>()
                .eq(WfTemplateNode::getTemplateId, templateId)
                .orderByAsc(WfTemplateNode::getNodeOrder).orderByAsc(WfTemplateNode::getId));
    }

    private long countNodes(Long templateId) {
        return nodeMapper.selectCount(new LambdaQueryWrapper<WfTemplateNode>()
                .eq(WfTemplateNode::getTemplateId, templateId));
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Node ordering
    // ═══════════════════════════════════════════════════════════

    private void shiftNodesForInsert(Long templateId, int targetOrder) {
        nodeMapper.update(null, new LambdaUpdateWrapper<WfTemplateNode>()
                .eq(WfTemplateNode::getTemplateId, templateId)
                .ge(WfTemplateNode::getNodeOrder, targetOrder)
                .setSql("node_order = node_order + 1"));
    }

    private void normalizeOrders(Long templateId) {
        List<WfTemplateNode> nodes = new ArrayList<>(listNodes(templateId));
        nodes.sort(Comparator
                .comparing((WfTemplateNode n) -> n.getNodeOrder() == null ? Integer.MAX_VALUE : n.getNodeOrder())
                .thenComparing(WfTemplateNode::getId));
        int order = 1;
        for (WfTemplateNode node : nodes) {
            if (node.getNodeOrder() == null || node.getNodeOrder() != order) {
                WfTemplateNode u = new WfTemplateNode();
                u.setId(node.getId());
                u.setNodeOrder(order);
                nodeMapper.updateById(u);
            }
            order++;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Validation
    // ═══════════════════════════════════════════════════════════

    private void validateNodeRequest(WorkflowTemplateNodeRequest request) {
        String mode = request.getApproveMode();
        if (mode != null && !mode.isBlank()
                && !WorkflowConstants.MODE_SEQUENTIAL.equals(mode)
                && !WorkflowConstants.MODE_COUNTERSIGN.equals(mode)
                && !WorkflowConstants.MODE_OR_SIGN.equals(mode))
            throw new BusinessException("TEMPLATE_NODE_MODE_INVALID", "审批模式不支持");
        validateApproverConfig(request.getApproverConfig());
        rejectUnsupportedConfig(request.getPassRuleJson(), "passRuleJson");
        rejectUnsupportedConfig(request.getRejectRuleJson(), "rejectRuleJson");
        rejectUnsupportedConfig(request.getConditionRule(), "conditionRule");
        rejectUnsupportedConfig(request.getNodeConfig(), "nodeConfig");
    }

    private void validateAmountRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new BusinessException("TEMPLATE_AMOUNT_RANGE_INVALID", "金额下限不能大于金额上限");
    }

    private void validateJsonOrBlank(String json, String fieldName) {
        if (json == null || json.isBlank()) return;
        try { objectMapper.readTree(json); }
        catch (Exception e) { throw new BusinessException("TEMPLATE_JSON_INVALID", fieldName + " 不是合法 JSON"); }
    }

    private void rejectUnsupportedConfig(String json, String fieldName) {
        if (json == null || json.isBlank()) return;
        validateJsonOrBlank(json, fieldName);
        throw new BusinessException("TEMPLATE_CONFIG_UNSUPPORTED",
                fieldName + " 尚未接入审批执行引擎，禁止保存为生效配置");
    }

    private void validateApproverConfig(String json) {
        if (json == null || json.isBlank()) return;
        final com.fasterxml.jackson.databind.JsonNode node;
        try { node = objectMapper.readTree(json); }
        catch (Exception e) { throw new BusinessException("TEMPLATE_JSON_INVALID", "approverConfig 不是合法 JSON"); }
        if (!node.isObject() || !node.hasNonNull("type")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "审批人配置必须是包含 type 的 JSON 对象");
        }
        String type = node.get("type").asText().toUpperCase();
        String required = switch (type) {
            case "USER" -> "userId";
            case "ROLE" -> "roleId";
            case "POSITION" -> "positionId";
            case "PROJECT_ROLE" -> "roleCode";
            default -> throw new BusinessException("UNSUPPORTED_APPROVER_TYPE", "不支持的审批人类型: " + type);
        };
        if (!node.hasNonNull(required) || node.get(required).asText().isBlank()) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", type + " 类型配置缺少 " + required);
        }
        Set<String> allowed = Set.of("type", required);
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new BusinessException("INVALID_APPROVER_CONFIG", "审批人配置包含未执行字段: " + field);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Node code uniqueness
    // ═══════════════════════════════════════════════════════════

    private String resolveNodeCode(Long templateId, String requestedCode, int targetOrder) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            ensureNodeCodeUnique(templateId, null, requestedCode.trim());
            return requestedCode.trim();
        }
        int suffix = Math.max(targetOrder, 1);
        while (nodeCodeExists(templateId, null, "N" + suffix)) suffix++;
        return "N" + suffix;
    }

    private void ensureNodeCodeUnique(Long templateId, Long currentNodeId, String nodeCode) {
        if (nodeCodeExists(templateId, currentNodeId, nodeCode))
            throw new BusinessException("TEMPLATE_NODE_CODE_DUPLICATE", "节点编码已存在");
    }

    private boolean nodeCodeExists(Long templateId, Long currentNodeId, String nodeCode) {
        LambdaQueryWrapper<WfTemplateNode> wrapper = new LambdaQueryWrapper<WfTemplateNode>()
                .eq(WfTemplateNode::getTemplateId, templateId).eq(WfTemplateNode::getNodeCode, nodeCode);
        if (currentNodeId != null) wrapper.ne(WfTemplateNode::getId, currentNodeId);
        return nodeMapper.selectCount(wrapper) > 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Node field mapping
    // ═══════════════════════════════════════════════════════════

    private WfTemplateNode buildNodeFromRequest(WfTemplate template, Long templateId,
                                                 WorkflowTemplateNodeRequest request, int targetOrder) {
        WfTemplateNode node = new WfTemplateNode();
        node.setTenantId(template.getTenantId());
        node.setTemplateId(templateId);
        node.setNodeCode(resolveNodeCode(templateId, request.getNodeCode(), targetOrder));
        node.setNodeName(request.getNodeName());
        node.setNodeOrder(targetOrder);
        applyNodeRequest(node, request);
        return node;
    }

    private void applyNodeRequest(WfTemplateNode node, WorkflowTemplateNodeRequest request) {
        node.setNodeType(defaultIfBlank(request.getNodeType(), "APPROVAL"));
        node.setApproveMode(defaultIfBlank(request.getApproveMode(), WorkflowConstants.MODE_SEQUENTIAL));
        node.setApproverConfig(request.getApproverConfig());
        node.setPassRuleJson(blankToNull(request.getPassRuleJson()));
        node.setRejectRuleJson(blankToNull(request.getRejectRuleJson()));
        node.setConditionRule(blankToNull(request.getConditionRule()));
        node.setNodeConfig(blankToNull(request.getNodeConfig()));
        node.setAllowTransfer(request.getAllowTransfer() == null ? 1 : request.getAllowTransfer());
        node.setAllowAddSign(request.getAllowAddSign() == null ? 1 : request.getAllowAddSign());
        node.setTimeoutHours(request.getTimeoutHours());
        node.setRemark(request.getRemark());
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers — Utility
    // ═══════════════════════════════════════════════════════════

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String defaultIfBlank(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value; }
}
