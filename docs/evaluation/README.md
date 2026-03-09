# AI 评测资产说明

## 目标

这套评测资产的目的不是做学术 benchmark，而是把项目从“感觉效果不错”变成“能重复跑、能固定看表、能在面试中拿证据说话”。

## 数据集组成

- `24` 条法律问答样例：`./dataset/legal_qa_cases.json`
- `3` 条合同审查样例：`./dataset/contract_review_cases.json`
- `6` 份知识库样例文档：`./dataset/knowledge-base/`
- `3` 份合同样例：`./dataset/contracts/`
- 结果模板：`./result-template.csv`
- 汇总脚本：`./aggregate-results.ps1`

## 指标定义

| 指标 | 字段 | 说明 |
| --- | --- | --- |
| 检索命中率 | `retrieval_hit` | 是否命中预期知识文档，取值 `0/1` |
| 回答完整性 | `answer_completeness` | 是否覆盖预期要点，推荐按 `0~5` 打分 |
| 结构化抽取成功率 | `structured_success` | 合同审查结构化字段是否完整，取值 `0/1` |
| P95 延迟 | `latency_ms` | 从发起请求到拿到最终结果的耗时 |
| 降级触发率 | `fallback_used` | 是否触发 fallback，取值 `0/1` |

## 推荐执行方式

### 问答类

对 `legal_qa_cases.json` 中每条 case：

1. 选择 `UNIFIED` 模式发起请求
2. 记录：
   - 实际模型
   - 路由原因
   - 是否使用知识库
   - 返回来源数
   - 回答文本
   - 延迟
3. 结合 `expectedDocIds` 与 `expectedPoints` 打分并填写 CSV

### 合同审查类

对 `contract_review_cases.json` 中每条 case：

1. 上传对应合同样例
2. 等待异步分析完成
3. 记录：
   - 风险条款提取是否完整
   - 结构化字段是否完整
   - 报告是否可下载
   - 总耗时
4. 填写 CSV

## CSV 填写约定

`result-template.csv` 中各列含义如下：

- `case_id`：样例 ID
- `category`：`qa` 或 `contract`
- `retrieval_hit`：`0/1`
- `answer_completeness`：`0~5`
- `structured_success`：`0/1`，仅合同类必填
- `latency_ms`：整数
- `fallback_used`：`0/1`
- `notes`：备注，例如实际模型、路由原因、错误信息

## 结果汇总

执行：

```bash
powershell -ExecutionPolicy Bypass -File .\docs\evaluation\aggregate-results.ps1 -InputCsv .\docs\evaluation\result-template.csv
```

脚本会输出：

- 检索命中率
- 回答完整性均分
- 结构化抽取成功率
- P95 延迟
- 降级触发率

## 使用建议

- 面试前至少跑 1 次并保留结果截图
- 线上演示优先挑 3 个最稳 case
- 若模型环境变化，重新跑一版并保留日期

