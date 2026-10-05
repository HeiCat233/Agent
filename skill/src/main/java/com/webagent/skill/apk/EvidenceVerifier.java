package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 6 证据回验（防幻觉关键）：解析 LLM 输出的 references 行，
 * 每条 file:lines 必须真实存在于本次提取片段；失实 -> 删除该引用并上报。
 * 若全部失实或无引用 -> 降级 UNCERTAIN/EVIDENCE_FAILED（由调用方执行）。
 */
@Component
public class EvidenceVerifier {

    /** references 行格式：references: file.smali:120-168（可多条，逗号/分号分隔）。 */
    private static final Pattern REF = Pattern.compile("([^:\\s]+\\.smali):(\\d+)\\s*-\\s*(\\d+)|([^:\\s]+\\.smali):(\\d+)");

    public record Result(List<Contracts.Reference> verified, int totalClaimed) {
        public double coverage() { return totalClaimed == 0 ? 0 : (double) verified.size() / totalClaimed; }
    }

    public Result verify(String llmOutput, List<Contracts.Fragment> fragments) {
        List<Contracts.Reference> verified = new ArrayList<>();
        int claimed = 0;
        Matcher m = REF.matcher(llmOutput == null ? "" : llmOutput);
        while (m.find()) {
            claimed++;
            String file = m.group(1) != null ? m.group(1) : m.group(4);
            int a = m.group(1) != null ? Integer.parseInt(m.group(2)) : Integer.parseInt(m.group(5));
            int b = m.group(1) != null ? Integer.parseInt(m.group(3)) : a;
            int lo = Math.min(a, b), hi = Math.max(a, b);
            boolean ok = fragments.stream().anyMatch(f ->
                    f.file().endsWith(file) || file.endsWith(fileNameOf(f.file()))) &&
                    lo <= f(fragments, file).lineEnd() && hi >= f(fragments, file).lineStart();
            if (ok) verified.add(new Contracts.Reference(file, lo + "-" + hi));
        }
        return new Result(verified, claimed);
    }

    private Contracts.Fragment f(List<Contracts.Fragment> fs, String file) {
        return fs.stream().filter(x -> x.file().endsWith(file) || file.endsWith(fileNameOf(x.file())))
                .findFirst().orElse(new Contracts.Fragment(file, 0, Integer.MAX_VALUE, ""));
    }

    private static String fileNameOf(String path) {
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i < 0 ? path : path.substring(i + 1);
    }
}
