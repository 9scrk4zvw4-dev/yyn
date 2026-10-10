package com.sky.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.ai.ollama")
@Data
public class AiProperties {
    private String baseUrl;
    private String modelName;
    private String apiKey;
    private String embeddingModelName;
}
