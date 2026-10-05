# ADR-009：LLM 双通道——云端 GLM（调试默认）/ 本地 Ollama（正式可选）

- **状态**：已接受
- **日期**：2026-10-03

## 背景

1. 架构（ADR-003）：LLM 仅做解释生成与可选 L2 意图分类；
2. 团队环境：JDK/Maven 装好即可开发，若强制"调试也必须先装 Ollama+下模型"，
   开发与组员环境门槛都被抬高；
3. 用户决策：**调试阶段默认使用 GLM 云端 API**；正式演示/离线场景可选本地 Ollama。

## 决策

```
LlmClient 接口（唯一抽象）
├── GlmClient        OpenAI 兼容端点（https://open.bigmodel.cn/api/paas/v4）
│                    —— 调试默认通道，key 用户自填
└── OllamaClient     http://localhost:11434 —— 正式/离线通道
```

- 通道由配置切换：`webagent.llm.channel = cloud-glm | local-ollama`（application.yml）；
- **key 管理红线**：API key 只经环境变量（`ZHIPUAI_API_KEY`）或
  `application-local.yml`（.gitignore 已排除）注入，**任何情况下不入 git**；
  误提交应急：立即在平台侧作废该 key 并轮换；
- **通道无关的安全设计**：四条铁律提示词（ai_rules.md）、PreGate/PostGate 双关卡、
  证据回验对两条通道一致生效——切换通道不改变任何边界行为；
- **隐私口径**：发往云端的内容仅限"提取出的代码片段 + 系统提示词"，样本为自研
  教学 APK/FOSS，不含敏感数据；未授权目标在 PreGate 已被拒，永不进入任何通道；
- 评测口径：M 系列指标结果须注明所用通道（两通道表现可能不同，分开记录）。

## 备选（被否决）

- 仅本地（开发门槛高、组员无法快速开工）；
- 仅云端（正式演示依赖网络与费用，且放弃本地离线能力）。

## 后果

- 正面：组员零模型环境即可开工调试；正式演示可离线；多模型切换成为评测变量
  （可对比 GLM 与本地 7B 的解释质量，反哺 O4 实验卡）；
- 负面：调试数据出网（已评估：仅代码片段，无敏感信息）；GLM 免费/付费额度需在
  成本记录（metrics M10）中按通道注明。
