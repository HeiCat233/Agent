# ADR-004：统一状态机与 reason_code 枚举

- **状态**：已接受
- **日期**：2026-09-30

## 背景

v1 中 REJECTED / BLOCKED / APPROVED / CONFIRMED / UNCERTAIN / INCONCLUSIVE 六个词混用：
- 验收准则 R06 使用了输出契约中不存在的 INCONCLUSIVE；
- 控制器规则 1/2 返回 REJECTED、规则 3/4 返回 BLOCKED，无判别标准；
- UNCERTAIN 被 BLOCK，与"不编造结论"承诺矛盾。

## 决策

### 状态机（全系统唯一权威，契约/控制器/验收/前端四处对齐）

```
REQUESTED ──PreGate──→ REJECTED            （入口拒绝，未进入分析）
    │
    └──通过──→ ANALYZING ──┬──→ CONFIRMED   （PostGate 通过，证据完整）
                          ├──→ UNCERTAIN    （诚实不确定，透传+review_required）
                          ├──→ BLOCKED      （PostGate 拦截攻击性输出）
                          └──→ INCONCLUSIVE （预算耗尽）
```

| 状态 | 触发层 | 含义 | 前端表现 |
|:--|:--|:--|:--|
| REQUESTED | — | 已受理 | — |
| REJECTED | PreGate | 未通过入口校验 | 红+原因+边界说明 |
| ANALYZING | Skill | 分析中 | 进度 |
| CONFIRMED | PostGate | 完成，证据完整 | 绿+证据面板 |
| UNCERTAIN | PostGate | 诚实不确定（混淆/证据不足/工具失败） | 黄+"建议教师复核" |
| BLOCKED | PostGate | 输出含攻击性内容 | 红+拦截说明 |
| INCONCLUSIVE | Skill | 预算耗尽未完成 | 灰+预算消耗报告 |

### reason_code 枚举（11 个）

| code | 层 | 含义 |
|:--|:--|:--|
| UNAUTHORIZED_TARGET | PreGate | 目标不在白名单 |
| AUTH_MISSING | PreGate | 未携带授权声明 |
| ATTACK_INTENT | PreGate | L0 命中攻击请求 |
| SESSION_FROZEN | PreGate | 会话累积被拒冻结 |
| BUDGET_INVALID | PreGate | budget 参数非法 |
| PAYLOAD_DETECTED | PostGate | 输出含可运行攻击内容 |
| BYPASS_CONTENT | PostGate | 输出含绕过指令（段落删除） |
| EVIDENCE_FAILED | PostGate | 引用回验失实 → UNCERTAIN |
| BUDGET_EXCEEDED | Skill | 步数耗尽 → INCONCLUSIVE |
| TOOL_FAILURE | Skill | 工具/LLM 失败 → UNCERTAIN |
| NO_CANDIDATE | Skill | 定位无候选 → UNCERTAIN |

## 判别标准（什么时候用哪个词）

- **REJECTED** = 入口拒绝，分析未发生；
- **BLOCKED** = 分析发生了，但出口拦截了输出；
- **UNCERTAIN** = 分析发生了，诚实结论是"不确定"，必须透传给用户；
- **INCONCLUSIVE** = 分析未完成（预算耗尽），区别于"完成但不确定"。

## 后果

- 每个验收用例的期望状态可以精确断言；前端徽章与 reason_code 一一对应；
- 违反状态机的输出会被 PostGate Q4/一致性检查直接判为缺陷进 finding_log。

## 关联

- 代码：agent/src/main/java/com/webagent/core/Contracts.java（Status/ReasonCode 枚举定义）
- 验收：R01-R08 全部依赖本状态机断言
