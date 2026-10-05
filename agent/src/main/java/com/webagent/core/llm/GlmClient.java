package com.webagent.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * GLM 云端通道（调试默认，ADR-009）：智谱 BigModel 的 OpenAI 兼容端点。
 * key 仅经环境变量 ZHIPUAI_API_KEY 注入，绝不入库。
 * 超时 60s 重试 1 次；仍失败抛 LlmUnavailableException -> UNCERTAIN/TOOL_FAILURE。
 */
@Component
@Primary
@ConditionalOnProperty(name = "webagent.llm.channel", havingValue = "cloud-glm")
public class GlmClient implements LlmClient {

    private static final ObjectMapper M = new ObjectMapper();
    private final RestClient http;
    private final String model;
    private final int timeoutSeconds;
    private final int retry;

    public GlmClient(@Value("${webagent.llm.cloud-glm.base-url}") String baseUrl,
                     @Value("${webagent.llm.cloud-glm.model:glm-4-flash}") String model,
                     @Value("${webagent.llm.timeout-seconds:60}") int timeoutSeconds,
                     @Value("${webagent.llm.retry:1}") int retry) {
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.retry = retry;
        String key = System.getenv().getOrDefault("ZHIPUAI_API_KEY", "");
        if (key.isBlank()) {
            throw new IllegalStateException("未设置 ZHIPUAI_API_KEY（cloud-glm 通道必需，见 RUNNING.md 2.0 节）");
        }
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + key)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LlmResponse explain(String systemPrompt, String untrustedCodePayload) {
        String body = chatBody(systemPrompt, untrustedCodePayload, 0.2);
        for (int attempt = 0; attempt <= retry; attempt++) {
            long t0 = System.currentTimeMillis();
            try {
                String resp = http.post().uri("/chat/completions")
                        .body(body)
                        .retrieve().body(String.class);
                return parse(resp, System.currentTimeMillis() - t0);
            } catch (Exception e) {
                if (attempt == retry) {
                    throw new LlmUnavailableException("GLM 调用失败（已重试 " + retry + " 次）: " + e.getMessage(), e);
                }
            }
        }
        throw new LlmUnavailableException("GLM 不可达", null);
    }

    /** LEARN_QA 兜底分类：输出限定 labels 之一。 */
    @Override
    public String classify(String query, String[] labels) {
        String sys = "你是意图分类器。只输出以下标签之一，不要任何其他文字：" + String.join("/", labels);
        String body = chatBody(sys, query, 0.0);
        String resp = http.post().uri("/chat/completions").body(body).retrieve().body(String.class);
        String content = parse(resp, 0).content().trim();
        for (String l : labels) {
            if (content.contains(l)) return l;
        }
        return null;
    }

    private String chatBody(String system, String user, double temperature) {
        try {
            return M.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)),
                    "temperature", temperature));
        } catch (Exception e) {
            throw new LlmUnavailableException("请求序列化失败", e);
        }
    }

    private LlmResponse parse(String resp, long latencyMs) {
        try {
            JsonNode root = M.readTree(resp);
            String content = root.path("choices").get(0).path("message").path("content").asText("");
            int prompt = root.path("usage").path("prompt_tokens").asInt(0);
            int completion = root.path("usage").path("completion_tokens").asInt(0);
            return new LlmResponse(content, prompt, completion, latencyMs);
        } catch (Exception e) {
            throw new LlmUnavailableException("GLM 响应解析失败: " + e.getMessage(), e);
        }
    }
}
