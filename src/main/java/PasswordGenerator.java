import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "123456"; // <-- 在这里输入你的明文密码
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("请将下面这个哈希值复制到数据库的 password_hash 字段中：");
        System.out.println(encodedPassword);
    }
}