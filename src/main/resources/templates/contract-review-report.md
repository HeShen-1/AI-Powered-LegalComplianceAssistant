# 法律合规智能审查报告

*Contract Compliance Review Report*

---

## 基本信息

| 项目 | 内容 |
|------|------|
| 合同文件名 | [(${review.originalFilename})] |
| 审查状态 | [(${reviewStatus})] |
| 风险等级 | **[(${riskLevelDisplay})]** |
| 创建时间 | [(${createdAt})] |
[# th:if="${completedAt != null}"]| 完成时间 | [(${completedAt})] |
[/]| 报告生成时间 | [(${reportGeneratedAt})] |

---

## 一、执行摘要

*Executive Summary*

[# th:if="${summary != null && summary.contractType != null}"]
### 【AI智能分析摘要】

**合同性质和类型**

[(${summary.contractType})]

**风险等级**

**[(${summary.riskLevel})]** - [(${summary.riskReason})]

**核心风险点**

[# th:each="risk : ${summary.coreRisks}"]
- [(${risk})]
[/]

**行动建议**

[# th:each="action : ${summary.actionSuggestions}"]
- [(${action})]
[/]

---

[/]

### 整体评估

本次合同审查基于AI智能分析技术，对合同文本进行全面的风险识别和合规性检查。审查结果显示该合同整体风险等级为：**[(${riskLevelDisplay})]**。[(${riskLevelDescription})]

[# th:if="${totalRisks != null && totalRisks > 0}"]
共识别出 **[(${totalRisks})]** 项潜在风险点，需要重点关注。
[/]

[# th:if="${coreRiskAlerts != null && !coreRiskAlerts.isEmpty()}"]
### 核心风险提示

[# th:each="alert, iterStat : ${coreRiskAlerts}"]
[(${iterStat.count})]、[(${alert})]
[/]
[/]

## 二、风险分布统计

*Risk Statistics*

[# th:if="${riskClauses == null || riskClauses.isEmpty()}"]
✅ 未发现具体风险条款。
[/]

[# th:if="${riskClauses != null && !riskClauses.isEmpty()}"]
| 风险等级 | 数量 | 占比 |
|---------|------|------|
[# th:each="stat : ${riskStatistics}"]
| **[(${stat.levelName})]** | [(${stat.count})] | [(${stat.percentage})]% |
[/]
[/]

## 三、风险条款详情

*Risk Clause Details*

[# th:if="${riskClauses == null || riskClauses.isEmpty()}"]
✅ 未发现具体风险条款。
[/]

[# th:if="${riskClauses != null && !riskClauses.isEmpty()}"]
[# th:each="clause, iterStat : ${riskClauses}"]
### [(${clause.riskIcon})] 风险点 [(${iterStat.count})]：[(${clause.riskType})] [**[(${clause.riskLevelDisplay})]**]

| 项目 | 内容 |
|------|------|
| **风险类型** | [(${clause.riskType})] |
| **风险等级** | **[(${clause.riskLevelDisplay})]** |
[# th:if="${clause.clauseText != null && !#strings.isEmpty(clause.clauseText)}"]| **条款内容** | [(${clause.clauseText})] |
[/][# th:if="${clause.riskDescription != null && !#strings.isEmpty(clause.riskDescription)}"]| **风险描述** | [(${clause.riskDescription})] |
[/][# th:if="${clause.suggestion != null && !#strings.isEmpty(clause.suggestion)}"]| **改进建议** | [(${clause.suggestion})] |
[/]

---

[/]
[/]

## 四、AI深度分析

*AI Deep Analysis*

[# th:if="${analysis != null}"]

### 法律性质分析

[# th:if="${analysis.legalNature != null}"]
- **合同类型**：[(${analysis.legalNature.contractType})]
- **适用法规**：[(${analysis.legalNature.governingLaws})]
- **法律关系认定**：[(${analysis.legalNature.legalRelationship})]
[/]

[# th:if="${analysis.keyClauses != null && !analysis.keyClauses.isEmpty()}"]
### 关键条款专业解读

[# th:each="clause, iterStat : ${analysis.keyClauses}"]
#### [(${iterStat.count})]、[(${clause.clauseName})]

**条款解读**：[(${clause.interpretation})]

**风险说明**：[(${clause.risk})]

[/]
[/]

[# th:if="${analysis.riskAssessments != null && !analysis.riskAssessments.isEmpty()}"]
### 法律风险深度评估

[# th:each="risk : ${analysis.riskAssessments}"]
#### [(${risk.riskCategory})] - [[(${risk.level})]级风险]

**风险描述**：[(${risk.description})]

**防范措施**：[(${risk.prevention})]

[/]
[/]

[# th:if="${analysis.complianceCheck != null}"]
### 合规性检查

- **适用法规**：[(${analysis.complianceCheck.regulation})]
- **符合性评估**：[(${analysis.complianceCheck.conformity})]
- **合规差距**：[(${analysis.complianceCheck.gaps})]
[/]

[# th:if="${analysis.businessImpact != null}"]
### 商业影响分析

- **受影响方**：[(${analysis.businessImpact.party})]
- **影响描述**：[(${analysis.businessImpact.impact})]
- **财务影响**：[(${analysis.businessImpact.financialImpact})]
[/]

[/]

[# th:if="${analysis == null}"]
*AI深度分析服务暂时不可用，建议重新生成报告或咨询技术支持。*
[/]

## 五、优先建议

*Priority Recommendations*

[# th:if="${improvements != null && improvements.suggestions != null && !improvements.suggestions.isEmpty()}"]
### AI增强改进建议

[# th:each="suggestion, iterStat : ${improvements.suggestions}"]
#### [(${iterStat.count})]、[优先级：[(${suggestion.priority})]] [(${suggestion.problemDescription})]

**问题描述**：[(${suggestion.problemDescription})]

**修改建议**：[(${suggestion.suggestedModification})]

**预期效果**：[(${suggestion.expectedEffect})]

---

[/]
[/]

### 通用合规建议

- 建议聘请专业法律顾问对高风险条款进行进一步审核
- 在合同签署前，确保所有风险点都已得到妥善处理
- 定期更新合同条款，以符合最新的法律法规要求
- 建立合同履行监督机制，及时发现和处理执行过程中的问题

## 六、免责声明

*Disclaimer*

> **重要提示**：
>
> 本报告由法律合规智能审查助手基于AI技术自动生成，仅供参考使用。报告内容基于算法分析，可能存在误差或遗漏。
>
> 在做出任何法律决策前，强烈建议咨询专业法律顾问。本系统及其运营方对因使用本报告产生的任何后果不承担法律责任。
>
> 本报告中的所有分析和建议仅代表系统观点，不构成正式法律意见。

---

**报告生成系统**：法律合规智能审查助手

**生成时间**：[(${reportGeneratedAt})]

**审查ID**：[(${review.id})]

---

*本报告为自动生成，保留所有权利。*

