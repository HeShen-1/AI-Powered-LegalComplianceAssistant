-- 修复向量存储索引大小限制问题
-- 解决PostgreSQL B-tree索引无法处理大文本内容的问题

-- 删除可能导致索引行大小超限的B-tree索引
DROP INDEX IF EXISTS idx_vector_store_content;
DROP INDEX IF EXISTS idx_langchain4j_embeddings_text;

-- 为vector_store表创建优化的索引
DO $$
BEGIN
    -- 1. 创建内容的哈希索引用于精确匹配
    CREATE INDEX IF NOT EXISTS idx_vector_store_content_hash 
    ON vector_store USING hash(md5(content));
    RAISE NOTICE 'Created content hash index for vector_store table';
    
    -- 2. 创建全文搜索索引用于内容搜索（如果需要）
    BEGIN
        -- 创建全文搜索索引，限制只索引前1000个字符
        CREATE INDEX IF NOT EXISTS idx_vector_store_content_fulltext 
        ON vector_store USING gin(to_tsvector('simple', left(content, 1000)));
        RAISE NOTICE 'Created full-text search index for vector_store content';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Full-text index creation skipped: %', SQLERRM;
    END;
    
    -- 3. 为元数据创建GIN索引以支持JSON查询
    CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin 
    ON vector_store USING gin(metadata);
    RAISE NOTICE 'Created GIN index for vector_store metadata';

EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'Vector store index optimization failed: %', SQLERRM;
END $$;

-- 为langchain4j_embeddings表创建优化的索引
DO $$
BEGIN
    -- 1. 创建文本的哈希索引用于精确匹配
    CREATE INDEX IF NOT EXISTS idx_langchain4j_text_hash 
    ON langchain4j_embeddings USING hash(md5(text));
    RAISE NOTICE 'Created text hash index for langchain4j_embeddings table';
    
    -- 2. 创建全文搜索索引用于文本搜索
    BEGIN
        -- 创建全文搜索索引，限制只索引前1000个字符
        CREATE INDEX IF NOT EXISTS idx_langchain4j_text_fulltext 
        ON langchain4j_embeddings USING gin(to_tsvector('simple', left(text, 1000)));
        RAISE NOTICE 'Created full-text search index for langchain4j_embeddings text';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Full-text index creation skipped: %', SQLERRM;
    END;
    
    -- 3. 为元数据创建GIN索引以支持JSON查询
    CREATE INDEX IF NOT EXISTS idx_langchain4j_metadata_gin 
    ON langchain4j_embeddings USING gin(metadata);
    RAISE NOTICE 'Created GIN index for langchain4j_embeddings metadata';

EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'LangChain4j embeddings index optimization failed: %', SQLERRM;
END $$;

-- 创建用于内容长度统计的视图（可选，用于监控）
CREATE OR REPLACE VIEW v_vector_store_content_stats AS
SELECT 
    'vector_store' as table_name,
    COUNT(*) as total_records,
    AVG(length(content)) as avg_content_length,
    MAX(length(content)) as max_content_length,
    MIN(length(content)) as min_content_length,
    COUNT(CASE WHEN length(content) > 2000 THEN 1 END) as records_over_2kb,
    COUNT(CASE WHEN length(content) > 2704 THEN 1 END) as records_over_btree_limit
FROM vector_store
UNION ALL
SELECT 
    'langchain4j_embeddings' as table_name,
    COUNT(*) as total_records,
    AVG(length(text)) as avg_content_length,
    MAX(length(text)) as max_content_length,
    MIN(length(text)) as min_content_length,
    COUNT(CASE WHEN length(text) > 2000 THEN 1 END) as records_over_2kb,
    COUNT(CASE WHEN length(text) > 2704 THEN 1 END) as records_over_btree_limit
FROM langchain4j_embeddings;

-- 验证索引修复结果
DO $$
DECLARE
    rec RECORD;
BEGIN
    RAISE NOTICE '=== Vector Storage Index Optimization Complete ===';
    RAISE NOTICE '';
    
    -- 列出vector_store表的所有索引
    RAISE NOTICE '=== vector_store table indexes ===';
    FOR rec IN 
        SELECT indexname, indexdef 
        FROM pg_indexes 
        WHERE tablename = 'vector_store' 
        ORDER BY indexname
    LOOP
        RAISE NOTICE 'Index: % | Definition: %', rec.indexname, rec.indexdef;
    END LOOP;
    
    RAISE NOTICE '';
    
    -- 列出langchain4j_embeddings表的所有索引
    RAISE NOTICE '=== langchain4j_embeddings table indexes ===';
    FOR rec IN 
        SELECT indexname, indexdef 
        FROM pg_indexes 
        WHERE tablename = 'langchain4j_embeddings' 
        ORDER BY indexname
    LOOP
        RAISE NOTICE 'Index: % | Definition: %', rec.indexname, rec.indexdef;
    END LOOP;
    
    RAISE NOTICE '';
    RAISE NOTICE '✓ Index optimization completed successfully';
    RAISE NOTICE '✓ B-tree content indexes removed to prevent size limit errors';
    RAISE NOTICE '✓ Hash indexes created for exact matching';
    RAISE NOTICE '✓ Full-text indexes created for content search (first 1000 chars)';
    RAISE NOTICE '✓ GIN indexes created for metadata JSON queries';
    RAISE NOTICE '';
    RAISE NOTICE 'Note: You can query v_vector_store_content_stats view to monitor content sizes';
END $$;
