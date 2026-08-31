package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.AiReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Mapper
@Repository
public interface AiReviewTaskMapper
{
    int insertTask(AiReviewTask task);

    AiReviewTask selectById(@Param("id") Long id);

    int countOpenTasksByWarningId(@Param("wId") Integer wId);

    List<AiReviewTask> selectPendingBatch(@Param("now") Date now, @Param("limit") int limit);

    int claimPendingTask(@Param("id") Long id, @Param("startedTime") Date startedTime, @Param("updateTime") Date updateTime);

    int claimRetryTask(@Param("id") Long id, @Param("now") Date now, @Param("startedTime") Date startedTime, @Param("updateTime") Date updateTime);

    int markSuccess(@Param("id") Long id,
                    @Param("serverId") Long serverId,
                    @Param("finishedTime") Date finishedTime,
                    @Param("updateTime") Date updateTime);

    int markFailed(@Param("id") Long id,
                   @Param("serverId") Long serverId,
                   @Param("retryCount") Integer retryCount,
                   @Param("maxRetries") Integer maxRetries,
                   @Param("nextRetryTime") Date nextRetryTime,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedTime") Date finishedTime,
                   @Param("updateTime") Date updateTime,
                   @Param("terminalFailed") boolean terminalFailed);

    int markSkipped(@Param("id") Long id,
                    @Param("errorMessage") String errorMessage,
                    @Param("finishedTime") Date finishedTime,
                    @Param("updateTime") Date updateTime);
}