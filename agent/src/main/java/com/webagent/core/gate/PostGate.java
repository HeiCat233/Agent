package com.webagent.core.gate;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostGate 出口关卡（ADR-001）：Q1 payload 扫描 Q2 引用回验 Q3 指令性内容删除
 * Q4 置信度复核 Q5 UNCERTAIN 透传（修正 v1 的 BLOCK 矛盾）。
 */
@Component
public class PostGate {

    /** Q1 payload 扫描模式表（Red Team 后持续补充，进 finding_log）。 */
    static final List<Pattern> PAYLOAD_PATTERNS = List.of(
            Pattern.compile("flag\\{[^}]{1,120}\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CTF\\{[^}]{1,120}\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("```\\w*\\n[\\s\\S]{0,2000}?\\b(import\\s+\\w+|def\\s+\\w+|function\\s+\\w+)\\b[\\s\\S]*```"),
            Pattern.compile("\\b[A-Za-z0-9+/]{240,}={0,2}\\b")   // 大块 base64（阈值取宽防误伤）
    );

    /** Q3 指令性内容特征（方案 1.2 灰区规则：绕过/patch/提取 的祈使句）。 */
    static final List<Pattern> BYPASS_PATTERNS = List.of(
            Pattern.compile("^\\s*(步骤\\d*|Step\\s*\\d*|第.{1,3}步)[:：].*(绕过|bypass|patch|提权).*$", Pattern.MULTILINE),
            Pattern.compile("(要|怎么|如何)绕过[^\n]{0,40}(校验|验证|登录|签名)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(提取|导出|dump)[^\n]{0,20}(密钥|key|secret)并", Pattern.CASE_INSENSITIVE)
    );

    /** Q1：命中 -> BLOCKED。扫描 explanation/pseudocode/learning_note 全部文本字段。 */
    public boolean payloadScan(Contracts.SkillOutput out) {
        String text = textOf(out);
        for (Pattern p : PAYLOAD_PATTERNS) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    /** Q2 引用回验：每条 file+lines 必须真实存在于本次提取片段（防幻觉，与 Stage 6 同机制）。 */
    public boolean verifyReferences(Contracts.SkillOutput out, List<Contracts.Fragment> fragments) {
        if (out.getEvidence().isEmpty()) return false;            // 无引用 = 不合格
        for (Contracts.Reference ref : out.getEvidence()) {
            boolean covered = fragments.stream().anyMatch(f ->
                    f.file().equals(ref.file()) && linesOverlap(f, ref.lines()));
            if (!covered) return false;
        }
        return true;
    }

    /** Q3：删除指令性段落（按行扫描，命中 BYPASS_PATTERNS 的整行删除），保留其余描述性内容。 */
    public void stripBypassContent(Contracts.SkillOutput out) {
        out.setExplanation(stripLines(out.getExplanation()));
        out.setPseudocode(stripLines(out.getPseudocode()));
    }

    /** 出口复检主流程。通过时原样返回 output；拦截时返回 BLOCKED 的 GateResult。 */
    public Contracts.GateResult review(Contracts.SkillOutput out, List<Contracts.Fragment> fragments) {
        if (payloadScan(out)) {                                                    // Q1
            return Contracts.GateResult.reject(Contracts.Status.BLOCKED,
                    Contracts.ReasonCode.PAYLOAD_DETECTED, "输出含潜在攻击性内容，已被拦截");
        }
        if (!verifyReferences(out, fragments)) {                                   // Q2
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.EVIDENCE_FAILED);
        }
        stripBypassContent(out);                                                   // Q3
        // Q4：置信度复核（由 AgentService 用规则重算值比对，见 computeConfidence 一致性）
        if (out.getStatus() == Contracts.Status.UNCERTAIN) {                       // Q5 透传
            out.setReviewRequired(true);
        }
        return Contracts.GateResult.pass(out);
    }

    // ---------- helpers ----------

    static String textOf(Contracts.SkillOutput o) {
        return String.join("\n", o.getExplanation() == null ? "" : o.getExplanation(),
                o.getPseudocode() == null ? "" : o.getPseudocode(),
                o.getLearningNote() == null ? "" : o.getLearningNote());
    }

    static boolean linesOverlap(Contracts.Fragment f, String range) {
        int[] lr = parseRange(range);
        if (lr == null) return f.lineStart() <= 1;               // 无行号引用只验证文件存在
        return lr[0] <= f.lineEnd() && lr[1] >= f.lineStart();
    }

    /** "120-168" -> [120,168]；单行 "120" -> [120,120]；解析失败返回 null。 */
    public static int[] parseRange(String lines) {
        if (lines == null) return null;
        Matcher m = Pattern.compile("(\\d+)\\s*(?:[-~]\\s*(\\d+))?").matcher(lines.trim());
        if (!m.find()) return null;
        int a = Integer.parseInt(m.group(1));
        int b = m.group(2) == null ? a : Integer.parseInt(m.group(2));
        return a <= b ? new int[]{a, b} : new int[]{b, a};
    }

    private static String stripLines(String text) {
        if (text == null || text.isBlank()) return text;
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean hit = BYPASS_PATTERNS.stream().anyMatch(p -> p.matcher(line).find());
            if (!hit) sb.append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
