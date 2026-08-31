package com.ruoyi.waring.domain;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Details {
    private String alarm_level_name;
    private String alarm_type_name;
    private String alarm_time;
    private String device_name;
    private Integer is_handle;
    private String h_title;
    private String h_create_time;
    private String h_remark;
    private String h_org_name;
    private String picture_absolute_url;
    private String video_absolute_url;
    private String sva_event_state;
    private String sva_behavior_type;
    private String sva_rule_id;
    private String sva_region_name;
    private String sva_line_name;
    private String sva_crossing_direction;
    private String end_time;
    private Long duration_ms;
    private String ai_review_status;
    private String ai_review_decision;
    private String ai_review_summary;
    private Double ai_false_positive_score;
    private String ai_review_time;
    private String sva_media_status;
    private String sva_media_error;
}
