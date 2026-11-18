package com.ytec.linebridge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 啟動時檢查既有資料庫結構並安全補齊新增欄位（確保舊版線上 Oracle 無痛升級）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigrationRunner.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumnExists(
                "LB_BOT_ACCOUNT",
                "WELCOME_ENABLED",
                "ALTER TABLE LB_BOT_ACCOUNT ADD WELCOME_ENABLED NUMBER(1) DEFAULT 1 NOT NULL"
        );
        ensureColumnExists(
                "LB_BOT_ACCOUNT",
                "WELCOME_MESSAGE",
                "ALTER TABLE LB_BOT_ACCOUNT ADD WELCOME_MESSAGE CLOB"
        );
    }

    private void ensureColumnExists(String tableName, String columnName, String alterSql) {
        try {
            // 使用 WHERE 1 = 0 探測欄位是否存在，同時相容 Oracle 11g+ 與 H2
            jdbcTemplate.queryForList("SELECT " + columnName + " FROM " + tableName + " WHERE 1 = 0");
        } catch (Exception probeEx) {
            try {
                log.info("偵測到 {}.{} 尚未建立，自動執行欄位升級...", tableName, columnName);
                jdbcTemplate.execute(alterSql);
                log.info("已完成 {}.{} 欄位升級", tableName, columnName);
            } catch (Exception alterEx) {
                log.error("自動升級資料表欄位失敗，請手動執行 SQL：{}，原因：{}", alterSql, alterEx.getMessage());
            }
        }
    }
}
