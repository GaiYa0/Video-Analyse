-- PR #30 睡岗俯仰角字段（演示机 MariaDB 3307）
-- 列已存在时会报错，可忽略
ALTER TABLE h_waring ADD COLUMN sva_pitch_degree DOUBLE NULL;
