package com.context_platform.aether.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 300; // 5 minutes

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    private record AttemptInfo(int count, Instant lockedUntil) {}

    public synchronized boolean isBlocked(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return false;
        }
        if (info.lockedUntil != null) {
            if (Instant.now().isBefore(info.lockedUntil)) {
                return true;
            } else {
                attempts.remove(key);
                return false;
            }
        }
        return false;
    }

    public synchronized void recordFailure(String key) {
        AttemptInfo current = attempts.get(key);
        int newCount = (current == null) ? 1 : current.count + 1;
        if (newCount >= MAX_ATTEMPTS) {
            attempts.put(key, new AttemptInfo(newCount, Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS)));
        } else {
            attempts.put(key, new AttemptInfo(newCount, null));
        }
    }

    public synchronized void recordSuccess(String key) {
        attempts.remove(key);
    }
}
