package com.webagent.core.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webagent.core.Contracts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可插拔 Skill 注册表（ADR-006/010）。三条接入路径：
 * ① 容器收集：Spring 容器中所有 SkillContract bean（AutoConfiguration/@Component 创建）自动注册——主路径；
 * ② 声明目录：skills/<名>/skill.json 提供 signals 触发词与 analysisTypes 元数据（路由用），
 *    其 entryClass 若无参可实例化则兜底注册；
 * ③ register() 代码注册。
 * 注册表不判断能力真伪——能力校验交给双关卡（ADR-001）。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Contracts.TargetType, Contracts.SkillContract> byType = new HashMap<>();
    private final List<SkillDeclaration> declarations = new ArrayList<>();
    private final List<Contracts.SkillContract> containerSkills;
    private final String skillsDir;

    public SkillRegistry(List<Contracts.SkillContract> containerSkills,
                         @Value("${webagent.skills-dir:}") String skillsDir) {
        this.containerSkills = containerSkills == null ? List.of() : containerSkills;
        this.skillsDir = skillsDir;
    }

    /** 容器 bean 注册（主路径）+ 声明目录元数据加载。 */
    @PostConstruct
    void init() {
        for (Contracts.SkillContract skill : containerSkills) {
            register(skill);
        }
        if (skillsDir != null && !skillsDir.isBlank()) {
            Path dir = Path.of(skillsDir);
            if (Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir, 2)) {
                    stream.filter(p -> p.getFileName().toString().equals("skill.json"))
                          .forEach(this::loadDeclaration);
                } catch (Exception e) {
                    log.error("skills 目录扫描失败: {}", e.getMessage());
                }
            } else {
                log.warn("skills 目录不存在（{}），仅容器注册的 Skill 可用", dir.toAbsolutePath());
            }
        }
    }

    /** 声明目录：读 signals/analysisTypes 元数据；entryClass 仅当可直接实例化时兜底注册。 */
    private void loadDeclaration(Path jsonFile) {
        try {
            JsonNode node = mapper.readTree(jsonFile.toFile());
            String key = node.get("key").asText();
            List<String> targetTypes = toStringList(node.get("targetTypes"));
            List<String> signals = node.has("signals") ? toStringList(node.get("signals")) : List.of();
            List<String> analysisTypes = node.has("analysisTypes") ? toStringList(node.get("analysisTypes")) : List.of();

            boolean registered = false;
            if (node.has("entryClass")) {
                try {
                    Object instance = Class.forName(node.get("entryClass").asText())
                            .getDeclaredConstructor().newInstance();
                    if (instance instanceof Contracts.SkillContract skill) {
                        for (String t : targetTypes) byType.putIfAbsent(Contracts.TargetType.valueOf(t), skill);
                        registered = true;
                    }
                } catch (Exception e) {
                    // 有参构造（Spring 注入类）无法直接实例化——由容器路径注册，此处仅记 debug
                    log.info("entryClass 走容器注册（{}）", e.getMessage());
                }
            }
            if (!registered) {
                // 元数据仅用于触发词路由；bean 本体应已由容器路径注册
                for (Contracts.SkillContract s : containerSkills) {
                    if (targetTypes.contains(s.targetType().name())) registered = true;
                }
            }
            declarations.add(new SkillDeclaration(key, targetTypes, analysisTypes, signals));
            log.info("Skill 声明已加载: {} signals={} beanRegistered={}", key, signals, registered);
        } catch (Exception e) {
            log.error("skill.json 加载失败（{}）: {}", jsonFile, e.getMessage());
        }
    }

    /** 代码注册（兼容路径）。 */
    public void register(Contracts.SkillContract skill) {
        byType.putIfAbsent(skill.targetType(), skill);
        log.info("Skill 已注册: {}", skill.getClass().getName());
    }

    public Contracts.SkillContract get(Contracts.TargetType type) { return byType.get(type); }

    public List<SkillDeclaration> declarations() { return List.copyOf(declarations); }

    /** 触发词确定性路由（ADR-010）：命中返回该 Skill 的 analysisTypes。 */
    public List<String> matchBySignals(String query) {
        String q = query == null ? "" : query;
        for (SkillDeclaration d : declarations) {
            for (String s : d.signals()) {
                if (!s.isBlank() && q.contains(s)) return d.analysisTypes();
            }
        }
        return List.of();
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(n -> out.add(n.asText()));
        return out;
    }

    public record SkillDeclaration(String key, List<String> targetTypes,
                                   List<String> analysisTypes, List<String> signals) {}
}
