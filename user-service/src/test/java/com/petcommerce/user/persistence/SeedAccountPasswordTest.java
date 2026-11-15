package com.petcommerce.user.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R-TEST-03: the demo accounts documented in the README must be able to log in with 'password123'. */
class SeedAccountPasswordTest {

    @Test
    void seededDemoAccountHashMatchesDocumentedPassword() throws Exception {
        String sql = new ClassPathResource("db/migration/V4__fix_seed_account_passwords.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("SET password_hash = '([^']+)'").matcher(sql);
        assertTrue(matcher.find(), "V4 migration must set a password hash");

        String seededHash = matcher.group(1);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("password123", seededHash));
        assertFalse(encoder.matches("password", seededHash));
    }
}
