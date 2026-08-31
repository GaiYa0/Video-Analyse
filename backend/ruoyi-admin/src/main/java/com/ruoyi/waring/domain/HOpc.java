package com.ruoyi.waring.domain;


import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HOpc {
    private int o_id;
    private String o_node;
    private String device_name;
    private String device_index;
    private int type;
}
