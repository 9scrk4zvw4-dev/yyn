package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.ai.dashscope")
@Data
public class AiProperties {

    /**
     * 阿里云百炼(DashScope) API Key
     */
    private String apiKey;

    /**
     * 模型名称，如 qwen-plus、qwen-turbo、qwen-max
     */
    private String model;

    /**
     * DashScope OpenAI兼容接口地址
     */
    private String baseUrl;
}
