package com.ytec.linebridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ytec.linebridge.entity.WebhookEventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookEventLogMapper extends BaseMapper<WebhookEventLog> {
}
