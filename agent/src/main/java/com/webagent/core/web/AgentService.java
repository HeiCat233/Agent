package com.webagent.core.web;

import com.webagent.core.Contracts;
import com.webagent.core.gate.PostGate;
import com.webagent.core.gate.PreGate;
import com.webagent.core.route.Router;
import com.webagent.core.store.SessionStore;
import org.springframework.stereotype.Service;

/**
 * 全链路编排（方案 3.2）：PreGate -> Router(解析+分发) -> Skill -> PostGate -> 预算核算/审计。
 * 唯一入口；Controller 不直接触碰关卡。
 */
@Service
public class AgentService {

    private final PreGate preGate;
    private final Router router;
    private final PostGate postGate;
    private final SessionStore sessions;

    public AgentService(PreGate preGate, Router router, PostGate postGate, SessionStore sessions) {
        this.preGate = preGate;
        this.router = router;
        this.postGate = postGate;
        this.sessions = sessions;
    }

    public Contracts.SkillOutput analyze(Contracts.AnalysisRequest req) {
        // ① 入口关卡（分析发生前拒绝）
        Contracts.GateResult pre = preGate.check(req);
        if (!pre.passed()) {
            var out = new Contracts.SkillOutput(pre.status());
            out.setReasonCode(pre.reasonCode());
            out.setExplanation(pre.userMessage());
            return out;
        }
        // ② 解析 + 分发到 Skill（预算单位=工具+LLM 调用）
        Contracts.AnalysisType type = router.resolve(req);
        Contracts.AnalysisRequest resolved = new Contracts.AnalysisRequest(
                req.targetId(), req.targetType(), type, req.query(),
                req.budget(), req.sessionId(), req.authorizationAck());
        Contracts.SkillOutput out;
        try {
            out = router.route(resolved, req.budget());
        } catch (Exception e) {
            out = new Contracts.SkillOutput(Contracts.Status.UNCERTAIN);
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
        }
        // ③ 出口关卡（LLM/工具输出不可信；Q2 回验需要片段——由 Skill 阶段内完成，
        //    此处复核状态一致性与最终审计）
        if (out.getStatus() == Contracts.Status.ANALYZING || out.getStatus() == Contracts.Status.REQUESTED) {
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
        }
        // ④ 预算核算 + 审计（append-only）
        sessions.consume(req.sessionId(), Math.max(1, out.getStepsUsed()));
        if (out.getStatus() == Contracts.Status.REJECTED || out.getStatus() == Contracts.Status.BLOCKED
                || out.isReviewRequired()) {
            sessions.audit(req.sessionId(), req.query(), out.getStatus(), out.getReasonCode(), out.getTraceId());
        }
        return out;
    }
}
