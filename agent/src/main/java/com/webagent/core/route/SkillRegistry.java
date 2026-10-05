package com.webagent.core.route;

import com.webagent.core.Contracts;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 分析器注册表：共享核心只面向 SkillContract 接口（ADR-006），不 import 分析器内部。 */
@Component
public class SkillRegistry {

    private final Map<Contracts.TargetType, Contracts.SkillContract> skills = new HashMap<>();

    public void register(Contracts.SkillContract skill) {
        skills.put(skill.targetType(), skill);
    }

    public Contracts.SkillContract get(Contracts.TargetType type) {
        return skills.get(type);
    }
}
