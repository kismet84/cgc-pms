-- CGC-COMPLETE-PROJECT v2 / PRODUCTION MEASUREMENT AND SITE DAILY LOG
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO ct_contract_item
  (id,tenant_id,contract_id,item_code,item_name,item_spec,unit,quantity,unit_price,amount,tax_rate,tax_amount,amount_without_tax,
   sort_order,created_by,updated_by,remark,created_at,updated_at,deleted_flag)
VALUES
  (520000000000005001,0,520000000000000701,'M52-OWNER-ITEM-001','主体结构施工计量项','按施工图及核定工程量','项',100,100000,10000000,
   9,825688.07,9174311.93,1,@demo_user,@demo_user,'业主合同正常计量来源',NOW(),NOW(),0),
  (520000000000005002,0,520000000000000701,'M52-OWNER-ITEM-ZERO','零单价观察项','只验证边界展示','项',1,0,0,
   0,0,0,2,@demo_user,@demo_user,'边界态：合法零单价',NOW(),NOW(),0);

INSERT INTO project_period_plan
  (id,tenant_id,project_id,schedule_plan_id,parent_period_plan_id,period_type,period_code,period_name,start_date,end_date,status,
   approval_instance_id,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000005101,0,520000000000000001,520000000000001001,NULL,'MONTHLY','M52-MONTH-202505','2025年5月计划','2025-05-01','2025-05-31',
   'APPROVED',NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'月计划正常态'),
  (520000000000005102,0,520000000000000001,520000000000001001,520000000000005101,'WEEKLY','M52-WEEK-202505-03','2025年5月第三周计划',
   '2025-05-12','2025-05-18','APPROVED',NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'日报进度关联周计划'),
  (520000000000005103,0,520000000000000001,520000000000001001,NULL,'WEEKLY','M52-WEEK-REJECTED','被驳回周计划',
   '2025-05-19','2025-05-25','REJECTED',NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：计划被驳回');

INSERT INTO project_period_plan_item
  (id,tenant_id,period_plan_id,wbs_task_id,target_progress,planned_quantity,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000005111,0,520000000000005101,520000000000001002,75,75,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000005112,0,520000000000005102,520000000000001002,60,10,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000005113,0,520000000000005103,520000000000001002,0,0,@demo_user,NOW(),@demo_user,NOW());

INSERT INTO measurement_period
  (id,tenant_id,project_id,contract_id,period_code,period_name,start_date,end_date,cutoff_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000005201,0,520000000000000001,520000000000000701,'M52-MEASURE-202505','2025年5月产值计量期','2025-05-01','2025-05-31','2025-05-31',
   'OPEN',0,@demo_user,NOW(),@demo_user,NOW(),0,'正常开放计量期'),
  (520000000000005202,0,520000000000000001,520000000000000701,'M52-MEASURE-202504','2025年4月已关闭计量期','2025-04-01','2025-04-30','2025-04-30',
   'CLOSED',0,@demo_user,NOW(),@demo_user,NOW(),0,'正常关闭态'),
  (520000000000005203,0,520000000000000001,520000000000000701,'M52-MEASURE-ONE-DAY','单日边界计量期','2025-06-01','2025-06-01','2025-06-01',
   'OPEN',0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：起止同日');

INSERT INTO production_measurement
  (id,tenant_id,project_id,contract_id,period_id,measure_code,measure_date,current_reported_amount,cumulative_reported_amount,status,
   approval_status,approval_instance_id,attachment_count,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000005301,0,520000000000000001,520000000000000701,520000000000005201,'M52-PM-OWNER-SUBMITTED','2025-05-31',500000,1000000,
   'OWNER_SUBMITTED','APPROVED',NULL,1,'PRODUCTION_MEASUREMENT_V1',0,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：内部审批通过并报送业主'),
  (520000000000005302,0,520000000000000001,520000000000000701,520000000000005203,'M52-PM-DRAFT-MIN','2025-06-01',0,0,
   'DRAFT','DRAFT',NULL,0,'PRODUCTION_MEASUREMENT_V1',0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：零单价最小计量草稿'),
  (520000000000005303,0,520000000000000001,520000000000000701,520000000000005202,'M52-PM-OWNER-RETURNED','2025-04-30',100000,500000,
   'OWNER_RETURNED','APPROVED',NULL,1,'PRODUCTION_MEASUREMENT_V1',1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：业主退回补充证据');

INSERT INTO production_measurement_line
  (id,tenant_id,measurement_id,source_type,contract_item_id,contract_change_id,item_code,item_name,item_spec,unit,contract_quantity,
   prior_approved_quantity,current_reported_quantity,cumulative_reported_quantity,unit_price,current_reported_amount,cumulative_reported_amount,
   evidence_count,sort_order,created_by,created_at)
VALUES
  (520000000000005311,0,520000000000005301,'CONTRACT_ITEM',520000000000005001,NULL,'M52-OWNER-ITEM-001','主体结构施工计量项','按施工图及核定工程量','项',
   100,5,5,10,100000,500000,1000000,1,1,@demo_user,NOW()),
  (520000000000005312,0,520000000000005302,'CONTRACT_ITEM',520000000000005002,NULL,'M52-OWNER-ITEM-ZERO','零单价观察项','只验证边界展示','项',
   1,0,1,1,0,0,0,0,1,@demo_user,NOW()),
  (520000000000005313,0,520000000000005303,'CONTRACT_ITEM',520000000000005001,NULL,'M52-OWNER-ITEM-001','主体结构施工计量项','按施工图及核定工程量','项',
   100,4,1,5,100000,100000,500000,1,1,@demo_user,NOW());

INSERT INTO owner_measurement_submission
  (id,tenant_id,project_id,contract_id,measurement_id,submission_code,revision_no,submitted_at,external_document_no,submitted_amount,
   confirmed_amount,deducted_amount,status,reviewer_name,review_comment,reviewed_at,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000005401,0,520000000000000001,520000000000000701,520000000000005301,'M52-OWNER-SUB-001',1,'2025-06-01 09:00:00','OWNER-DOC-2025-05',
   500000,0,0,'SUBMITTED',NULL,NULL,NULL,1,0,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：等待业主核定'),
  (520000000000005402,0,520000000000000001,520000000000000701,520000000000005303,'M52-OWNER-SUB-RETURNED',1,'2025-05-01 09:00:00',NULL,
   100000,0,0,'RETURNED','演示业主代表','现场签证证据不足，请补充影像资料','2025-05-02 10:00:00',1,1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：业主退回');

INSERT INTO owner_measurement_review_line
  (id,tenant_id,submission_id,measurement_line_id,submitted_quantity,submitted_amount,confirmed_quantity,confirmed_amount,deducted_amount,
   deduction_reason,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000005411,0,520000000000005401,520000000000005311,5,500000,NULL,NULL,NULL,NULL,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000005412,0,520000000000005402,520000000000005313,1,100000,0,0,100000,'证据不足暂不核定',@demo_user,NOW(),@demo_user,NOW());

INSERT INTO site_daily_log
  (id,tenant_id,project_id,report_date,construction_content,issues_delays,next_day_plan,weather_summary,on_site_headcount,status,
   submitted_by,submitted_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000005501,0,520000000000000001,'2025-05-15','主楼三层梁板钢筋绑扎、模板复核及混凝土浇筑准备；完成隐蔽验收前自检，现场施工内容使用较长文本验证详情页换行与可读性。',
   '下午短时降雨导致吊装暂停30分钟，未影响关键线路。','完成三层梁板混凝土浇筑并同步质量旁站。','多云转小雨，18—26℃，东南风2级',128,
   'SUBMITTED',@demo_user,'2025-05-15 19:00:00',@demo_user,NOW(),@demo_user,NOW(),0,'正常态：字段完整日报'),
  (520000000000005502,0,520000000000000001,'2025-05-16','现场停工进行安全教育。',NULL,NULL,NULL,0,'DRAFT',NULL,NULL,@demo_user,NOW(),@demo_user,NOW(),0,
   '边界态：可选字段为空且在场人数为0'),
  (520000000000005503,0,520000000000000001,'2025-05-17','主体结构施工按计划推进。','材料到场延迟，预计影响次日工序。','调整作业顺序并跟踪到货。','晴，20—29℃',36,
   'SUBMITTED',@demo_user,'2025-05-17 18:30:00',@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：存在延误问题');

INSERT INTO site_daily_progress
  (id,tenant_id,daily_log_id,project_id,schedule_plan_id,weekly_plan_id,wbs_task_id,previous_progress,current_progress,completed_quantity,
   work_description,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000005511,0,520000000000005501,520000000000000001,520000000000001001,520000000000005102,520000000000001002,55,60,5,
   '主体结构施工累计进度由55%推进至60%',@demo_user,NOW(),@demo_user,NOW()),
  (520000000000005512,0,520000000000005503,520000000000000001,520000000000001001,520000000000005102,520000000000001002,60,60,0,
   '材料延迟导致当日进度保持不变',@demo_user,NOW(),@demo_user,NOW());
