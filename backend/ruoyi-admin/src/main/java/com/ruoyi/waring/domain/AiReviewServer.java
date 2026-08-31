package com.ruoyi.waring.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewServer
{
    private Long id;

    private String name;

    private String serverType;

    private String endpointUrl;

    private String model;

    private String apiKey;

    private Integer timeoutMs;

    private Integer enabled;

    private String remark;

    private Date createTime;

    private Date updateTime;
}