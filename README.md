# 逆向学习辅助Agent（A组·APK方向）

> AI 原生软件工程课程大作业。在授权白名单内解释 APK 结构与逻辑，只输出结构理解，
> 不输出攻击内容。B组（另一组）做小程序分析器，共享同一套规范，异题互审。

## 快速导航

| 文件/目录 | 用途 |
|:--|:--|
| [选型与方案总览.md](选型与方案总览.md) | **决策速查手册**：目的/选型对比（为什么选它）/组队分工/资源预算/可修改开关 |
| [组员上手指南.md](组员上手指南.md) | **组员自助开工**：clone/环境矩阵/首次验证/日常工作流/FAQ |
| [项目方案v2.md](项目方案v2.md) | **主方案**：定位/架构/管线/控制器/评测/O1-O7（第一部分=方案，第二部分=下游Agent提示词） |
| [目录总览.md](目录总览.md) | **全景图**：完整文件树+归属（共享/A组/B组）+两组要求与区别+互审机制 |
| [实施路线图.md](实施路线图.md) | **怎么干**：8个Phase每步谁做 + 必须由人做的10件事 |
| [next_agent_prompt.md](next_agent_prompt.md) | 发给下游开发 Agent 的完整提示词 |
| brief/ | 1-2页答辩摘要 |
| design/ | ADR-001~006 + ai_rules（系统提示词）+ 状态与权限矩阵 |
| eval/ | R01-R08 验收准则 + 数据集计划 + 指标操作化定义 |
| evidence/ | O3 证据：版本清单 + run 归档模板 + 失败案例 + 不确定清单 |
| release/ | O6 交接：RUNNING 启动说明 + 版本 + reset.sh + 恢复测试 |
| review/ | O1/O4/O5/O7 + 互审 + 签署（运行后填真实记录，EXAMPLE 行需替换） |
| agent/ | 共享核心实现：contracts（契约+状态机）/ pregate / postgate / router / llm / REST API |
| skill/ | A组差异化实现：APK 分析 Skill（6阶段管线 + 目录注释重写），已跑通真实 APK |
| skill_miniprogram/ | B组：技术方案 + skill.json 声明示例 + 实现指引 |
| web/ | 前端四模块结构与 API 约定（当前为静态调试页，React 版待做） |

## O1-O7 能力 ↔ 交付物映射

| 能力 | 交付物 |
|:--|:--|
| O1 人机责任 | review/rejection_log.md |
| O2 工程契约 | eval/acceptance_criteria.md（R01-R08 正负用例） |
| O3 可信验证 | evidence/（manifest + run归档 + failures + inconclusive_list） |
| O4 工程取舍 | review/experiment_card.md |
| O5 变化与对抗 | review/change_proposal.md + finding_log.md |
| O6 交接与恢复 | release/（RUNNING/reset.sh/recovery_test） |
| O7 风险与个人 | review/risk_table.md + personal_statement.md |

## 当前状态（2026-10-08）

- ✅ 方案 v2.0 + 选型总览 + 组员上手指南 + 10 篇 ADR + 课程交付模板全部就绪
- ✅ 后端全链路可运行：双关卡（PreGate/PostGate）、三级意图过滤、声明式 Skill 路由、
  LLM 三通道（mock/cloud-glm/local-ollama）、APK 六阶段管线、目录注释重写（FR-09）、REST API
- ✅ 24 个单元/集成测试全绿；已用真实 APK 跑通 Stage1-6 冒烟
- ✅ B组方案（skill_miniprogram/技术方案.md + skill.json 声明示例）已就绪
- ⬜ 待办（组员接手）：GLM 真通道联调 / React 前端 / Docker 沙箱 / Phase 2 教学样本 APK / 评测记录
- ⚠️ 提醒：所有模板中的 EXAMPLE 行须在交付前替换为真实记录；空模板不扣分，编造数据是红线

## 边界声明

本系统只分析白名单内的授权样本（自研教学 APK + FOSS），只输出描述性结构理解；
不提供攻击 payload、绕过步骤、密钥提取脚本；不做动态调试、脱壳、加固对抗。
