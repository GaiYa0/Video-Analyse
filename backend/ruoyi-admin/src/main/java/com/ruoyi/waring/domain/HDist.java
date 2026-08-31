package com.ruoyi.waring.domain;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HDist {
    private int d_id;
    private String id;
    private String person_name;
    private String pass_time;
    private String person_no;
    private String index_path_name;
    private String pass_time_exc;
    private String pass_time_ms;
    private String site_name;
    private String attendance_identification;
    private String attendance_identification_absolute;
    private int type;
    private Long begin;
    private Long end;
}
