---
name: apk-analysis
description: APK 结构与逻辑分析（A组核心 Skill）。触发关键词：目录结构、组件清单、登录逻辑、函数解释、调用关系、调用图、字符串、常量检索、注释树、目录重写。边界：只读分析、描述性输出、每条结论必须带可回验的 file:line 引用。
---

# apk-analysis（APK 分析 Skill）

## 何时用
用户问题涉及 APK 的目录/组件/函数/调用/字符串，或请求目录注释重写（FR-09）。

## 覆盖的分析类型
DIRECTORY_STRUCTURE / COMPONENT_LIST / FUNCTION_LOGIC / CALL_GRAPH /
STRING_SEARCH / DIRECTORY_REWRITE（详见 skill.json 的 analysisTypes）。

## 流程（6 阶段，方案第四章）
解码 → 索引 → 定位 → 提取 → 受限解释 → 证据回验；置信度规则计算。

## 产物
SkillOutput（状态机 + 引用 + 置信度）；DIRECTORY_REWRITE 附可导出 .md 注释树。

## 边界
只读分析（不重命名/不重打包/不触碰原样本）；描述性输出；不输出可运行攻击内容
（PostGate 兜底）。非 APK 目标由路由器按 target_type 分发，不进入本 Skill。
