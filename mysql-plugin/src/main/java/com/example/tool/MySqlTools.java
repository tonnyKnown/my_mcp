package com.example.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class MySqlTools {

    private static final Logger log = LoggerFactory.getLogger(MySqlTools.class);

    private static final int MAX_SQL_LENGTH = 1000;
    private static final int MAX_QUERY_ROWS = 50;
    private static final Pattern READ_ONLY_QUERY_PATTERN = Pattern.compile("(?is)^\\s*SELECT\\b");
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|REPLACE|MERGE|CALL|EXEC|EXECUTE|GRANT|REVOKE|LOAD|LOCK|UNLOCK|KILL|SET|USE|ANALYZE|OPTIMIZE|REPAIR|RENAME)\\b");
    private static final Pattern FILE_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD\\s+DATA|INFILE)\\b");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    @Tool(description = "执行 SQL 查询语句（SELECT）")
    public String mysqlQuery(
            @ToolParam(description = "SQL 查询语句") String sql) {
        try {
            log.info("mysqlQuery called, sql={}", sql);
            String validationError = validateReadOnlySql(sql);
            if (validationError != null) {
                return validationError;
            }
            
            List<Map<String, Object>> results = executeQuery(sql);
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "【仅只读查询】执行只读 SELECT 查询，不可执行 INSERT/UPDATE/DELETE。修改数据请使用其他专用工具（如 updateOrderStatus）")
    public String mysqlExecute(
            @ToolParam(description = "SQL 更新语句") String sql) {
        try {
            log.info("mysqlExecute called, sql={}", sql);
            String validationError = validateSqlBasic(sql);
            if (validationError != null) {
                return validationError;
            }
            return "SQL execution rejected: dangerous operations are disabled. Only read-only SELECT queries are allowed.";
        } catch (Exception e) {
            return "执行失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取数据库表列表")
    public String mysqlListTables() {
        try {
            List<String> tables = new ArrayList<>();
            try (Connection conn = getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"});
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            return objectMapper.writeValueAsString(tables);
        } catch (Exception e) {
            return "获取表列表失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取表结构信息")
    public String mysqlDescribeTable(
            @ToolParam(description = "表名") String tableName) {
        try {
            List<Map<String, Object>> columns = new ArrayList<>();
            try (Connection conn = getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, null);
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    columns.add(column);
                }
            }
            return objectMapper.writeValueAsString(columns);
        } catch (Exception e) {
            return "获取表结构失败: " + e.getMessage();
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private String validateReadOnlySql(String sql) {
        String validationError = validateSqlBasic(sql);
        if (validationError != null) {
            return validationError;
        }

        String normalizedSql = stripTrailingSemicolon(sql.trim());
        if (!READ_ONLY_QUERY_PATTERN.matcher(normalizedSql).find()) {
            return "SQL validation failed: only SELECT queries are allowed";
        }
        if (DANGEROUS_SQL_PATTERN.matcher(normalizedSql).find()) {
            return "SQL validation failed: dangerous SQL operation is not allowed";
        }
        if (FILE_SQL_PATTERN.matcher(normalizedSql).find()) {
            return "SQL validation failed: file read/write SQL is not allowed";
        }
        return null;
    }

    private String validateSqlBasic(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "Parameter validation failed: missing required parameter sql";
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            return "Parameter validation failed: parameter sql length must be <= " + MAX_SQL_LENGTH;
        }

        String trimmedSql = sql.trim();
        if (containsSqlComment(trimmedSql)) {
            return "SQL validation failed: SQL comments are not allowed";
        }

        String withoutTrailingSemicolon = stripTrailingSemicolon(trimmedSql);
        if (withoutTrailingSemicolon.contains(";")) {
            return "SQL validation failed: multiple SQL statements are not allowed";
        }
        return null;
    }

    private boolean containsSqlComment(String sql) {
        return sql.contains("--") || sql.contains("#") || sql.contains("/*") || sql.contains("*/");
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmedSql = sql.trim();
        if (trimmedSql.endsWith(";")) {
            return trimmedSql.substring(0, trimmedSql.length() - 1).trim();
        }
        return trimmedSql;
    }

    private List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.setMaxRows(MAX_QUERY_ROWS);
            try (ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next() && results.size() < MAX_QUERY_ROWS) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
            }
        }
        return results;
    }

    private int executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
