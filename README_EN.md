[简体中文](./README.md) | [English](./README_EN.md)

# AI-Powered Legal Compliance Assistant

A legal compliance intelligent review system based on Spring AI + RAG + Agent.

## Project Overview

This project is an AI-powered legal service platform that integrates automated review, intelligent Q&A, and professional report generation. It aims to help small to medium-sized enterprises and law firms improve contract review efficiency and reduce legal risks. By leveraging advanced AI technology, the platform achieves a deep understanding and analysis of legal texts, providing users with accurate and efficient legal compliance support.

## Technology Stack

- **Backend Framework**: Java 21 + Spring Boot 3
- **AI Core**: Spring AI + LangChain4j
- **Agent**: ReAct Agent
- **Knowledge Base**: RAG (Retrieval-Augmented Generation)
- **Vector Database**: PGVector
- **Model Deployment**: Ollama + OpenAI Platform
- **Asynchronous Communication**: SSE (Server-Sent Events)
- **Tool Calling**: Tool Calling
- **PDF Processing**: iText
- **Web Scraping**: Jsoup
- **Serialization**: Kryo
- **API Documentation**: Knife4j
- **Deployment Strategy**: Serverless

## Core Features

### 1. Unified Chat Service
- **Unified API Endpoint**: Provides `/chat` and `/chat/stream` as unified endpoints that intelligently route requests to the most suitable backend AI service.
- **Multiple Chat Modes**:
    - **Basic Mode**: Local Ollama model + RAG for general legal Q&A.
    - **Advanced Mode**: DeepSeek Agent model with tool-calling and complex reasoning capabilities for in-depth legal analysis.
    - **Advanced RAG Mode**: LangChain4j advanced RAG framework, offering query transformation, multi-source retrieval, and re-ranking.
    - **Unified Smart Mode**: Automatically selects between `Advanced Mode` and `Advanced RAG Mode` based on question complexity.
- **Chat History Management**: Automatically saves the complete conversation history between the user and the AI, supporting session-based review and management.

### 2. Intelligent Contract Review
- **Multi-Format Support**: Supports uploading contract files in `.docx`, `.pdf`, and `.txt` formats.
- **Asynchronous Processing**: Uses SSE (Server-Sent Events) to push review progress in real-time, optimizing the user experience.
- **Risk Identification & Highlighting**: The AI automatically identifies potential risk clauses in contracts and categorizes them into high, medium, and low-risk levels.
- **Modification Suggestions**: Provides professional suggestions and legal justifications for risky clauses.
- **Review History**: Users can access their historical review records and detailed results.

### 3. Professional Compliance Report Generation
- **One-Click Generation**: Generates professional PDF review reports for completed contract reviews with a single click.
- **Comprehensive Content**: Reports include risk statistics charts, detailed risk clauses, modification suggestions, and an overall compliance score.
- **Standardized Format**: Uses a standardized report format for easy archiving and sharing.

### 4. Knowledge Base Management (Admin Function)
- **Document Management**: Administrators can upload, delete, and manage legal documents used for RAG, with support for batch operations.
- **Automated Processing**: Uploaded documents are automatically parsed, chunked, vectorized, and stored in the vector database.
- **Index Maintenance**: Provides vector database rebuilding, cleaning, and statistics functions to ensure efficient knowledge base operation.
- **Statistics & Maintenance**: Provides knowledge base statistics and index rebuilding functionalities.

### 5. Security & User Management
- **Authentication**: Implements user registration and login authentication based on Spring Security and JWT.
- **Authorization**: Supports `USER` and `ADMIN` roles to ensure operational security.
- **User Management**: Admins can perform comprehensive CRUD operations and manage the status of user information.

## Quick Start

### Prerequisites

- Java 21+
- PostgreSQL 12+ (with PGVector extension enabled)
- Ollama (for local AI model serving)
- Maven 3.8+

### Installation Steps

1. **Clone the project**
   ```bash
   git clone <repository-url>
   cd LegalAssistant
   ```

2. **Install PostgreSQL and PGVector**
   ```sql
   -- Execute in PostgreSQL
   CREATE DATABASE legal_assistant;
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

3. **Install and configure Ollama**
   ```bash
   # Install Ollama
   curl -fsSL https://ollama.ai/install.sh | sh
   
   # Pull required models
   ollama pull qwen2:7b
   ollama pull nomic-embed-text
   ```
   **Note**: The advanced legal consultation feature relies on the [DeepSeek](https://platform.deepseek.com/) API. Please configure your `DEEPSEEK_API_KEY` in `application.yml`.

4. **Configure database connection**
   
   Edit `src/main/resources/application.yml` and update the database connection info:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/legal_assistant
       username: your_username
       password: your_password
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

6. **Verify installation**
   
   Access the following endpoints to verify the system status:
   - Basic Health Check: http://localhost:8080/api/health
   - Detailed Health Check: http://localhost:8080/api/health/detailed
   - AI Service Test: http://localhost:8080/api/health/ai/test
   - API Documentation: http://localhost:8080/api/doc.html

## Project Structure

```
src/
├── main/
│   ├── java/com/river/legalassistant/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # Controllers
│   │   ├── entity/          # Entity classes
│   │   ├── repository/      # Data access layer
│   │   ├── service/         # Service layer
│   │   └── LegalAssistantApplication.java
│   └── resources/
│       ├── application.yml  # Application configuration
│       └── db/migration/    # Database migration scripts
└── test/                    # Test code
```

## API Overview

The system provides the following core API endpoints:

- `POST /chat`: Unified chat endpoint supporting multiple modes.
- `POST /chat/stream`: Unified streaming chat endpoint.
- `GET /chat/sessions`: Get the current user's chat session list.
- `GET /chat/sessions/{sessionId}`: Get detailed messages for a specific session.
- `DELETE /chat/sessions/{sessionId}`: Delete a specific chat session.
- `POST /contracts/upload`: Upload a contract file and create a review task.
- `GET /contracts/{reviewId}/analyze-async`: Asynchronously perform contract review and get progress via SSE.
- `GET /contracts/{reviewId}/report`: Generate and download the PDF review report.
- `POST /knowledge-base/documents/upload-single`: (Admin) Upload a single document to the knowledge base.
- `POST /admin/vector-db/rebuild-sync`: (Admin) Synchronously rebuild the vector database.

For a complete list of APIs and their usage, please refer to the API documentation.

## API Documentation

After starting the application, visit http://localhost:8080/api/doc.html to view the complete API documentation generated by Knife4j.

## Health Check

The system provides multiple health check endpoints for monitoring service status:

- `GET /health` - Basic health check to confirm if the application is running.
- `GET /health/detailed` - Detailed health check, including the status of the database and AI services.
- `GET /health/ai/test` - AI service functional test to verify chat and embedding functionalities.
- `GET /health/info` - System information, displaying application version, Java environment, etc.

## Configuration

### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/legal_assistant
    username: postgres
    password: postgres
```

### AI Model Configuration
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

## Troubleshooting

### Common Issues

1. **Database Connection Fails**
   - Confirm that the PostgreSQL service is running.
   - Check if the database connection settings in `application.yml` are correct.
   - Ensure the PGVector extension has been successfully created in the target database.

2. **Ollama Connection Fails**
   - Confirm that the Ollama service is running locally.
   - Check if the required models have been successfully downloaded using the `ollama pull` command.
   - Verify that the `base-url` in `application.yml` is correct.

3. **Application Fails to Start**
   - Check if your Java version is 21 or higher.
   - Run `mvn clean install` to ensure all dependencies are correctly installed.
   - Check the startup logs for detailed error messages.

## Contribution Guidelines

1. Fork the project
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Create a Pull Request

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Contact

- Issue Tracker: Issues
- Email: river-911@qq.com

## Changelog

### v1.2.0 (This Update)
- ✅ **Unified Chat Service**: Added unified `/chat` and `/chat/stream` API endpoints with intelligent routing and multi-model support.
- ✅ **Chat History Management**: Implemented full chat history functionality, supporting session-based message management.
- ✅ **Advanced RAG Integration**: Integrated the LangChain4j Advanced RAG framework to improve Q&A quality.
- ✅ **DeepSeek Agent**: Integrated the DeepSeek model and ReAct Agent to enhance complex problem-solving capabilities.
- ✅ **Vector Database Management**: Added advanced maintenance features for the vector database, including rebuilding, cleaning, and statistics.
- ✅ **Code Structure Optimization**: Refactored and separated controllers for AI, user authentication, and knowledge base modules to improve maintainability.

### v1.1.0
- ✅ **Intelligent Contract Review**: Implemented multi-format file upload, asynchronous analysis with progress push, and risk clause identification/classification.
- ✅ **RAG Legal Q&A**: Completed RAG-based Q&A functionality with the knowledge base.
- ✅ **AI Legal Agent**: Integrated ReAct Agent with tool-calling capabilities for complex legal consultations.
- ✅ **Compliance Report Generation**: Implemented one-click generation of professional PDF review reports.
- ✅ **Knowledge Base Management**: Provided a complete backend for document management for administrators.
- ✅ **User System**: Integrated Spring Security for user registration, login, and role-based access control.
- ✅ **API Docs & Health Check**: Integrated Knife4j and Actuator for comprehensive API documentation and monitoring endpoints.

### v1.0.0
- ✅ Set up the basic project environment.
- ✅ Integrated Spring AI + Ollama.
- ✅ Configured PostgreSQL + PGVector.
- ✅ Added the basic project structure.
