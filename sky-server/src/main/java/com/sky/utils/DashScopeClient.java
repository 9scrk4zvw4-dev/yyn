package com.sky.utils;

import com.sky.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DashScope（通义千问）OpenAI兼容接口客户端
 */
@Slf4j
@Component
public class DashScopeClient {

    @Autowired
    private AiProperties aiProperties;

    /**
     * 调用大模型对话接口
     *
     * @param requestBody 请求体（JSON字符串）
     * @return 响应JSON字符串
     */
    public String chatCompletion(String requestBody) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String result = "";
        try {
            HttpPost httpPost = new HttpPost(aiProperties.getBaseUrl() + "/chat/completions");
            httpPost.setHeader("Authorization", "Bearer " + aiProperties.getApiKey());
            httpPost.setHeader("Content-Type", "application/json");

            StringEntity entity = new StringEntity(requestBody, "utf-8");
            entity.setContentEncoding("utf-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);

            // 大模型响应较慢，超时设为120秒
            httpPost.setConfig(RequestConfig.custom()
                    .setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000)
                    .setSocketTimeout(120000)
                    .build());

            log.debug("DashScope请求体: {}", requestBody);
            response = httpClient.execute(httpPost);
            result = EntityUtils.toString(response.getEntity(), "UTF-8");
            log.debug("DashScope响应: {}", result);
        } catch (Exception e) {
            throw new RuntimeException("调用DashScope失败", e);
        } finally {
            try {
                if (response != null) response.close();
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public String getModel() {
        return aiProperties.getModel();
    }
}
