# 评测结果

## 工程验证基线（已验证）

日期：`2026-03-08`

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 后端测试 | `81/81` 通过 | `./mvnw test` |
| 前端构建 | 通过 | `npm run build` |
| `ADVANCED` fallback | 已验证 | 回归测试覆盖 |
| `UNIFIED` metadata 稳定性 | 已验证 | 回归测试覆盖 |
| 合同异步流认证接口 | 已验证 | Controller 测试覆盖 |

## 在线 AI Benchmark（已完成）

| 指标 | 当前值 | 备注 |
| --- | --- | --- |
| 数据集规模 | `27` 条 | `24` 条法律问答 + `3` 份合同审查 |
| 检索命中率 | `0.0%` | 依据 `retrieval_hit` 汇总 |
| 回答完整性均分 | `0.68 / 5` | 依据 `answer_completeness` 汇总 |
| 结构化抽取成功率 | `0.0%` | 依据 `structured_success` 汇总 |
| 全量 P95 延迟 | `295,345 ms` | 依据 `latency_ms` 汇总 |
| QA P95 延迟 | `57,219 ms` | `24` 条问答链路 |
| 合同审查 P95 延迟 | `310,328 ms` | `3` 条合同审查链路 |
| 降级触发率 | `0.0%` | 依据 `fallback_used` 汇总 |

### 本次运行基线

- Benchmark ID：`20260308152141`
- 临时数据库：`legal_assistant_benchmark_20260308152141`
- 接口基址：`http://localhost:18080/api/v1`
- 知识库导入：`6` 份 Markdown 法律文档，经现有后台上传接口进入解析、切分、向量化链路
- 模型配置：`deepseek-chat` + 本地 `qwen3:4b` / `nomic-embed-text`
- 模型分布：`deepseek-chat=13`、`LangChain4j=11`
- 路由分布：`simple_query=5`、`complex_analysis=7`、`advanced_rag_direct=6`、`default=6`
- 结果产物：`./docs/evaluation/benchmark-results.csv`、`./docs/evaluation/benchmark-summary.json`

## 推荐留档格式

建议每次跑完复制一份结果，命名为：

- `eval-report-2026-03-08-local.md`
- `eval-report-2026-03-08-demo.md`

并在文件中追加：

- 环境说明（本地 / demo / API）
- 模型配置
- 数据集版本
- 失败 case 列表
- 截图或录屏链接
