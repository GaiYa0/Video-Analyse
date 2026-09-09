-- 睡岗质量分字段（演示机 MariaDB 3307 / 本机 WSL 3306）
-- Analyzer 从 #76 起在 detect.event / addFromSvaSimple 里带 sleepScore（0-100）。
-- 列已存在时会报错，可忽略后继续。
ALTER TABLE h_waring ADD COLUMN sva_sleep_score DOUBLE NULL COMMENT '睡岗质量分 0-100';

SELECT w_id, alarm_type_name, sva_sleep_score FROM h_waring ORDER BY w_id DESC LIMIT 5;
