-- 第四版：员工停用/备注/手机号，师傅停用并关联登录账号，凭证地址加长以便存多张图片。
-- 不修改 V1、V2、V3。H2（MySQL 模式）和 MySQL 8 都能执行。
-- 图片文件本身不进数据库，只在 payment.voucher_urls 里存可访问的地址。

-- 员工账号补上手机、状态和备注。status：ENABLED 可登录，DISABLED 停用。
-- technician_id：角色是师傅并且开了登录时，指向 technician.id。没有登录的师傅可以不写这一列。
ALTER TABLE sys_user ADD COLUMN mobile VARCHAR(20) DEFAULT NULL COMMENT '手机号';
ALTER TABLE sys_user ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED 启用，DISABLED 停用';
ALTER TABLE sys_user ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';
ALTER TABLE sys_user ADD COLUMN technician_id BIGINT DEFAULT NULL COMMENT '师傅登录账号对应的 technician.id';

-- 师傅补上启用状态。停用后不能再派工，但历史工单上的姓名快照还在。
-- user_id：这位师傅如果能登录后台，指向 sys_user.id。
ALTER TABLE technician ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED 可派工，DISABLED 停用';
ALTER TABLE technician ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';
ALTER TABLE technician ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT '可登录时对应的 sys_user.id';

-- 一张图的地址大约几十个字符，多张时原来的 1000 可能不够，加长到 2000。
ALTER TABLE payment MODIFY COLUMN voucher_urls VARCHAR(2000) DEFAULT NULL COMMENT '凭证地址 JSON 数组，可空';
