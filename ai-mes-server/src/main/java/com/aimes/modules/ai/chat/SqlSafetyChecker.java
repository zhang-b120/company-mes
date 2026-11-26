package com.aimes.modules.ai.chat;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * SQL安全校验器 — 确保AI生成的SQL只读且安全
 */
@Component
public class SqlSafetyChecker {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ", "CREATE ",
            "TRUNCATE ", "REPLACE ", "GRANT ", "REVOKE ",
            "CALL ", "EXEC ", "EXECUTE ",
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "LOAD DATA",
            "INFORMATION_SCHEMA", "PERFORMANCE_SCHEMA",
            "MYSQL.", "SYS."
    );

    /**
     * 校验SQL是否安全（只允许SELECT）
     */
    public boolean isSafe(String sql) {
        if (sql == null || sql.isBlank()) return false;

        String upper = sql.toUpperCase().trim();

        // 必须以SELECT开头
        if (!upper.startsWith("SELECT")) return false;

        // 禁止分号（防多语句注入）
        if (sql.contains(";")) return false;

        // 禁止注释（防注入绕过）
        if (sql.contains("--") || sql.contains("/*")) return false;

        // 检查禁止关键词
        for (String forbidden : FORBIDDEN_KEYWORDS) {
            if (upper.contains(forbidden)) return false;
        }

        return true;
    }

    /**
     * 确保SQL有LIMIT限制，防止返回海量数据
     */
    public String ensureLimit(String sql, int maxRows) {
        String upper = sql.toUpperCase();
        if (!upper.contains("LIMIT")) {
            return sql + " LIMIT " + maxRows;
        }
        return sql;
    }
}
