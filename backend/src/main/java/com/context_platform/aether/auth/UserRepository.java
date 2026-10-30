package com.context_platform.aether.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public User create(String email, String passwordHash, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
            INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, now(), now())
        """)
        .param(id)
        .param(email.toLowerCase().trim())
        .param(passwordHash)
        .param(displayName.trim())
        .update();

        return findById(id).orElseThrow();
    }

    public Optional<User> findById(UUID id) {
        return jdbcClient.sql("""
            SELECT id, email, password_hash, display_name, created_at, updated_at
            FROM users WHERE id = ?
        """)
        .param(id)
        .query(User.class)
        .optional();
    }

    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql("""
            SELECT id, email, password_hash, display_name, created_at, updated_at
            FROM users WHERE lower(email) = ?
        """)
        .param(email.toLowerCase().trim())
        .query(User.class)
        .optional();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcClient.sql("SELECT count(*) FROM users WHERE lower(email) = ?")
            .param(email.toLowerCase().trim())
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }
}
