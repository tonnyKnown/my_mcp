package com.example.filesystem;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class FilesystemTools {

    @Value("${filesystem.base-path:./tempFile}")
    private String basePath;

    @Tool(description = "读取指定文件的内容")
    public String filesystemRead(
            @ToolParam(description = "文件路径（相对于基础目录）") String path) {
        try {
            Path filePath = resolvePath(path);
            if (!Files.exists(filePath)) {
                return "文件不存在: " + path;
            }
            String content = Files.readString(filePath);
            return "文件内容:\n" + content;
        } catch (Exception e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "将内容写入指定文件")
    public String filesystemWrite(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "文件内容") String content) {
        try {
            Path filePath = resolvePath(path);
            Files.writeString(filePath, content);
            return "文件写入成功: " + filePath;
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "列出指定目录下的所有文件和子目录")
    public String filesystemList(
            @ToolParam(description = "目录路径") String path) {
        try {
            Path dirPath = resolvePath(path);
            if (!Files.exists(dirPath)) {
                return "目录不存在: " + path;
            }
            if (!Files.isDirectory(dirPath)) {
                return "不是目录: " + path;
            }
            List<String> files = new ArrayList<>();
            Files.list(dirPath).forEach(p -> files.add(p.getFileName().toString()));
            return "目录内容:\n" + String.join("\n", files);
        } catch (Exception e) {
            return "列出目录失败: " + e.getMessage();
        }
    }

    @Tool(description = "删除指定的文件")
    public String filesystemDelete(
            @ToolParam(description = "要删除的文件路径") String path) {
        try {
            Path filePath = resolvePath(path);
            if (!Files.exists(filePath)) {
                return "文件不存在: " + path;
            }
            Files.delete(filePath);
            return "文件删除成功: " + path;
        } catch (Exception e) {
            return "删除文件失败: " + e.getMessage();
        }
    }

    private Path resolvePath(String relativePath) throws Exception {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new Exception("路径越界: " + relativePath);
        }
        return resolved;
    }
}