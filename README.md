# gateway

## 接口配置表

```sql
-- ----------------------------
-- Table structure for ibi_interface
-- ----------------------------
DROP TABLE IF EXISTS `ibi_interface`;
CREATE TABLE `ibi_interface` (
  `id` char(32) NOT NULL COMMENT 'UUID主键',
  `type` tinyint(3) unsigned NOT NULL COMMENT '接口类型:0.公开;1.私有;2.授权',
  `name` varchar(64) NOT NULL COMMENT '接口名称',
  `method` varchar(8) NOT NULL COMMENT 'HTTP请求方法',
  `url` varchar(128) NOT NULL COMMENT '接口URL',
  `auth_code` varchar(32) DEFAULT NULL COMMENT '授权码',
  `is_limit` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否限流:0.不限流;1.限流',
  `limit_gap` int(10) unsigned DEFAULT NULL DEFAULT 0 COMMENT '最小间隔(秒),0表示无调用时间间隔',
  `limit_cycle` int(10) unsigned DEFAULT NULL COMMENT '限流周期(秒),null表示不进行周期性限流',
  `limit_max` int(10) unsigned DEFAULT NULL COMMENT '限制次数/限流周期,null表示不进行周期性限流',
  `message` varchar(32) DEFAULT NULL COMMENT '限流消息',
  `remark` varchar(1024) DEFAULT NULL COMMENT '描述',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_interface_hash` (`method`,`url`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='接口表';
```
