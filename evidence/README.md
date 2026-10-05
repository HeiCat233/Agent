# evidence/ —— 可信验证证据（O3 交付物）

## 目录约定

```
evidence/
├── version_manifest.json    # 版本清单：每次评测涉及的工具/模型/代码版本
├── run_template/            # run 归档模板（复制为 run_XXX/ 后填写）
│   ├── input.json           # 评测输入（样本ID/query/参数）
│   ├── output.json          # 系统原始输出（含状态/引用/置信度）
│   ├── trace.log            # 关键 Trace（关卡判定/管线阶段/回验结果）
│   └── cost.json            # 成本（时长/步数/模型调用次数）
├── failures/                # 失败案例归档（fail_XXX_简述.md）
└── inconclusive_list.md     # UNCERTAIN / INCONCLUSIVE 案例清单
```

## 使用规则

1. **每次评测 = 一个 run_XXX/**，由评测脚本自动生成，禁止手改；更正=新 run+说明；
2. 失败的 run **不删除**（O3 要求"定位失败、区分推断"）；
3. 每个指标结果必须能通过 trace_id 追溯到对应 run；
4. 归档时区分：**事实**（工具输出、比对结果）与**推断**（LLM 解释、人工判定），
   在 output.json 的 `inference` 字段标注。
