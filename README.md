[简体中文](./README.md) | [English](./README_EN.md)

# 法律合规智能审查助手

基于 Spring AI + RAG + Agent 的法律合规智能审查系统。

## 项目概述

本项目是一个集自动化审查、智能化问答、专业化报告生成于一体的 AI 法律服务平台，旨在帮助中小企业和律师事务所提高合同审查效率，降低法律风险。平台通过先进的 AI 技术，实现了对法律文本的深度理解和分析，为用户提供精准、高效的法律合规支持。

## 技术栈

- **后端框架**: Java 21 + Spring Boot 3
- **AI 核心**: Spring AI + LangChain4j
- **智能体**: ReAct Agent
- **知识库**: RAG (Retrieval-Augmented Generation)
- **向量数据库**: PGVector
- **模型部署**: Ollama + OpenAI平台
- **异步通信**: SSE (Server-Sent Events)
- **工具调用**: Tool Calling
- **PDF 处理**: iText
- **网页抓取**: Jsoup
- **序列化**: Kryo
- **API 文档**: Knife4j
- **部署方案**: Serverless

## 核心功能

### 1. 智能合同审查
- **多格式支持**: 支持 `.docx`, `.pdf`, `.txt` 格式的合同文件上传。
- **异步处理**: 采用SSE（Server-Sent Events）技术，实时推送审查进度，优化用户体验。
- **风险识别与标注**: AI自动识别合同中的潜在风险条款，并按照高、中、低三个等级进行分类。
- **修改建议**: 针对风险条款，提供专业的修改建议和法律依据。
- **审查历史**: 用户可以查看自己的历史审查记录和详细结果。

### 2. RAG 法律问答
- **知识库驱动**: 基于RAG（检索增强生成）技术，结合内置的法律知识库，提供精准的法律问题解答。
- **上下文理解**: 支持多轮对话，理解上下文关联，提供更自然的交互体验。
- **法律条文引用**: 在回答中能够准确引用相关的法律法规条文，增强答案的权威性。

### 3. 智能法律顾问 (Agent)
- **工具调用**: 基于ReAct Agent实现，具备调用外部工具（如在线搜索、数据库查询）的能力，解决复杂法律问题。
- **深度分析**: 可对用户提出的复杂场景进行深度分析，提供综合性的法律建议。
- **动态交互**: 智能体能够根据问题动态规划执行步骤，提供更智能的咨询服务。

### 4. 专业的合规报告生成
- **一键生成**: 对审查完成的合同，可一键生成专业的PDF格式审查报告。
- **内容全面**: 报告包含风险统计图表、风险条款详情、修改建议和整体合规评分。
- **格式规范**: 采用规范化的报告格式，便于归档和分享。

### 5. 知识库管理 (管理员功能)
- **文档管理**: 管理员可以上传、删除、管理用于RAG的法律文档。
- **自动处理**: 上传的文档会自动进行内容解析、文本分割和向量化，并存入向量数据库。
- **统计与维护**: 提供知识库文档统计和索引重建功能。

### 6. 安全与用户管理
- **身份认证**: 基于 Spring Security 实现用户注册和登录认证。
- **权限控制**: 支持用户和管理员两种角色，保障操作安全。
- **用户管理**: 管理员可以对用户信息进行管理和维护。

## 快速开始

### 环境要求

- Java 21+
- PostgreSQL 12+（需要启用 PGVector 扩展）
- Ollama（本地 AI 模型服务）
- Maven 3.8+

### 安装步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd LegalAssistant
   ```

2. **安装 PostgreSQL 和 PGVector**
   ```sql
   -- 在 PostgreSQL 中执行
   CREATE DATABASE legal_assistant;
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

3. **安装和配置 Ollama**
   ```bash
   # 安装 Ollama
   curl -fsSL https://ollama.ai/install.sh | sh
   
   # 下载所需模型
   ollama pull qwen2:7b
   ollama pull nomic-embed-text
   ```

4. **配置数据库连接**
   
   编辑 `src/main/resources/application.yml`，更新数据库连接信息：
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/legal_assistant
       username: your_username
       password: your_password
   ```

5. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

6. **验证安装**
   
   访问以下端点验证系统状态：
   - 基础健康检查: http://localhost:8080/api/health
   - 详细健康检查: http://localhost:8080/api/health/detailed
   - AI 服务测试: http://localhost:8080/api/health/ai/test
   - API 文档: http://localhost:8080/api/doc.html

## 项目结构

```
src/
├── main/
│   ├── java/com/river/legalassistant/
│   │   ├── config/          # 配置类
│   │   ├── controller/      # 控制器
│   │   ├── entity/          # 实体类
│   │   ├── repository/      # 数据访问层
│   │   ├── service/         # 服务层
│   │   └── LegalAssistantApplication.java
│   └── resources/
│       ├── application.yml  # 应用配置
│       └── db/migration/    # 数据库迁移脚本
└── test/                    # 测试代码
```

## API 概览

系统提供以下核心API端点：

- `POST /api/contracts/upload`: 上传合同文件并创建审查任务。
- `POST /api/contracts/{reviewId}/analyze-async`: 异步执行合同审查，通过SSE返回进度。
- `GET /api/contracts/{reviewId}`: 获取指定审查任务的详细结果。
- `GET /api/contracts/{reviewId}/report`: 生成并下载PDF审查报告。
- `POST /api/ai/chat/rag`: 基于RAG知识库进行法律问答。
- `POST /api/ai/agent/consult`: 与智能法律顾问进行咨询。
- `POST /api/knowledge-base/documents`: (管理员) 上传文档到知识库。

完整的API列表和使用方法请查阅API文档。

## API 文档

启动应用后，访问 http://localhost:8080/api/doc.html 查看由Knife4j生成的完整 API 文档。

## 健康检查

系统提供多个健康检查端点，用于监控服务状态：

- `GET /health` - 基础健康检查，确认应用是否运行。
- `GET /health/detailed` - 详细健康检查，包含数据库和AI服务状态。
- `GET /health/ai/test` - AI服务功能测试，验证聊天和向量化功能是否正常。
- `GET /health/info` - 系统信息，显示应用版本、Java环境等信息。

## 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/legal_assistant
    username: postgres
    password: postgres
```

### AI 模型配置
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen2:7b
      embedding:
        options:
          model: nomic-embed-text
```

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 确认 PostgreSQL 服务正在运行。
   - 检查 `application.yml` 中的数据库连接配置是否正确。
   - 确认 PGVector 扩展已在目标数据库中成功创建。

2. **Ollama 连接失败**
   - 确认 Ollama 服务正在本地运行。
   - 检查 `ollama pull` 命令是否已成功下载所需模型。
   - 验证 `application.yml` 中的 `base-url` 配置是否正确。

3. **应用启动失败**
   - 检查 Java 版本是否为 21 或更高版本。
   - 运行 `mvn clean install` 确认所有依赖已正确安装。
   - 查看启动日志获取详细错误信息。

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 联系方式

- 问题反馈: Issues
- 邮箱: river-911@qq.com

## 更新日志

### v1.1.0
- ✅ **智能合同审查**: 实现多格式文件上传、异步分析与进度推送、风险条款识别与分级。
- ✅ **RAG法律问答**: 完成基于知识库的检索增强生成问答功能。
- ✅ **智能法律顾问**: 集成ReAct Agent，具备工具调用能力，支持复杂法律咨询。
- ✅ **合规报告生成**: 实现一键生成PDF格式的专业审查报告。
- ✅ **知识库管理**: 为管理员提供完整的后台文档管理功能。
- ✅ **用户体系**: 集成Spring Security，支持用户注册、登录和角色权限管理。
- ✅ **API文档与健康检查**: 集成Knife4j和Actuator，提供完善的API文档和监控端点。

### v1.0.0
- ✅ 完成基础环境搭建
- ✅ 集成 Spring AI + Ollama
- ✅ 配置 PostgreSQL + PGVector
- ✅ 添加基础项目结构
