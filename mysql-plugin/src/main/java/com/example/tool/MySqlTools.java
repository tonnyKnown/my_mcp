package com.example.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MySqlTools {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mysql.host:localhost}")
    private String host;

    @Value("${mysql.port:3306}")
    private int port;

    @Value("${mysql.database:example_db}")
    private String database;

    @Value("${mysql.username:root}")
    private String username;

    @Value("${mysql.password:}")
    private String password;

    @Tool(description = "执行 SQL 查询语句（SELECT）")
    public String mysqlQuery(
            @ToolParam(description = "SQL 查询语句") String sql) {
        try {
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return "只支持 SELECT 查询语句";
            }
            
            List<Map<String, Object>> results = executeQuery(sql);
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "执行 SQL 更新语句（INSERT/UPDATE/DELETE）")
    public String mysqlExecute(
            @ToolParam(description = "SQL 更新语句") String sql) {
        try {
            String upperSql = sql.trim().toUpperCase();
            if (!upperSql.startsWith("INSERT") && !upperSql.startsWith("UPDATE") && !upperSql.startsWith("DELETE")) {
                return "只支持 INSERT/UPDATE/DELETE 语句";
            }
            
            int affectedRows = executeUpdate(sql);
            return "执行成功，影响行数: " + affectedRows;
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
                ResultSet rs = metaData.getTables(database, null, "%", new String[]{"TABLE"});
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
                ResultSet rs = metaData.getColumns(database, null, tableName, null);
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
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    private List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
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