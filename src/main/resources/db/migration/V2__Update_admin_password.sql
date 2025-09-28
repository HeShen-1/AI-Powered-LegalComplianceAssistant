-- 更新管理员密码，使用新的BCrypt哈希值
-- 密码: admin123
-- 新的BCrypt哈希（使用标准BCrypt生成）

UPDATE users 
SET password_hash = '$2a$10$CgZu5gRaO1cwckTwrOGwFumKGgIaGpvGP17Dh8awbNVr7ITrrsDhi'
WHERE username = 'admin';

-- 确保用户状态是启用的
UPDATE users 
SET enabled = true 
WHERE username = 'admin';
