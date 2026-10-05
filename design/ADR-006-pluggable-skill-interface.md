# ADR-006：可插拔 Skill 接口与两组分工

- **状态**：已接受
- **日期**：2026-09-30

## 背景

课程组织为 A/B 两组异题互审：**两组的差异只在实现的内容**——A组分析器针对 APK，
B组分析器针对小程序；架构、契约、控制器、状态机、评测口径、O1-O7 流程全部共享。

## 决策

### 分层

```
共享核心（设计规范一份，两组各自实现自己的实例）
  ├─ Web 前端四模块
  ├─ PreGate / PostGate 双关卡（ADR-001）
  ├─ 三级意图过滤（ADR-002）
  ├─ 规则路由器 + 统一状态机（ADR-004）
  └─ Skill 契约 + 6 阶段管线框架

差异化实现（唯一实质不同点）
  ├─ A组：skill_apk      —— apktool + Smali 解析
  └─ B组：skill_miniprogram —— wxapkg 解包 + JS/WXML 静态解析
```

### 接口约束（互审可交叉运行的前提）

- 两个分析器实现**同一个** agent 模块 `Contracts.java` 定义的 Skill 接口
  （`target_type = APK | MINIPROGRAM`）；
- 路由器按 `target_type` 分发，核心代码不 import 任何分析器内部细节；
- 接口一致性用同一套契约测试验证——因此 B 组的分析器理论上可接入 A 组核心跑通
  冒烟用例，这是互审时的加分验证项。

### A/B 对照表

| 维度 | A组（APK） | B组（小程序） |
|:--|:--|:--|
| 分析对象 | Android APK | 微信/QQ 小程序 |
| 解码 | apktool + baksmali | wxapkg 解包 |
| 索引对象 | classes / methods / strings / api_calls | pages / components / JS函数 / wxml节点 / 配置 |
| 定位策略 | 字符串反查 / API指纹 / 入口Activity | 页面路由反查 / API指纹(wx.request等) / app.json 入口页面 |
| 输出形态 | 注释版目录树、Smali 方法解释、方法调用图 | 注释版 pages 结构树、JS 函数解释、页面-函数调用图 |
| DIRECTORY_REWRITE 对应物 | 注释版 APK 目录树 | 注释版 pages 结构树 |
| 典型问题 | "这个 Activity 的登录逻辑在哪" | "这个页面的支付逻辑在哪" |
| 评测样本 | 自研教学APK×4 + FOSS×2 + ProGuard混淆×1 | 自研教学小程序 + 开源模板，含注入串样本 |

### 异题互审机制

| 共同工程问题 | A组 | B组 |
|:--|:--|:--|
| 新规则生效，历史可解释 | 新增白名单规则后旧结果可经 trace_id 追溯 | 同左 |
| Change 接力 | 候选：L2 上线 / 加固APK尽力分析 | 候选：eval 动态解析处理 |
| 受控 Red Team | 第6周 B组对 A组系统 ≥10 场景 | A组对 B组同规模执行 |

互审内容：实验卡 / 负向验收 / 失败案例 / 交接包。
不比较：业务绝对性能（分析对象不同，无可比性）。

## 后果

- 正面：两组工作量对称；契约冻结后可并行开发；互审有客观抓手（同一接口+同一验收模板）；
- 负面：共享规范变更需双方同步（以 peer_review.md 签字的版本为准）。
