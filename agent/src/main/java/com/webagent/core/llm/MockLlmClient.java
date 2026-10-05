package com.webagent.core.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 通道（开发/测试默认，无 key 可跑通全链路）：确定性响应。
 * 行为=从输入片段提取可验证信息生成解释，保证引用回验（Stage 6/Q2）可通过。
 * 真通道切换：application.yml 中 webagent.llm.channel=cloud-glm | local-ollama。
 */
@Component
@ConditionalOnProperty(name = "webagent.llm.channel", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResponse explain(String systemPrompt, String untrustedCodePayload) {
        // 从 payload 中提取方法行数与 const-string，生成可回验的描述性输出
        int lines = countLines(untrustedCodePayload);
        String firstConst = firstMatch(untrustedCodePayload, "const-string(?:/jumbo)?\\s+v\\d+,\\s+\"([^\"]{1,60})\"");
        String firstInvoke = firstMatch(untrustedCodePayload, "invoke-\\w+(?:/range)?\\s+\\{[^}]*\\},\\s+(L[^;]+;)->\\S+");

        StringBuilder exp = new StringBuilder();
        exp.append("【MOCK 解释】该方法约 ").append(lines).append(" 行（smali）。");
        if (firstConst != null) {
            exp.append("方法内包含字符串常量 \"").append(firstConst)
               .append("\"（mock 提取，引用见下方片段首行）。");
        }
        if (firstInvoke != null) {
            exp.append("调用了 ").append(firstInvoke).append("。");
        }
        exp.append("此为 MockLlmClient 的确定性输出，用于开发调试与契约测试；")
           .append("真实解释请切换 webagent.llm.channel=cloud-glm。");

        String out = new StringBuilder()
                .append("explanation: ").append(exp).append("\n")
                .append("references: ").append(firstConst == null ? "[片段首行]" : "\"" + firstConst + "\" 所在行")
                .append("\n").toString();
        return new LlmResponse(out.toString(), estimateTokens(untrustedCodePayload), estimateTokens(out.toString()), 5);
    }

    /** 三分类兜底：mock 用关键词匹配，保证 Router 测试确定性。 */
    @Override
    public String classify(String query, String[] labels) {
        String q = query == null ? "" : query.toLowerCase();
        if (q.contains("目录") || q.contains("结构")) return "DIRECTORY_STRUCTURE";
        if (q.contains("调用") || q.contains("图")) return "CALL_GRAPH";
        if (q.contains("字符串") || q.contains("常量")) return "STRING_SEARCH";
        if (q.contains("重写") || q.contains("注释树")) return "DIRECTORY_REWRITE";
        if (q.contains("组件")) return "COMPONENT_LIST";
        return "FUNCTION_LOGIC";
    }

    private static int countLines(String s) {
        return s == null ? 0 : (int) s.lines().count();
    }

    private static String firstMatch(String s, String regex) {
        if (s == null) return null;
        var m = java.util.regex.Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static int estimateTokens(String s) {
        return s == null ? 0 : Math.max(1, s.length() / 4);
    }
}
