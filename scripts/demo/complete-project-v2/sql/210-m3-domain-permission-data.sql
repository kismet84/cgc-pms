-- CGC-COMPLETE-PROJECT v2 / DEV-ONLY M3 SPLIT-PERMISSION AND STAGE DATA
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_org := (SELECT org_id FROM sys_user WHERE id=@demo_admin LIMIT 1);
SET @demo_password_hash := (SELECT password FROM sys_user WHERE id=@demo_admin LIMIT 1);
SET @demo_project := 520000000000000001;

-- ISSUE-053-016: stable schedule query-only identity for the M3 delivery live gate.
INSERT INTO sys_role
  (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
VALUES
  (520000000000013001,0,'M3_SCHEDULE_QUERY','M3计划只读','BUSINESS','ENABLE','SELF',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3计划分权验收',3)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),status='ENABLE',data_scope='SELF',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000013002,0,'demo.schedule.query',@demo_password_hash,'计划只读验收',NULL,'demo.schedule.query@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3计划分权验收')
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id) VALUES
  (520000000000013003,0,520000000000013002,520000000000013001)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id) VALUES
  (520000000000013004,0,520000000000013001,803),
  (520000000000013005,0,520000000000013001,1085);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000013006,0,520000000000009002,520000000000013002,'SCHEDULE_VIEWER','计划只读',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3计划分权验收：复用在建项目')
ON DUPLICATE KEY UPDATE project_id=VALUES(project_id),user_id=VALUES(user_id),role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_role
  (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
VALUES
  (520000000000010001,0,'M3_QS_QUERY','M3质量查询','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3),
  (520000000000010002,0,'M3_QS_PLAN','M3质量计划维护','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3),
  (520000000000010003,0,'M3_QS_INSPECTION','M3质量检查维护','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3),
  (520000000000010004,0,'M3_QS_RECTIFY','M3质量整改','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3),
  (520000000000010005,0,'M3_QS_REINSPECT','M3质量复检','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3),
  (520000000000010006,0,'M3_QS_CONSEQUENCE','M3质量后果','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收',3)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),status='ENABLE',data_scope='ALL',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010011,0,'demo.qs.query',@demo_password_hash,'质量查询验收',NULL,'demo.qs.query@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010012,0,'demo.qs.plan',@demo_password_hash,'质量计划验收',NULL,'demo.qs.plan@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010013,0,'demo.qs.inspection',@demo_password_hash,'质量检查验收',NULL,'demo.qs.inspection@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010014,0,'demo.qs.rectify',@demo_password_hash,'质量整改验收',NULL,'demo.qs.rectify@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010015,0,'demo.qs.reinspect',@demo_password_hash,'质量复检验收',NULL,'demo.qs.reinspect@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010016,0,'demo.qs.consequence',@demo_password_hash,'质量后果验收',NULL,'demo.qs.consequence@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收')
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id) VALUES
  (520000000000010021,0,520000000000010011,520000000000010001),
  (520000000000010022,0,520000000000010012,520000000000010002),
  (520000000000010023,0,520000000000010013,520000000000010003),
  (520000000000010024,0,520000000000010014,520000000000010004),
  (520000000000010025,0,520000000000010015,520000000000010005),
  (520000000000010026,0,520000000000010016,520000000000010006)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id) VALUES
  (520000000000010031,0,520000000000010001,1098),
  (520000000000010032,0,520000000000010002,1098),(520000000000010033,0,520000000000010002,1099),
  (520000000000010034,0,520000000000010003,1098),(520000000000010035,0,520000000000010003,1100),
  (520000000000010036,0,520000000000010004,1098),(520000000000010037,0,520000000000010004,1101),
  (520000000000010038,0,520000000000010005,1098),(520000000000010039,0,520000000000010005,1102),
  (520000000000010040,0,520000000000010006,1098),(520000000000010041,0,520000000000010006,1103);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010051,0,@demo_project,520000000000010011,'QUALITY_VIEWER','质量查询',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010052,0,@demo_project,520000000000010012,'QUALITY_PLAN_OWNER','质量计划',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010053,0,@demo_project,520000000000010013,'QUALITY_INSPECTOR','质量检查',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010054,0,@demo_project,520000000000010014,'QUALITY_RECTIFIER','质量整改',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010055,0,@demo_project,520000000000010015,'QUALITY_REINSPECTOR','质量复检',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收'),
  (520000000000010056,0,@demo_project,520000000000010016,'QUALITY_CONSEQUENCE','质量后果',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量分权验收')
ON DUPLICATE KEY UPDATE role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO qs_inspection_plan
  (id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,start_date,end_date,owner_user_id,status,activated_by,activated_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010101,0,@demo_project,'M53-QS-PLAN-ACTIVE','M3质量阶段验收计划','QUALITY','WEEKLY','2026-07-01','2026-12-31',520000000000010012,'ACTIVE',@demo_admin,NOW(),0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收')
ON DUPLICATE KEY UPDATE status='ACTIVE',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO qs_inspection_record
  (id,tenant_id,plan_id,project_id,inspection_code,inspection_date,location,inspector_user_id,conclusion,summary,status,submitted_by,submitted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010111,0,520000000000010101,@demo_project,'M53-QS-INSP-DRAFT','2026-07-20','主楼A区',520000000000010013,'PENDING','待提交检查','DRAFT',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010112,0,520000000000010101,@demo_project,'M53-QS-INSP-SUBMITTED','2026-07-19','主楼B区',520000000000010013,'ISSUES','含待复检问题','SUBMITTED',520000000000010013,NOW(),0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收')
ON DUPLICATE KEY UPDATE conclusion=VALUES(conclusion),summary=VALUES(summary),status=VALUES(status),deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO qs_issue
  (id,tenant_id,plan_id,inspection_id,project_id,issue_code,issue_type,category,severity,title,description,responsible_kind,responsible_partner_id,responsible_user_id,due_date,status,closed_by,closed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010121,0,520000000000010101,520000000000010112,@demo_project,'M53-QS-ISSUE-PENDING','QUALITY','混凝土','HIGH','蜂窝麻面待复检','修补完成，等待独立复检','INTERNAL',NULL,520000000000010014,'2026-07-31','PENDING_REINSPECTION',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010122,0,520000000000010101,520000000000010112,@demo_project,'M53-QS-ISSUE-CONSEQUENCE','QUALITY','材料','MEDIUM','材料复验问题已关闭','用于后果登记正向验收','PARTNER',520000000000000102,520000000000010014,'2026-07-31','CLOSED',520000000000010015,NOW(),0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010123,0,520000000000010101,520000000000010112,@demo_project,'M53-QS-ISSUE-RECTIFY','QUALITY','实体质量','HIGH','墙柱垂直度待整改','用于整改角色正向验收','INTERNAL',NULL,520000000000010014,'2026-08-05','RECTIFYING',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收')
ON DUPLICATE KEY UPDATE status=VALUES(status),closed_by=VALUES(closed_by),closed_at=VALUES(closed_at),deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO qs_rectification
  (id,tenant_id,issue_id,project_id,round_no,action_description,responsible_user_id,planned_complete_date,actual_completed_at,status,submitted_by,submitted_at,reinspection_comment,reinspected_by,reinspected_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010131,0,520000000000010121,@demo_project,1,'首轮修补强度不足',520000000000010014,'2026-07-25','2026-07-24 16:00:00','REJECTED',520000000000010014,'2026-07-24 16:00:00','复检不通过',520000000000010015,'2026-07-25 09:00:00',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010132,0,520000000000010121,@demo_project,2,'重新凿毛并采用修补料处理',520000000000010014,'2026-07-30','2026-07-29 16:00:00','SUBMITTED',520000000000010014,'2026-07-29 16:00:00',NULL,NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收')
ON DUPLICATE KEY UPDATE status=VALUES(status),submitted_by=VALUES(submitted_by),submitted_at=VALUES(submitted_at),reinspection_comment=VALUES(reinspection_comment),reinspected_by=VALUES(reinspected_by),reinspected_at=VALUES(reinspected_at),deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO sys_file
  (id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000010141,0,'QS_INSPECTION','INSPECTION_EVIDENCE',520000000000010111,'m53-inspection.pdf','M3检查证据.pdf',128,'application/pdf','QS_INSPECTION/520000000000010111/m53-inspection.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010142,0,'QS_ISSUE','ISSUE_EVIDENCE',520000000000010121,'m53-issue.pdf','M3问题证据.pdf',128,'application/pdf','QS_ISSUE/520000000000010121/m53-issue.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010143,0,'QS_RECTIFICATION','RECTIFICATION_EVIDENCE',520000000000010132,'m53-rectification.pdf','M3整改证据.pdf',128,'application/pdf','QS_RECTIFICATION/520000000000010132/m53-rectification.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收'),
  (520000000000010144,0,'QS_RECTIFICATION','REINSPECTION_EVIDENCE',520000000000010132,'m53-reinspection.pdf','M3复检证据.pdf',128,'application/pdf','QS_RECTIFICATION/520000000000010132/m53-reinspection.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质量阶段验收')
ON DUPLICATE KEY UPDATE document_type=VALUES(document_type),business_id=VALUES(business_id),virus_scan_status='CLEAN',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

-- The original control-closure stage is intentionally one-shot. Restore corrected UTF-8
-- schedule text from this idempotent M3 stage so existing demo databases do not retain
-- rows imported by an older, incorrectly decoded package.
UPDATE project_corrective_action
SET reason='关键线路材料到场延迟。',
    action_plan='调整资源投入并按周复核关键线路。',
    updated_by=@demo_admin,
    updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000008166 AND deleted_flag=0;

-- ISSUE-053-015: closeout split permissions, legal closed-chain facts and writable stages.
INSERT INTO sys_role
  (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
VALUES
  (520000000000012001,0,'M3_CLOSEOUT_QUERY','M3收尾查询','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012002,0,'M3_CLOSEOUT_INITIATE','M3发起收尾','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012003,0,'M3_CLOSEOUT_SECTION','M3分部验收','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012004,0,'M3_CLOSEOUT_ACCEPTANCE','M3竣工验收','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012005,0,'M3_CLOSEOUT_SETTLEMENT','M3最终结算','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012006,0,'M3_CLOSEOUT_COLLECTION','M3尾款核验','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012007,0,'M3_CLOSEOUT_WARRANTY','M3质保维护','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012008,0,'M3_CLOSEOUT_DEFECT','M3缺陷维护','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012009,0,'M3_CLOSEOUT_DEFECT_VERIFY','M3缺陷复验','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012010,0,'M3_CLOSEOUT_ARCHIVE','M3档案移交','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3),
  (520000000000012011,0,'M3_CLOSEOUT_CLOSE','M3项目关闭','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收',3)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),status='ENABLE',data_scope='ALL',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012021,0,'demo.closeout.query',@demo_password_hash,'收尾查询验收',NULL,'demo.closeout.query@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012022,0,'demo.closeout.initiate',@demo_password_hash,'收尾发起验收',NULL,'demo.closeout.initiate@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012023,0,'demo.closeout.section',@demo_password_hash,'收尾分部验收',NULL,'demo.closeout.section@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012024,0,'demo.closeout.acceptance',@demo_password_hash,'收尾竣工验收',NULL,'demo.closeout.acceptance@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012025,0,'demo.closeout.settlement',@demo_password_hash,'收尾结算验收',NULL,'demo.closeout.settlement@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012026,0,'demo.closeout.collection',@demo_password_hash,'收尾尾款验收',NULL,'demo.closeout.collection@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012027,0,'demo.closeout.warranty',@demo_password_hash,'收尾质保验收',NULL,'demo.closeout.warranty@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012028,0,'demo.closeout.defect',@demo_password_hash,'收尾缺陷验收',NULL,'demo.closeout.defect@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012029,0,'demo.closeout.defect-verify',@demo_password_hash,'收尾缺陷复验',NULL,'demo.closeout.defect-verify@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012030,0,'demo.closeout.archive',@demo_password_hash,'收尾档案验收',NULL,'demo.closeout.archive@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012031,0,'demo.closeout.close',@demo_password_hash,'收尾关闭验收',NULL,'demo.closeout.close@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收')
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id) VALUES
  (520000000000012041,0,520000000000012021,520000000000012001),(520000000000012042,0,520000000000012022,520000000000012002),
  (520000000000012043,0,520000000000012023,520000000000012003),(520000000000012044,0,520000000000012024,520000000000012004),
  (520000000000012045,0,520000000000012025,520000000000012005),(520000000000012046,0,520000000000012026,520000000000012006),
  (520000000000012047,0,520000000000012027,520000000000012007),(520000000000012048,0,520000000000012028,520000000000012008),
  (520000000000012049,0,520000000000012029,520000000000012009),(520000000000012050,0,520000000000012030,520000000000012010),
  (520000000000012051,0,520000000000012031,520000000000012011)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id) VALUES
  (520000000000012061,0,520000000000012001,1121),
  (520000000000012062,0,520000000000012002,1121),(520000000000012063,0,520000000000012002,1122),
  (520000000000012064,0,520000000000012003,1121),(520000000000012065,0,520000000000012003,1123),
  (520000000000012066,0,520000000000012004,1121),(520000000000012067,0,520000000000012004,1124),
  (520000000000012068,0,520000000000012005,1121),(520000000000012069,0,520000000000012005,1125),
  (520000000000012070,0,520000000000012006,1121),(520000000000012071,0,520000000000012006,1126),
  (520000000000012072,0,520000000000012007,1121),(520000000000012073,0,520000000000012007,1127),
  (520000000000012074,0,520000000000012008,1121),(520000000000012075,0,520000000000012008,1128),
  (520000000000012076,0,520000000000012009,1121),(520000000000012077,0,520000000000012009,1129),
  (520000000000012078,0,520000000000012010,1121),(520000000000012079,0,520000000000012010,1130),
  (520000000000012080,0,520000000000012011,1121),(520000000000012081,0,520000000000012011,1131);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012101,0,@demo_project,520000000000012021,'CLOSEOUT_VIEWER','收尾查询',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012102,0,@demo_project,520000000000012022,'CLOSEOUT_INITIATOR','收尾发起',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012103,0,@demo_project,520000000000012023,'CLOSEOUT_SECTION','分部验收',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012104,0,@demo_project,520000000000012024,'CLOSEOUT_ACCEPTANCE','竣工验收',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012105,0,@demo_project,520000000000012025,'CLOSEOUT_SETTLEMENT','最终结算',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012106,0,@demo_project,520000000000012026,'CLOSEOUT_COLLECTION','尾款核验',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012107,0,@demo_project,520000000000012027,'CLOSEOUT_WARRANTY','质保维护',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012108,0,@demo_project,520000000000012028,'CLOSEOUT_DEFECT','缺陷维护',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012109,0,@demo_project,520000000000012029,'CLOSEOUT_DEFECT_VERIFY','缺陷复验',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012110,0,@demo_project,520000000000012030,'CLOSEOUT_ARCHIVE','档案移交',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收'),
  (520000000000012111,0,@demo_project,520000000000012031,'CLOSEOUT_CLOSER','项目关闭',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾分权验收')
ON DUPLICATE KEY UPDATE role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO owner_settlement
  (id,tenant_id,project_id,contract_id,revenue_id,settlement_code,settlement_period,settlement_date,gross_amount,tax_amount,
   retention_amount,net_receivable_amount,due_date,customer_id,status,attachment_count,formula_version,version,
   reported_amount,deducted_amount,settlement_type,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012201,0,@demo_project,520000000000000701,520000000000002601,'M53-CLOSEOUT-STL-LEGAL','FINAL','2026-06-30',
   2000000,0,100000,1900000,'2026-07-15',520000000000000101,'RECEIVABLE_CREATED',1,'OWNER_SETTLEMENT_V1',0,
   2000000,0,'FINAL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3合法收尾最终结算')
ON DUPLICATE KEY UPDATE project_id=VALUES(project_id),contract_id=VALUES(contract_id),settlement_code=VALUES(settlement_code),
  settlement_period=VALUES(settlement_period),settlement_date=VALUES(settlement_date),gross_amount=VALUES(gross_amount),
  tax_amount=VALUES(tax_amount),retention_amount=VALUES(retention_amount),net_receivable_amount=VALUES(net_receivable_amount),
  due_date=VALUES(due_date),customer_id=VALUES(customer_id),status=VALUES(status),reported_amount=VALUES(reported_amount),
  deducted_amount=VALUES(deducted_amount),settlement_type=VALUES(settlement_type),deleted_flag=0,
  updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO account_receivable
  (id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,receivable_code,original_amount,
   collected_amount,credited_amount,outstanding_amount,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012202,0,@demo_project,520000000000000701,520000000000012201,520000000000000101,'REGULAR','M53-CLOSEOUT-AR-REGULAR',1900000,1900000,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3尾款已收'),
  (520000000000012203,0,@demo_project,520000000000000701,520000000000012201,520000000000000101,'RETENTION','M53-CLOSEOUT-AR-RETENTION',100000,100000,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质保金已收')
ON DUPLICATE KEY UPDATE collected_amount=VALUES(collected_amount),outstanding_amount=0,status='COLLECTED',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO collection_record
  (id,tenant_id,project_id,contract_id,customer_id,fund_account_id,collection_code,external_txn_no,collected_at,amount,
   allocated_amount,unallocated_amount,payer_name,status,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012204,0,@demo_project,520000000000000701,520000000000000101,520000000000002301,'M53-CLOSEOUT-COL-REGULAR','M53-CLOSEOUT-TXN-REGULAR',NOW(),1900000,1900000,0,'演示建设单位','SUCCESS',1,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3尾款回收'),
  (520000000000012205,0,@demo_project,520000000000000701,520000000000000101,520000000000002301,'M53-CLOSEOUT-COL-RETENTION','M53-CLOSEOUT-TXN-RETENTION',NOW(),100000,100000,0,'演示建设单位','SUCCESS',1,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质保金回收')
ON DUPLICATE KEY UPDATE amount=VALUES(amount),allocated_amount=VALUES(allocated_amount),unallocated_amount=0,status='SUCCESS',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO collection_allocation (id,tenant_id,collection_id,receivable_id,allocated_amount,allocation_type,created_by,created_at) VALUES
  (520000000000012206,0,520000000000012204,520000000000012202,1900000,'COLLECTION',@demo_admin,NOW()),
  (520000000000012207,0,520000000000012205,520000000000012203,100000,'COLLECTION',@demo_admin,NOW())
ON DUPLICATE KEY UPDATE allocated_amount=VALUES(allocated_amount);

UPDATE project_closeout SET final_owner_settlement_id=520000000000012201,tail_collection_verified_at=COALESCE(tail_collection_verified_at,NOW()),
  status='CLOSED',actual_completion_date=COALESCE(actual_completion_date,'2026-06-30'),closed_by=@demo_admin,closed_at=COALESCE(closed_at,NOW()),
  updated_by=@demo_admin,updated_at=NOW(),deleted_flag=0 WHERE tenant_id=0 AND id=520000000000003501;
UPDATE closeout_warranty SET receivable_id=520000000000012203,warranty_amount=100000,responsible_user_id=@demo_admin,status='RELEASED',
  released_by=@demo_admin,released_at=COALESCE(released_at,NOW()),updated_by=@demo_admin,updated_at=NOW(),deleted_flag=0
  WHERE tenant_id=0 AND id=520000000000007421;

-- ISSUE-053-016: browser-positive samples for defect maintenance, defect verification and project close.
INSERT INTO pm_project
  (id,tenant_id,org_id,project_code,project_name,project_type,project_address,owner_unit,supervisor_unit,design_unit,
   contract_amount,target_cost,planned_start_date,planned_end_date,actual_start_date,actual_end_date,project_manager_id,status,approval_status,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009008,0,@demo_org,'XM-20260722-901','收尾缺陷登记演示项目','CONSTRUCTION','演示地址8号','演示建设单位','演示监理','演示设计',100000,80000,'2025-01-01','2026-06-30','2025-01-01',NULL,@demo_admin,'ACTIVE','APPROVED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记正向阶段'),
  (520000000000009010,0,@demo_org,'XM-20260722-903','收尾关闭演示项目','CONSTRUCTION','演示地址10号','演示建设单位','演示监理','演示设计',100000,80000,'2025-01-01','2026-06-30','2025-01-01',NULL,@demo_admin,'ACTIVE','APPROVED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3项目关闭正向阶段')
ON DUPLICATE KEY UPDATE project_code=VALUES(project_code),project_name=VALUES(project_name),status='ACTIVE',approval_status='APPROVED',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012501,0,520000000000009008,@demo_admin,'PROJECT_MANAGER','项目经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记责任人'),
  (520000000000012503,0,520000000000009010,@demo_admin,'PROJECT_MANAGER','项目经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3项目关闭责任人')
ON DUPLICATE KEY UPDATE status='ACTIVE',end_date=NULL,updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,
   paid_amount,signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000012504,0,520000000000009010,'CT-20260722-901','关闭阶段已结清合同','MAIN',520000000000000101,520000000000000102,100000,100000,100000,'2025-01-01','2025-01-01','2026-06-30','SETTLED','APPROVED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3关闭阶段合法合同',100000,0),
  (520000000000012507,0,520000000000009008,'CT-20260722-902','缺陷处理阶段已结清合同','MAIN',520000000000000101,520000000000000102,100000,100000,100000,'2025-01-01','2025-01-01','2026-06-30','SETTLED','APPROVED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记合同',100000,0),
  (520000000000012508,0,520000000000009008,'CT-20260722-903','缺陷复验阶段已结清合同','MAIN',520000000000000101,520000000000000102,100000,100000,100000,'2025-01-01','2025-01-01','2026-06-30','SETTLED','APPROVED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷复验合同：复用缺陷处理项目',100000,0)
ON DUPLICATE KEY UPDATE contract_code=VALUES(contract_code),contract_status='SETTLED',approval_status='APPROVED',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO owner_settlement
  (id,tenant_id,project_id,contract_id,revenue_id,settlement_code,settlement_period,settlement_date,gross_amount,tax_amount,
   retention_amount,net_receivable_amount,due_date,customer_id,status,attachment_count,formula_version,version,
   reported_amount,deducted_amount,settlement_type,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012505,0,520000000000009010,520000000000012504,NULL,'M53-CLOSE-READY-STL','FINAL','2026-06-30',100000,0,1000,99000,'2026-07-15',520000000000000101,'RECEIVABLE_CREATED',1,'OWNER_SETTLEMENT_V1',0,100000,0,'FINAL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3关闭阶段最终结算'),
  (520000000000012509,0,520000000000009008,520000000000012507,NULL,'M53-DEFECT-STL','FINAL','2026-06-30',100000,0,500,99500,'2026-07-15',520000000000000101,'RECEIVABLE_CREATED',1,'OWNER_SETTLEMENT_V1',0,100000,0,'FINAL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记最终结算'),
  (520000000000012510,0,520000000000009008,520000000000012508,NULL,'M53-VERIFY-STL','FINAL','2026-06-30',100000,0,500,99500,'2026-07-15',520000000000000101,'RECEIVABLE_CREATED',1,'OWNER_SETTLEMENT_V1',0,100000,0,'FINAL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷复验最终结算：复用缺陷处理项目')
ON DUPLICATE KEY UPDATE project_id=VALUES(project_id),contract_id=VALUES(contract_id),settlement_code=VALUES(settlement_code),
  settlement_period=VALUES(settlement_period),settlement_date=VALUES(settlement_date),gross_amount=VALUES(gross_amount),
  tax_amount=VALUES(tax_amount),retention_amount=VALUES(retention_amount),net_receivable_amount=VALUES(net_receivable_amount),
  due_date=VALUES(due_date),customer_id=VALUES(customer_id),status=VALUES(status),reported_amount=VALUES(reported_amount),
  deducted_amount=VALUES(deducted_amount),settlement_type=VALUES(settlement_type),deleted_flag=0,
  updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO account_receivable
  (id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,receivable_code,original_amount,
   collected_amount,credited_amount,outstanding_amount,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012506,0,520000000000009010,520000000000012504,520000000000012505,520000000000000101,'REGULAR','M53-CLOSE-READY-AR',99000,99000,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3关闭阶段常规应收已清'),
  (520000000000012514,0,520000000000009008,520000000000012507,520000000000012509,520000000000000101,'RETENTION','M53-DEFECT-AR',500,500,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记质保金已收'),
  (520000000000012515,0,520000000000009008,520000000000012508,520000000000012510,520000000000000101,'RETENTION','M53-VERIFY-AR',500,500,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷复验质保金已收'),
  (520000000000012516,0,520000000000009010,520000000000012504,520000000000012505,520000000000000101,'RETENTION','M53-CLOSE-READY-RETENTION',1000,1000,0,0,'2026-07-15','COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3关闭阶段质保金已收')
ON DUPLICATE KEY UPDATE project_id=VALUES(project_id),contract_id=VALUES(contract_id),settlement_id=VALUES(settlement_id),
  customer_id=VALUES(customer_id),receivable_type=VALUES(receivable_type),receivable_code=VALUES(receivable_code),
  original_amount=VALUES(original_amount),collected_amount=VALUES(collected_amount),credited_amount=VALUES(credited_amount),
  outstanding_amount=VALUES(outstanding_amount),due_date=VALUES(due_date),status=VALUES(status),deleted_flag=0,
  updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO project_closeout
  (id,tenant_id,project_id,closeout_code,planned_completion_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012301,0,520000000000009002,'M53-CLOSEOUT-INITIATED','2027-06-30','INITIATED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3分部验收可写阶段'),
  (520000000000012302,0,520000000000009001,'M53-CLOSEOUT-SECTION','2027-06-30','SECTION_ACCEPTANCE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3竣工验收可写阶段'),
  (520000000000012303,0,520000000000009004,'M53-CLOSEOUT-FINAL','2027-06-30','FINAL_ACCEPTANCE_APPROVED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3结算绑定可写阶段'),
  (520000000000012304,0,520000000000009005,'M53-CLOSEOUT-SETTLEMENT','2027-06-30','FINAL_SETTLEMENT_BOUND',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3尾款核验可写阶段'),
  (520000000000012305,0,520000000000009006,'M53-CLOSEOUT-TAIL','2027-06-30','TAIL_PAYMENT_COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3质保登记可写阶段'),
  (520000000000012306,0,520000000000009007,'M53-CLOSEOUT-WARRANTY','2027-06-30','WARRANTY_RELEASED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3档案移交可写阶段'),
  (520000000000012307,0,520000000000009008,'M53-CLOSEOUT-DEFECT','2026-06-30','TAIL_PAYMENT_COLLECTED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记与复验共用阶段'),
  (520000000000012309,0,520000000000009010,'M53-CLOSEOUT-CLOSE','2026-06-30','READY_TO_CLOSE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3项目关闭可写阶段')
ON DUPLICATE KEY UPDATE status=VALUES(status),deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

UPDATE project_closeout SET final_owner_settlement_id=520000000000012509,tail_collection_verified_at=COALESCE(tail_collection_verified_at,NOW()),
  updated_by=@demo_admin,updated_at=NOW() WHERE id=520000000000012307 AND tenant_id=0;
UPDATE project_closeout SET final_owner_settlement_id=520000000000012505,tail_collection_verified_at=COALESCE(tail_collection_verified_at,NOW()),
  updated_by=@demo_admin,updated_at=NOW() WHERE id=520000000000012309 AND tenant_id=0;

INSERT INTO closeout_warranty
  (id,tenant_id,closeout_id,project_id,contract_id,receivable_id,warranty_code,warranty_amount,warranty_start_date,warranty_end_date,
   responsible_user_id,status,released_by,released_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012511,0,520000000000012307,520000000000009008,520000000000012507,520000000000012514,'M53-WARRANTY-DEFECT',500,'2026-06-01','2027-06-01',@demo_admin,'ACTIVE',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷登记正向样本'),
  (520000000000012512,0,520000000000012307,520000000000009008,520000000000012508,520000000000012515,'M53-WARRANTY-VERIFY',500,'2026-06-01','2027-06-01',@demo_admin,'DEFECT_LIABILITY',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷复验正向样本'),
  (520000000000012513,0,520000000000012309,520000000000009010,520000000000012504,520000000000012516,'M53-WARRANTY-CLOSE',1000,'2026-06-01','2026-06-30',@demo_admin,'RELEASED',@demo_admin,NOW(),1,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3项目关闭合法质保样本')
ON DUPLICATE KEY UPDATE closeout_id=VALUES(closeout_id),project_id=VALUES(project_id),contract_id=VALUES(contract_id),
  receivable_id=VALUES(receivable_id),warranty_code=VALUES(warranty_code),warranty_amount=VALUES(warranty_amount),
  warranty_start_date=VALUES(warranty_start_date),warranty_end_date=VALUES(warranty_end_date),status=VALUES(status),
  responsible_user_id=VALUES(responsible_user_id),released_by=VALUES(released_by),released_at=VALUES(released_at),
  deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO closeout_defect
  (id,tenant_id,closeout_id,project_id,warranty_id,defect_code,defect_title,defect_description,responsible_user_id,rectification_deadline,
   status,rectification_content,rectified_by,rectified_at,verified_by,verified_at,verification_comment,version,created_by,created_at,updated_by,
   updated_at,deleted_flag,remark)
VALUES
  (520000000000012521,0,520000000000012307,520000000000009008,520000000000012512,'M53-DEFECT-VERIFY','待复验缺陷','缺陷已整改等待独立复验',@demo_admin,'2026-07-31','PENDING_VERIFICATION','已完成整改',@demo_admin,NOW(),NULL,NULL,NULL,1,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3缺陷复验正向样本')
ON DUPLICATE KEY UPDATE status='PENDING_VERIFICATION',rectification_content=VALUES(rectification_content),rectified_by=VALUES(rectified_by),rectified_at=VALUES(rectified_at),verified_by=NULL,verified_at=NULL,verification_comment=NULL,deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO closeout_archive_transfer
  (id,tenant_id,closeout_id,project_id,transfer_code,transfer_date,recipient_organization,recipient_name,archive_location,transfer_scope,
   status,accepted_by,accepted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012531,0,520000000000012309,520000000000009010,'M53-ARCHIVE-CLOSE','2026-07-01','演示建设单位档案室','档案管理员','档案室A柜','完整竣工资料','ACCEPTED',@demo_admin,NOW(),1,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3项目关闭合法档案样本')
ON DUPLICATE KEY UPDATE status='ACCEPTED',accepted_by=@demo_admin,accepted_at=COALESCE(accepted_at,NOW()),deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO sys_file
  (id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000012401,0,'CLOSEOUT_SECTION_ACCEPTANCE','SECTION_ACCEPTANCE_RECORD',520000000000007401,'m53-closeout-section.pdf','M3分部验收记录.pdf',128,'application/pdf','CLOSEOUT_SECTION_ACCEPTANCE/520000000000007401/m53-closeout-section.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾阶段证据'),
  (520000000000012402,0,'CLOSEOUT_FINAL_ACCEPTANCE','FINAL_ACCEPTANCE_CERTIFICATE',520000000000007411,'m53-closeout-final.pdf','M3竣工验收证书.pdf',128,'application/pdf','CLOSEOUT_FINAL_ACCEPTANCE/520000000000007411/m53-closeout-final.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾阶段证据'),
  (520000000000012403,0,'CLOSEOUT_DEFECT','DEFECT_RECTIFICATION_EVIDENCE',520000000000007431,'m53-closeout-defect.pdf','M3缺陷整改证据.pdf',128,'application/pdf','CLOSEOUT_DEFECT/520000000000007431/m53-closeout-defect.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾阶段证据'),
  (520000000000012404,0,'CLOSEOUT_WARRANTY','WARRANTY_RELEASE_VOUCHER',520000000000007421,'m53-closeout-warranty.pdf','M3质保释放凭证.pdf',128,'application/pdf','CLOSEOUT_WARRANTY/520000000000007421/m53-closeout-warranty.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾阶段证据'),
  (520000000000012405,0,'CLOSEOUT_ARCHIVE_TRANSFER','ARCHIVE_TRANSFER_LIST',520000000000007441,'m53-closeout-archive.pdf','M3档案移交清单.pdf',128,'application/pdf','CLOSEOUT_ARCHIVE_TRANSFER/520000000000007441/m53-closeout-archive.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3收尾阶段证据')
ON DUPLICATE KEY UPDATE document_type=VALUES(document_type),business_id=VALUES(business_id),virus_scan_status='CLEAN',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO sys_role
  (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
VALUES
  (520000000000011001,0,'M3_TECH_QUERY','M3技术查询','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011002,0,'M3_TECH_SCHEME_MAINTAIN','M3技术方案维护','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011003,0,'M3_TECH_SCHEME_SUBMIT','M3技术方案提交','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011004,0,'M3_TECH_DRAWING_RECEIVE','M3图纸接收','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011005,0,'M3_TECH_DRAWING_REVIEW','M3图纸会审','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011006,0,'M3_TECH_RFI_RAISE','M3RFI发起','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011007,0,'M3_TECH_RFI_RESPOND','M3RFI回复','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011008,0,'M3_TECH_RFI_ACCEPT','M3RFI接受','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011009,0,'M3_TECH_DISCLOSURE','M3技术交底','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3),
  (520000000000011010,0,'M3_TECH_ARCHIVE','M3技术归档','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收',3)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),status='ENABLE',data_scope='ALL',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011011,0,'demo.tech.query',@demo_password_hash,'技术查询验收',NULL,'demo.tech.query@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011012,0,'demo.tech.scheme-maintain',@demo_password_hash,'技术方案维护验收',NULL,'demo.tech.scheme-maintain@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011013,0,'demo.tech.scheme-submit',@demo_password_hash,'技术方案提交验收',NULL,'demo.tech.scheme-submit@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011014,0,'demo.tech.drawing-receive',@demo_password_hash,'技术图纸接收验收',NULL,'demo.tech.drawing-receive@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011015,0,'demo.tech.drawing-review',@demo_password_hash,'技术图纸会审验收',NULL,'demo.tech.drawing-review@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011016,0,'demo.tech.rfi-raise',@demo_password_hash,'技术RFI发起验收',NULL,'demo.tech.rfi-raise@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011017,0,'demo.tech.rfi-respond',@demo_password_hash,'技术RFI回复验收',NULL,'demo.tech.rfi-respond@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011018,0,'demo.tech.rfi-accept',@demo_password_hash,'技术RFI接受验收',NULL,'demo.tech.rfi-accept@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011019,0,'demo.tech.disclosure',@demo_password_hash,'技术交底验收',NULL,'demo.tech.disclosure@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011020,0,'demo.tech.archive',@demo_password_hash,'技术归档验收',NULL,'demo.tech.archive@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收')
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id) VALUES
  (520000000000011031,0,520000000000011011,520000000000011001),(520000000000011032,0,520000000000011012,520000000000011002),
  (520000000000011033,0,520000000000011013,520000000000011003),(520000000000011034,0,520000000000011014,520000000000011004),
  (520000000000011035,0,520000000000011015,520000000000011005),(520000000000011036,0,520000000000011016,520000000000011006),
  (520000000000011037,0,520000000000011017,520000000000011007),(520000000000011038,0,520000000000011018,520000000000011008),
  (520000000000011039,0,520000000000011019,520000000000011009),(520000000000011040,0,520000000000011020,520000000000011010)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id) VALUES
  (520000000000011041,0,520000000000011001,1111),
  (520000000000011042,0,520000000000011002,1111),(520000000000011043,0,520000000000011002,1112),
  (520000000000011044,0,520000000000011003,1111),(520000000000011045,0,520000000000011003,1113),
  (520000000000011046,0,520000000000011004,1111),(520000000000011047,0,520000000000011004,1114),
  (520000000000011048,0,520000000000011005,1111),(520000000000011049,0,520000000000011005,1115),
  (520000000000011050,0,520000000000011006,1111),(520000000000011051,0,520000000000011006,1116),
  (520000000000011052,0,520000000000011007,1111),(520000000000011053,0,520000000000011007,1117),
  (520000000000011054,0,520000000000011008,1111),(520000000000011055,0,520000000000011008,1118),
  (520000000000011056,0,520000000000011009,1111),(520000000000011057,0,520000000000011009,1119),
  (520000000000011058,0,520000000000011010,1111),(520000000000011059,0,520000000000011010,1120);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011071,0,@demo_project,520000000000011011,'TECH_VIEWER','技术查询',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011072,0,@demo_project,520000000000011012,'TECH_SCHEME_MAINTAIN','方案维护',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011073,0,@demo_project,520000000000011013,'TECH_SCHEME_SUBMIT','方案提交',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011074,0,@demo_project,520000000000011014,'TECH_DRAWING_RECEIVE','图纸接收',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011075,0,@demo_project,520000000000011015,'TECH_DRAWING_REVIEW','图纸会审',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011076,0,@demo_project,520000000000011016,'TECH_RFI_RAISE','RFI发起',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011077,0,@demo_project,520000000000011017,'TECH_RFI_RESPOND','RFI回复',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011078,0,@demo_project,520000000000011018,'TECH_RFI_ACCEPT','RFI接受',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011079,0,@demo_project,520000000000011019,'TECH_DISCLOSURE','技术交底',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收'),
  (520000000000011080,0,@demo_project,520000000000011020,'TECH_ARCHIVE','技术归档',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术分权验收')
ON DUPLICATE KEY UPDATE role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO tech_drawing
  (id,tenant_id,project_id,drawing_code,drawing_name,specialty,source_organization,current_version_id,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011210,0,@demo_project,'M53-DRAW-RECEIVED','M3待会审结构图','结构','M3演示设计院',NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收')
ON DUPLICATE KEY UPDATE drawing_name=VALUES(drawing_name),status='ACTIVE',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO tech_drawing_version
  (id,tenant_id,project_id,drawing_id,version_no,previous_version_id,source_rfi_id,received_at,received_by,change_summary,status,approved_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011202,0,@demo_project,520000000000011210,'A',NULL,NULL,NOW(),520000000000011014,'初版图纸待会审','RECEIVED',NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收')
ON DUPLICATE KEY UPDATE status='RECEIVED',approved_at=NULL,deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

UPDATE tech_drawing SET current_version_id=520000000000011202,updated_by=@demo_admin,updated_at=NOW() WHERE id=520000000000011210 AND tenant_id=0;

INSERT INTO tech_rfi
  (id,tenant_id,project_id,drawing_version_id,review_id,rfi_code,subject,question,priority,raised_by,raised_at,response_due_date,status,closed_by,closed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011201,0,@demo_project,520000000000006512,520000000000006522,'M53-RFI-SUBMITTED','结构节点做法待回复','用于RFI回复角色正向验收','HIGH',520000000000011016,NOW(),'2026-08-15','SUBMITTED',NULL,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收')
ON DUPLICATE KEY UPDATE status='SUBMITTED',closed_by=NULL,closed_at=NULL,deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();

INSERT INTO sys_file
  (id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000011301,0,'TECH_DRAWING_VERSION','DRAWING_FILE',520000000000011202,'m53-drawing.pdf','M3技术图纸.pdf',128,'application/pdf','TECH_DRAWING_VERSION/520000000000011202/m53-drawing.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收'),
  (520000000000011302,0,'TECH_DRAWING_REVIEW','REVIEW_MINUTES',520000000000006521,'m53-review.pdf','M3图纸会审纪要.pdf',128,'application/pdf','TECH_DRAWING_REVIEW/520000000000006521/m53-review.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收'),
  (520000000000011303,0,'TECH_RFI','RFI_EVIDENCE',520000000000006531,'m53-rfi.pdf','M3RFI证据.pdf',128,'application/pdf','TECH_RFI/520000000000006531/m53-rfi.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收'),
  (520000000000011304,0,'TECH_RFI_RESPONSE','DESIGN_RESPONSE',520000000000006541,'m53-response.pdf','M3设计回复.pdf',128,'application/pdf','TECH_RFI_RESPONSE/520000000000006541/m53-response.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收'),
  (520000000000011305,0,'TECH_DISCLOSURE','DISCLOSURE_RECORD',520000000000006551,'m53-disclosure.pdf','M3技术交底记录.pdf',128,'application/pdf','TECH_DISCLOSURE/520000000000006551/m53-disclosure.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收'),
  (520000000000011306,0,'TECH_ARCHIVE','ACCEPTANCE_ARCHIVE',520000000000006571,'m53-archive.pdf','M3技术验收归档.pdf',128,'application/pdf','TECH_ARCHIVE/520000000000006571/m53-archive.pdf','cgc-pms','CLEAN',NOW(),@demo_admin,NOW(),@demo_admin,NOW(),0,'M3技术阶段验收')
ON DUPLICATE KEY UPDATE document_type=VALUES(document_type),business_id=VALUES(business_id),virus_scan_status='CLEAN',deleted_flag=0,updated_by=VALUES(updated_by),updated_at=NOW();
