-- 创建聊天会话表
CREATE TABLE chat_session (
    id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建会话更新时间索引
CREATE INDEX idx_chat_session_user_id ON chat_session(user_id);
CREATE INDEX idx_chat_session_updated_at ON chat_session(updated_at DESC);

-- 创建聊天消息表
CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_role CHECK (role IN ('user', 'assistant'))
);

-- 创建消息查询索引
CREATE INDEX idx_chat_message_session_id ON chat_message(session_id);
CREATE INDEX idx_chat_message_created_at ON chat_message(created_at);

-- 添加注释
COMMENT ON TABLE chat_session IS '聊天会话表，存储用户的对话会话';
COMMENT ON TABLE chat_message IS '聊天消息表，存储会话中的具体消息';
COMMENT ON COLUMN chat_session.id IS '会话ID (UUID)';
COMMENT ON COLUMN chat_session.user_id IS '用户ID';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.created_at IS '创建时间';
COMMENT ON COLUMN chat_session.updated_at IS '最后更新时间';
COMMENT ON COLUMN chat_message.id IS '消息ID';
COMMENT ON COLUMN chat_message.session_id IS '会话ID';
COMMENT ON COLUMN chat_message.role IS '角色 (user/assistant)';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.metadata IS '元数据 (JSON格式)';
COMMENT ON COLUMN chat_message.created_at IS '创建时间';

