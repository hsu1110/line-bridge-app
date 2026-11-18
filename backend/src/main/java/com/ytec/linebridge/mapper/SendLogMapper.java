package com.ytec.linebridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ytec.linebridge.entity.SendLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SendLogMapper extends BaseMapper<SendLog> {

    /**
     * SQL 聚合查詢指定起始時間之後的發送總數、成功與失敗數（避免撈出全表 CLOB）
     */
    @Select("""
            SELECT
              COUNT(*) AS TODAY_TOTAL,
              COALESCE(SUM(CASE WHEN STATUS = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS TODAY_SUCCESS,
              COALESCE(SUM(CASE WHEN STATUS = 'FAILED' THEN 1 ELSE 0 END), 0) AS TODAY_FAILED
            FROM LB_SEND_LOG
            WHERE CREATED_AT >= #{startTime}
            """)
    Map<String, Object> selectPeriodStats(@Param("startTime") LocalDateTime startTime);

    /**
     * SQL 聚合查詢指定起始時間之後的前 5 名熱門模板（相容 Oracle 11g+ ROWNUM 與 H2 Oracle 模式）
     */
    @Select("""
            SELECT T_CODE AS TEMPLATE_CODE, CNT AS USE_COUNT
            FROM (
              SELECT
                CASE
                  WHEN TEMPLATE_CODE IS NULL OR TRIM(TEMPLATE_CODE) IS NULL OR LENGTH(TRIM(TEMPLATE_CODE)) = 0
                  THEN 'DIRECT'
                  ELSE TEMPLATE_CODE
                END AS T_CODE,
                COUNT(*) AS CNT
              FROM LB_SEND_LOG
              WHERE CREATED_AT >= #{startTime}
              GROUP BY
                CASE
                  WHEN TEMPLATE_CODE IS NULL OR TRIM(TEMPLATE_CODE) IS NULL OR LENGTH(TRIM(TEMPLATE_CODE)) = 0
                  THEN 'DIRECT'
                  ELSE TEMPLATE_CODE
                END
              ORDER BY CNT DESC
            )
            WHERE ROWNUM <= 5
            """)
    List<Map<String, Object>> selectTopTemplateStats(@Param("startTime") LocalDateTime startTime);
}
