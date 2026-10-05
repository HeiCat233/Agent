# agent/ —— 共享核心（A组实现实例，Java/Spring Boot 3）

> 双关卡 + 路由 + 契约。设计依据：design/ADR-001（双关卡）、ADR-002（意图过滤）、
> ADR-004（状态机）、ADR-006（可插拔接口）、ADR-008（技术栈=Java）。
> 状态与枚举的唯一权威=`Contracts.java`。

## Maven 模块结构

```
pom.xml（根 parent：agent + skill 两模块，Java 17，Spring Boot 3.3）
agent/
├── pom.xml                        Spring Boot 主工程（repackage exec classifier，供 skill 依赖契约）
└── src/main/java/com/webagent/core/
    ├── AgentApplication.java      启动类（可信面仅监听本机，ADR-007）
    ├── Contracts.java             【唯一权威】嵌套枚举(TargetType/AnalysisType/Status/ReasonCode/
    │                              IntentVerdict) + record(AnalysisRequest/Reference/Fragment/
    │                              ConfidenceBreakdown/GateResult) + SkillOutput + SkillContract 接口
    ├── gate/
    │   ├── PreGate.java           入口关卡 P1-P6
    │   ├── IntentFilter.java      三级过滤 L0 正则/L1 豁免短语/L2 开关（ADR-002）
    │   ├── Budget.java            P5 预算 + P6 会话冻结
    │   └── PostGate.java          出口关卡 Q1-Q5（UNCERTAIN 透传）
    └── route/
        ├── SkillRegistry.java     可插拔分析器注册表（仅面向 SkillContract）
        └── Router.java            规则路由（不用 LLM）+ 按 target_type 分发
```

## 开发指引（给下游开发 Agent）

1. Phase 1 先补全 `Contracts` 的契约单元测试（状态机迁移合法性、reason_code 全覆盖），
   用 JUnit 5（spring-boot-starter-test）；
2. gate 包中的 TODO 按 ADR-001/002 实现；L0/L1 模式表改动须经负责人审定签字
   （status_and_permissions.md）；
3. 持久化：SQLite（sqlite-jdbc，pom 中已预留注释）——索引、会话、拦截日志 append-only；
4. 红线：任何代码路径不得绕过 PreGate；UNCERTAIN 不得被 BLOCK。

## 与 B组的接口

- B组的 `skill_miniprogram` 实现 `com.webagent.core.Contracts.SkillContract`；
- 互审时用本模块的 PreGate/PostGate/Router + B组分析器做交叉冒烟（接口一致性验证）。

## 构建与运行

```bash
mvn -pl agent -am spring-boot:run     # 或 IDEA 打开根 pom 直接跑 AgentApplication
mvn test                              # 全模块测试
```
