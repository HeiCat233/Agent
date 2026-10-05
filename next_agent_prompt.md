# 任务：实现"逆向学习辅助Agent"（A组·APK方向）—— 下游开发Agent提示词 v2

> 本文件是《项目方案v2.md》第二部分的独立副本，供直接复制转发给下一个开发 Agent。
> 配套阅读：《实施路线图.md》（分Phase的执行安排与人工任务清单）。

## 一、背景与铁律

这是 AI 原生软件工程课程大作业。系统在授权白名单内解释 APK 的结构与逻辑，只输出
"这段代码在做什么"，不输出"如何攻击它"。

铁律（违反任何一条即返工）：
1. 只分析白名单目标；
2. 不输出可运行攻击代码 / 绕过步骤 / 密钥提取脚本；
3. 无法分析时诚实返回 UNCERTAIN，不编造；
4. 边界 100% 由硬编码控制器保证，不依赖模型拒答；模型一律使用官方发布权重与官方 API；
5. 文档中所有示例数据标注 EXAMPLE，评测记录必须是真实运行结果。

## 二、技术栈

- LLM 双通道（ADR-009）：**调试默认 GLM 云端**（智谱 OpenAI 兼容端点，key 走环境变量
  ZHIPUAI_API_KEY 或 application-local.yml，绝不入库）；正式可选 Ollama + Qwen2.5-Coder-7B
  （temperature=0.2，见 design/ADR-003）；两通道共用 LlmClient 接口，四铁律+双关卡一致
- 后端：Java 17 + Spring Boot 3（Maven 多模块）+ SQLite（sqlite-jdbc）——决策见 ADR-008
- 沙箱：Docker（apktool 执行环境：无网络、只读挂载、2C2G、120s 超时）
- 前端：React + TS + TailwindCSS
- 路由不用 LLM：规则路由，LLM 仅做解释生成与可选 L2 意图分类

## 三、架构（双关卡 + 可插拔分析 Skill）

```
PreGate（白名单精确匹配/授权声明/三级意图过滤L0-L1-L2/预算/会话累积冻结）
→ 规则路由（analysis_type 显式优先；自由文本→LLM 3s 分类，超时回落+反问；
   按 target_type 路由：APK→skill_apk，MINIPROGRAM→skill_miniprogram，
   两者实现同一 Skill 接口 agent 的 Contracts.java）
→ APK分析Skill 六阶段管线：
   apktool解码 → SQLite索引(classes/methods/strings/api_calls)
   → 候选定位(字符串反查/API指纹/入口点，空候选→UNCERTAIN)
   → smali证据提取(≤200行带行号)
   → LLM受限解释(<untrusted_code>数据包裹+四条铁律提示词)
   → 证据回验(每条 file:line 引用必须真实存在于提取片段，失实→降级UNCERTAIN)
→ PostGate（payload扫描/引用回验/绕过内容删除/置信度复核/UNCERTAIN透传）
→ 前端
```

## 四、目录注释重写（FR-09 / analysis_type=DIRECTORY_REWRITE）

- 规则骨架（不经 LLM，天然可信）：顶级包列表、每包类数量、Manifest 组件归属、
  lib/assets/res 标准语义；
- LLM 仅做包级用途注释（输入同样数据包裹）；
- 混淆名（a.b.c 形式）推断必须标 `inference=true` 并计入置信度扣分；
- 关键文件高亮规则：MainActivity / 自定义 Application / lib/*.so / 加固特征文件；
- LLM 注释中每个类名/文件路径必须经索引回验，失实删除；
- 前端可导出 .md。**只读分析，禁止重命名/重打包/触碰原样本。**

## 五、状态机（全系统唯一权威）

```
REQUESTED → REJECTED(PreGate) | ANALYZING → CONFIRMED | UNCERTAIN(透传+review_required)
           | BLOCKED(PostGate) | INCONCLUSIVE(预算耗尽)
```

reason_code 枚举：UNAUTHORIZED_TARGET / AUTH_MISSING / ATTACK_INTENT /
SESSION_FROZEN / BUDGET_INVALID / PAYLOAD_DETECTED / BYPASS_CONTENT /
EVIDENCE_FAILED / BUDGET_EXCEEDED / TOOL_FAILURE / NO_CANDIDATE

## 六、Skill 契约

输入：target_id / target_type(APK|MINIPROGRAM) /
analysis_type(DIRECTORY_STRUCTURE|COMPONENT_LIST|FUNCTION_LOGIC|CALL_GRAPH|
STRING_SEARCH|DIRECTORY_REWRITE|LEARN_QA) / query / budget∈[1,100] 默认50 /
session_id / authorization_ack

输出 JSON：

```json
{
  "status": "CONFIRMED",
  "reason_code": null,
  "analysis": {"type": "...", "target": "...", "explanation": "...",
               "pseudocode": "...", "learning_note": "...", "injection_suspected": false},
  "evidence": {"references": [{"file": "...", "lines": "120-168"}], "source": "..."},
  "confidence": {"value": 0.86, "breakdown": {}},
  "boundary_check": {"learning_purpose": true, "attack_payload_included": false,
                     "unauthorized_target": false},
  "review_required": false,
  "trace_id": "run_XXXXXX_001"
}
```

异常：Skill 异常 / LLM 超时（60s 重试 1 次）→ UNCERTAIN + TOOL_FAILURE。

## 七、置信度（规则计算，禁止 LLM 自报）

```
0.40×decompile_quality + 0.30×evidence_coverage + 0.20×method_clarity
+ 0.10×(1−obfuscation_penalty)
≥0.75→CONFIRMED；0.40-0.75→UNCERTAIN；<0.40→UNCERTAIN+提示更换问法
```

阈值写配置文件，变更走 O5 Change 流程。

## 八、评测

数据集：自研教学 APK×4（植入硬编码口令/弱哈希/明文SP/未校验deeplink，其中1个含
prompt 注入串）+ FOSS×2，≥70 条标注方法；正向 query 集≥50 条（含 expect/experiment
等含"exp"正常用法、含"破解"的防御性提问）；负向 query 集≥50 条（6类）；ProGuard
混淆样本×1。

指标（全部注明"在本数据集上"）：结构准确率≥90%；解释准确率≥85%（双评+仲裁）；
可读性≥4.0/5（3人评分）；攻击拦截率 100%；学习误判率≤5%；UNCERTAIN 识别率≥80%；
注释树引用有效率≥95%；注释树用途准确率≥80%。

## 九、交付物

```
brief/ design/(ADR-001~006+ai_rules+status_and_permissions) eval/(R01-R08正负用例)
evidence/(manifest/run归档/failures/inconclusive) release/(RUNNING/reset.sh/recovery_test)
review/(rejection_log/experiment_card/change_proposal/finding_log/peer_review/signoff/
risk_table/personal_statement) web/ agent/ skill/
```

## 十、验收 DoD

- R01-R08 正负用例全绿；
- 他人按 RUNNING.md 在干净环境恢复成功；
- 每个指标有 trace_id 与成本记录；
- 拦截日志 append-only 可查询。

## 十一、需要人工配合的节点（开发 Agent 应主动提醒用户）

1. 模型选型拍板（回填 design/ADR-003 选择记录）；
2. 教学样本 APK 编写编译（用户亲自做，Agent 可生成代码框架但不代承担授权）；
3. 契约评审签字（review/peer_review.md）；
4. L0/L1 边界规则审定（design/ADR-002）；
5. ground truth 标注与可读性评分（eval/）；
6. O1/O5/O7 的决策与签署（review/）。
