package com.webagent.core.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 本地 Ollama 通道（正式/离线可选，ADR-009）：宿主机 Ollama 守护进程。
 * 模型与参数见 design/ADR-003 选择记录（temperature=0.2，num_ctx>=8192）。
 */
@Component
@ConditionalOnProperty(name = "webagent.llm.channel", havingValue = "local-ollama")
public class OllamaClient implements LlmClient {

    private final RestClient http;

    public OllamaClient(@Value("${webagent.llm.ollama.base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public LlmResponse explain(String systemPrompt, String untrustedCodePayload) {
        // TODO Phase 3：POST /api/generate 或 /api/chat（options: temperature=0.2, num_ctx=8192）
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    @Override
    public String classify(String query, String[] labels) {
        throw new UnsupportedOperationException("TODO Phase 4");
    }
}
