# QA RAG DeepSeek 调优设计

## 背景

`2026-03-08` 的完整 benchmark 已显示当前 QA 链路存在两个核心问题：

- `retrieval_hit_rate = 0.0%`
- `answer_completeness_average = 0.68 / 5`

结合 benchmark 结果与当前实现，可以推断问题主要不在“没有向量库”，而在“检索结果没有稳定进入最终回答链路，也没有稳定回传给 benchmark / 前端”。

## 本轮目标

把 QA 场景统一收敛为：

- **本地仅负责 embedding / 向量检索**
- **最终答案统一由 DeepSeek 生成**
- **统一响应稳定返回 sources / sourceCount / route metadata**
- **benchmark 与应用使用一致的检索命中语义**

本轮优先提升：

1. QA 检索命中率
2. QA 回答完整性

本轮暂不优先优化：

1. 合同审查结构化成功率
2. 合同审查时延

## 方案对比

### 方案 A：只改 benchmark 判分

优点：

- 修改最少
- 很快看到分数变化

缺点：

- 项目真实能力没有提升
- 面试时容易被追问穿

### 方案 B：改 QA 主链路 + 同步 benchmark

优点：

- 应用真实行为与 benchmark 结果一致
- 面试可讲“本地检索 + 云端生成”的工程取舍
- 能同时提升结果可信度与展示价值

缺点：

- 需要同时改服务端链路与 benchmark 取数

### 方案 C：重写整套路由 / RAG

优点：

- 理论上上限最高

缺点：

- 风险大
- 时间成本高
- 容易牵出无关回归

## 采用方案

采用 **方案 B**。

## 设计

### 1. QA 生成模型统一

对于启用知识库的 QA 请求：

- 不再使用本地聊天模型作为最终回答模型
- 本地 Ollama 仅保留 embedding 能力
- 最终回答优先由 DeepSeek 生成

这样可以把“知识 grounding”与“答案生成质量”分层：

- 本地负责低成本检索
- 云端负责更高质量生成

### 2. 路由语义收紧

对于 `useKnowledgeBase=true` 的法律问答：

- `UNIFIED` 下简单问答优先进入“检索增强问答”路径
- 避免当前“simple_query 直接本地回答 / sourceCount=0”的情况
- `ADVANCED_RAG` 保持检索语义，但最终答案也应优先走 DeepSeek 生成

### 3. sources 标准化

无论底层使用 Spring AI 还是 LangChain4j：

- 响应都要返回稳定的 `sources`
- `sourceCount` 必须与 `sources` 一致
- `metadata.actualModel` 必须反映最终生成模型
- `metadata.routeReason` 必须反映实际路由

### 4. benchmark 同步

benchmark runner 本轮应显式采用：

- 本地 embedding 模型：`nomic-embed-text`
- 生成模型：DeepSeek

同时 benchmark 需要：

- 兼容新的 `sources` 格式
- 以统一语义判定检索命中
- 让结果与用户实际看到的响应一致

## 影响范围

预计主要涉及：

- QA 路由与统一聊天响应
- RAG / 检索增强回答链路
- benchmark runner / scoring helper
- README / eval report 中的模型基线说明

## 测试策略

### 服务端

- 覆盖 `useKnowledgeBase=true` 时 QA 走检索增强路径
- 覆盖 QA 最终模型优先为 DeepSeek
- 覆盖 `sources` / `sourceCount` / metadata 返回完整

### benchmark

- 覆盖 sources 解析兼容性
- 覆盖检索命中判定逻辑

### 端到端

- 先跑小批量 QA 样例验证趋势
- 再跑完整 27 条 benchmark

## 风险与控制

### 风险 1：DeepSeek 依赖增加时延

控制：

- 优先解决命中率和完整性
- 时延数据作为下一轮优化项

### 风险 2：不同链路返回结构不一致

控制：

- 在统一响应层做 sources 标准化

### 风险 3：benchmark 分数改善但路由解释变差

控制：

- 保留 `actualModel` / `routeReason` / `sourceCount` / `latencyMs`
- README 同步更新“本地 embedding + DeepSeek 生成”基线说明
