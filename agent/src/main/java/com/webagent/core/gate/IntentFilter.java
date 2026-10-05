package com.webagent.core.gate;

import com.webagent.core.Contracts;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 三级意图过滤（ADR-002）：L0 硬拦截 -> L1 学习语境豁免 -> (L2 LLM 兜底，默认关闭)。
 * v1 教训：禁止裸子串匹配（"exp" 会命中 expected）；模式表改动须经负责人审定签字
 * （status_and_permissions.md）并记录变更。
 */
public class IntentFilter {

    /** L0 硬拦截：明确的攻击请求模式（初版；Red Team 后持续补充，变更记录进 ADR-002）。 */
    static final List<Pattern> L0_PATTERNS = List.of(
            Pattern.compile("(给我|帮我|替我).{0,6}(写|生成|做|弄).{0,8}(破解|注册机|keygen|exp\\b|exploit|脱壳)"),
            Pattern.compile("(绕过|bypass|patch)\\s*(这个|该|它的|this)?\\s*(校验|验证|登录|签名|signature)"),
            Pattern.compile("(提取|导出|dump).{0,6}(密钥|key|secret|flag)"),
            Pattern.compile("(写|生成).{0,6}(注入|exploit|payload)\\s*(脚本|代码)?"),
            Pattern.compile("(破解|crack).{0,6}(密码|口令|password)\\s*(是什么|多少)"),
            Pattern.compile("ignore (previous|above) (instructions|rules)")  // 英文注入直拦
    );

    /** L1 学习语境豁免：命中敏感词但带学习短语 -> 放行并转防御视角框架。 */
    static final List<String> L1_LEARNING_PHRASES = List.of(
            "如何防止", "怎么防", "怎样防", "防破解", "防篡改", "防御", "加固",
            "原理", "为什么", "是什么", "学习", "理解", "分析一下", "帮我看看", "帮我分析"
    );

    /** ADR-002：L2 默认关闭，上线走 O5 Change-01。 */
    private final boolean l2Enabled;

    public IntentFilter(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    /** @return 判定 + audit 标记（UNCLEAR 按 L1 放行并标 audit，交 PostGate 出口把关）。 */
    public Verdict classify(String query) {
        String q = query == null ? "" : query;
        for (Pattern p : L0_PATTERNS) {
            if (p.matcher(q).find()) return new Verdict(Contracts.IntentVerdict.ATTACK, false);
        }
        for (String phrase : L1_LEARNING_PHRASES) {
            if (q.contains(phrase)) return new Verdict(Contracts.IntentVerdict.LEARNING, false);
        }
        return new Verdict(Contracts.IntentVerdict.UNCLEAR, true);   // L2 关闭：放行+audit
    }

    public record Verdict(Contracts.IntentVerdict verdict, boolean audit) {}
}
