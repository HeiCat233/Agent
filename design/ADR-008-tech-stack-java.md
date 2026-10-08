# ADR-008：技术栈切换——后端 Python/FastAPI → Java/Spring Boot 3

- **状态**：已接受（用户 2026-10-03 拍板）
- **日期**：2026-10-03

## 背景

用户明确表示"更熟悉 Java 的技术栈，FastAPI 不太懂"。原方案技术栈为 Python
（FastAPI + SQLite + 自研调度）。课程项目由团队自己维护与答辩，**语言的熟悉度
直接决定开发速度、排障能力与答辩自信**，因此切换后端语言。

## 决策

| 环节 | 原方案 | 新方案 | 说明 |
|:--|:--|:--|:--|
| Web 后端 | FastAPI | **Spring Boot 3 + Maven + Java 17** | Java 生态事实标准；JDK 17 与 apktool 的 JDK 要求统一 |
| Ollama 调用 | HTTP API | **不变**（Spring RestClient 直连 `localhost:11434`） | LLM 调用本来就是语言无关 |
| apktool 沙箱 | Docker 容器 | **不变**（ADR-001/T4） | Java 进程内直调 apktool API 会失去沙箱边界，仍经容器执行 |
| Smali 解析 | Python 手写 | **baksmali Java 库**（JesusFreke/smali，apktool 同生态） | **Java 路线的加分项**：Stage 2 索引/Stage 4 提取用原生库，比手写解析器更可靠 |
| SQLite | python sqlite3 | sqlite-jdbc 驱动（Spring JDBC） | — |
| 前端 | React + TS | **不变** | 与 B组共享的前端设计不动 |
| 部署 | 宿主机 + Docker 沙箱 | **不变**（ADR-007） | — |

**不变的 设计**（语言无关）：双关卡控制器规则、三级意图过滤、统一状态机与
reason_code、Skill 契约（JSON Schema）、6 阶段管线、置信度公式、评测口径与
全部验收准则。B 组（小程序方向）不受影响——契约以 JSON/状态机为准，语言各自自选。

## 影响面与处置

1. 旧 Python 骨架（agent/*.py、skill/apk_skill.py）从工作区删除，**git 历史保留**
   （对应"新规则生效，历史可解释"的互审要求）；
2. 新 Maven 多模块骨架：根 pom（parent）+ `agent/`（Spring Boot 主工程：契约/双关卡/
   路由）+ `skill/`（skill-apk 模块：六阶段管线骨架）——目录名与文档映射保持一致；
3. ADR-001/005 中的 Python 代码骨架保留但加标注"示意伪代码，实现语言=Java（ADR-008）"；
4. RUNNING.md 前置依赖改为 JDK 17 + Maven（2026-10-05 组长机已装绿色版并实测通过：
   JDK 17.0.2 / Maven 3.9.9 位于 `D:\dev`，用 `env.bat` 注入；组员也可用 IDEA 内置 Maven）；
   契约/验收/评测文档无语言绑定，不需要改。

## 后果

- 正面：团队用熟悉的语言开发；baksmali 原生库提升解析可靠性；Spring 生态便于后续
  加鉴权/持久层；
- 负面：启动配置比 FastAPI 略重；LLM 相关教程多以 Python 为主（本设计只需 HTTP
  直连 Ollama，影响可控）；
- 环境状态：JDK 17 与 Maven 已在组长机就绪（`env.bat`）；组员按《组员上手指南.md》自行安装。
