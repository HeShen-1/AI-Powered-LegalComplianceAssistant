package com.river.LegalAssistant.validator;

import com.river.LegalAssistant.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 密码验证器
 * 确保密码符合安全要求
 */
@Component
public class PasswordValidator {
    
    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    
    /**
     * 验证密码是否符合要求
     *
     * @param password 待验证的密码
     * @throws ValidationException 如果密码不符合要求
     */
    public void validate(String password) {
        List<String> errors = new ArrayList<>();
        
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("密码长度至少" + MIN_LENGTH + "位");
        }
        
        if (password != null) {
            if (!UPPERCASE.matcher(password).find()) {
                errors.add("密码必须包含大写字母");
            }
            
            if (!LOWERCASE.matcher(password).find()) {
                errors.add("密码必须包含小写字母");
            }
            
            if (!DIGIT.matcher(password).find()) {
                errors.add("密码必须包含数字");
            }
            
            if (!SPECIAL.matcher(password).find()) {
                errors.add("密码必须包含特殊字符 (!@#$%^&*(),.?\":{}|<>)");
            }
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("密码不符合要求: " + String.join(", ", errors));
        }
    }
    
    /**
     * 检查密码强度
     *
     * @param password 密码
     * @return 强度等级: WEAK, MEDIUM, STRONG
     */
    public PasswordStrength checkStrength(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return PasswordStrength.WEAK;
        }
        
        int score = 0;
        
        // 长度加分
        if (password.length() >= 12) score += 2;
        else if (password.length() >= MIN_LENGTH) score += 1;
        
        // 字符类型加分
        if (UPPERCASE.matcher(password).find()) score++;
        if (LOWERCASE.matcher(password).find()) score++;
        if (DIGIT.matcher(password).find()) score++;
        if (SPECIAL.matcher(password).find()) score++;
        
        if (score >= 6) return PasswordStrength.STRONG;
        if (score >= 4) return PasswordStrength.MEDIUM;
        return PasswordStrength.WEAK;
    }
    
    /**
     * 密码强度枚举
     */
    public enum PasswordStrength {
        WEAK,
        MEDIUM,
        STRONG
    }
}

