-- 告警邮件通知配置（演示机 MariaDB 3307 / 本机 WSL 3306）
-- 配置项归同学 C，本脚本只起草，等 C 确认后再跑。
--
-- 与 scripts/add_sleep_webhook_config.sql 是同一套分级通知的两条通道：
--   企业微信 webhook = 即时群消息；邮件 = 可留档的正式通知。
-- 默认全部关闭，避免演示时误发。

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '告警邮件通知开关', 'sva.sleep.email.enabled', 'false', 'Y', 'admin', NOW(), '严重档告警是否发邮件'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sva.sleep.email.enabled');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '告警邮件收件人上限', 'sva.sleep.email.maxRecipients', '10', 'Y', 'admin', NOW(), '按组织找人时最多发几封'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sva.sleep.email.maxRecipients');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '告警邮件兜底收件人', 'sva.sleep.email.fallback', '', 'Y', 'admin', NOW(), '组织下无人配邮箱时用，多个用逗号分隔'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sva.sleep.email.fallback');

-- SMTP 账号不放在这里（含密码），走 application.yml 的 spring.mail.*
SELECT config_id, config_key, config_value FROM sys_config WHERE config_key LIKE 'sva.sleep.email.%';
