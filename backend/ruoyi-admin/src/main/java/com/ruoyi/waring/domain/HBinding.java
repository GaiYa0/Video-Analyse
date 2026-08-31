package com.ruoyi.waring.domain;


import com.ruoyi.common.core.domain.BaseEntity;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HBinding extends BaseEntity {
    private int b_id;
    private String device_name;
    private String device_index;
    private String team_name;
}
