package com.ruoyi.waring.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewResult
{
    public static final String DECISION_TRUE_ALARM = "true_alarm";
    public static final String DECISION_FALSE_ALARM = "false_alarm";
    public static final String DECISION_UNCERTAIN = "uncertain";

    private Long id;

    private Long taskId;

    private Integer wId;

    private String decision;

    private Double confidence;

    private Double falsePositiveScore;

    private String summary;

    private String reason;

    private String rawResponseJson;

    private Date createTime;
}