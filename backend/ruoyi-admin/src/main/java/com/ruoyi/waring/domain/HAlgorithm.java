package com.ruoyi.waring.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HAlgorithm extends BaseEntity {

    private Integer id;

    private String code;

    private String name;

    private String algorithmCode;

    private String algorithmName;

    private String algorithm_code;

    private String algorithm_name;

    private String version;

    private String status;

    private String campus;

    private String date;

    private String others;
}
