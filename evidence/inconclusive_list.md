# UNCERTAIN / INCONCLUSIVE 案例清单（inconclusive_list.md）

> O3 要求：诚实的不确定必须被记录与追溯，而不是被掩盖。
> 每条：run_id / 样本 / 触发原因（reason_code）/ 是否合理（人工复核）/ 后续动作。

| # | run_id | 样本 | reason_code | 人工复核结论 | 后续动作 |
|:--|:--|:--|:--|:--|:--|
| EXAMPLE 1 | run_20260410_002 | teach01-proguard | TOOL_FAILURE | 合理（baksmali 超时） | 提升 Docker 超时至 180s，重测 |
| EXAMPLE 2 | run_20260412_001 | teach01-proguard | NO_CANDIDATE | 合理（混淆名无字符串可反查） | 无（产品预期行为） |

<!-- 交付时替换 EXAMPLE 行为真实记录；空表不扣分，编造记录才是红线 -->
