# ADR-010：声明式 Skill 接入（SKILL.md + skill.json 双轨制）

- **状态**：已接受
- **日期**：2026-10-03
- **参考**：用户提供的 reverse-skill 项目（D:\develop\reverse\reverse-skill\reverse-skill）
  的"文档 + 注册表"双轨设计与触发词路由。

## 背景

ADR-006 定义了代码级可插拔接口（实现 SkillContract + Spring 扫描）。参考 reverse-skill
后，升级为**声明式接入**：新增一个 Skill = 建一个声明目录 + 放实现 jar/类，
无需改动核心代码，且触发词路由让"计划器不用 LLM"原则进一步落地。

## 决策

### 双轨结构（每个 Skill 一个目录）

```
skills/<skill-name>/
├── SKILL.md     人读：frontmatter(name/description 含触发词) + 何时用/流程/产物/边界
└── skill.json   机读注册表：
    {
      "key": "apk-analysis",
      "targetTypes": ["APK"],
      "entryClass": "com.webagent.skill.apk.ApkAnalysisSkill",
      "signals": ["目录结构", "登录逻辑", "调用关系", "注释树", ...],
      "analysisTypes": ["DIRECTORY_STRUCTURE", "FUNCTION_LOGIC", ...],
      "formalValidation": ["R01", "R02", "R08"],     // 指向验收用例，与 eval/ 对齐
      "boundaries": "只读分析；描述性输出；证据引用必须过回验"
    }
```

### 注册与路由

1. **SkillRegistry 启动扫描** `skills/` 目录 → 读 skill.json → 实例化 entryClass
   （实现 SkillContract）→ 按 targetTypes 注册；
2. **触发词路由**：LEARN_QA 自由文本先用所有 skill.json 的 `signals` 做**确定性
   匹配**（命中→选中该 Skill 的默认 analysisType）；未命中才走 LLM 轻量分类
   （3s 超时回落）——进一步减少 LLM 依赖，与"计划器不用 LLM"原则一致；
3. **@Component 兼容路径保留**：Spring 扫描的 SkillContract 实现仍可直接注册
   （无声明目录时兜底），声明目录优先。

### 安全边界四层（融合 reverse-skill 的分层思路）

| 层 | 载体 | 内容 |
|:--|:--|:--|
| 1 提示词层 | design/ai_rules.md | 四条铁律（对一切 Skill 生效） |
| 2 声明层 | skills/*/SKILL.md frontmatter | 该 Skill 的边界声明（boundaries 字段同步写） |
| 3 入口层 | PreGate | 白名单/意图过滤/预算（Skill 无法绕过） |
| 4 出口层 | PostGate | payload 扫描/引用回验（Skill 输出不可信） |

### 一致性校验（借鉴 reverse-skill 的 QA needle 机制）

`formalValidation` 指向的 R 编号必须真实存在于 eval/acceptance_criteria.md；
checklist 加入 Phase 5 评测前自检（未实现自动化脚本前人工核对，finding_log 记录）。

## 备选（被否决）

- 档位2 插件目录热加载（jar 丢入即加载）：工程量中等，课程周期内收益不足，
  列为 O5 Change 候选；
- 档位3 HTTP 外部 Skill 服务（跨语言）：B组小程序分析器若为独立服务可采用，
  当前以"同契约各自实现"满足互审，列为 O5 Change 候选。

## 后果

- 正面：新增 Skill 零核心改动；触发词路由降低 LLM 依赖；声明与文档同步演进；
- 负面：skill.json 与代码的一致性需要校验纪律（formalValidation + 人工核对）。
