package com.zhongbang.controller.adapter.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhongbang.controller.config.ZhongbangProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 的对话接口和 OpenAI 兼容：POST {baseUrl}/chat/completions。
 * 密钥放在 Authorization 头里，不写进日志。超时时间来自配置，默认 25 秒。
 */
@Component
public class HttpDeepSeekClient implements DeepSeekClient {

    private final ZhongbangProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpDeepSeekClient(ZhongbangProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        int seconds = Math.max(5, properties.getDeepseek().getTimeoutSeconds());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        ZhongbangProperties.Deepseek config = properties.getDeepseek();
        if (!config.configured()) {
            throw new DeepSeekException("还没有配置 DeepSeek。请在启动环境里设置 DEEPSEEK_API_KEY 后再试。");
        }
        Map<String, Object> body = Map.of(
                "model", config.getModel() == null || config.getModel().isBlank() ? "deepseek-chat" : config.getModel(),
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        try {
            String raw = restClient.post()
                    .uri(endpoint(config.getBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.getApiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return readContent(raw);
        } catch (DeepSeekException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new DeepSeekException("分析服务返回了错误（HTTP " + ex.getStatusCode().value() + "），请稍后再试");
        } catch (ResourceAccessException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                throw new DeepSeekException("分析超时，请稍后再试");
            }
            throw new DeepSeekException("连不上分析服务，请稍后再试");
        } catch (Exception ex) {
            throw new DeepSeekException("分析服务暂时不可用，请稍后再试");
        }
    }

    private String readContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DeepSeekException("模型没有返回内容，请稍后再试");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new DeepSeekException("模型没有返回内容，请稍后再试");
            }
            return content.asText();
        } catch (DeepSeekException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DeepSeekException("分析服务返回的内容无法读取，请稍后再试");
        }
    }

    /** 配置可以只写到域名，也可以写完整的 /chat/completions。 */
    static String endpoint(String baseUrl) {
        String root = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.endsWith("/chat/completions")) {
            return root;
        }
        return root + "/chat/completions";
    }
}
