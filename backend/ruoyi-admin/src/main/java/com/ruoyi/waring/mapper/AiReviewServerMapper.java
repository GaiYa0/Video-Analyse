package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.AiReviewServer;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AiReviewServerMapper
{
    AiReviewServer selectFirstEnabled();

    AiReviewServer selectEnabledById(Long id);
}