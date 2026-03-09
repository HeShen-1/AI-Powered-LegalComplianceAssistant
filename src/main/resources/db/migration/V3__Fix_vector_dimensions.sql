-- 修复向量维度不匹配问题
-- nomic-embed-text 模型实际生成 768 维向量，但数据库表定义为 384 维

-- 删除现有的向量存储表
DROP TABLE IF EXISTS vector_store CASCADE;

-- 重新创建向量存储表，使用正确的 768 维
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 如果有vector扩展，使用vector类型，768维
        EXECUTE '
        CREATE TABLE vector_store (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            content TEXT NOT NULL,
            metadata JSONB,
            embedding vector(768),
            document_id BIGINT REFERENCES knowledge_documents(id) ON DELETE CASCADE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )';
        RAISE NOTICE 'Created vector_store table with vector(768) type';
    ELSE
        -- 如果没有vector扩展，使用数组类型作为备选
        EXECUTE '
        CREATE TABLE vector_store (
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

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_vector_store_document_id ON vector_store(document_id);

-- 为向量相似度搜索创建索引
DO $$
BEGIN
    -- 检查是否有vector扩展和vector_store表
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') AND 
       EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'vector_store') THEN
        
        -- 尝试创建HNSW索引
        BEGIN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops)';
            RAISE NOTICE 'HNSW vector index created successfully for 768 dimensions';
        EXCEPTION WHEN OTHERS THEN
            -- 如果HNSW不支持，尝试创建IVFFlat索引
            BEGIN
                EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING ivfflat (embedding vector_cosine_ops)';
                RAISE NOTICE 'IVFFlat vector index created as fallback for 768 dimensions';
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'Vector index creation failed, continuing without vector index. Error: %', SQLERRM;
            END;
        END;
    ELSE
        RAISE NOTICE 'PGVector extension not available, skipping vector index creation';
    END IF;
END $$;
