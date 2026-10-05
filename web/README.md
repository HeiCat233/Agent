# web/ —— Web 前端（React + TS + TailwindCSS）

> 四模块共享设计。各组实现各自前端实例；状态徽章文案与 ADR-004 状态机一一对应。

## 模块与页面要素

| 模块 | 页面要素 |
|:--|:--|
| 目标管理 | 注册向导（上传APK→自动SHA-256→勾选授权声明→入白名单）；白名单列表带来源标签（自研/FOSS/教师指定） |
| 分析对话 | 会话流；分析类型快捷按钮（含"目录注释重写"）；自由提问框；**状态徽章**：CONFIRMED绿 / UNCERTAIN黄+“建议教师复核” / BLOCKED红 / REJECTED红+原因 |
| 证据面板 | smali 片段高亮+行号；引用点击**跳转对应行**；置信度四项分解可视化；注释树渲染 + **导出 .md 按钮**（FR-09） |
| 拦截日志 | append-only 只读表格（时间/query/状态/reason_code/trace_id）——透明性即卖点 |

## API 约定

| 方法 | 路径 | 说明 |
|:--|:--|:--|
| POST | /targets | 注册目标（含授权声明） |
| POST | /analyze | 发起分析（SSE 流式返回） |
| GET | /sessions/{id} | 会话历史 |
| GET | /audit-log | 拦截日志（只读） |
| GET | /analysis/{id}/export | 导出注释树 .md（FR-09） |

## 交互红线

- UNCERTAIN 必须**展示给用户**并带复核标注，不得静默吞掉或伪装成错误；
- REJECTED/BLOCKED 必须展示 reason_code 对应的边界说明文案；
- 导出 .md 文件名建议：`annotated_tree_{target_id}_{date}.md`。
