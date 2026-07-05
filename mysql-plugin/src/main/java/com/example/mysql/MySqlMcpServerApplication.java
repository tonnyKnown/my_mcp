package com.example.mysql;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MySqlMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MySqlMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider mysqlToolCallbackProvider(MySqlTools mysqlTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mysqlTools)
                .build();
    }
}