-- 严重睡岗（15s 档）站外通知配置（演示机 MariaDB 3307 / 本机 WSL 3306）
-- 配置项归同学 C，本脚本只起草，等 C 确认后再跑。
-- 键名与 AiReviewServiceImpl 的 ai.review.enabled 同风格。
--
-- sva.sleep.webhook.enabled = true  才推送；默认 false，避免演示时误发
-- sva.sleep.webhook.url     留空则只打日志，不报错

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '严重睡岗通知开关', 'sva.sleep.webhook.enabled', 'false', 'Y', 'admin', NOW(), '睡岗升到 15s 档时是否推送站外通知'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sva.sleep.webhook.enabled');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '严重睡岗通知地址', 'sva.sleep.webhook.url', '', 'Y', 'admin', NOW(), '企业微信群机器人 webhook 地址，留空则只打日志'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sva.sleep.webhook.url');

SELECT config_id, config_key, config_value FROM sys_config WHERE config_key LIKE 'sva.sleep.webhook.%';
