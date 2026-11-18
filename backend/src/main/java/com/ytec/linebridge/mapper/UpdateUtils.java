package com.ytec.linebridge.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * MyBatis-Plus 更新輔助工具（確保清空可選欄位時明確寫入 SQL NULL，相容 Oracle CLOB / VARCHAR2）
 */
public final class UpdateUtils {

    private UpdateUtils() {
    }

    public static <T> void setNullableString(
            LambdaUpdateWrapper<T> wrapper,
            SFunction<T, String> getter,
            String sqlColumnName,
            String value) {
        if (value == null) {
            wrapper.setSql(sqlColumnName + " = NULL");
        } else {
            wrapper.set(getter, value);
        }
    }
}
