-- 添加演示用户
-- 用户名: demo
-- 密码: 123456

INSERT INTO users (username, full_name, email, password_hash, role, enabled, created_at, updated_at) 
VALUES (
    'demo',
    '演示用户',
    'demo@example.com',
    '$2a$10$N.sDvYoFvYJJXLJj8xYV5ObXSfTGvdNz.f./LGWMLfaAwUc/WCdGe', -- 密码: 123456
    'USER',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
