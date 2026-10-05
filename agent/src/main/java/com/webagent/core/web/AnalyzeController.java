package com.webagent.core.web;

import com.webagent.core.Contracts;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** FR-07/FR-04…：统一分析入口（同步 JSON；SSE 流式后置）。 */
@RestController
@RequestMapping("/analyze")
public class AnalyzeController {

    private final AgentService service;

    public AnalyzeController(AgentService service) { this.service = service; }

    @PostMapping
    public Map<String, Object> analyze(@RequestBody Map<String, Object> body) {
        var req = new Contracts.AnalysisRequest(
                str(body, "target_id"),
                Contracts.TargetType.valueOf(str(body, "target_type", "APK")),
                parseType(str(body, "analysis_type", "LEARN_QA")),
                str(body, "query"),
                body.get("budget") == null ? Contracts.DEFAULT_BUDGET : Integer.parseInt(String.valueOf(body.get("budget"))),
                str(body, "session_id", "sess_default"),
                Boolean.TRUE.equals(body.get("authorization_ack"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("authorization_ack"))));
        Contracts.SkillOutput out = service.analyze(req);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", out.getStatus().name());
        resp.put("reason_code", out.getReasonCode() == null ? null : out.getReasonCode().name());
        resp.put("analysis", Map.of(
                "type", out.getAnalysisType() == null ? "" : out.getAnalysisType(),
                "target", out.getTarget() == null ? "" : out.getTarget(),
                "explanation", nz(out.getExplanation()),
                "pseudocode", nz(out.getPseudocode()),
                "learning_note", nz(out.getLearningNote()),
                "annotated_tree_md", nz(out.getAnnotatedTreeMd()),
                "injection_suspected", out.isInjectionSuspected()));
        resp.put("evidence", out.getEvidence().stream()
                .map(r -> Map.of("file", r.file(), "lines", r.lines())).toList());
        resp.put("confidence", out.getConfidence() == null ? null : Map.of(
                "value", out.getConfidence().value(),
                "breakdown", Map.of("decompile", out.getConfidence().decompile(),
                        "evidence", out.getConfidence().evidence(),
                        "clarity", out.getConfidence().clarity(),
                        "obfuscation", out.getConfidence().obfuscation())));
        resp.put("review_required", out.isReviewRequired());
        resp.put("steps_used", out.getStepsUsed());
        resp.put("trace_id", out.getTraceId());
        return resp;
    }

    private static Contracts.AnalysisType parseType(String s) {
        try { return Contracts.AnalysisType.valueOf(s); }
        catch (Exception e) { return Contracts.AnalysisType.LEARN_QA; }
    }

    private static String str(Map<String, Object> m, String k) { return str(m, k, null); }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
