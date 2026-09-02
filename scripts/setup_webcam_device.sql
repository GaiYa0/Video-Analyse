-- 本机摄像头设备（RTMP live/webcam）
INSERT INTO h_device (
  ape_id, name, stream_source_type, direct_source_url,
  monitor_status, zlm_server_id, sva_server_id, is_online,
  create_time, update_time
)
SELECT
  'cam918429', '工位摄像头', 'DIRECT', 'rtmp://127.0.0.1:9995/live/webcam',
  'STOPPED', 1, 1, '1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM h_device WHERE ape_id = 'cam918429');

UPDATE h_device
SET name = '工位摄像头',
    stream_source_type = 'DIRECT',
    direct_source_url = 'rtmp://127.0.0.1:9995/live/webcam',
    update_time = NOW()
WHERE ape_id = 'cam918429';

SELECT ape_id, name, direct_source_url, monitor_status FROM h_device WHERE ape_id = 'cam918429';
