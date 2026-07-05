package com.example.filesystem;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FilesystemMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilesystemMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider filesystemToolCallbackProvider(FilesystemTools filesystemTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(filesystemTools)
                .build();
    }
}