package com.ruoyi.waring.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HHandle extends BaseEntity {
    private Integer h_id;
    private String id;
    private Integer w_id;
    private String h_title;
    private String h_remark;
    private String h_create_time;
    private String h_org_index;
    private String h_time;
    private String h_org_name;
}
