package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import com.webagent.core.gate.PostGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** skill-apk 单元测试：索引/定位/提取/回验（合成 smali fixture，无需真实 APK）。 */
class ApkPipelineTest {

    private static final String SMALI = """
            .class public Lcom/example/teach01/LoginActivity;
            .super Landroid/app/Activity;
            .method public checkPassword(Ljava/lang/String;)Z
                .locals 2
                const-string v0, "admin123"
                invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                return v0
            .end method
            .method public onCreate(Landroid/os/Bundle;)V
                .locals 1
                const-string v0, "login_success"
                invoke-static {v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method
            """;

    private Path workdirWithFixture() throws Exception {
        Path dir = Files.createTempDirectory("apktest");
        Path cls = dir.resolve("smali/com/example/teach01");
        Files.createDirectories(cls);
        Files.writeString(cls.resolve("LoginActivity.smali"), SMALI);
        return dir;
    }

    @Test
    void Stage2_索引_方法与字符串() throws Exception {
        Path dir = workdirWithFixture();
        SmaliIndexer indexer = new SmaliIndexer();
        Path db = indexer.buildIndex(dir, dir.resolve("index.db"));
        try (var c = indexer.open(db)) {
            var byStr = indexer.findByString(c, "%admin123%");
            assertEquals(1, byStr.size());
            assertEquals("checkPassword", byStr.get(0).name());
            assertEquals("com.example.teach01.LoginActivity", byStr.get(0).className());
            assertTrue(byStr.get(0).lineStart() > 0 && byStr.get(0).lineEnd() > byStr.get(0).lineStart());
        }
    }

    @Test
    void Stage3_定位_API指纹命中登录() throws Exception {
        Path dir = workdirWithFixture();
        SmaliIndexer indexer = new SmaliIndexer();
        Path db = indexer.buildIndex(dir, dir.resolve("index.db"));
        Locator locator = new Locator();
        try (var c = indexer.open(db)) {
            var candidates = locator.locate(c, "登录时口令是怎么校验的？");
            assertFalse(candidates.isEmpty(), "登录意图应通过 API 指纹命中");
        }
    }

    @Test
    void Stage3_定位_无候选_返回空() throws Exception {
        Path dir = workdirWithFixture();
        SmaliIndexer indexer = new SmaliIndexer();
        Path db = indexer.buildIndex(dir, dir.resolve("index.db"));
        Locator locator = new Locator();
        try (var c = indexer.open(db)) {
            assertTrue(locator.locate(c, "量子纠缠的原理是什么").isEmpty(),
                "无关问题不应产生候选（NO_CANDIDATE 路径）");
        }
    }

    @Test
    void Stage4_提取_片段与行号(@TempDir Path tmpUnused) throws Exception {
        Path dir = workdirWithFixture();
        SmaliIndexer indexer = new SmaliIndexer();
        Path db = indexer.buildIndex(dir, dir.resolve("index.db"));
        List<SmaliIndexer.IndexedMethod> ms;
        try (var c = indexer.open(db)) { ms = indexer.findByString(c, "%admin123%"); }
        var frags = new Extractor().extract(ms, 5);
        assertEquals(1, frags.size());
        Contracts.Fragment f = frags.get(0);
        assertTrue(f.content().contains("admin123"));
        assertTrue(f.lineStart() <= 4 && f.lineEnd() >= f.lineStart());
    }

    @Test
    void Stage6_回验_真实引用通过_编造引用删除() {
        var frag = new Contracts.Fragment("smali/com/example/teach01/LoginActivity.smali", 4, 10, "...");
        EvidenceVerifier v = new EvidenceVerifier();
        String llmOut = "references: smali/com/example/teach01/LoginActivity.smali:4-10, LoginActivity.smali:999-1001";
        var result = v.verify(llmOut, List.of(frag));
        assertEquals(2, result.totalClaimed());
        assertEquals(1, result.verified().size(), "编造的 999-1001 必须被删除");
        assertEquals(0.5, result.coverage());
    }

    @Test
    void Mock解释_无引用时走诚实降级路径() throws Exception {
        Path dir = workdirWithFixture();
        SmaliIndexer indexer = new SmaliIndexer();
        Path db = indexer.buildIndex(dir, dir.resolve("index.db"));
        List<SmaliIndexer.IndexedMethod> ms;
        try (var c = indexer.open(db)) { ms = indexer.findByString(c, "%admin123%"); }
        var frags = new Extractor().extract(ms, 5);
        String payload = Extractor.payload(frags);

        var mock = new com.webagent.core.llm.MockLlmClient();
        var out = mock.explain("sys", payload);
        var so = new Contracts.SkillOutput(Contracts.Status.CONFIRMED);
        so.setExplanation(out.content());
        var gate = new PostGate().review(so, frags);
        assertTrue(gate.passed());
        assertEquals(Contracts.Status.UNCERTAIN, so.getStatus(), "Mock 无标准引用输出必须走诚实降级路径");
        assertTrue(so.isReviewRequired());
    }

    @Test
    void 置信度_混淆名扣分_与低证据共同导致UNCERTAIN() {
        var calc = new ConfidenceCalculator();
        var clean = calc.compute(1.0, 1.0, 50, false);
        var obf = calc.compute(1.0, 1.0, 50, true);
        assertTrue(obf.value() < clean.value(), "混淆名必须降低置信度（0.1 权重）");
        assertEquals(Contracts.Status.CONFIRMED, calc.verdict(clean));
        // 混淆(0.1 权重扣分)单独不足以降档；与低证据覆盖叠加时降级：
        // 0.4*1 + 0.3*0.2 + 0.2*1 + 0.1*0.5 = 0.71 -> UNCERTAIN
        var obfLowEvidence = calc.compute(1.0, 0.2, 50, true);
        assertEquals(Contracts.Status.UNCERTAIN, calc.verdict(obfLowEvidence));
    }
}
