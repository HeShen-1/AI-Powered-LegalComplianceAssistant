-- 重置向量存储表结构
-- 解决Flyway校验和不匹配问题

-- 删除所有向量存储相关的表和索引
DROP TABLE IF EXISTS vector_store CASCADE;
DROP TABLE IF EXISTS langchain4j_embeddings CASCADE;

-- 创建Spring AI兼容的vector_store表
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 创建Spring AI兼容的表结构
        CREATE TABLE vector_store (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            content TEXT NOT NULL,
            metadata JSONB DEFAULT '{}'::jsonb,
            embedding vector(768) NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created Spring AI compatible vector_store table with vector(768) type';
    ELSE
        -- 如果没有vector扩展，使用数组类型作为备选
        CREATE TABLE vector_store (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            content TEXT NOT NULL,
            metadata JSONB DEFAULT '{}'::jsonb,
            embedding FLOAT8[] NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created Spring AI compatible vector_store table with FLOAT8[] type';
    END IF;
    
    -- 创建索引以优化查询性能
    CREATE INDEX IF NOT EXISTS idx_vector_store_content ON vector_store(content);
    CREATE INDEX IF NOT EXISTS idx_vector_store_created_at ON vector_store(created_at);
    
    -- 为向量相似度搜索创建索引
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 尝试创建HNSW索引（性能最佳）
        BEGIN
            CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw ON vector_store USING hnsw (embedding vector_cosine_ops);
            RAISE NOTICE 'HNSW vector index created successfully for similarity search';
        EXCEPTION WHEN OTHERS THEN
            -- 如果HNSW不支持，尝试创建IVFFlat索引
            BEGIN
                CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_ivf ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
                RAISE NOTICE 'IVFFlat vector index created as fallback for similarity search';
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'Vector index creation failed: %. Continuing without vector index.', SQLERRM;
            END;
        END;
    END IF;
END $$;

-- 创建LangChain4j专用的嵌入表
DO $$
BEGIN
    -- 检查是否有vector扩展
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 创建LangChain4j兼容的表结构
        CREATE TABLE langchain4j_embeddings (
            embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            embedding vector(768) NOT NULL,
            text TEXT NOT NULL,
            metadata JSONB DEFAULT '{}'::jsonb,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created LangChain4j embeddings table with vector(768) type';
    ELSE
        -- 如果没有vector扩展，使用数组类型作为备选
        CREATE TABLE langchain4j_embeddings (
            embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            embedding FLOAT8[] NOT NULL,
            text TEXT NOT NULL,
            metadata JSONB DEFAULT '{}'::jsonb,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created LangChain4j embeddings table with FLOAT8[] type';
    END IF;
    
    -- 创建索引以优化查询性能
    CREATE INDEX IF NOT EXISTS idx_langchain4j_embeddings_text ON langchain4j_embeddings(text);
    CREATE INDEX IF NOT EXISTS idx_langchain4j_embeddings_created_at ON langchain4j_embeddings(created_at);
    
    -- 为向量相似度搜索创建索引
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        -- 尝试创建HNSW索引（性能最佳）
        BEGIN
            CREATE INDEX IF NOT EXISTS idx_langchain4j_embeddings_hnsw ON langchain4j_embeddings USING hnsw (embedding vector_cosine_ops);
            RAISE NOTICE 'HNSW vector index created for LangChain4j embeddings table';
        EXCEPTION WHEN OTHERS THEN
            -- 如果HNSW不支持，尝试创建IVFFlat索引
            BEGIN
                CREATE INDEX IF NOT EXISTS idx_langchain4j_embeddings_ivf ON langchain4j_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
                RAISE NOTICE 'IVFFlat vector index created for LangChain4j embeddings table';
            EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'Vector index creation failed for LangChain4j table: %. Continuing without vector index.', SQLERRM;
            END;
        END;
    END IF;
END $$;

-- 验证最终表结构
DO $$
DECLARE
    rec RECORD;
BEGIN
    RAISE NOTICE '=== Final vector storage tables structure ===';
    
    -- 验证Spring AI表
    RAISE NOTICE '=== vector_store table (Spring AI) ===';
    FOR rec IN 
        SELECT column_name, data_type, is_nullable, column_default
        FROM information_schema.columns 
        WHERE table_name = 'vector_store' 
        ORDER BY ordinal_position
    LOOP
        RAISE NOTICE 'Column: % | Type: % | Nullable: % | Default: %', 
            rec.column_name, rec.data_type, rec.is_nullable, rec.column_default;
    END LOOP;
    
    -- 验证LangChain4j表
    RAISE NOTICE '=== langchain4j_embeddings table (LangChain4j) ===';
    FOR rec IN 
        SELECT column_name, data_type, is_nullable, column_default
        FROM information_schema.columns 
        WHERE table_name = 'langchain4j_embeddings' 
        ORDER BY ordinal_position
    LOOP
        RAISE NOTICE 'Column: % | Type: % | Nullable: % | Default: %', 
            rec.column_name, rec.data_type, rec.is_nullable, rec.column_default;
    END LOOP;
    
    -- 验证表结构
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'vector_store') AND
       EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'langchain4j_embeddings') THEN
        RAISE NOTICE '✓ Both vector storage tables created successfully';
        RAISE NOTICE '✓ Dual vector storage architecture is ready';
    ELSE
        RAISE WARNING '✗ Vector storage tables creation failed';
    END IF;
END $$;
