-- CGC-COMPLETE-PROJECT v2 / SUPPLIER SOURCING AND TECHNICAL MANAGEMENT
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO md_partner
  (id,tenant_id,partner_code,partner_name,partner_type,credit_code,legal_person,contact_name,contact_phone,bank_name,bank_account,
   qualification_level,blacklist_flag,risk_level,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,default_lead_days)
VALUES
  (520000000000006001,0,'M52-SUPPLIER-B','演示候选供应商乙','SUPPLIER','M52-SUPPLIER-B-CREDIT','李演示','李采购','13800000001','演示银行','M52-BANK-B',
   '一级',1,'HIGH','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：履约低分后进入黑名单',15),
  (520000000000006002,0,'M52-SUPPLIER-C','演示候选供应商丙','SUPPLIER','M52-SUPPLIER-C-CREDIT','王演示','王采购','13800000002','演示银行','M52-BANK-C',
   '二级',0,'LOW','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'正常候选供应商，含完整资料',0);

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,
   paid_amount,signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000006190,0,520000000000000001,'M52-PURCHASE-B','演示低分供应商历史采购合同','PURCHASE',520000000000000101,520000000000006001,
   10000,10000,10000,'2025-02-01','2025-02-01','2025-04-30','COMPLETED','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,
   '低分供应商履约评价独立合同',10000,0);

INSERT INTO mat_purchase_request
  (id,tenant_id,project_id,contract_id,purpose,request_code,approval_status,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006091,0,520000000000000001,520000000000000702,'临时支撑材料采购','M52-PR-SOURCE-DRAFT','APPROVED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'寻源草稿独立采购申请'),
  (520000000000006092,0,520000000000000001,520000000000000702,'计划取消的零星材料采购','M52-PR-SOURCE-CANCEL','APPROVED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'寻源取消态独立采购申请'),
  (520000000000006095,0,520000000000000001,520000000000006190,'低分供应商历史采购','M52-PR-SUPPLIER-B','APPROVED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'低分供应商履约评价独立来源');

INSERT INTO mat_purchase_request_item
  (id,tenant_id,request_id,material_id,budget_line_id,wbs_task_id,quantity,estimated_unit_price,estimated_amount,unit,planned_date,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006093,0,520000000000006091,520000000000000201,520000000000000802,520000000000001002,1,1000,1000,'吨','2025-12-31',
   @demo_user,NOW(),@demo_user,NOW(),0,'边界态最小数量'),
  (520000000000006094,0,520000000000006092,520000000000000201,520000000000000802,520000000000001002,2,1000,2000,'吨','2025-02-10',
   @demo_user,NOW(),@demo_user,NOW(),0,'取消寻源来源'),
  (520000000000006096,0,520000000000006095,520000000000000201,520000000000000802,520000000000001002,10,1000,10000,'吨','2025-03-10',
   @demo_user,NOW(),@demo_user,NOW(),0,'低分供应商历史订单来源');

INSERT INTO mat_purchase_order
  (id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,order_date,delivery_date,total_amount,
   approval_status,order_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006191,0,520000000000000001,520000000000006095,520000000000006190,520000000000006001,
   'M52-PO-SUPPLIER-B','MATERIAL','2025-02-15','2025-03-10',10000,'APPROVED','COMPLETED',@demo_user,NOW(),@demo_user,NOW(),0,
   '业务异常态：迟交和质量问题历史订单');

INSERT INTO mat_purchase_order_item
  (id,tenant_id,order_id,request_item_id,budget_line_id,project_id,material_id,material_name,specification,unit,quantity,
   unit_price,tax_rate,amount,tax_amount,amount_without_tax,received_quantity,version,wbs_task_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006192,0,520000000000006191,520000000000006096,520000000000000802,520000000000000001,
   520000000000000201,'演示钢筋','HRB400E','吨',10,1000,13,10000,1150.44,8849.56,10,0,520000000000001002,
   @demo_user,NOW(),@demo_user,NOW(),0,'低分供应商订单明细');

INSERT INTO sp_sourcing_event
  (id,tenant_id,project_id,purchase_request_id,sourcing_code,sourcing_title,sourcing_type,deadline,currency_code,status,awarded_quote_id,
   awarded_partner_id,contract_id,award_reason,published_by,published_at,awarded_by,awarded_at,contracted_by,contracted_at,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006101,0,520000000000000001,520000000000001101,'M52-SOURCE-AWARD-001','演示钢筋采购询价','INQUIRY','2025-02-10 18:00:00','CNY','AWARDED',
   NULL,NULL,NULL,NULL,@demo_user,'2025-02-01 09:00:00',@demo_user,'2025-02-11 09:00:00',NULL,NULL,1,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：完成比价并定标'),
  (520000000000006102,0,520000000000000001,520000000000006091,'M52-SOURCE-DRAFT-001','边界态：标题较长的零附件采购招标草稿，用于验证表格省略和详情完整展示','TENDER','2025-12-31 23:59:59','CNY','DRAFT',
   NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：草稿、无邀请供应商'),
  (520000000000006103,0,520000000000000001,520000000000006092,'M52-SOURCE-CANCEL-001','已取消的临时采购询价','INQUIRY','2025-02-05 18:00:00','CNY','CANCELLED',
   NULL,NULL,NULL,'项目计划调整，取消本次寻源',@demo_user,'2025-02-01 10:00:00',NULL,NULL,NULL,NULL,1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：寻源取消');

INSERT INTO sp_sourcing_supplier
  (id,tenant_id,sourcing_event_id,partner_id,invitation_status,invited_at,responded_at,disqualification_reason,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006111,0,520000000000006101,520000000000000102,'QUOTED','2025-02-01 10:00:00','2025-02-05 10:00:00',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'正常报价供应商'),
  (520000000000006112,0,520000000000006101,520000000000006002,'QUOTED','2025-02-01 10:00:00','2025-02-06 10:00:00',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'备选报价供应商'),
  (520000000000006113,0,520000000000006101,520000000000006001,'DISQUALIFIED','2025-02-01 10:00:00','2025-02-04 10:00:00','供应商已进入黑名单',@demo_user,NOW(),@demo_user,NOW(),0,'异常态：资格不通过');

INSERT INTO sp_supplier_quote
  (id,tenant_id,sourcing_event_id,sourcing_supplier_id,partner_id,quote_code,total_amount,tax_rate,delivery_days,validity_date,
   commercial_terms,status,submitted_by,submitted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006121,0,520000000000006101,520000000000006111,520000000000000102,'M52-QUOTE-WIN-001',100000,13,7,'2025-03-31',
   '含税到场价，货到验收后30日付款。','WINNER',@demo_user,'2025-02-05 10:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'正常中标报价'),
  (520000000000006122,0,520000000000006101,520000000000006112,520000000000006002,'M52-QUOTE-LOST-001',102000,13,0,'2025-03-31',
   '边界态：承诺当日交付，付款条件为货到验收后30日。','LOST',@demo_user,'2025-02-06 10:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：交付天数为0');

INSERT INTO sp_bid_evaluation
  (id,tenant_id,sourcing_event_id,quote_id,partner_id,commercial_score,technical_score,delivery_score,quality_score,total_score,
   evaluation_comment,evaluated_by,evaluated_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006131,0,520000000000006101,520000000000006121,520000000000000102,95,90,90,92,92.25,'综合得分最高，价格与质量均满足要求。',@demo_user,'2025-02-10 09:00:00',@demo_user,NOW(),@demo_user,NOW(),0,'正常评标'),
  (520000000000006132,0,520000000000006101,520000000000006122,520000000000006002,88,92,100,90,91.50,'交付得分高，但综合报价略高。',@demo_user,'2025-02-10 09:10:00',@demo_user,NOW(),@demo_user,NOW(),0,'备选评标');

UPDATE sp_sourcing_event
SET awarded_quote_id=520000000000006121,awarded_partner_id=520000000000000102,contract_id=520000000000000702,
    award_reason='综合评分最高且报价最优'
WHERE id=520000000000006101;

INSERT INTO sp_performance_evaluation
  (id,tenant_id,project_id,partner_id,contract_id,purchase_order_id,evaluation_code,period_start,period_end,delivery_score,quality_score,
   service_score,commercial_score,total_score,grade,on_time_flag,approved_receipt_count,unqualified_receipt_count,return_count,
   finalized_settlement_count,quality_safety_fact_count,quality_safety_average,evaluation_comment,recommend_blacklist,status,confirmed_by,
   confirmed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006201,0,520000000000000001,520000000000000102,520000000000000702,520000000000001201,'M52-PERF-A-001','2025-01-01','2025-06-30',
   95,92,90,94,93.05,'A',1,1,0,0,1,1,92,'交付、质量、服务和商业表现稳定。',0,'CONFIRMED',@demo_user,NOW(),1,@demo_user,NOW(),@demo_user,NOW(),0,'正常高分履约'),
  (520000000000006202,0,520000000000000001,520000000000006001,520000000000006190,520000000000006191,'M52-PERF-E-001','2025-01-01','2025-06-30',
   20,30,25,35,27.75,'E',0,0,2,1,0,2,25,'多次迟交并出现质量问题，建议纳入黑名单。',1,'CONFIRMED',@demo_user,NOW(),1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：低分履约');

INSERT INTO sp_blacklist_record
  (id,tenant_id,performance_evaluation_id,partner_id,project_id,action_type,reason,status,submitted_by,submitted_at,reviewed_by,
   reviewed_at,review_comment,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006211,0,520000000000006202,520000000000006001,520000000000000001,'ADD','履约综合评分低于30分且存在重复质量问题。','APPROVED',
   @demo_user,'2025-07-01 09:00:00',@demo_user,'2025-07-01 10:00:00','同意纳入黑名单',1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：黑名单已审批');

INSERT INTO sp_supplier_return
  (id,tenant_id,project_id,partner_id,contract_id,purchase_order_id,receipt_id,warehouse_id,return_code,return_date,return_quantity,
   return_amount,reason,status,idempotency_key,confirmed_by,confirmed_at,reversed_by,reversed_at,reversal_reason,version,created_by,
   created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006301,0,520000000000000001,520000000000000102,520000000000000702,520000000000001201,520000000000001301,
   520000000000000301,'M52-SUP-RETURN-DRAFT','2025-04-05',1,1000,'抽检发现包装破损，拟退回供应商。','DRAFT','M52:SUPPLIER_RETURN:DRAFT:001',
   NULL,NULL,NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：草稿退供，不影响库存');

INSERT INTO sp_supplier_return_item
  (id,tenant_id,return_id,receipt_item_id,order_item_id,quality_disposition_id,original_stock_txn_id,original_cost_item_id,material_id,
   return_source,quantity,unit_cost,amount,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006311,0,520000000000006301,520000000000001302,520000000000001202,NULL,520000000000001601,NULL,520000000000000201,
   'QUALIFIED',1,1000,1000,@demo_user,NOW(),@demo_user,NOW(),0,'合格入库后拟退供，尚未确认');

INSERT INTO technical_scheme
  (id,tenant_id,project_id,scheme_code,scheme_name,scheme_type,responsible_user_id,planned_effective_date,status,approval_instance_id,
   approved_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006401,0,520000000000000001,'M52-TECH-SCHEME-APPROVED','主体结构专项施工方案','SPECIAL',@demo_user,'2025-01-10','APPROVED',NULL,
   '2025-01-09 16:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：已审批专项方案'),
  (520000000000006402,0,520000000000000001,'M52-TECH-SCHEME-DRAFT','临时支撑施工方案草稿','METHOD_STATEMENT',@demo_user,'2025-06-30','DRAFT',NULL,
   NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：未提交草稿'),
  (520000000000006403,0,520000000000000001,'M52-TECH-SCHEME-REJECTED','雨季施工组织方案','CONSTRUCTION_ORGANIZATION',@demo_user,'2025-05-01','REJECTED',NULL,
   NULL,1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：审批驳回待修改');

INSERT INTO tech_drawing
  (id,tenant_id,project_id,drawing_code,drawing_name,specialty,source_organization,current_version_id,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006501,0,520000000000000001,'M52-DRAW-STRUCT-001','主楼三层结构施工图','结构','演示设计院',NULL,'ACTIVE',@demo_user,NOW(),@demo_user,NOW(),0,'正常在用图纸'),
  (520000000000006502,0,520000000000000001,'M52-DRAW-ARCHIVE-001','已归档临建设施图','建筑','演示设计院',NULL,'ARCHIVED',@demo_user,NOW(),@demo_user,NOW(),0,'边界态：归档图纸');

INSERT INTO tech_drawing_version
  (id,tenant_id,project_id,drawing_id,version_no,previous_version_id,source_rfi_id,received_at,received_by,change_summary,status,approved_at,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006511,0,520000000000000001,520000000000006501,'A',NULL,NULL,'2025-01-05 09:00:00',@demo_user,'首次接收正式施工图','APPROVED','2025-01-08 16:00:00',
   1,@demo_user,NOW(),@demo_user,NOW(),0,'正常已批准版本'),
  (520000000000006512,0,520000000000000001,520000000000006501,'B',520000000000006511,NULL,'2025-05-10 09:00:00',@demo_user,'梁柱节点标高调整，等待RFI闭环','RFI_PENDING',NULL,
   0,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：RFI处理中'),
  (520000000000006513,0,520000000000000001,520000000000006502,'A',NULL,NULL,'2024-12-01 09:00:00',@demo_user,NULL,'ARCHIVED','2024-12-05 16:00:00',
   1,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：无变更摘要的归档版本');

UPDATE tech_drawing SET current_version_id=520000000000006512 WHERE id=520000000000006501;
UPDATE tech_drawing SET current_version_id=520000000000006513 WHERE id=520000000000006502;

INSERT INTO tech_drawing_review
  (id,tenant_id,project_id,drawing_version_id,review_code,review_date,chair_user_id,participant_summary,conclusion,review_summary,
   requires_rfi,status,confirmed_by,confirmed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006521,0,520000000000000001,520000000000006511,'M52-DRAW-REVIEW-PASS','2025-01-07',@demo_user,'项目经理、技术负责人、施工员、质量员',
   'PASS','图纸满足施工要求，无需澄清。',0,'CONFIRMED',@demo_user,'2025-01-07 16:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'正常会审通过'),
  (520000000000006522,0,520000000000000001,520000000000006512,'M52-DRAW-REVIEW-RFI','2025-05-11',@demo_user,'项目经理、技术负责人、设计代表、监理代表',
   'CONDITIONAL','梁柱节点标高存在歧义，须取得设计澄清后施工。',1,'CONFIRMED',@demo_user,'2025-05-11 16:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：有条件通过并触发RFI');

INSERT INTO tech_rfi
  (id,tenant_id,project_id,drawing_version_id,review_id,rfi_code,subject,question,priority,raised_by,raised_at,response_due_date,status,
   closed_by,closed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006531,0,520000000000000001,520000000000006512,520000000000006522,'M52-RFI-URGENT-001','梁柱节点标高澄清',
   '图纸B版节点详图与平面标高不一致，请确认最终施工标高及是否需要设计变更。','URGENT',@demo_user,'2025-05-11 17:00:00','2025-05-13','RESPONDED',
   NULL,NULL,1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：紧急RFI已回复待复核'),
  (520000000000006532,0,520000000000000001,520000000000006511,520000000000006521,'M52-RFI-CANCEL-001','已取消的一般澄清',
   '边界态问题，后经现场核实无需设计回复。','NORMAL',@demo_user,'2025-01-07 17:00:00','2025-01-20','CANCELLED',
   @demo_user,'2025-01-08 09:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：RFI取消');

INSERT INTO tech_rfi_response
  (id,tenant_id,rfi_id,response_no,response_content,change_required,responder_name,responded_by,responded_at,status,reviewed_by,
   reviewed_at,review_comment,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000006541,0,520000000000006531,1,'以节点详图标高为准，并补发设计变更通知单。',1,'演示设计代表',@demo_user,'2025-05-12 15:00:00','SUBMITTED',
   NULL,NULL,NULL,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000006542,0,520000000000006531,2,'补充说明未明确受影响楼层范围。',1,'演示设计代表',@demo_user,'2025-05-13 09:00:00','REJECTED',
   @demo_user,'2025-05-13 10:00:00','请补充楼层范围后重新回复',@demo_user,NOW(),@demo_user,NOW());

INSERT INTO tech_disclosure
  (id,tenant_id,project_id,drawing_version_id,scheme_id,disclosure_code,disclosure_title,disclosure_date,presenter_user_id,
   recipient_summary,disclosure_content,status,confirmed_by,confirmed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006551,0,520000000000000001,520000000000006511,520000000000006401,'M52-DISCLOSURE-001','主体结构施工技术交底','2025-01-10',@demo_user,
   '钢筋班组、木工班组、混凝土班组共36人','说明结构图纸版本、关键节点、质量控制点、安全注意事项和验收要求。','CONFIRMED',@demo_user,'2025-01-10 17:00:00',
   1,@demo_user,NOW(),@demo_user,NOW(),0,'正常确认交底'),
  (520000000000006552,0,520000000000000001,520000000000006512,NULL,'M52-DISCLOSURE-VOID','作废的B版图纸交底','2025-05-12',@demo_user,
   '钢筋班组','因RFI未闭环，本交底作废。','VOID',@demo_user,'2025-05-12 17:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'异常态：交底作废');

INSERT INTO tech_construction_reference
  (id,tenant_id,project_id,drawing_version_id,disclosure_id,daily_log_id,wbs_task_id,reference_date,work_area,reference_description,
   status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006561,0,520000000000000001,520000000000006511,520000000000006551,520000000000005501,520000000000001002,
   '2025-05-15','主楼三层','日报施工事实引用A版结构图及已确认技术交底。','RECORDED',@demo_user,NOW(),@demo_user,NOW(),0,'正常施工引用'),
  (520000000000006562,0,520000000000000001,520000000000006512,520000000000006552,520000000000005503,520000000000001002,
   '2025-05-17','主楼三层','B版RFI未闭环，该施工引用已作废。','VOID',@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：引用作废');

INSERT INTO tech_acceptance_archive
  (id,tenant_id,project_id,drawing_version_id,construction_reference_id,quality_inspection_id,archive_code,acceptance_date,
   acceptance_conclusion,archive_location,status,archived_by,archived_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000006571,0,520000000000000001,520000000000006511,520000000000006561,520000000000003102,'M52-TECH-ARCHIVE-001','2025-05-25',
   'PASS','项目资料室/技术档案/主体结构/A版','ARCHIVED',@demo_user,'2025-05-26 09:00:00',@demo_user,NOW(),@demo_user,NOW(),0,'正常验收归档'),
  (520000000000006572,0,520000000000000001,520000000000006512,520000000000006562,520000000000003102,'M52-TECH-ARCHIVE-DRAFT','2025-05-26',
   'CONDITIONAL_PASS','待RFI闭环后确定归档位置','DRAFT',NULL,NULL,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：有条件通过但尚未归档');

INSERT INTO tech_item
  (id,tenant_id,project_id,item_type,item_code,item_title,item_level,item_status,discovered_at,due_date,closed_at,responsible_user_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark,source_type,source_id)
VALUES
  (520000000000006581,0,520000000000000001,'RFI','M52-TECH-ITEM-OPEN','梁柱节点标高RFI待复核','HIGH','OPEN','2025-05-11 17:00:00','2025-05-13 18:00:00',NULL,@demo_user,
   @demo_user,NOW(),@demo_user,NOW(),0,'技术待办正常开放态','TECH_RFI',520000000000006531),
  (520000000000006582,0,520000000000000001,'DRAWING','M52-TECH-ITEM-CLOSED','A版结构图会审完成','NORMAL','CLOSED','2025-01-05 09:00:00','2025-01-10 18:00:00','2025-01-07 16:00:00',@demo_user,
   @demo_user,NOW(),@demo_user,NOW(),0,'正常关闭态','DRAWING_REVIEW',520000000000006521),
  (520000000000006583,0,520000000000000001,'SCHEME','M52-TECH-ITEM-OVERDUE','雨季方案审批驳回待修改','URGENT','OVERDUE','2025-04-20 09:00:00','2025-05-01 18:00:00',NULL,@demo_user,
   @demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：逾期未关闭','TECHNICAL_SCHEME',520000000000006403);
