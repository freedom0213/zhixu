package com.zhixu.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tj.ai")
public class AiProperties {
    private boolean enabled = true;
    private String apiKey = "your key";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash";
    private String dataDir = "/data/ai";
}
