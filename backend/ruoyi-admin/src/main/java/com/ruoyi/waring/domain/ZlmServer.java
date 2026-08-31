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
public class ZlmServer {

    private Long id;

    private String name;

    private String app;

    private String host;

    private Integer api_port;

    private Integer media_http_port;

    private Integer media_rtsp_port;

    private String secret;

    private Integer enabled;
}
