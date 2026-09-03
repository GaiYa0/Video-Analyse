-- P3 国标设备字段（演示机 MariaDB 3307 / 库 easySVA）
-- 列已存在时对应 ALTER 会报错，可忽略后继续
ALTER TABLE h_device ADD COLUMN device_type VARCHAR(16) NOT NULL DEFAULT 'rtsp' COMMENT 'rtsp or gb28181';
ALTER TABLE h_device ADD COLUMN gb_device_id VARCHAR(64) NULL COMMENT 'GB28181 device id';
ALTER TABLE h_device ADD COLUMN gb_platform_id VARCHAR(64) NULL COMMENT 'GB28181 platform id';
UPDATE h_device SET device_type = 'rtsp' WHERE device_type IS NULL OR device_type = '';
