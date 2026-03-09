package com.river.LegalAssistant.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        String rawPassword = resolvePassword(args);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("Generated BCrypt hash:");
        System.out.println(encodedPassword);
    }

    private static String resolvePassword(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return args[0];
        }

        String envPassword = System.getenv("RAW_PASSWORD");
        if (envPassword != null && !envPassword.isBlank()) {
            return envPassword;
        }

        throw new IllegalArgumentException("Provide a password as the first argument or via RAW_PASSWORD.");
    }
}

