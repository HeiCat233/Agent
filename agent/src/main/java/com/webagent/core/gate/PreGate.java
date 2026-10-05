package com.webagent.core.gate;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

/**
 * PreGate 入口关卡 —— 纯硬编码，不依赖 LLM（ADR-001/002）。
 * 任何代码路径不得绕过 PreGate（代码评审红线，见 status_and_permissions.md）。
 */
@Component
public class PreGate {

    private final IntentFilter intentFilter = new IntentFilter();
    private final Budget budget = new Budget();
    private final java.util.Set<String> whitelist = new java.util.HashSet<>();

    /** P1：target_id 规范化（trim/小写/去路径分隔），防 T3 白名单绕过。 */
    static String normalize(String targetId) {
        return targetId.trim().toLowerCase().replace("\\", "").replace("/", "");
    }

    public void register(String targetId) {
        whitelist.add(normalize(targetId));
    }

    public Contracts.GateResult check(Contracts.AnalysisRequest req) {
        String target = normalize(req.targetId());
        if (!whitelist.contains(target)) {                                     // P1
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.UNAUTHORIZED_TARGET, "目标未在授权学习白名单内");
        }
        if (!req.authorizationAck()) {                                         // P2
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.AUTH_MISSING, "缺少授权声明");
        }
        if (req.budget() < Contracts.MIN_BUDGET || req.budget() > Contracts.MAX_BUDGET) { // P5
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.BUDGET_INVALID, "budget 参数非法");
        }
        IntentFilter.Verdict verdict = intentFilter.classify(req.query());     // P3/P4
        if (verdict.verdict() == Contracts.IntentVerdict.ATTACK) {
            budget.recordReject(req.sessionId());
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.ATTACK_INTENT, "本系统仅用于学习用途的结构分析");
        }
        StringBuilder denyReason = new StringBuilder();
        if (!budget.allow(req.sessionId(), req.budget(), denyReason)) {        // P6
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.SESSION_FROZEN, "会话已冻结（多次边界违规）");
        }
        // TODO：verdict.audit()==true 时写审计日志（audit 表，append-only）
        return Contracts.GateResult.pass();
    }
}
