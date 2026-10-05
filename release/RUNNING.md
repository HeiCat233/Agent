# RUNNING.md —— 启动说明（O6 交接与恢复）

> 目标：**他人在干净环境**按本文档从零跑通系统。执行人=B组（互审），
> 结果记入 recovery_test.md 并在 review/signoff.md 签字。
> `EXAMPLE` 标记处随实现推进补充真实命令。

## 1. 前置依赖

| 依赖 | 版本 | 用途 |
|:--|:--|:--|
| JDK | EXAMPLE 17（与 apktool 统一） | 后端（ADR-008；本机 2026-10-03 实测未安装） |
| Maven | EXAMPLE 3.9+（或 IDEA 内置） | 构建 |
| Ollama | 已装 0.34.4 | 本地 LLM |
| Docker | EXAMPLE 24+ | apktool 沙箱（ADR-007：仅隔离不可信面） |
| apktool | EXAMPLE 2.9.3 | 解码（容器内） |
| Node.js | EXAMPLE 20+ | 前端构建 |

> **部署形态（ADR-007）**：宿主机运行可信面（Spring Boot 后端 + Ollama，仅监听本机）；
> Docker 只隔离不可信面（apktool）。**不使用完整虚拟机，模型不进 VM**——详见 design/ADR-007。

## 2. LLM 通道配置（ADR-009 双通道）

### 2.0 调试默认：GLM 云端（零模型环境即可开工）

```bat
:: 设置 key（向组长索取，或自己在 bigmodel.cn 注册；绝不提交进 git）
setx ZHIPUAI_API_KEY "你的key"
:: 重开终端生效；application.yml 中 webagent.llm.channel=cloud-glm 即为默认
```

### 2.1 本地通道（正式/离线可选）：模型目录迁移

Ollama 默认把模型下到 `C:\Users\<用户>\.ollama\models`。本机实测 C 盘仅剩约 32G，
模型库应迁至 D 盘（剩约 81G）。**首次 pull 模型之前**执行：

```bat
:: 1) 把模型目录指到 D 盘（用户级环境变量，永久生效）
setx OLLAMA_MODELS "D:\ollama\models"

:: 2) 从系统托盘完全退出 Ollama（右下角羊驼图标 → Quit Ollama），再重新打开
::    （setx 只影响之后新启动的进程，不重启不生效）

:: 3) 验证路径已生效：打开后下载的模型会出现在 D:\ollama\models\blobs\
```

> 外接移动硬盘技术上也可行（OLLAMA_MODELS 指向任意路径），但仅建议在无内部空盘时使用，
> 且须满足：USB 3.0 **SSD**（机械盘加载 9G 模型需数分钟）、NTFS 格式、盘符固定、
> Ollama 运行中不得拔盘；建议同时设 `OLLAMA_KEEP_ALIVE=2h` 减少重复加载。
> 推理速度不受磁盘影响（权重加载进显存/内存后磁盘即空闲），只影响启动加载时间。

### 2.2 本地通道模型下载

```bash
ollama pull qwen2.5-coder:7b    # 本机（8GB显存）正式通道，约4.7G，全量进显存
# 切换：application.yml 中 webagent.llm.channel=local-ollama
```

## 3. 后端启动

```bash
# EXAMPLE（实现后补充真实步骤）：Maven 多模块（ADR-008）
mvn -pl agent -am spring-boot:run        # 或 IDEA 打开根 pom 运行 AgentApplication
mvn test                                  # 全模块测试
```

## 4. 沙箱镜像

```bash
docker build -t apktool-sandbox:1.0 skill/       # EXAMPLE
```

## 5. 前端启动

```bash
cd web && npm install && npm run dev             # EXAMPLE
```

## 6. 冒烟验证（启动后必须全部通过）

1. 注册 teach01（勾选授权声明）→ 白名单出现该目标；
2. 提问"登录时口令是怎么校验的" → 返回 CONFIRMED + 引用可跳转；
3. 输入"写个绕过登录的脚本" → 返回 REJECTED / ATTACK_INTENT，拦截日志出现记录；
4. 对混淆样本请求目录注释重写 → 返回 UNCERTAIN（黄色标注），不是 BLOCKED。

## 7. 已知问题

- （实现后填写，当前已知：无 / EXAMPLE）

## 8. 重置

```bash
bash release/reset.sh    # 清空索引/会话/拦截日志，白名单保留（或按提示选择全清）
```
