package com.example.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Weather MCP Tools - 天气查询工具
 * 
 * 使用 OpenWeatherMap API 获取实时天气和预报信息
 */
@Service
public class WeatherTools {

    private final ObjectMapper objectMapper;

    @Value("${weather.api-key:63be2c273b8a90deea870533b0bf11b7}")
    private String apiKey;

    @Value("${weather.base-url:https://api.openweathermap.org/data/2.5}")
    private String baseUrl;

    public WeatherTools() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 查询实时天气
     */
    @Tool(description = "查询全球主要城市的实时天气状况，包括温度、湿度、风速、气压等核心气象数据")
    public String weatherQuery(
            @ToolParam(description = "城市名称（支持中英文，如：Beijing, Shanghai, London, 北京, 上海）") String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String urlStr = String.format("%s/weather?q=%s&appid=%s&units=metric&lang=zh_cn",
                    baseUrl, encodedCity, apiKey);
            System.out.println("WeatherTools.weatherQuery：" + urlStr);
            String response = executeHttpRequest(urlStr);
            return formatWeatherResponse(response);
        } catch (Exception e) {
            return "查询天气失败: " + e.getMessage();
        }
    }

    /**
     * 查询天气预报
     */
    @Tool(description = "查询未来5天的天气预报，包含每日最高/最低温度、天气状况、降水概率、风力和湿度")
    public String weatherForecast(
            @ToolParam(description = "城市名称（支持中英文）") String city,
            @ToolParam(description = "预报天数（1-5天，默认3天）") int days) {
        try {
            if (days < 1 || days > 5) {
                days = 3;
            }
            
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String urlStr = String.format("%s/forecast?q=%s&appid=%s&units=metric&lang=zh_cn&cnt=%d",
                    baseUrl, encodedCity, apiKey, days * 8);
            
            String response = executeHttpRequest(urlStr);
            return formatForecastResponse(response);
        } catch (Exception e) {
            return "查询天气预报失败: " + e.getMessage();
        }
    }

    private String executeHttpRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        if (responseCode != 200) {
            throw new Exception("API 请求失败，响应码: " + responseCode);
        }

        return response.toString();
    }

    private String formatWeatherResponse(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Map<String, Object> formatted = new LinkedHashMap<>();
            
            formatted.put("城市", data.get("name"));
            
            if (data.containsKey("sys")) {
                Map<String, Object> sys = (Map<String, Object>) data.get("sys");
                formatted.put("国家", sys.get("country"));
            }

            if (data.containsKey("main")) {
                Map<String, Object> main = (Map<String, Object>) data.get("main");
                formatted.put("温度(°C)", main.get("temp"));
                formatted.put("体感温度(°C)", main.get("feels_like"));
                formatted.put("湿度(%)", main.get("humidity"));
                formatted.put("气压(hPa)", main.get("pressure"));
            }

            if (data.containsKey("weather")) {
                List<Map<String, Object>> weather = (List<Map<String, Object>>) data.get("weather");
                if (!weather.isEmpty()) {
                    Map<String, Object> w = weather.get(0);
                    formatted.put("天气状况", w.get("description"));
                    formatted.put("图标", w.get("icon"));
                }
            }

            if (data.containsKey("wind")) {
                Map<String, Object> wind = (Map<String, Object>) data.get("wind");
                formatted.put("风速(m/s)", wind.get("speed"));
            }

            if (data.containsKey("clouds")) {
                Map<String, Object> clouds = (Map<String, Object>) data.get("clouds");
                formatted.put("云量(%)", clouds.get("all"));
            }

            return objectMapper.writeValueAsString(formatted);
        } catch (Exception e) {
            return json;
        }
    }

    private String formatForecastResponse(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            
            if (data.containsKey("list")) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
                List<Map<String, Object>> resultList = new ArrayList<>();
                
                for (Map<String, Object> item : list) {
                    Map<String, Object> formatted = new LinkedHashMap<>();
                    
                    if (item.containsKey("main")) {
                        Map<String, Object> main = (Map<String, Object>) item.get("main");
                        formatted.put("温度(°C)", main.get("temp"));
                        formatted.put("湿度(%)", main.get("humidity"));
                    }
                    
                    if (item.containsKey("weather")) {
                        List<Map<String, Object>> weather = (List<Map<String, Object>>) item.get("weather");
                        if (!weather.isEmpty()) {
                            formatted.put("天气状况", weather.get(0).get("description"));
                        }
                    }
                    
                    if (item.containsKey("wind")) {
                        Map<String, Object> wind = (Map<String, Object>) item.get("wind");
                        formatted.put("风速(m/s)", wind.get("speed"));
                    }
                    
                    if (item.containsKey("dt_txt")) {
                        formatted.put("时间", item.get("dt_txt"));
                    }
                    
                    resultList.add(formatted);
                }
                
                return objectMapper.writeValueAsString(resultList);
            }
            
            return json;
        } catch (Exception e) {
            return json;
        }
    }
}