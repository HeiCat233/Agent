package com.webagent.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享契约与枚举 —— 全系统唯一权威（与 design/ADR-004 严格一致）。
 *
 * 修改本文件 = 修改两组共享契约，必须经 B组评审（review/peer_review.md）并走 O5 流程。
 * 状态机与 reason_code 判别标准见 ADR-004；置信度规则见方案 4.7 节。
 * 语言无关：B组（小程序方向）按本契约的 JSON/状态机自行实现（ADR-006）。
 */
public final class Contracts {
    private Contracts() {}

    // ===== 置信度与预算常量（变更走 O5 流程，见 status_and_permissions.md）=====
    public static final double CONFIRMED_THRESHOLD = 0.75;
    public static final double UNCERTAIN_THRESHOLD = 0.40;
    public static final int DEFAULT_BUDGET = 50;
    public static final int MIN_BUDGET = 1;
    public static final int MAX_BUDGET = 100;
    public static final int SESSION_REJECT_LIMIT = 2;   // P6：同 session 被拒 >=2 次冻结

    /** ADR-006：APK=A组实现分析器，MINIPROGRAM=B组实现分析器（同一接口）。 */
    public enum TargetType { APK, MINIPROGRAM }

    public enum AnalysisType {
        DIRECTORY_STRUCTURE,   // FR-02
        COMPONENT_LIST,        // FR-03
        FUNCTION_LOGIC,        // FR-04
        CALL_GRAPH,            // FR-05
        STRING_SEARCH,         // FR-06
        DIRECTORY_REWRITE,     // FR-09 目录注释重写（ADR-005）
        LEARN_QA               // FR-07 学习问答（路由到上述类型）
    }

    /**
     * 状态机（ADR-004）：REJECTED=入口拒绝(分析未发生)；BLOCKED=出口拦截(分析已发生)；
     * UNCERTAIN=完成但诚实不确定(必须透传给用户)；INCONCLUSIVE=预算耗尽未完成。
     */
    public enum Status {
        REQUESTED, REJECTED, ANALYZING, CONFIRMED, UNCERTAIN, BLOCKED, INCONCLUSIVE
    }

    public enum ReasonCode {
        UNAUTHORIZED_TARGET,   // PreGate 目标不在白名单
        AUTH_MISSING,          // PreGate 未携带授权声明
        ATTACK_INTENT,         // PreGate L0 命中攻击请求
        SESSION_FROZEN,        // PreGate 会话累积被拒冻结
        BUDGET_INVALID,        // PreGate budget 参数非法
        PAYLOAD_DETECTED,      // PostGate 输出含可运行攻击内容
        BYPASS_CONTENT,        // PostGate 输出含绕过指令(段落删除)
        EVIDENCE_FAILED,       // PostGate 引用回验失实 -> UNCERTAIN
        BUDGET_EXCEEDED,       // Skill 步数耗尽 -> INCONCLUSIVE
        TOOL_FAILURE,          // Skill 工具/LLM 失败 -> UNCERTAIN
        NO_CANDIDATE           // Skill 定位无候选 -> UNCERTAIN
    }

    public enum IntentVerdict { LEARNING, ATTACK, UNCLEAR }

    // ===== 数据契约（JSON 形态见方案第六章）=====

    public record AnalysisRequest(
            String targetId,          // 白名单内目标 ID（SHA-256 前12位）
            TargetType targetType,
            AnalysisType analysisType,
            String query,
            int budget,               // [1,100]，默认 50；单位=工具+LLM 调用次数
            String sessionId,
            boolean authorizationAck) {}

    public record Reference(String file, String lines) {}   // lines 例："120-168"（含端点）

    /** Stage 4 提取的 smali 片段，是 Stage 6 / PostGate Q2 回验的比对基准。 */
    public record Fragment(String file, int lineStart, int lineEnd, String content) {}

    /** 置信度四项分解（规则计算，禁止 LLM 自报，方案 4.7）。 */
    public record ConfidenceBreakdown(double decompile, double evidence,
                                      double clarity, double obfuscation) {
        public double value() {
            return Math.round((0.40 * decompile + 0.30 * evidence
                    + 0.20 * clarity + 0.10 * obfuscation) * 100.0) / 100.0;
        }
    }

    /** Skill 输出（可变：PostGate 会降级/标注，故不用 record）。 */
    public static final class SkillOutput {
        private Status status;
        private ReasonCode reasonCode;
        private final List<Reference> evidence = new ArrayList<>();
        private ConfidenceBreakdown confidence;
        private boolean reviewRequired;
        private boolean injectionSuspected;
        private String traceId = "";

        public SkillOutput(Status status) { this.status = status; }

        /** PostGate Q2：引用回验失实 -> 降级为 UNCERTAIN（ADR-004）。 */
        public void downgrade(Status newStatus, ReasonCode reason) {
            this.status = newStatus;
            this.reasonCode = reason;
            if (newStatus == Status.UNCERTAIN) this.reviewRequired = true;
        }

        public Status getStatus() { return status; }
        public ReasonCode getReasonCode() { return reasonCode; }
        public List<Reference> getEvidence() { return evidence; }
        public ConfidenceBreakdown getConfidence() { return confidence; }
        public void setConfidence(ConfidenceBreakdown c) { this.confidence = c; }
        public boolean isReviewRequired() { return reviewRequired; }
        public void setReviewRequired(boolean v) { this.reviewRequired = v; }
        public boolean isInjectionSuspected() { return injectionSuspected; }
        public void setInjectionSuspected(boolean v) { this.injectionSuspected = v; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String t) { this.traceId = t; }
    }

    public record GateResult(boolean passed, Status status, ReasonCode reasonCode,
                             SkillOutput output, String userMessage) {
        public static GateResult pass() { return new GateResult(true, null, null, null, ""); }
        public static GateResult pass(SkillOutput out) { return new GateResult(true, null, null, out, ""); }
        public static GateResult reject(Status s, ReasonCode r, String msg) {
            return new GateResult(false, s, r, null, msg);
        }
    }

    /** 可插拔分析 Skill 接口（ADR-006）：skill-apk 与 B组分析器均实现本接口；
     *  共享核心不 import 任何分析器内部细节。 */
    public interface SkillContract {
        TargetType targetType();
        SkillOutput analyze(AnalysisRequest req, int budgetLeft);
    }
}
