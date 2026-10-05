package com.webagent.core.gate;

import com.webagent.core.Contracts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** PostGate 行为测试（验收 R03/R07 + Q5 透传）。 */
class PostGateTest {

    private final PostGate gate = new PostGate();

    private Contracts.Fragment frag() {
        String smali = """
                .class public Lcom/example/teach01/LoginActivity;
                .super Landroid/app/Activity;
                .method public checkPassword(Ljava/lang/String;)Z
                    const-string v0, "admin123"
                    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    return v0
                .end method""";
        return new Contracts.Fragment("smali/com/example/teach01/LoginActivity.smali", 3, 9, smali);
    }

    @Test
    void Q1_flag输出_BLOCKED() {
        var out = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        out.setExplanation("答案是 flag{this_is_flag}");
        var r = gate.review(out, List.of(frag()));
        assertFalse(r.passed());
        assertEquals(Contracts.Status.BLOCKED, r.status());
        assertEquals(Contracts.ReasonCode.PAYLOAD_DETECTED, r.reasonCode());
    }

    @Test
    void Q2_无引用_降级UNCERTAIN() {
        var out = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        out.setExplanation("此方法比较用户输入与硬编码常量。");
        var r = gate.review(out, List.of(frag()));
        assertTrue(r.passed());
        assertEquals(Contracts.Status.UNCERTAIN, out.getStatus());
        assertEquals(Contracts.ReasonCode.EVIDENCE_FAILED, out.getReasonCode());
        assertTrue(out.isReviewRequired(), "UNCERTAIN 透传给用户");
    }

    @Test
    void Q2_有效引用_保持CONFIRMED() {
        var out = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        out.setExplanation("此方法将输入与常量比较。");
        out.addReference(new Contracts.Reference("smali/com/example/teach01/LoginActivity.smali", "3-9"));
        var r = gate.review(out, List.of(frag()));
        assertTrue(r.passed());
        assertEquals(Contracts.Status.CONFIRMED, out.getStatus());
        assertFalse(out.isReviewRequired());
    }

    @Test
    void Q3_指令性行_被删除() {
        var out = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        out.setExplanation("方法做了常量比较。\n步骤1：修改smali绕过签名校验\n此外该方法调用了equals。");
        out.addReference(new Contracts.Reference("smali/com/example/teach01/LoginActivity.smali", "3-9"));
        gate.stripBypassContent(out);
        assertFalse(out.getExplanation().contains("绕过"), "指令性行必须被删除");
        assertTrue(out.getExplanation().contains("常量比较"));
    }

    @Test
    void 行号解析_区间与单行() {
        assertArrayEquals(new int[]{120, 168}, PostGate.parseRange("120-168"));
        assertArrayEquals(new int[]{42, 42}, PostGate.parseRange("42"));
        assertNull(PostGate.parseRange("abc"));
    }
}
