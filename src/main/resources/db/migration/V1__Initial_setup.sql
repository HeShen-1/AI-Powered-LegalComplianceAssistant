-- 初始化数据库脚本
-- 启用必要的扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 尝试启用 PGVector 扩展，如果不可用则跳过
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
    RAISE NOTICE 'PGVector extension loaded successfully';
EXCEPTION 
    WHEN undefined_file THEN
        RAISE NOTICE 'PGVector extension not available, vector functionality will be limited';
    WHEN OTHERS THEN
        RAISE NOTICE 'Failed to load PGVector extension: %', SQLERRM;
END $$;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    document_type VARCHAR(50) NOT NULL, -- 'LAW', 'REGULATION', 'CASE', 'CONTRACT_TEMPLATE'
    source_file VARCHAR(255),
    file_hash VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量存储表（用于 RAG）
-- 根据是否有vector扩展来决定embedding字段类型
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 如果有vector扩展，使用vector类型
        EXECUTE '
        CREATE TABLE IF NOT EXISTS vector_store (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            content TEXT NOT NULL,
            metadata JSONB,
            embedding vector(384),
            document_id BIGINT REFERENCES knowledge_documents(id) ON DELETE CASCADE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )';
    ELSE
        -- 如果没有vector扩展，使用数组类型作为备选
        EXECUTE '
        CREATE TABLE IF NOT EXISTS vector_store (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            content TEXT NOT NULL,
            metadata JSONB,
            embedding FLOAT8[],
            document_id BIGINT REFERENCES knowledge_documents(id) ON DELETE CASCADE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )';
        RAISE NOTICE 'Created vector_store table with FLOAT8[] instead of vector type';
    END IF;
END $$;

-- 创建合同审查记录表
CREATE TABLE IF NOT EXISTS contract_reviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    original_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    content_text TEXT,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    risk_level VARCHAR(10), -- 'HIGH', 'MEDIUM', 'LOW'
    total_risks INTEGER DEFAULT 0,
    review_result JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- 创建风险条款表
CREATE TABLE IF NOT EXISTS risk_clauses (
    id BIGSERIAL PRIMARY KEY,
    contract_review_id BIGINT NOT NULL REFERENCES contract_reviews(id) ON DELETE CASCADE,
    clause_text TEXT NOT NULL,
    risk_type VARCHAR(50) NOT NULL,
    risk_level VARCHAR(10) NOT NULL, -- 'HIGH', 'MEDIUM', 'LOW'
    risk_description TEXT NOT NULL,
    suggestion TEXT,
    legal_basis TEXT,
    position_start INTEGER,
    position_end INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建法律问答记录表
CREATE TABLE IF NOT EXISTS legal_qa_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    question TEXT NOT NULL,
    answer TEXT,
    context_used JSONB, -- 存储 RAG 检索到的相关文档
    response_time_ms INTEGER,
    feedback_rating INTEGER CHECK (feedback_rating >= 1 AND feedback_rating <= 5),
    feedback_comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_type ON knowledge_documents(document_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_created_at ON knowledge_documents(created_at);

-- 为向量相似度搜索创建索引
DO $$
BEGIN
    -- 检查是否有vector扩展和vector_store表
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') AND 
       EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'vector_store') THEN
        
        -- 尝试创建HNSW索引
        BEGIN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops)';
            RAISE NOTICE 'HNSW vector index created successfully';
        EXCEPTION WHEN OTHERS THEN
            -- 如果HNSW不支持，尝试创建IVFFlat索引
            BEGIN
                EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING ivfflat (embedding vector_cosine_ops)';
                RAISE NOTICE 'IVFFlat vector index created as fallback';
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'Vector index creation failed, continuing without vector index. Error: %', SQLERRM;
            END;
        END;
    ELSE
        RAISE NOTICE 'PGVector extension not available, skipping vector index creation';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_vector_store_document_id ON vector_store(document_id);

CREATE INDEX IF NOT EXISTS idx_contract_reviews_user_id ON contract_reviews(user_id);
CREATE INDEX IF NOT EXISTS idx_contract_reviews_status ON contract_reviews(review_status);
CREATE INDEX IF NOT EXISTS idx_contract_reviews_created_at ON contract_reviews(created_at);

CREATE INDEX IF NOT EXISTS idx_risk_clauses_contract_review_id ON risk_clauses(contract_review_id);
CREATE INDEX IF NOT EXISTS idx_risk_clauses_risk_level ON risk_clauses(risk_level);

CREATE INDEX IF NOT EXISTS idx_legal_qa_sessions_user_id ON legal_qa_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_legal_qa_sessions_session_id ON legal_qa_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_legal_qa_sessions_created_at ON legal_qa_sessions(created_at);

CREATE INDEX IF NOT EXISTS idx_system_configs_key ON system_configs(config_key);

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, description) VALUES
('app.version', '1.0.0', '应用版本'),
('rag.chunk_size', '1000', 'RAG 文档分块大小'),
('rag.chunk_overlap', '100', 'RAG 文档分块重叠大小'),
('rag.similarity_threshold', '0.7', 'RAG 相似度阈值'),
('ai.model.chat', 'qwen2.5:7b', '默认对话模型'),
('ai.model.embedding', 'nomic-embed-text', '默认嵌入模型')
ON CONFLICT (config_key) DO NOTHING;

-- 插入默认管理员用户（密码：admin123，实际使用时应使用加密密码）
INSERT INTO users (username, email, password_hash, full_name, role) VALUES
('admin', 'admin@legalassistant.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9tYoHPwk/1cLlMe', '系统管理员', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- 创建触发器函数用于更新 updated_at 字段
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为需要的表创建 updated_at 触发器
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_documents_updated_at BEFORE UPDATE ON knowledge_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_system_configs_updated_at BEFORE UPDATE ON system_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
