package com.webagent.skill.apk;

import com.webagent.core.llm.ExplainService;
import com.webagent.core.store.WhitelistStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * skill-apk 自动配置（ADR-010 插件化接入的官方机制）：
 * 通过 META-INF/spring/...AutoConfiguration.imports 注册，无需主应用扫描外部 jar。
 * B组分析器接入时提供同结构文件即可（跨语言时改为 HTTP 服务，见 ADR-010 档位3）。
 */
@AutoConfiguration
public class SkillApkAutoConfiguration {

    @Bean
    public ApktoolRunner apktoolRunner(
            @Value("${webagent.apktool.jar-path:D:/dev/apktool/apktool.jar}") String jarPath,
            @Value("${webagent.sandbox.timeout-seconds:120}") long timeoutSeconds) {
        return new ApktoolRunner(jarPath, timeoutSeconds);
    }

    @Bean
    public SmaliIndexer smaliIndexer() { return new SmaliIndexer(); }

    @Bean
    public Extractor extractor() { return new Extractor(); }

    @Bean
    public EvidenceVerifier evidenceVerifier() { return new EvidenceVerifier(); }

    @Bean
    public ConfidenceCalculator confidenceCalculator() { return new ConfidenceCalculator(); }

    @Bean
    public Locator locator() { return new Locator(); }

    @Bean
    public AnnotatedTreeGenerator annotatedTreeGenerator(
            SmaliIndexer indexer, ExplainService explain,
            @Value("${webagent.annotate.llm-package-notes:true}") String llmEnabled) {
        return new AnnotatedTreeGenerator(indexer, explain, llmEnabled);
    }

    @Bean
    public ApkAnalysisSkill apkAnalysisSkill(
            ApktoolRunner runner, SmaliIndexer indexer, Extractor extractor, Locator locator,
            EvidenceVerifier verifier, ConfidenceCalculator confidence, ExplainService explain,
            AnnotatedTreeGenerator treeGenerator, WhitelistStore whitelist,
            @Value("${webagent.skill-apk.data-dir:skill/data}") String dataDir) {
        return new ApkAnalysisSkill(runner, indexer, extractor, locator, verifier,
                confidence, explain, treeGenerator, whitelist, dataDir);
    }
}
