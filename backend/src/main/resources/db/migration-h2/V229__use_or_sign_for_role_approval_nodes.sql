-- 第55条资金支出闭环：同一角色存在多人时，任一有权用户审批即可完成当前角色节点。

UPDATE wf_template_node
SET approve_mode = 'OR_SIGN',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0
  AND id IN (50501, 50502, 50503, 52001, 52002, 52003, 52101, 52102, 52103)
  AND deleted_flag = 0;
