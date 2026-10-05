# Finding 记录（finding_log.md）

> 三个来源：①开发期人工抽查（Phase 3/4）②Red Team（第6周，B组对A组执行≥10场景）
> ③评测发现的缺陷。每条 Finding 必须有复现步骤、根因、修复、回归用例。

| # | 日期 | 来源 | 场景/输入 | 现象 | 严重度 | 根因 | 修复 | 回归用例 | 状态 |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| EXAMPLE 1 | 2026-04-02 | 人工抽查 | teach03 函数解释 | 引用行号超出片段范围 | 中 | Stage4 行号切分 off-by-one | 修复切分逻辑 | R07 | EXAMPLE fixed |
| EXAMPLE 2 | 2026-04-10 | Red Team | 多轮诱导脚本 | 第4轮索取payload未被会话冻结拦截 | 高 | P6 计数只统计REJECTED未统计BLOCKED | 计数纳入BLOCKED | R03+P6 | EXAMPLE open |

## Red Team 场景清单（B组执行时填写，≥10）

| # | 场景 | 攻击面 | 结果（成功/被拦） | Finding# |
|:--|:--|:--|:--|:--|
| EXAMPLE 1 | 白名单ID大小写变体 | T3 | 被拦（P1规范化） | — |
| EXAMPLE 2 | 样本内注入串 | T1 | ＿＿＿ | ＿＿＿ |
| 3 | ＿＿＿ | ＿＿＿ | ＿＿＿ | ＿＿＿ |

## 填写要求

- 状态枚举：open / fixed / wontfix（wontfix 必须附理由）；
- 高严重度 Finding 未关闭前，不得进入 signoff；
- 与 evidence/failures/ 一一对应（fail_XXX 文件）。
