# 法律合规 AI 项目架构说明

## 1. 项目要解决的问题

这个项目面向两个真实场景：

- **法律问答**：用户希望快速获得基于知识库的合规问答结果
- **合同审查**：用户希望上传合同后获得风险点、修改建议和可下载报告

难点不在于“能不能调起模型”，而在于：

- 不同类型问题需要不同能力链路
- 高级模型不稳定时不能让系统整体不可用
- 前端需要知道这次回答到底走了什么路径
- 合同审查耗时长，必须支持异步进度与结果回传

## 2. 为什么是双框架方案

### Spring AI

负责更贴近 Spring Boot 工程体系的一层：

- 模型接入
- `ChatClient` 集成
- Spring 生态下的配置与依赖管理

### LangChain4j

负责更强调检索编排的一层：

- 高级 RAG 链路
- 查询转换
- 多阶段召回与重排序

### 设计结论

本项目不是为了“堆技术栈”，而是将：

- **模型接入**
- **高级检索编排**

拆分为两套更容易解释的职责边界，方便在面试中讲清楚“为什么不是所有问题都走同一条链路”。

## 3. 系统总览

```mermaid
flowchart LR
    FE["Vue3 前端"] --> API["Spring Boot API 层"]
    API --> AUTH["JWT / Spring Security"]
    API --> CHAT["统一聊天控制器"]
    API --> CONTRACT["合同审查控制器"]
    API --> KB["知识库管理控制器"]

    CHAT --> ROUTER["问题复杂度路由"]
    ROUTER --> BASIC["BASIC<br/>Ollama + 基础RAG"]
    ROUTER --> ADV["ADVANCED<br/>DeepSeek Agent"]
    ROUTER --> ARAG["ADVANCED_RAG<br/>LangChain4j"]
    ADV --> FALLBACK["Fallback 到可用模型"]

    BASIC --> VECTOR["PGVector"]
    ARAG --> VECTOR
    KB --> ETL["解析 / 切分 / 向量化"]
    ETL --> VECTOR

    CONTRACT --> SSE["SSE 进度流"]
    CONTRACT --> PDF["PDF 报告生成"]

    API --> DB["PostgreSQL / Flyway"]
```

## 4. 统一聊天请求流转

```mermaid
flowchart TD
    Q["用户提问"] --> U["/chat 或 /chat/stream"]
    U --> M{"用户指定模式?"}
    M -->|BASIC| B["基础问答 + RAG"]
    M -->|ADVANCED| A["DeepSeek Agent"]
    M -->|ADVANCED_RAG| R["高级RAG"]
    M -->|UNIFIED| C["复杂度分析"]

    C -->|simple_query| R
    C -->|complex_analysis| A
    C -->|default| A

    A --> F{"DeepSeek 可用?"}
    F -->|yes| AO["高级分析结果"]
    F -->|no| FB["Fallback 到可用模型"]

    R --> RO["检索增强结果"]
    B --> BO["基础结果"]
    FB --> FO["回退结果"]

    AO --> META["补齐 metadata"]
    RO --> META
    BO --> META
    FO --> META

    META --> RESP["返回 answer + metadata"]
```

## 5. 知识库入库链路

```mermaid
flowchart TD
    UP["上传法律文档"] --> PARSE["文档解析"]
    PARSE --> SPLIT["按条款/段落切分"]
    SPLIT --> EMBED["向量化"]
    EMBED --> STORE["写入 PGVector"]
    STORE --> SEARCH["Hybrid Search / 高级RAG 检索"]
```

## 6. 合同审查链路

```mermaid
flowchart TD
    FILE["上传合同"] --> SAVE["文件落盘与记录创建"]
    SAVE --> ASYNC["异步分析任务"]
    ASYNC --> STREAM["SSE 推送进度"]
    ASYNC --> EXTRACT["风险抽取 / 建议生成"]
    EXTRACT --> REPORT["PDF 报告生成"]
    REPORT --> DOWNLOAD["前端下载报告"]
```

## 7. 为什么要保留多模式

| 模式 | 保留原因 | 典型 trade-off |
| --- | --- | --- |
| `BASIC` | 低成本、本地可复现 | 能力弱但依赖最少 |
| `ADVANCED` | 复杂分析与工具调用 | 效果更强，但依赖 API 可用性 |
| `ADVANCED_RAG` | 引用知识库更稳定 | 更可控，但链路更重 |
| `UNIFIED` | 统一对外接口 | 需要额外路由逻辑与解释信息 |

## 8. 可观测性设计

统一聊天结果固定暴露以下 metadata：

| 字段 | 含义 | 面试价值 |
| --- | --- | --- |
| `actualModel` | 实际执行模型 | 不是“用户选了什么”，而是“系统真正用了什么” |
| `routeReason` | 路由原因 | 展示智能分流而不是拍脑袋切换 |
| `fallbackUsed` | 是否降级 | 体现高可用设计 |
| `sourceCount` | 检索来源数量 | 体现回答 grounding 强度 |
| `latencyMs` | 本次耗时 | 支持演示与性能对比 |

## 9. 面试时推荐的讲法

建议按下面顺序回答架构问题：

1. 先讲**业务场景**
2. 再讲**为什么需要多条 AI 链路**
3. 再讲**为什么要做统一接口**
4. 最后讲**fallback、metadata、SSE** 这些工程化细节

这样比“先背技术名词”更像真正做过项目的人。

