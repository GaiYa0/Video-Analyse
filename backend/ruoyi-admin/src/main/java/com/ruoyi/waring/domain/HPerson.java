package com.ruoyi.waring.domain;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HPerson {

    private String is_handle;

    private Long begin;

    private Long end;

    private Long statistic_time;

    private int statistic_in_person_count;

    private int statistic_out_person_count;

    private int statistic_person_count;

    private String area_name;

    private int type;
}
