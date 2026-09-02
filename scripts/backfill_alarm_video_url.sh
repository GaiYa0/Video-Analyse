#!/usr/bin/env bash
# 将磁盘上已生成的 alarm/.../main.mp4 回写到 h_waring.video_url（素材回调漏写时的补救）
set -euo pipefail

UPLOAD="${UPLOAD_DIR:-/var/www/SVA-web/upload}"
Q() { mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA -N -B -e "$1"; }

echo "扫描 video_url 为空的告警，匹配 $UPLOAD/alarm/<control_code>/<id>/main.mp4 ..."
count=0
while IFS=$'\t' read -r id control_code; do
  [ -z "$id" ] && continue
  [ -z "$control_code" ] && continue
  mp4="$UPLOAD/alarm/$control_code/$id/main.mp4"
  if [ ! -f "$mp4" ]; then
    continue
  fi
  rel="alarm/$control_code/$id/main.mp4"
  Q "UPDATE h_waring SET video_url='${rel}', video_absolute_url='/${rel}' WHERE id='${id}';"
  echo "  ok $id -> /$rel"
  count=$((count + 1))
done < <(Q "SELECT id, control_code FROM h_waring WHERE (video_url IS NULL OR video_url='') AND control_code IS NOT NULL AND control_code<>'';")

echo "回写完成: $count 条"
