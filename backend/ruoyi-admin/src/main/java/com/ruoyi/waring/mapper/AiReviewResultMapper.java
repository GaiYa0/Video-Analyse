package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.AiReviewResult;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AiReviewResultMapper
{
    int insertResult(AiReviewResult result);

    AiReviewResult selectLatestByWarningId(Integer wId);
}