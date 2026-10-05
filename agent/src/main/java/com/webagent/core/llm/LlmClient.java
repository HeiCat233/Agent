package com.webagent.core.llm;

import com.webagent.core.Contracts;

/**
 * LLM 调用唯一抽象（ADR-009 双通道）：cloud-glm（调试默认）/ local-ollama（正式可选）。
 * 四条铁律提示词（design/ai_rules.md）与边界检查对两通道一致生效——切换通道不改变任何边界行为。
 */
public interface LlmClient {

    /** 发送受限解释请求：systemPrompt + <untrusted_code> 包裹的片段。
     *  超时 60s 重试 1 次，仍失败抛 LlmUnavailableException -> UNCERTAIN/TOOL_FAILURE。 */
    LlmResponse explain(String systemPrompt, String untrustedCodePayload);

    /** 可选：LEARN_QA 意图三分类兜底（3s 超时），仅当 skill.json 触发词未命中时调用。 */
    String classify(String query, String[] labels);

    enum Channel { CLOUD_GLM, LOCAL_OLLAMA }

    record LlmResponse(String content, int promptTokens, int completionTokens, long latencyMs) {}

    class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
