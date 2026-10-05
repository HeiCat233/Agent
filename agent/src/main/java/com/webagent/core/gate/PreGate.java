package com.webagent.core.gate;

import com.webagent.core.Contracts;
import com.webagent.core.store.SessionStore;
import com.webagent.core.store.WhitelistStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PreGate 入口关卡（ADR-001）：P1 白名单 P2 授权声明 P3/P4 意图过滤 P5 预算 P6 会话冻结。
 * 纯硬编码，不依赖 LLM；任何代码路径不得绕过（代码评审红线）。
 */
@Component
public class PreGate {

    private final WhitelistStore whitelist;
    private final SessionStore sessions;
    private final IntentFilter intentFilter;

    public PreGate(WhitelistStore whitelist, SessionStore sessions,
                   @Value("${webagent.intent-filter.l2-enabled:false}") boolean l2Enabled) {
        this.whitelist = whitelist;
        this.sessions = sessions;
        this.intentFilter = new IntentFilter(l2Enabled);
    }

    /** 供 REST /targets 调用：注册即入白名单（P2 在此强制）。 */
    public String registerTarget(String name, String sourceTag, String apkPath, boolean authorized) {
        return whitelist.register(name, sourceTag, apkPath, authorized);
    }

    public Contracts.GateResult check(Contracts.AnalysisRequest req) {
        String traceId = "pg_" + UUID.randomUUID().toString().substring(0, 8);
        String query = req.query() == null ? "" : req.query();

        // P6 会话冻结检查（冻结后除白名单错误外的一切请求直接拒绝）
        if (sessions.isFrozen(req.sessionId())) {
            sessions.audit(req.sessionId(), query, Contracts.Status.REJECTED,
                    Contracts.ReasonCode.SESSION_FROZEN, traceId);
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.SESSION_FROZEN, "会话已冻结（多次边界违规）");
        }
        // P1 白名单（规范化后精确匹配，防 T3 变体绕过）
        var target = whitelist.find(req.targetId());
        if (target.isEmpty()) {
            sessions.recordReject(req.sessionId(), query, Contracts.Status.REJECTED,
                    Contracts.ReasonCode.UNAUTHORIZED_TARGET, traceId);
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.UNAUTHORIZED_TARGET, "目标未在授权学习白名单内");
        }
        // P2 授权声明（注册时已强制，请求级再校验一次防伪造）
        if (!req.authorizationAck()) {
            sessions.recordReject(req.sessionId(), query, Contracts.Status.REJECTED,
                    Contracts.ReasonCode.AUTH_MISSING, traceId);
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.AUTH_MISSING, "缺少授权声明");
        }
        // P5 预算参数
        if (req.budget() < Contracts.MIN_BUDGET || req.budget() > Contracts.MAX_BUDGET) {
            sessions.recordReject(req.sessionId(), query, Contracts.Status.REJECTED,
                    Contracts.ReasonCode.BUDGET_INVALID, traceId);
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.BUDGET_INVALID, "budget 参数非法（1-100）");
        }
        // P3/P4 意图过滤：L0 硬拦截 / L1 豁免 / UNCLEAR 放行+audit
        IntentFilter.Verdict verdict = intentFilter.classify(query);
        if (verdict.verdict() == Contracts.IntentVerdict.ATTACK) {
            sessions.recordReject(req.sessionId(), query, Contracts.Status.REJECTED,
                    Contracts.ReasonCode.ATTACK_INTENT, traceId);
            return Contracts.GateResult.reject(Contracts.Status.REJECTED,
                    Contracts.ReasonCode.ATTACK_INTENT, "本系统仅用于学习用途的结构分析");
        }
        if (verdict.audit()) {
            sessions.audit(req.sessionId(), query, Contracts.Status.ANALYZING, null, traceId);
        }
        return Contracts.GateResult.pass();
    }
}
