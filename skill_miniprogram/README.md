# skill_miniprogram/ —— 小程序分析 Skill（B组占位）

> **B组唯一实质差异所在**。B组在自己的仓库实现本目录对应物：实现与 A组**同一个**
> agent 模块的 `Contracts.java` 的 `SkillContract` 接口（target_type=MINIPROGRAM），
> 通过共享核心的 router 挂载。共享核心设计（双关卡/状态机/契约）见 A组仓库
> design/ 与 agent/，双方遵守同一份规范（ADR-006）。

## B组实现指引（对应 A组 6 阶段管线的小程序等价物）

| A组阶段 | B组等价物 |
|:--|:--|
| Stage 1 解码（apktool） | wxapkg 解包（小程序包）→ 源码目录树 + app.json 组件/页面清单 |
| Stage 2 索引 | pages / components / JS函数 / wxml节点 / 配置项 建索引 |
| Stage 3 定位 | 页面路由反查 → API指纹（wx.request / wx.setStorage / wx.login 等）→ app.json 入口页面 |
| Stage 4 提取 | JS 函数片段（带 file:line），WXML 关键节点 |
| Stage 5 解释 | 同一 ai_rules.md 系统提示词 + `<untrusted_code>` 数据包裹 |
| Stage 6 回验 | 引用对回提取片段（同机制，复用 ADR-004/001 规则） |
| FR-09 注释重写 | 注释版 pages 结构树（页面用途/组件依赖/关键路径，inference 标注同规则） |

## B组注意（来自 A组方案的共享约束）

1. 状态机与 reason_code 与 A组完全一致（ADR-004），前端徽章共用同一套文案；
2. 小程序样本同样走白名单+授权声明制度；自研教学小程序 + 开源模板，含注入串样本；
3. 意图过滤/PostGate 规则表与 A组共享（审 ADR-002 签字版），差异只在分析器；
4. 互审时用 A组共享核心 + B组分析器做交叉冒烟（接口一致性的加分验证）。
