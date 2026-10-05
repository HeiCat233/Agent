package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import com.webagent.core.llm.ExplainService;
import com.webagent.core.store.WhitelistStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * APK 分析 Skill（A组差异化实现，ADR-006/010）：六阶段管线 + FR-09 注释树。
 * 契约入口 analyze() 按 analysisType 分派；预算耗尽 -> INCONCLUSIVE/BUDGET_EXCEEDED；
 * 任何工具/LLM 失败 -> UNCERTAIN/TOOL_FAILURE（不向用户暴露堆栈）。
 */
@Component
public class ApkAnalysisSkill implements Contracts.SkillContract {

    private final ApktoolRunner runner;
    private final SmaliIndexer indexer;
    private final Extractor extractor;
    private final Locator locator;
    private final EvidenceVerifier verifier;
    private final ConfidenceCalculator confidence;
    private final ExplainService explain;
    private final AnnotatedTreeGenerator treeGenerator;
    private final WhitelistStore whitelist;
    private final Path dataDir;

    public ApkAnalysisSkill(ApktoolRunner runner, SmaliIndexer indexer, Extractor extractor,
                            Locator locator, EvidenceVerifier verifier,
                            ConfidenceCalculator confidence, ExplainService explain,
                            AnnotatedTreeGenerator treeGenerator, WhitelistStore whitelist,
                            @Value("${webagent.skill-apk.data-dir:skill/data}") String dataDir) {
        this.runner = runner;
        this.indexer = indexer;
        this.extractor = extractor;
        this.locator = locator;
        this.verifier = verifier;
        this.confidence = confidence;
        this.explain = explain;
        this.treeGenerator = treeGenerator;
        this.whitelist = whitelist;
        this.dataDir = Path.of(dataDir);
    }

    @Override
    public Contracts.TargetType targetType() { return Contracts.TargetType.APK; }

    @Override
    public Contracts.SkillOutput analyze(Contracts.AnalysisRequest req, int budgetLeft) {
        String trace = "run_" + UUID.randomUUID().toString().substring(0, 8);
        var out = new Contracts.SkillOutput(Contracts.Status.ANALYZING);
        out.setAnalysisType(req.analysisType().name());
        out.setTraceId(trace);
        try {
            var target = whitelist.find(req.targetId())
                    .orElseThrow(() -> new IllegalStateException("目标不存在（PreGate 已校验，此处为防御性检查）"));
            Path apk = Path.of(target.apkPath());
            Path workdir = dataDir.resolve("work-" + req.targetId());
            Path indexDb = dataDir.resolve("index-" + req.targetId() + ".db");

            int steps = 0;
            // Stage 1+2：确保索引就绪（缓存复用；预算=decode 3 + index 2）
            if (!Files.isRegularFile(indexDb)) {
                runner.decode(apk, workdir); steps += 3;
                indexer.buildIndex(workdir, indexDb); steps += 2;
            }
            if (steps > budgetLeft) {
                out.setStatus(Contracts.Status.INCONCLUSIVE);
                out.setReasonCode(Contracts.ReasonCode.BUDGET_EXCEEDED);
                out.setStepsUsed(steps);
                return out;
            }
            switch (req.analysisType()) {
                case DIRECTORY_STRUCTURE -> fillDirectoryStructure(out, workdir);
                case COMPONENT_LIST -> fillComponentList(out, workdir);
                case FUNCTION_LOGIC, LEARN_QA -> fillFunctionLogic(out, req, indexDb, budgetLeft - steps);
                case CALL_GRAPH -> fillCallGraph(out, req, indexDb);
                case STRING_SEARCH -> fillStringSearch(out, req, indexDb);
                case DIRECTORY_REWRITE -> fillDirectoryRewrite(out, workdir, indexDb);
                default -> out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            }
            out.setStepsUsed(steps + out.getStepsUsed());
            return out;
        } catch (com.webagent.core.llm.LlmClient.LlmUnavailableException e) {
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            out.setExplanation("解释模型暂不可用（已重试），本次不编造结论。");
            out.setStepsUsed(1);
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            return out;
        } catch (Exception e) {
            out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.TOOL_FAILURE);
            out.setExplanation("工具执行失败：" + rootMessage(e) + "。本次不编造结论。");
            out.setStepsUsed(1);
            return out;
        }
    }

    // ---------- FR-02：目录结构（确定性，直接 CONFIRMED）----------
    private void fillDirectoryStructure(Contracts.SkillOutput out, Path workdir) throws Exception {
        out.setExplanation(runner.directoryTree(workdir, 3));
        out.setLearningNote("目录树来自 apktool 确定性输出；结合 DIRECTORY_REWRITE 获取注释版。");
        var cb = confidence.compute(1.0, 1.0, 0, false);
        out.setConfidence(cb);
        out.setStatus(Contracts.Status.CONFIRMED);
        out.setStepsUsed(1);
    }

    // ---------- FR-03：组件清单（确定性）----------
    private void fillComponentList(Contracts.SkillOutput out, Path workdir) throws Exception {
        Path manifest = workdir.resolve("AndroidManifest.xml");
        out.setExplanation(runner.componentList(manifest));
        out.addReference(new Contracts.Reference(manifest.toString(), "1-1"));
        var cb = confidence.compute(1.0, 1.0, 0, false);
        out.setConfidence(cb);
        out.setStatus(Contracts.Status.CONFIRMED);
        out.setStepsUsed(1);
    }

    // ---------- FR-04/07：函数解释（定位→提取→解释→回验→置信度）----------
    private void fillFunctionLogic(Contracts.SkillOutput out, Contracts.AnalysisRequest req,
                                   Path indexDb, int budgetLeft) throws Exception {
        try (Connection c = indexer.open(indexDb)) {
            List<Locator.Candidate> candidates = locator.locate(c, req.query());
            if (candidates.isEmpty()) {
                out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.NO_CANDIDATE);
                out.setExplanation("未在索引中定位到与问题相关的方法（不编造结论）。建议：换关键词，或先用目录注释重写了解结构。");
                out.setStepsUsed(1);
                return;
            }
            var fragments = extractor.extract(toMethods(candidates), Contracts.MAX_CANDIDATES);
            if (fragments.isEmpty()) {
                out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.NO_CANDIDATE);
                out.setStepsUsed(1);
                return;
            }
            var exp = explain.explain(Extractor.payload(fragments));
            out.setExplanation(exp.explanation());
            out.setPseudocode(exp.pseudocode());
            out.setLearningNote(exp.learningNote());
            out.setInjectionSuspected(exp.injectionSuspected());
            out.setTarget(candidates.get(0).method().className() + "#" + candidates.get(0).method().name());

            // Stage 6 回验：LLM references 必须落在片段内
            var vr = verifier.verify(exp.explanation() + "\n" + exp.pseudocode(), fragments);
            out.getEvidence().addAll(vr.verified());

            double coverage = vr.totalClaimed() == 0 ? 0.0 : vr.coverage();
            boolean obf = candidates.get(0).method().className().matches(".*(/([a-z])){2,};");
            var cb = confidence.compute(1.0, coverage, fragLines(fragments), obf);
            out.setConfidence(cb);
            out.setStatus(confidence.verdict(cb));
            if (out.getStatus() == Contracts.Status.CONFIRMED && out.getEvidence().isEmpty()) {
                out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.EVIDENCE_FAILED);
            }
            out.setStepsUsed(2);
        }
    }

    // ---------- FR-05：调用关系（类内方法清单，静态可达）----------
    private void fillCallGraph(Contracts.SkillOutput out, Contracts.AnalysisRequest req, Path indexDb) throws Exception {
        try (Connection c = indexer.open(indexDb)) {
            var methods = indexer.findByClass(c, "%" + guessClassToken(req.query()) + "%");
            if (methods.isEmpty()) {
                out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.NO_CANDIDATE);
                out.setStepsUsed(1);
                return;
            }
            StringBuilder sb = new StringBuilder("静态调用清单（类内方法，按索引顺序）：\n");
            String cls = null;
            for (var m : methods) {
                if (!m.className().equals(cls)) { cls = m.className(); sb.append("\n类: ").append(cls).append('\n'); }
                sb.append("  - ").append(m.name()).append(m.descriptor().substring(m.descriptor().indexOf('(')))
                  .append("  (smali ").append(m.file()).append(':').append(m.lineStart()).append('-').append(m.lineEnd()).append(")\n");
            }
            out.setExplanation(sb.toString());
            out.addReference(new Contracts.Reference(methods.get(0).file(),
                    methods.get(0).lineStart() + "-" + methods.get(0).lineEnd()));
            out.setConfidence(confidence.compute(1.0, 1.0, methods.size(), false));
            out.setStatus(Contracts.Status.CONFIRMED);
            out.setStepsUsed(1);
        }
    }

    // ---------- FR-06：字符串检索 ----------
    private void fillStringSearch(Contracts.SkillOutput out, Contracts.AnalysisRequest req, Path indexDb) throws Exception {
        try (Connection c = indexer.open(indexDb)) {
            List<String> toks = Locator.keywords(req.query());
            String kw = toks.isEmpty() ? req.query().trim() : toks.get(toks.size() - 1);
            var hits = indexer.findByString(c, "%" + Locator.escapeLike(kw) + "%");
            if (hits.isEmpty()) {
                out.downgrade(Contracts.Status.UNCERTAIN, Contracts.ReasonCode.NO_CANDIDATE);
                out.setExplanation("索引中未找到包含 \"" + kw + "\" 的字符串常量。");
                out.setStepsUsed(1);
                return;
            }
            StringBuilder sb = new StringBuilder("包含 \"" + kw + "\" 的方法（≤" + Contracts.MAX_CANDIDATES + "）：\n");
            for (var m : hits) {
                sb.append("- ").append(m.className()).append('#').append(m.name())
                  .append("  (").append(m.file()).append(':').append(m.lineStart()).append(")\n");
            }
            out.setExplanation(sb.toString());
            out.addReference(new Contracts.Reference(hits.get(0).file(), hits.get(0).lineStart() + "-" + hits.get(0).lineEnd()));            out.setConfidence(confidence.compute(1.0, 1.0, 0, false));
            out.setStatus(Contracts.Status.CONFIRMED);
            out.setStepsUsed(1);
        }
    }

    // ---------- FR-09：目录注释重写 ----------
    private void fillDirectoryRewrite(Contracts.SkillOutput out, Path workdir, Path indexDb) throws Exception {
        var result = treeGenerator.generate(workdir, indexDb);
        out.setAnnotatedTreeMd(result.markdown());
        out.setExplanation("注释版目录树已生成（可导出 .md）。摘要见 annotatedTree 字段。");
        out.setConfidence(confidence.compute(1.0, 1.0, 0, false));
        out.setStatus(Contracts.Status.CONFIRMED);
        out.setStepsUsed(1 + result.llmStepsUsed());
    }

    // ---------- helpers ----------
    private List<SmaliIndexer.IndexedMethod> toMethods(List<Locator.Candidate> candidates) {
        return candidates.stream().map(Locator.Candidate::method).toList();
    }

    private static int fragLines(List<Contracts.Fragment> fs) {
        return fs.stream().mapToInt(f -> f.lineEnd() - f.lineStart() + 1).sum();
    }

    private static String guessClassToken(String query) {
        for (String tok : query.split("[\\s，。？?！!,.]+")) {
            if (tok.matches(".*[A-Za-z].*") && tok.length() >= 4) return tok;
        }
        return "Activity";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
