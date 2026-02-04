package com.biasharahub;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class BiasharaHubApplicationTests {

    @Test
    void contextLoads() {
    }

    /** Run this test to get a BCrypt hash for "password123"; use it in 006-update-demo-password-to-password123.xml if login fails. */
    @Test
    void generatePassword123Hash() {
        String hash = new BCryptPasswordEncoder().encode("password123");
        Assertions.assertTrue(new BCryptPasswordEncoder().matches("password123", hash));
        System.out.println("BCrypt hash for 'password123' (use in 006 XML): " + hash);
    }
}
