-- CGC-COMPLETE-PROJECT v2 / DOCUMENT GENERATION AND SYSTEM FORM SAMPLES
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_org := (SELECT org_id FROM sys_user WHERE id=@demo_user LIMIT 1);
SET @demo_password_hash := (SELECT password FROM sys_user WHERE id=@demo_user LIMIT 1);

INSERT INTO biz_document_template
  (id,tenant_id,template_code,template_name,business_type,engine_type,enabled,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008501,0,'M52-PAYMENT-PDF','演示付款申请PDF模板','PAYMENT','HTML_PDF',1,@demo_user,NOW(),@demo_user,NOW(),0,'稳定字段、单层循环、发布态模板样本');

INSERT INTO biz_document_template_version
  (id,tenant_id,template_id,version_no,status,schema_version,template_content,content_hash,field_manifest,published_by,published_at,created_by,created_at,
   updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008502,0,520000000000008501,1,'PUBLISHED','PAYMENT_V1',
   '<h1>{{project.projectName}}</h1><p>付款申请：{{payment.applicationNo}}</p><p>金额：{{payment.amount}}</p>',
   'a8ebb51b4097367689bad4a2e207556c7adb84200f9544dbe1c6571a56261ab7',
   JSON_ARRAY(JSON_OBJECT('key','project.projectName','required',true),JSON_OBJECT('key','payment.applicationNo','required',true),JSON_OBJECT('key','payment.amount','required',true)),
   @demo_user,'2026-07-18 09:00:00',@demo_user,NOW(),@demo_user,NOW(),0,'正常发布版本'),
  (520000000000008503,0,520000000000008501,2,'DRAFT','PAYMENT_V1',
   '<h1>{{project.projectName}}</h1><p>付款申请：{{payment.applicationNo}}</p><p>金额：{{payment.amount}}</p><p>备注：{{payment.remark}}</p>',
   'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
   JSON_ARRAY(JSON_OBJECT('key','project.projectName','required',true),JSON_OBJECT('key','payment.applicationNo','required',true),JSON_OBJECT('key','payment.amount','required',true),JSON_OBJECT('key','payment.remark','required',false)),
   NULL,NULL,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：新增可选字段的草稿版本');

INSERT INTO biz_document_default_binding
  (tenant_id,business_type,template_id,template_version_id,lock_version,created_by,created_at,updated_by,updated_at)
VALUES
  (0,'PAYMENT',520000000000008501,520000000000008502,1,@demo_user,NOW(),@demo_user,NOW());

INSERT INTO biz_document_generation
  (id,tenant_id,generation_no,business_type,business_id,template_id,template_version_id,schema_version,source_digest,output_sha256,renderer_id,
   renderer_version,status,file_id,idempotency_key,retry_of_generation_id,failure_code,requested_by,requested_at,completed_at,created_by,created_at,
   updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008511,0,'M52-DOC-PENDING-001','PAYMENT',520000000000002401,520000000000008501,520000000000008502,'PAYMENT_V1',
   'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',NULL,'HTML_PDF','1.0.0','PENDING',NULL,'M52:DOC:PAYMENT:PENDING:001',NULL,NULL,
   @demo_user,NOW(),NULL,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：等待渲染'),
  (520000000000008512,0,'M52-DOC-FAILED-001','PAYMENT',520000000000002401,520000000000008501,520000000000008502,'PAYMENT_V1',
   'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',NULL,'HTML_PDF','1.0.0','FAILED',NULL,'M52:DOC:PAYMENT:FAILED:001',NULL,'DOCUMENT_RENDER_TIMEOUT',
   @demo_user,'2026-07-18 09:05:00','2026-07-18 09:06:00',@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：渲染超时，可重试');

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008521,0,'demo.manager',@demo_password_hash,'演示项目经理','13800005252','demo.manager@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_user,NOW(),@demo_user,NOW(),0,
   'UI表单展示账号；复用随机初始化密码哈希，不提供固定明文密码');

INSERT INTO sys_user_role
  (id,tenant_id,user_id,role_id)
VALUES
  (520000000000008522,0,520000000000008521,2);
