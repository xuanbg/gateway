# gateway

## 接口配置表

```sql
-- ----------------------------
-- Table structure for ibi_interface
-- ----------------------------
DROP TABLE IF EXISTS `ibi_interface`;
CREATE TABLE `ibi_interface` (
  `id` char(32) NOT NULL COMMENT 'UUID主键',
  `name` varchar(64) NOT NULL COMMENT '接口名称',
  `method` varchar(8) NOT NULL COMMENT 'HTTP请求方法',
  `url` varchar(128) NOT NULL COMMENT '接口URL',
  `auth_code` varchar(32) DEFAULT NULL COMMENT '授权码,如接口需要鉴权,则必须设置授权码',
  `limit_gap` int(10) unsigned DEFAULT NULL DEFAULT 0 COMMENT '最小间隔(秒),0表示无调用时间间隔',
  `limit_cycle` int(10) unsigned DEFAULT NULL COMMENT '限流周期(秒),null表示不进行周期性限流',
  `limit_max` int(10) unsigned DEFAULT NULL COMMENT '限制次数/限流周期,null表示不进行周期性限流',
  `message` varchar(32) DEFAULT NULL COMMENT '限流消息',
  `remark` varchar(1024) DEFAULT NULL COMMENT '描述',
  `is_verify` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否验证Token:0.公开接口,不需要验证Token;1.私有接口,需要验证Token',
  `is_limit` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否限流:0.不限流;1.限流',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_interface_hash` (`method`,`url`) USING BTREE,
  KEY `idx_interface_created_time` (`created_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='接配置口表';
```

### 初始化数据

```sql
INSERT `ibi_interface`(`id`, `name`, `method`, `url`, `auth_code`, `limit_gap`, `limit_cycle`, `limit_max`, `message`, `is_verify`, `is_limit`) VALUES
(replace(uuid(), '-', ''), '获取Code', 'GET', '/base/auth/v1.0/tokens/codes', NULL, 1, 86400, 360, '获取Code接口每24小时调用次数为360次,请合理使用', 0, 1),
(replace(uuid(), '-', ''), '获取Token', 'POST', '/base/auth/v1.0/tokens', NULL, 1, 86400, 360, '获取Token接口每24小时调用次数为360次,请合理使用', 0, 1),
(replace(uuid(), '-', ''), '通过微信授权码获取Token', 'POST', '/base/auth/v1.0/tokens/withWechatCode', NULL, 1, 86400, 360, '获取Token接口每24小时调用次数为360次,请合理使用', 0, 1),
(replace(uuid(), '-', ''), '通过微信UnionId获取Token', 'POST', '/base/auth/v1.0/tokens/withWechatUnionId', NULL, 1, 86400, 360, '获取Token接口每24小时调用次数为360次,请合理使用', 0, 1),
(replace(uuid(), '-', ''), '验证Token', 'GET', '/base/auth/v1.0/tokens/status', NULL, NULL, NULL, NULL, NULL, 1, 0),
(replace(uuid(), '-', ''), '刷新Token', 'PUT', '/base/auth/v1.0/tokens', NULL, 1, 3600, 10, '刷新Token接口每小时调用次数为10次,请合理使用', 0, 1),
(replace(uuid(), '-', ''), '用户账号离线', 'DELETE', '/base/auth/v1.0/tokens', NULL, 1, NULL, NULL, NULL, 1, 1),
(replace(uuid(), '-', ''), '获取用户导航栏', 'GET', '/base/auth/v1.0/navigators', NULL, 1, NULL, NULL, NULL, 1, 1),
(replace(uuid(), '-', ''), '获取模块功能', 'GET', '/base/auth/v1.0/navigators/{id}/functions', NULL, 1, NULL, NULL, NULL, 1, 1);
```
