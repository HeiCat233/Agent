package com.webagent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 契约测试（O2）：状态机语义、reason_code 全集、置信度公式与阈值。 */
class ContractsTest {

    @Test
    void reasonCode_全集11个且与ADR004一致() {
        assertEquals(11, Contracts.ReasonCode.values().length);
        for (Contracts.ReasonCode r : Contracts.ReasonCode.values()) assertNotNull(r.name());
    }

    @Test
    void 状态机_七个状态() {
        assertEquals(7, Contracts.Status.values().length);
    }

    @Test
    void 置信度_公式与阈值() {
        // 0.4*1.0 + 0.3*1.0 + 0.2*1.0 + 0.1*1.0 = 1.0 -> CONFIRMED（>=0.75）
        var full = new Contracts.ConfidenceBreakdown(1.0, 1.0, 1.0, 1.0);
        assertEquals(1.0, full.value());
        // 0.4*1 + 0.3*0.2 + 0.2*1 + 0.1*1 = 0.76 -> 仍 CONFIRMED
        var mid = new Contracts.ConfidenceBreakdown(1.0, 0.2, 1.0, 1.0);
        assertTrue(mid.value() >= 0.75);
        // 0.4*0.5 + 0.3*0.2 + 0.2*0.5 + 0.1*1 = 0.5 -> UNCERTAIN
        var low = new Contracts.ConfidenceBreakdown(0.5, 0.2, 0.5, 1.0);
        assertTrue(low.value() < 0.75 && low.value() >= 0.40);
    }

    @Test
    void downgrade_降级为UNCERTAIN时必须标记复核() {
        var out = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.EVIDENCE_FAILED);
        assertEquals(Contracts.Status.UNCERTAIN, out.getStatus());
        assertEquals(Contracts.ReasonCode.EVIDENCE_FAILED, out.getReasonCode());
        assertTrue(out.isReviewRequired(), "UNCERTAIN 必须 review_required（Q5 透传）");
    }
}
