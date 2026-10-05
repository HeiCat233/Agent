package com.webagent.core.gate;

import com.webagent.core.Contracts;
import java.util.HashMap;
import java.util.Map;

/** P5 预算 + P6 会话累积（同 session 被拒 >= SESSION_REJECT_LIMIT 次冻结，ADR-001）。 */
public class Budget {
    private final Map<String, Integer> used = new HashMap<>();      // session_id -> 已用步数
    private final Map<String, Integer> rejected = new HashMap<>();  // session_id -> 被拒次数

    public boolean allow(String sessionId, int budget, StringBuilder denyReason) {
        if (rejected.getOrDefault(sessionId, 0) >= Contracts.SESSION_REJECT_LIMIT) {
            denyReason.append("SESSION_FROZEN");
            return false;
        }
        // TODO Phase 4：步数累计与 budget 比对（Skill 每步消耗上报）
        used.putIfAbsent(sessionId, 0);
        return true;
    }

    public void recordReject(String sessionId) {
        rejected.merge(sessionId, 1, Integer::sum);
    }

    public void consume(String sessionId, int steps) {
        used.merge(sessionId, steps, Integer::sum);
    }
}
