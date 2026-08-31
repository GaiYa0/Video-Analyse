package com.ruoyi.waring.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewTask
{
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private Long id;

    private Integer wId;

    private String reviewType;

    private String mediaUrl;

    private String promptSnapshot;

    private Long serverId;

    private String status;

    private Integer retryCount;

    private Integer maxRetries;

    private Date nextRetryTime;

    private Date startedTime;

    private Date finishedTime;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;
}