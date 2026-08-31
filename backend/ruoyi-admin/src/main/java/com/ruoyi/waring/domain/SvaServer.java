package com.ruoyi.waring.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SvaServer {

    private Long id;

    private String name;

    private String app;

    private String host;

    private Integer analyzer_port;

    private Integer enabled;
}
