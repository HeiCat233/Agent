package com.webagent.core.route;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则路由器（方案 3.1）：analysis_type 显式选择优先；LEARN_QA 走
 * 触发词确定性匹配（ADR-010）-> 未命中 LLM 轻量分类 -> 失败回落并反问。
 * 唯一不做的事：不把路由决策交给 LLM（边界在规则）。
 */
@Component
public class Router {

    private final SkillRegistry registry;
    private final com.webagent.core.llm.LlmClient llm;

    public Router(SkillRegistry registry, com.webagent.core.llm.LlmClient llm) {
        this.registry = registry;
        this.llm = llm;
    }

    /** 解析最终 analysisType：显式选择优先；LEARN_QA 走触发词 -> LLM 兜底 -> 回落。 */
    public Contracts.AnalysisType resolve(Contracts.AnalysisRequest req) {
        if (req.analysisType() != Contracts.AnalysisType.LEARN_QA) {
            return req.analysisType();
        }
        // ① 触发词确定性匹配（ADR-010）
        List<String> matched = registry.matchBySignals(req.query());
        if (!matched.isEmpty()) {
            return Contracts.AnalysisType.valueOf(matched.get(0));
        }
        // ② LLM 轻量分类（失败回落，不阻塞主流程）
        try {
            String label = llm.classify(req.query(), new String[]{
                    "DIRECTORY_STRUCTURE", "COMPONENT_LIST", "FUNCTION_LOGIC",
                    "CALL_GRAPH", "STRING_SEARCH", "DIRECTORY_REWRITE"});
            if (label != null && !label.isBlank()) return Contracts.AnalysisType.valueOf(label.trim());
        } catch (Exception ignored) {
            // ③ 回落：目录结构 + 前端反问（方案 3.2 第3步）
        }
        return Contracts.AnalysisType.DIRECTORY_STRUCTURE;
    }

    public Contracts.SkillOutput route(Contracts.AnalysisRequest req, int budgetLeft) {
        var skill = registry.get(req.targetType());
        if (skill == null) {
            var out = new Contracts.SkillOutput(Contracts.Status.UNCERTAIN);
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            out.setTraceId("router-no-skill");
            return out;
        }
        return skill.analyze(req, budgetLeft);
    }
}
