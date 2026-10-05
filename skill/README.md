# skill/ —— APK 分析 Skill（A组差异化实现，Maven 模块 skill-apk）

> A组唯一实质差异所在：实现 `com.webagent.core.Contracts.SkillContract` 接口
> （ADR-006），通过共享核心的 Router 挂载（target_type=APK）。
> 技术栈见 ADR-008：Java 17 + baksmali 原生库。

## Maven 结构

```
skill/
├── pom.xml                        依赖 agent（契约接口）；baksmali 依赖已注释（Phase 3 启用）
└── src/main/java/com/webagent/skill/apk/
    └── ApkAnalysisSkill.java      六阶段管线 + generateAnnotatedTree(FR-09) 骨架
```

## 6 阶段管线（ApkAnalysisSkill 骨架已切分好）

```
Stage 1 解码    apktool d（Docker 沙箱，ADR-007）→ 目录树 + 组件清单        FR-02/03
Stage 2 索引    SQLite: classes/methods/strings/api_calls（baksmali 库）    管线基础
Stage 3 定位    字符串反查 → API指纹 → 入口点；空候选→UNCERTAIN             FR-04/07
Stage 4 提取    smali 片段（≤200行，带 file:line）
Stage 5 解释    本地 LLM（<untrusted_code> 包裹 + ai_rules 四铁律）          FR-04
Stage 6 回验    引用逐条对回片段，失实删除→降级 UNCERTAIN                    防幻觉
```

附加能力：

- **FR-09 目录注释重写**：`generateAnnotatedTree()`——规则骨架 + LLM 包级注释 +
  混淆名 inference 标注 + 关键文件高亮 + 索引回验，前端可导出 .md（ADR-005）；
- **置信度规则计算**：`computeConfidence()`——四项分解，禁止 LLM 自报（方案 4.7）。

## 依赖与环境

- JDK 17（与 apktool 统一）+ Maven；Docker 沙箱：无网络、样本只读挂载、2C2G、120s 超时；
- Ollama + 选定模型（design/ADR-003，temperature=0.2）。

## 实现顺序建议（对齐实施路线图 Phase 3）

1. Stage 1/2（先让 FR-02/03 跑通，B组可开始抽验）；
2. Stage 3/4（定位质量决定产品体验，重点打磨三种策略）；
3. Stage 5/6 + 置信度（接 LLM 后立刻接回验，二者不可拆分交付）；
4. FR-09 注释树（复用 1/2 产物，最后做）。
