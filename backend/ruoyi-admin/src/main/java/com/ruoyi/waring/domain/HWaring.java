package com.ruoyi.waring.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HWaring extends BaseEntity {

    @Excel(name = "ID", cellType = Excel.ColumnType.NUMERIC)
    private Integer w_id;

    private String id;

    private String alarm_type;

    @Excel(name = "告警类型")
    private String alarm_type_name;

    private String alarm_level;

    @Excel(name = "告警级别")
    private String alarm_level_name;

    private String device_id;

    @Excel(name = "告警设备")
    private String device_name;

    private String org_index;

    @Excel(name = "所属组织")
    private String org_name;

    private String longitude;

    private String latitude;

    @Excel(name = "告警时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss", type = Excel.Type.EXPORT)
    private String alarm_time;

    private String picture_url;

    private String picture_absolute_url;

    private String video_url;

    private String video_absolute_url;

    private String sva_media_status;

    private String sva_media_error;

    private Long begin;

    private Long end;

    private Integer is_handle;

    private int is_enable;

    private String team;

    private String ip;

    private String control_code;

    private String sva_event_key;

    private String sva_event_state;

    private String sva_behavior_type;

    private String sva_rule_id;

    private Long sva_business_event_id;

    private String sva_business_event_name;

    private String sva_business_template_id;

    private Integer sva_business_template_version;

    private String sva_region_id;

    private String sva_region_name;

    private String sva_line_id;

    private String sva_line_name;

    private String sva_crossing_direction;

    private Integer sva_track_id;

    private String end_time;

    private Long duration_ms;

    private Boolean ai_review_enabled;

    private String ai_review_prompt;

    private String ai_review_status;

    private String ai_review_decision;

    private String ai_review_summary;

    private Double ai_false_positive_score;

    private String ai_review_time;
}
