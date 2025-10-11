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

### 1. 统一聊天服务
- **统一API入口**: 提供 `/chat` 和 `/chat/stream` 统一接口，根据参数智能路由到最合适的后端AI服务。
- **多种聊天模式**:
    - **基础模式**: 本地Ollama模型 + RAG，适用通用法律问答。
    - **高级模式**: DeepSeek Agent模型，具备工具调用和复杂推理能力，处理深度法律分析。
    - **高级RAG模式**: LangChain4j高级RAG框架，提供查询转换、多源检索和重排序能力。
    - **统一智能模式**: 根据问题复杂度自动选择`高级模式`或`高级RAG模式`。
- **聊天历史管理**: 自动保存用户与AI的完整对话历史，支持按会话查阅和管理。

### 2. 智能合同审查
- **多格式支持**: 支持 `.docx`, `.pdf`, `.txt` 格式的合同文件上传。
- **异步处理**: 采用SSE（Server-Sent Events）技术，实时推送审查进度，优化用户体验。
- **风险识别与标注**: AI自动识别合同中的潜在风险条款，并按照高、中、低三个等级进行分类。
- **修改建议**: 针对风险条款，提供专业的修改建议和法律依据。
- **审查历史**: 用户可以查看自己的历史审查记录和详细结果。

### 3. 专业的合规报告生成
- **一键生成**: 对审查完成的合同，可一键生成专业的PDF格式审查报告。
- **内容全面**: 报告包含风险统计图表、风险条款详情、修改建议和整体合规评分。
- **格式规范**: 采用规范化的报告格式，便于归档和分享。

### 4. 知识库管理 (管理员功能)
- **文档管理**: 管理员可以上传、删除、管理用于RAG的法律文档，支持批量操作。
- **自动处理**: 上传的文档会自动进行内容解析、文本分割和向量化，并存入向量数据库。
- **索引运维**: 提供向量数据库重建、清理和统计功能，确保知识库高效运行。
- **统计与维护**: 提供知识库文档统计和索引重建功能。

### 5. 安全与用户管理
- **身份认证**: 基于 Spring Security 和 JWT 实现用户注册和登录认证。
- **权限控制**: 支持`用户`和`管理员`两种角色，保障操作安全。
- **用户管理**: 管理员可以对用户信息进行全面的增删改查和状态管理。

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
   **注意**: 高级法律顾问功能依赖 [DeepSeek](https://platform.deepseek.com/) API，请在 `application.yml` 中配置您的 `DEEPSEEK_API_KEY`。

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

- `POST /chat`: 统一聊天入口，支持多种模式。
- `POST /chat/stream`: 统一流式聊天入口。
- `GET /chat/sessions`: 获取当前用户的聊天会话列表。
- `GET /chat/sessions/{sessionId}`: 获取指定会话的详细消息。
- `DELETE /chat/sessions/{sessionId}`: 删除指定聊天会话。
- `POST /contracts/upload`: 上传合同文件并创建审查任务。
- `GET /contracts/{reviewId}/analyze-async`: 异步执行合同审查，通过SSE返回进度。
- `GET /contracts/{reviewId}/report`: 生成并下载PDF审查报告。
- `POST /knowledge-base/documents/upload-single`: (管理员) 上传单个文档到知识库。
- `POST /admin/vector-db/rebuild-sync`: (管理员) 同步重建向量数据库。

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

### v1.2.0 (本次更新)
- ✅ **统一聊天服务**: 新增 `/chat` 和 `/chat/stream` 统一API入口，实现智能路由和多模型支持。
- ✅ **聊天历史管理**: 实现完整的聊天历史记录功能，支持按会话管理消息。
- ✅ **高级RAG集成**: 集成LangChain4j Advanced RAG框架，提升问答质量。
- ✅ **DeepSeek Agent**: 集成DeepSeek模型和ReAct Agent，增强复杂问题处理能力。
- ✅ **向量数据库管理**: 增加向量数据库重建、清理和统计等高级运维功能。
- ✅ **代码结构优化**: 将AI相关、用户认证、知识库等模块的Controller进行拆分和重构，提升可维护性。

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
