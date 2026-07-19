-- CGC-COMPLETE-PROJECT v1 / QUALITY, WORKFLOW, ALERT AND CLOSEOUT
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO project_progress_snapshot
  (id,tenant_id,project_id,schedule_plan_id,snapshot_date,planned_progress,actual_progress,deviation_percent,
   lagging_task_count,status,formula_version,created_by,created_at)
VALUES
  (520000000000003001,0,520000000000000001,520000000000001001,'2026-06-30',100,100,0,0,'COMPLETED',
   'PROGRESS_SNAPSHOT_V1',@demo_user,NOW());

INSERT INTO qs_inspection_plan
  (id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,start_date,end_date,owner_user_id,status,
   activated_by,activated_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003101,0,520000000000000001,'M52-QS-PLAN-001','演示质量安全检查计划','SAFETY','SINGLE','2026-05-01','2026-05-31',
   @demo_user,'COMPLETED',@demo_user,NOW(),0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO qs_inspection_record
  (id,tenant_id,plan_id,project_id,inspection_code,inspection_date,location,inspector_user_id,conclusion,summary,status,
   submitted_by,submitted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003102,0,520000000000003101,520000000000000001,'M52-QS-INSP-001','2026-05-25','主楼',@demo_user,
   'ISSUES','发现临边防护问题并闭环','SUBMITTED',@demo_user,NOW(),0,@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1');

INSERT INTO qs_issue
  (id,tenant_id,plan_id,inspection_id,project_id,issue_code,issue_type,category,severity,title,description,responsible_kind,
   responsible_user_id,due_date,status,closed_by,closed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003203,0,520000000000003101,520000000000003102,520000000000000001,'M52-QS-ISSUE-001','SAFETY',
   '临边防护','HIGH','主楼临边防护缺失','检查发现一处临边防护缺失','INTERNAL',@demo_user,'2026-05-28','CLOSED',
   @demo_user,NOW(),0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO qs_rectification
  (id,tenant_id,issue_id,project_id,round_no,action_description,responsible_user_id,planned_complete_date,actual_completed_at,
   status,submitted_by,submitted_at,reinspection_comment,reinspected_by,reinspected_at,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003204,0,520000000000003203,520000000000000001,1,'补齐防护栏杆并复检',@demo_user,'2026-05-28',
   '2026-05-27 16:00:00','PASSED',@demo_user,'2026-05-27 16:00:00','复检通过',@demo_user,'2026-05-28 09:00:00',0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO wf_node_instance
  (id,tenant_id,instance_id,node_code,node_name,node_order,approve_mode,node_status,round_no,started_at,ended_at,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003301,0,520000000000000901,'M52-APPROVE','演示审批节点',1,'OR','COMPLETED',1,NOW(),NOW(),
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO wf_task
  (id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name,task_status,round_no,
   task_version,received_at,handled_at,action_type,comment,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003302,0,520000000000000901,520000000000003301,'BID_COST_TARGET_TRANSFER',520000000000000601,
   @demo_user,'平台管理员','COMPLETED',1,0,NOW(),NOW(),'APPROVE','审批通过',@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1');

INSERT INTO wf_record
  (id,tenant_id,instance_id,node_instance_id,task_id,round_no,business_type,business_id,node_code,node_name,action_type,
   action_name,operator_id,operator_name,comment,record_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003303,0,520000000000000901,520000000000003301,520000000000003302,1,'BID_COST_TARGET_TRANSFER',
   520000000000000601,'M52-APPROVE','演示审批节点','APPROVE','同意',@demo_user,'平台管理员','审批通过','SUCCESS',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO alert_log
  (id,tenant_id,project_id,contract_id,alert_domain,alert_category,source_type,source_id,dedup_key,rule_type,severity,
   message,triggered_at,is_read,acknowledged_by,acknowledged_at,process_status,processed_at,processed_by,escalation_level,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003401,0,520000000000000001,520000000000000703,'QUALITY_SAFETY','SAFETY','QS_ISSUE',520000000000003203,
   'CGC-DEMO-M52:QS-ISSUE:001','QUALITY_SAFETY_ISSUE','HIGH','演示质量安全问题已闭环','2026-05-25 10:00:00',1,
   @demo_user,'2026-05-25 11:00:00','RESOLVED','2026-05-28 09:00:00',@demo_user,0,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO alert_lifecycle_event
  (id,tenant_id,alert_id,event_type,from_status,to_status,operator_id,remark,occurred_at,payload_json,payload_hash)
VALUES
  (520000000000003402,0,520000000000003401,'RESOLVE','ACKNOWLEDGED','RESOLVED',@demo_user,'整改复检通过',
   '2026-05-28 09:00:00','{"package":"CGC-COMPLETE-PROJECT","version":1}',
   SHA2('{"package":"CGC-COMPLETE-PROJECT","version":1}',256));

INSERT INTO project_closeout
  (id,tenant_id,project_id,closeout_code,planned_completion_date,actual_completion_date,status,tail_collection_verified_at,
   closed_by,closed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000003501,0,520000000000000001,'M52-CLOSEOUT-001','2026-06-30','2026-06-30','CLOSED',NOW(),
   @demo_user,NOW(),0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');
