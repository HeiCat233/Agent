package com.webagent.core.route;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

/**
 * 规则路由器 —— 不用 LLM 决定走哪个分析器（方案 3.1）。
 * 1) analysis_type 显式选择优先；自由文本（LEARN_QA）才走 LLM 轻量分类（3s 超时回落+反问）；
 * 2) 按 target_type 分发到可插拔 Skill（ADR-006）。
 */
@Component
public class Router {

    private final SkillRegistry registry;

    public Router(SkillRegistry registry) {
        this.registry = registry;
    }

    public Contracts.AnalysisType resolveAnalysisType(Contracts.AnalysisRequest req) {
        if (req.analysisType() != Contracts.AnalysisType.LEARN_QA) {
            return req.analysisType();
        }
        // TODO Phase 4：调用本地 LLM 3s 分类到具体 AnalysisType；
        //  超时/失败回落 DIRECTORY_STRUCTURE 并向前端反问（方案 3.2 第3步）
        throw new UnsupportedOperationException("LEARN_QA 的 LLM 轻量分类未实现");
    }

    public Contracts.SkillOutput route(Contracts.AnalysisRequest req, int budgetLeft) {
        var skill = registry.get(req.targetType());
        if (skill == null) {
            var out = new Contracts.SkillOutput(Contracts.Status.UNCERTAIN);
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            out.setTraceId("router-no-skill");
            return out;
        }
        // TODO Phase 1：resolveAnalysisType 写回后调用 skill.analyze(req, budgetLeft)
        return skill.analyze(req, budgetLeft);
    }
}
