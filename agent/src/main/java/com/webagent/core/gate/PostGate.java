package com.webagent.core.gate;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * PostGate 出口关卡 —— LLM 输出不可信，返回前全部复检（ADR-001）。
 * 关键行为：UNCERTAIN 必须透传给用户（Q5，修正 v1 的 BLOCK 矛盾）；
 * 只有含可运行攻击内容的输出才 BLOCKED（Q1）。
 */
@Component
public class PostGate {

    /** Q1 payload 扫描模式表（Red Team 后持续补充）。 */
    static final Pattern[] PAYLOAD_PATTERNS = {
            Pattern.compile("flag\\{.*\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CTF\\{.*\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("```[\\s\\S]*(import |def |function )[\\s\\S]*```"), // 完整脚本特征
            Pattern.compile("\\b[A-Za-z0-9+/]{200,}={0,2}\\b")                   // 大块 base64
    };

    /** Q3 指令性内容特征（方案 1.2 灰区规则：绕过/patch/提取 的祈使句）。 */
    static final Pattern[] BYPASS_PATTERNS = {
            Pattern.compile("(如何|怎么|steps to).{0,8}(绕过|bypass|patch)"),
            Pattern.compile("(提取|导出).{0,6}(密钥|key|secret)")
    };

    /** Q1：返回 true 表示命中攻击性内容（-> BLOCKED）。 */
    public boolean payloadScan(Contracts.SkillOutput out) {
        // TODO：连同 pseudocode/evidence 一并扫描（当前仅示意 evidence 文本）
        String text = out.getEvidence().toString() + out.getTraceId();
        for (Pattern p : PAYLOAD_PATTERNS) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    /** Q2 证据回验：每条 file:line 必须真实存在于本次提取片段（防幻觉，与 Stage 6 同机制）。 */
    public boolean verifyReferences(Contracts.SkillOutput out, List<Contracts.Fragment> fragments) {
        var validFiles = fragments.stream().map(Contracts.Fragment::file).collect(java.util.stream.Collectors.toSet());
        if (out.getEvidence().isEmpty() || fragments.isEmpty()) return false;
        for (Contracts.Reference ref : out.getEvidence()) {
            // TODO Phase 4：解析 ref.lines 行号区间并与片段行号范围求交
            if (!validFiles.contains(ref.file())) return false;
        }
        return true;
    }

    /** Q3：删除指令性段落（绕过/patch/提取密钥），保留其余描述性内容。 */
    public void stripBypassContent(Contracts.SkillOutput out) {
        // TODO Phase 4：按段落定位 BYPASS_PATTERNS 命中并删除
    }

    /** 出口复检主流程（Q1-Q5）。 */
    public Contracts.GateResult review(Contracts.SkillOutput out, List<Contracts.Fragment> fragments) {
        if (payloadScan(out)) {                                                // Q1
            return Contracts.GateResult.reject(Contracts.Status.BLOCKED,
                    Contracts.ReasonCode.PAYLOAD_DETECTED, "输出含潜在攻击性内容，已被拦截");
        }
        if (!verifyReferences(out, fragments)) {                               // Q2
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.EVIDENCE_FAILED);
        }
        stripBypassContent(out);                                               // Q3
        // TODO Q4：置信度复核——out.confidence.value() 必须等于规则重算值
        if (out.getStatus() == Contracts.Status.UNCERTAIN) {                   // Q5 透传
            out.setReviewRequired(true);
        }
        return Contracts.GateResult.pass(out);
    }
}
