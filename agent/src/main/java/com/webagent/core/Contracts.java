package com.webagent.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享契约 —— 全系统唯一权威（design/ADR-004）。
 * 修改本文件 = 修改两组共享契约，必须经 B组评审（review/peer_review.md）并走 O5 流程。
 */
public final class Contracts {
    private Contracts() {}

    // ===== 常量（变更走 O5 流程，见 status_and_permissions.md）=====
    public static final double CONFIRMED_THRESHOLD = 0.75;
    public static final double UNCERTAIN_THRESHOLD = 0.40;
    public static final int DEFAULT_BUDGET = 50;
    public static final int MIN_BUDGET = 1;
    public static final int MAX_BUDGET = 100;
    public static final int SESSION_REJECT_LIMIT = 2;
    public static final int MAX_FRAGMENT_LINES = 200;
    public static final int MAX_CANDIDATES = 5;

    public enum TargetType { APK, MINIPROGRAM }

    public enum AnalysisType {
        DIRECTORY_STRUCTURE, COMPONENT_LIST, FUNCTION_LOGIC,
        CALL_GRAPH, STRING_SEARCH, DIRECTORY_REWRITE, LEARN_QA
    }

    /** 状态机（ADR-004）：REJECTED=入口拒绝(分析未发生)；BLOCKED=出口拦截(分析已发生)；
     *  UNCERTAIN=完成但诚实不确定(必须透传)；INCONCLUSIVE=预算耗尽。 */
    public enum Status { REQUESTED, REJECTED, ANALYZING, CONFIRMED, UNCERTAIN, BLOCKED, INCONCLUSIVE }

    public enum ReasonCode {
        UNAUTHORIZED_TARGET, AUTH_MISSING, ATTACK_INTENT, SESSION_FROZEN, BUDGET_INVALID,
        PAYLOAD_DETECTED, BYPASS_CONTENT, EVIDENCE_FAILED,
        BUDGET_EXCEEDED, TOOL_FAILURE, NO_CANDIDATE
    }

    public enum IntentVerdict { LEARNING, ATTACK, UNCLEAR }

    // ===== 数据契约 =====

    public record AnalysisRequest(
            String targetId, TargetType targetType, AnalysisType analysisType,
            String query, int budget, String sessionId, boolean authorizationAck) {}

    public record Reference(String file, String lines) {}

    /** Stage 4 提取片段：Stage 6 / PostGate Q2 回验的比对基准。 */
    public record Fragment(String file, int lineStart, int lineEnd, String content) {}

    public record ConfidenceBreakdown(double decompile, double evidence,
                                      double clarity, double obfuscation) {
        public double value() {
            return Math.round((0.40 * decompile + 0.30 * evidence
                    + 0.20 * clarity + 0.10 * obfuscation) * 100.0) / 100.0;
        }
    }

    /** Skill 输出（可变：PostGate 会降级/删段/标注，故不用 record）。 */
    public static final class SkillOutput {
        private Status status;
        private ReasonCode reasonCode;
        private String analysisType;        // 实际执行的 AnalysisType.name()
        private String target;              // 方法/类/包标识
        private String explanation;
        private String pseudocode;
        private String learningNote;
        private String annotatedTreeMd;     // FR-09：注释树 Markdown（DIRECTORY_REWRITE）
        private boolean injectionSuspected;
        private final List<Reference> evidence = new ArrayList<>();
        private ConfidenceBreakdown confidence;
        private boolean reviewRequired;
        private String traceId = "";
        private int stepsUsed;              // 本次分析消耗的步数（P5 预算核算，单位=工具+LLM调用）

        public SkillOutput(Status status) { this.status = status; }

        /** PostGate Q2 / Stage 6：证据失实 -> 降级 UNCERTAIN（ADR-004）。 */
        public void downgrade(Status newStatus, ReasonCode reason) {
            this.status = newStatus;
            this.reasonCode = reason;
            if (newStatus == Status.UNCERTAIN) this.reviewRequired = true;
        }

        public Status getStatus() { return status; }
        public void setStatus(Status s) { this.status = s; }
        public ReasonCode getReasonCode() { return reasonCode; }
        public void setReasonCode(ReasonCode r) { this.reasonCode = r; }
        public String getAnalysisType() { return analysisType; }
        public void setAnalysisType(String t) { this.analysisType = t; }
        public String getTarget() { return target; }
        public void setTarget(String t) { this.target = t; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String e) { this.explanation = e; }
        public String getPseudocode() { return pseudocode; }
        public void setPseudocode(String p) { this.pseudocode = p; }
        public String getLearningNote() { return learningNote; }
        public void setLearningNote(String n) { this.learningNote = n; }
        public String getAnnotatedTreeMd() { return annotatedTreeMd; }
        public void setAnnotatedTreeMd(String md) { this.annotatedTreeMd = md; }
        public boolean isInjectionSuspected() { return injectionSuspected; }
        public void setInjectionSuspected(boolean v) { this.injectionSuspected = v; }
        public List<Reference> getEvidence() { return evidence; }
        public void addReference(Reference r) { evidence.add(r); }
        public ConfidenceBreakdown getConfidence() { return confidence; }
        public void setConfidence(ConfidenceBreakdown c) { this.confidence = c; }
        public boolean isReviewRequired() { return reviewRequired; }
        public void setReviewRequired(boolean v) { this.reviewRequired = v; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String t) { this.traceId = t; }
        public int getStepsUsed() { return stepsUsed; }
        public void setStepsUsed(int s) { this.stepsUsed = s; }
    }

    public record GateResult(boolean passed, Status status, ReasonCode reasonCode,
                             SkillOutput output, String userMessage) {
        public static GateResult pass() { return new GateResult(true, null, null, null, ""); }
        public static GateResult pass(SkillOutput out) { return new GateResult(true, null, null, out, ""); }
        public static GateResult reject(Status s, ReasonCode r, String msg) {
            return new GateResult(false, s, r, null, msg);
        }
    }

    /** 可插拔分析 Skill 接口（ADR-006/010）：A/B 两组分析器均实现本接口。 */
    public interface SkillContract {
        TargetType targetType();
        /** 预算单位=工具+LLM 调用次数；预算耗尽 -> INCONCLUSIVE/BUDGET_EXCEEDED。 */
        SkillOutput analyze(AnalysisRequest req, int budgetLeft);
    }
}
