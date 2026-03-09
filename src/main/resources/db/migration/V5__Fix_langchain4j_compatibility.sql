-- 修复LangChain4j PgVectorEmbeddingStore兼容性问题
-- LangChain4j期望的表结构与当前表结构不匹配

-- 删除现有的向量存储表
DROP TABLE IF EXISTS vector_store CASCADE;

-- 创建兼容LangChain4j PgVectorEmbeddingStore的表结构
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 创建兼容LangChain4j的表结构
        EXECUTE '
        CREATE TABLE vector_store (
            embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            embedding vector(768) NOT NULL,
            text TEXT NOT NULL,
            metadata JSONB DEFAULT ''{}''::jsonb,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )';
        RAISE NOTICE 'Created LangChain4j compatible vector_store table with vector(768) type';
    ELSE
        -- 如果没有vector扩展，使用数组类型作为备选
        EXECUTE '
        CREATE TABLE vector_store (
            embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            embedding FLOAT8[] NOT NULL,
            text TEXT NOT NULL,
            metadata JSONB DEFAULT ''{}''::jsonb,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )';
        RAISE NOTICE 'Created LangChain4j compatible vector_store table with FLOAT8[] type';
    END IF;
END $$;

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_vector_store_text ON vector_store(text);
CREATE INDEX IF NOT EXISTS idx_vector_store_created_at ON vector_store(created_at);

-- 为向量相似度搜索创建索引
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 尝试创建HNSW索引（性能最佳）
        BEGIN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw ON vector_store USING hnsw (embedding vector_cosine_ops)';
            RAISE NOTICE 'HNSW vector index created successfully for similarity search';
        EXCEPTION WHEN OTHERS THEN
            -- 如果HNSW不支持，尝试创建IVFFlat索引
            BEGIN
                EXECUTE 'CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_ivf ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)';
                RAISE NOTICE 'IVFFlat vector index created as fallback for similarity search';
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'Vector index creation failed: %. Continuing without vector index.', SQLERRM;
            END;
        END;
    ELSE
        RAISE NOTICE 'PGVector extension not available, skipping vector index creation';
    END IF;
END $$;

-- 验证表结构
DO $$
DECLARE
    rec RECORD;
BEGIN
    -- 检查表是否创建成功
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'vector_store') THEN
        RAISE NOTICE 'vector_store table created successfully with LangChain4j compatible structure';
        
        -- 显示表结构信息
        FOR rec IN 
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns 
            WHERE table_name = 'vector_store' 
            ORDER BY ordinal_position
        LOOP
            RAISE NOTICE 'Column: % | Type: % | Nullable: % | Default: %', 
                rec.column_name, rec.data_type, rec.is_nullable, rec.column_default;
        END LOOP;
    ELSE
        RAISE WARNING 'Failed to create vector_store table';
    END IF;
END $$;
