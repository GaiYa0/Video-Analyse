package com.ruoyi.waring.domain;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HType {
    private int t_id;
    private String device_id;
    private String device_name;
    private String alarm_type;
    private String alarm_type_name;
    private String alarm_level;
    private String alarm_level_name;
    private String org_index;
    private String org_name;
    private String create_time;
    private String update_time;
    private String create_user;
    private String update_user;
    private int is_handle;
}
