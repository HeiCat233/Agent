# 恢复测试记录（recovery_test.md）—— O6 完成判据

> 执行人：B组（在**干净环境**：新机器/新容器/全新 clone）
> 方法：只依据 release/RUNNING.md 操作，遇到任何含糊之处立即记录——文档问题也是缺陷。

## 测试记录（EXAMPLE 行，执行后替换）

| # | 步骤 | 结果 | 用时 | 遇到的问题 |
|:--|:--|:--|:--|:--|
| EXAMPLE 1 | 安装依赖 | ✅ | 12min | 无 |
| EXAMPLE 2 | ollama pull 模型 | ✅ | 9min | 需注明镜像源 |
| EXAMPLE 3 | 后端启动 | ❌→修复 | — | RUNNING.md 缺少端口说明（已补） |
| EXAMPLE 4 | 冒烟4项 | ✅ | — | UNCERTAIN 正确显示黄色标注 |

## 结论

- EXAMPLE：恢复成功；RUNNING.md 修订 1 处；见 review/signoff.md 签署。

## 填写要求

- 真实执行后填写，不预填结论；
- 干净环境指：未安装过本项目的机器或全新容器；
- 任何"EXAMPLE"残留视为交接未完成。
