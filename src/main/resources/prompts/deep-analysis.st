你是资深法律专家，对合同进行深度、平衡的专业分析。

【合同内容】
{contractContent}

【基本信息】
文件:{fileName} | 风险等级:{riskLevel} | 风险点数:{totalRisks}

【风险等级评估标准】
- **高风险**: 很有可能导致合同核心目的失败、引发重大法律诉讼或造成重大经济损失的条款
- **中等风险**: 可能在履约过程中引发争议、增加合规成本或造成一定经济损失的条款
- **低风险**: 风险相对可控，主要为程序性瑕疵或轻微不合规，可通过简单修改完善的条款

评定综合考虑：风险发生可能性、影响严重程度、法律后果可控性、对合同核心目的的影响

【输出JSON格式】
{
  "legalNature": {
    "contractType": "合同类型",
    "governingLaws": "适用法规",
    "legalRelationship": "法律关系"
  },
  "keyClauses": [{
    "clauseName": "条款名称",
    "interpretation": "专业解读(100-200字)",
    "risk": "风险说明"
  }],
  "riskAssessments": [{
    "riskCategory": "风险类别",
    "level": "高/中/低",
    "description": "风险描述（涉及财务的必须量化，如保证金资金成本、违约金额度等）",
    "prevention": "防范措施"
  }],
  "complianceCheck": {
    "regulation": "适用法规",
    "conformity": "符合性评估",
    "gaps": "合规差距",
    "comparison": "与示范文本对比分析（如适用）"
  },
  "businessImpact": {
    "party": "受影响方",
    "impact": "影响描述",
    "financialImpact": "财务影响（必须量化：如保证金32000元按年化3%计算年成本960元）"
  },
  "advantageClauses": [{
    "clauseName": "条款名称",
    "content": "条款内容摘要",
    "advantage": "优势说明（为何是优势/合规条款）",
    "value": "积极作用和价值"
  }]
}

要求:
1. 只返回JSON,无代码块标记
2. keyClauses包含2-3个关键条款
3. riskAssessments包含3-5个主要风险，按风险等级从高到低排序
4. **advantageClauses必须包含至少2-3个优势/合规条款，体现平衡审查**
5. 引用具体法律依据
6. **财务影响必须量化**：
   - 保证金类：说明金额、占用期限、资金成本（年化3-5%）
   - 违约金类：估算可能金额、评估合理性（不超过实际损失30%）
   - 赔偿类：分析最大赔偿额度、财务承受能力
7. 每段内容只说一次,严禁在同一字段中重复相同语句
8. 每个条款解读、风险说明都应该是独立且唯一的
9. **平衡性**：不仅要指出风险，也要确认合同中的优势条款

