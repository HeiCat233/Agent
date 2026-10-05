# failures/ —— 失败案例归档

> 每个失败案例一个文件：`fail_XXX_简述.md`。失败 run 不删除，修复后以新 run 重测。
> 这是 O3"定位失败"与 O5"Finding→修复"的素材来源。

## 模板（fail_XXX_简述.md）

```markdown
# fail_001_<简述>
- 日期：
- 关联 run：evidence/run_XXX/
- 现象（事实）：
- 根因（区分事实/推断）：
- 修复：
- 回归用例：eval/acceptance_criteria.md 的 R__
- 状态：open / fixed / wontfix（附理由）
```

## EXAMPLE（示意格式，交付时替换为真实记录）

# fail_001_混淆样本误报CONFIRMED（EXAMPLE）
- 日期：EXAMPLE 2026-04-02
- 关联 run：EXAMPLE evidence/run_20260402_003/
- 现象：EXAMPLE teach01-proguard 的方法解释返回 CONFIRMED，但置信度分解显示 evidence=0.3
- 根因（推断）：EXAMPLE Stage 6 只校验引用存在性，未校验引用覆盖率下限
- 修复：EXAMPLE 置信度规则增加 evidence_coverage 下限 0.5，低于则强制 UNCERTAIN
- 回归用例：R04
- 状态：EXAMPLE fixed
