package com.example.weather;

import com.example.weather.WeatherTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Weather MCP Server Application
 * 
 * 提供天气查询 MCP 工具，自动注册到 Nacos MCP Registry
 */
@SpringBootApplication
public class WeatherMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherMcpServerApplication.class, args);
    }

    /**
     * 注册 MCP 工具回调提供者
     */
    @Bean
    public ToolCallbackProvider weatherToolCallbackProvider(WeatherTools weatherTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherTools)
                .build();
    }
}