INSERT INTO av_algorithm (sort, code, name, api_url, object_count, object_str, remark, state)
SELECT 99, 'on_sleep_pose', '睡岗检测', '', 1, 'person', '', 0
WHERE NOT EXISTS (SELECT 1 FROM av_algorithm WHERE code = 'on_sleep_pose');

SELECT code, name, object_str, object_count FROM av_algorithm WHERE code = 'on_sleep_pose';
