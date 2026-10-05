package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

/**
 * 置信度规则计算（方案 4.7，禁止 LLM 自报）：
 * 0.40×decompile + 0.30×evidence + 0.20×clarity + 0.10×(1-obfuscation)
 * ≥0.75 CONFIRMED；0.40-0.75 UNCERTAIN；<0.40 UNCERTAIN+提示更换问法。
 */
@Component
public class ConfidenceCalculator {

    public Contracts.ConfidenceBreakdown compute(double decompileQuality, double evidenceCoverage,
                                                 int methodLines, boolean obfuscatedName) {
        double clarity = methodLines <= 0 ? 0.5
                : (methodLines <= 150 ? 1.0 : Math.max(0.0, 1.0 - (methodLines - 150) / 300.0));
        double obf = obfuscatedName ? 0.5 : 1.0;
        return new Contracts.ConfidenceBreakdown(
                clamp(decompileQuality), clamp(evidenceCoverage), clarity, obf);
    }

    public Contracts.Status verdict(Contracts.ConfidenceBreakdown cb) {
        double v = cb.value();
        if (v >= Contracts.CONFIRMED_THRESHOLD) return Contracts.Status.CONFIRMED;
        return Contracts.Status.UNCERTAIN;     // <0.75 一律 UNCERTAIN（诚实路径）
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
