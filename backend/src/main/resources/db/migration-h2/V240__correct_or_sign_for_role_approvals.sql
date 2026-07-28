UPDATE wf_template_node
SET approve_mode = 'OR_SIGN'
WHERE id IN (
    50011, 50012, 50013,
    50201, 50202, 50203,
    50301, 50302, 50303,
    53301, 53302, 53303,
    53401, 53402
)
  AND deleted_flag = 0;
