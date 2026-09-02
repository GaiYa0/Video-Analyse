/** 告警视频证据 URL 与缺省提示（待处理/误报/整改等页共用） */

export function resolveAlarmVideoUrl(row, toAbsoluteMediaUrl) {
  if (!row || typeof toAbsoluteMediaUrl !== 'function') {
    return '';
  }
  const absoluteVideoPath = row.video_absolute_url;
  if (absoluteVideoPath) {
    const url = toAbsoluteMediaUrl(absoluteVideoPath);
    if (url) {
      return url;
    }
  }
  const relativeVideoPath = row.video_url;
  if (/^\/?(alarm|zlm)\//i.test(relativeVideoPath || '')) {
    const normalized = relativeVideoPath.startsWith('/')
      ? relativeVideoPath
      : `/${relativeVideoPath}`;
    return toAbsoluteMediaUrl(normalized);
  }
  return relativeVideoPath ? toAbsoluteMediaUrl(relativeVideoPath) : '';
}

export function getVideoEvidenceUnavailableMessage(row) {
  if (!row) {
    return '视频不存在';
  }
  const status = String(row.sva_media_status || '').toLowerCase();
  if (status === 'record_started') {
    return '视频正在生成，请稍后再试';
  }
  if (status === 'record_failed') {
    const err = row.sva_media_error;
    return err ? `录像失败：${err}` : '录像失败，请检查布控录像引擎或 ZLM 日志';
  }
  if (status === 'success' && !row.video_url && !row.video_absolute_url) {
    return '截图已保存，视频尚未回写（刚告警请稍等数秒再试）';
  }
  return '视频不存在';
}
