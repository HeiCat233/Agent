package com.webagent.core.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GLM 云端通道（调试默认，ADR-009）：智谱 BigModel 的 OpenAI 兼容端点。
 * API key 只经环境变量 ZHIPUAI_API_KEY 或 application-local.yml 注入，绝不入 git。
 */
@Component
@Primary
public class GlmClient implements LlmClient {

    private final RestClient http;

    public GlmClient(@Value("${webagent.llm.cloud-glm.base-url}") String baseUrl,
                     @Value("${ZHIPUAI_API_KEY:}") String apiKey) {
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public LlmResponse explain(String systemPrompt, String untrustedCodePayload) {
        // TODO Phase 3：POST /chat/completions（model=glm-4-flash，temperature=0.2）；
        //  解析 OpenAI 兼容响应；超时 60s 重试 1 次 -> LlmUnavailableException
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    @Override
    public String classify(String query, String[] labels) {
        // TODO Phase 4：LEARN_QA 触发词未命中时的兜底三分类（3s 超时）
        throw new UnsupportedOperationException("TODO Phase 4");
    }
}
