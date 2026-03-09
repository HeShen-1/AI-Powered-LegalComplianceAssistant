-- 为ollama和DeepSeek模型分别创建聊天记忆表
-- 基于SpringAI官方文档的JdbcChatMemoryRepository表结构

-- Ollama模型聊天记忆表
CREATE TABLE IF NOT EXISTS ollama_chat_memory (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL, -- USER, ASSISTANT, SYSTEM, TOOL_CALL, TOOL_RESPONSE
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    message_index INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT unique_ollama_conversation_message UNIQUE (conversation_id, message_index)
);

-- DeepSeek模型聊天记忆表
CREATE TABLE IF NOT EXISTS deepseek_chat_memory (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL, -- USER, ASSISTANT, SYSTEM, TOOL_CALL, TOOL_RESPONSE
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    message_index INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT unique_deepseek_conversation_message UNIQUE (conversation_id, message_index)
);

-- 为快速查询创建索引
CREATE INDEX IF NOT EXISTS idx_ollama_chat_memory_conversation_id ON ollama_chat_memory (conversation_id);
CREATE INDEX IF NOT EXISTS idx_ollama_chat_memory_created_at ON ollama_chat_memory (created_at);
CREATE INDEX IF NOT EXISTS idx_ollama_chat_memory_message_type ON ollama_chat_memory (message_type);

CREATE INDEX IF NOT EXISTS idx_deepseek_chat_memory_conversation_id ON deepseek_chat_memory (conversation_id);
CREATE INDEX IF NOT EXISTS idx_deepseek_chat_memory_created_at ON deepseek_chat_memory (created_at);
CREATE INDEX IF NOT EXISTS idx_deepseek_chat_memory_message_type ON deepseek_chat_memory (message_type);

-- 创建更新时间触发器函数（如果不存在）
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为表添加更新时间触发器
DROP TRIGGER IF EXISTS update_ollama_chat_memory_updated_at ON ollama_chat_memory;
CREATE TRIGGER update_ollama_chat_memory_updated_at
    BEFORE UPDATE ON ollama_chat_memory
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_deepseek_chat_memory_updated_at ON deepseek_chat_memory;
CREATE TRIGGER update_deepseek_chat_memory_updated_at
    BEFORE UPDATE ON deepseek_chat_memory
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 插入测试数据（可选）
INSERT INTO ollama_chat_memory (conversation_id, message_type, content, metadata) VALUES 
('test-conversation-1', 'SYSTEM', '你是一个专业的法律合规智能助手。', '{"model": "qwen2:1.5b", "provider": "ollama"}'),
('test-conversation-1', 'USER', '你好，我需要法律帮助。', '{"timestamp": "2025-10-02T00:00:00Z"}'),
('test-conversation-1', 'ASSISTANT', '您好！我是专业的法律合规智能助手，很高兴为您提供帮助。请告诉我您遇到的法律问题。', '{"model": "qwen2:1.5b", "provider": "ollama"}')
ON CONFLICT (conversation_id, message_index) DO NOTHING;

INSERT INTO deepseek_chat_memory (conversation_id, message_type, content, metadata) VALUES 
('test-conversation-2', 'SYSTEM', '你是一个专业的法律AI助手，拥有深度推理和工具调用能力。', '{"model": "deepseek-chat", "provider": "deepseek"}'),
('test-conversation-2', 'USER', '帮我分析一个合同的风险。', '{"timestamp": "2025-10-02T00:00:00Z"}'),
('test-conversation-2', 'ASSISTANT', '我将为您进行专业的合同风险分析。请提供合同内容，我会从多个维度进行详细评估。', '{"model": "deepseek-chat", "provider": "deepseek"}')
ON CONFLICT (conversation_id, message_index) DO NOTHING;
