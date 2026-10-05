package com.webagent.skill.apk;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

/**
 * APK 分析 Skill（A组差异化实现）—— 6 阶段管线 + 目录注释重写（FR-09）。
 *
 * 设计依据：方案第四章（管线）、ADR-005（注释重写）、ADR-006（可插拔接口）、
 * ADR-008（baksmali 原生 Java 库用于 Stage 2/4）。
 * 本文件为骨架：签名/阶段切分已定，实现交给 Phase 3（见实施路线图）。
 */
@Component
public class ApkAnalysisSkill implements Contracts.SkillContract {

    static final int MAX_FRAGMENT_LINES = 200;
    static final int MAX_CANDIDATES = 5;

    @Override
    public Contracts.TargetType targetType() {
        return Contracts.TargetType.APK;
    }

    // ---------- Stage 1 解码 ----------
    /** apktool d -f <apk> -o <workdir>（Docker 沙箱：无网络/只读挂载/120s 超时，ADR-007）。
     *  产出：目录树（FR-02）+ AndroidManifest.xml 组件清单（FR-03）。
     *  失败 -> UNCERTAIN / TOOL_FAILURE（不向用户暴露堆栈）。 */
    public void stage1Decode(String apkPath, String workdir) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- Stage 2 索引 ----------
    /** 遍历 smali（建议 baksmali 库，ADR-008）建 SQLite 索引：
     *  classes / methods / strings / api_calls。一次构建多次查询，同 target 只建一次（缓存）。 */
    public void stage2BuildIndex(String workdir, String dbPath) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- Stage 3 定位 ----------
    /** 三种策略按序：字符串反查 -> API指纹 -> 入口点追踪。候选<=MAX_CANDIDATES。
     *  空候选 -> UNCERTAIN / NO_CANDIDATE（禁止编造）。 */
    public java.util.List<String> stage3Locate(String query, String dbPath) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- Stage 4 证据提取 ----------
    /** 切 smali 片段（带 file+行号范围），单片段<=MAX_FRAGMENT_LINES 行，
     *  超出取方法签名+关键调用子图。 */
    public java.util.List<Contracts.Fragment> stage4Extract(java.util.List<String> candidates, String dbPath) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- Stage 5 LLM 受限解释 ----------
    /** 调用本地 LLM（Spring RestClient 直连 Ollama，ADR-003/008）。输入以
     *  <untrusted_code> 数据包裹，系统提示词全文=design/ai_rules.md（四条铁律，不可修改）。
     *  超时 60s 重试 1 次 -> UNCERTAIN / TOOL_FAILURE。 */
    public Contracts.SkillOutput stage5LlmExplain(java.util.List<Contracts.Fragment> fragments, String query) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- Stage 6 证据回验 ----------
    /** 逐条校验 LLM 引用的 file:line 真实存在于片段；失实 -> 删除该引用并降级。 */
    public boolean stage6Verify(Contracts.SkillOutput out, java.util.List<Contracts.Fragment> fragments) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- 置信度（规则计算，禁止 LLM 自报，方案 4.7）----------
    /** 0.40*decompile + 0.30*evidence + 0.20*clarity + 0.10*(1-obfuscation)。
     *  >=0.75 CONFIRMED；0.40-0.75 UNCERTAIN；<0.40 UNCERTAIN+提示更换问法。 */
    public Contracts.ConfidenceBreakdown computeConfidence(double decompileOk, double evidenceCoverage,
                                                           int methodLines, boolean obfuscated) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- FR-09 目录注释重写（ADR-005）----------
    /** 注释版目录树。关键设计：规则优先、LLM 最小化。
     *  1) 规则骨架（不经 LLM）：顶级包/类数量/组件归属/lib+assets+res 标准语义；
     *  2) LLM 仅做包级用途注释（输入数据包裹，复用 ai_rules 铁律）；
     *  3) 混淆名推断必须标 inference=true 并扣置信度；
     *  4) 关键文件高亮：MainActivity/自定义Application/lib/*.so/加固特征文件；
     *  5) LLM 注释中每个类名/文件路径经索引回验，失实删除。
     *  前端可渲染并导出 .md。只读分析：禁止重命名/重打包/触碰原样本。 */
    public void generateAnnotatedTree(String workdir, String dbPath) {
        throw new UnsupportedOperationException("TODO Phase 3");
    }

    // ---------- 契约入口 ----------
    @Override
    public Contracts.SkillOutput analyze(Contracts.AnalysisRequest req, int budgetLeft) {
        // TODO Phase 3：按 req.analysisType() 分派到上述阶段组合；每步消耗预算，
        //  预算耗尽 -> INCONCLUSIVE / BUDGET_EXCEEDED
        throw new UnsupportedOperationException("TODO Phase 3");
    }
}
