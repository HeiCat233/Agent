package com.webagent.core.gate;

import com.webagent.core.Contracts;
import java.util.regex.Pattern;

/**
 * 三级意图过滤（ADR-002）：L0 硬拦截 -> L1 学习语境豁免 -> (L2 LLM 兜底，默认关闭)。
 *
 * v1 教训：禁止对 "exp" 等做裸子串匹配（会命中 expected/experiment，误判率必超 5%）。
 * L0/L1 模式表正式版须由负责人审定签字（status_and_permissions.md）。
 */
public class IntentFilter {

    /** L0 硬拦截模式（初版示例，Red Team 后持续补充）。 */
    static final Pattern[] L0_PATTERNS = {
            Pattern.compile("(给我|帮我).{0,6}(写|生成|做).{0,8}(破解|注册机|keygen|exp)"),
            Pattern.compile("绕过(这个|该|它的)?(校验|验证|登录|签名)"),
            Pattern.compile("提取(密钥|key|flag)")          // 无学习语境修饰时
    };

    /** L1 学习语境豁免短语：命中敏感词但带这些短语 -> 放行，转防御视角框架。 */
    static final String[] L1_LEARNING_PHRASES = {
            "如何防止", "怎么防", "原理", "为什么", "学习", "理解", "分析", "是什么"
    };

    /** ADR-002：L2 默认关闭，上线须走 O5 Change-01。 */
    static final boolean L2_ENABLED = false;

    /** @return [判定, audit 标记]；UNCLEAR 按 L1 放行并标 audit（宁可放过给 PostGate 审）。 */
    public Verdict classify(String query) {
        for (Pattern p : L0_PATTERNS) {
            if (p.matcher(query).find()) {
                return new Verdict(Contracts.IntentVerdict.ATTACK, false);
            }
        }
        for (String phrase : L1_LEARNING_PHRASES) {
            if (query.contains(phrase)) {
                return new Verdict(Contracts.IntentVerdict.LEARNING, false);
            }
        }
        if (L2_ENABLED) {
            // TODO：本地 LLM 三分类（攻击/学习/无关），3s 超时；失败按 L1 放行 + audit=true
            throw new UnsupportedOperationException("L2 未实现（默认关闭，见 ADR-002）");
        }
        return new Verdict(Contracts.IntentVerdict.UNCLEAR, true);
    }

    public record Verdict(Contracts.IntentVerdict verdict, boolean audit) {}
}
