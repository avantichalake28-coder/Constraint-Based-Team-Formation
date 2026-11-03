package com.teamformation.backend.security.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory hourly throttle for resend-verification requests.
 *
 * <p>Every address, existing or not, is counted so the throttle itself cannot
 * be used to discover whether an account exists. State is per application
 * instance and is lost on restart; a multi-instance deployment needs a shared
 * store such as Redis.</p>
 */
@Service
public class VerificationResendRateLimiter {

    static final int MAX_REQUESTS = 3;
    static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentMap<String, AttemptWindow> requests = new ConcurrentHashMap<>();

    public boolean tryAcquire(String email) {
        String key = key(email);
        Instant now = Instant.now();
        boolean[] allowed = new boolean[1];
        requests.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.windowStart().plus(WINDOW))) {
                allowed[0] = true;
                return new AttemptWindow(1, now);
            }
            if (current.requests() >= MAX_REQUESTS) {
                allowed[0] = false;
                return current;
            }
            allowed[0] = true;
            return new AttemptWindow(current.requests() + 1, current.windowStart());
        });
        return allowed[0];
    }

    public void clearAll() {
        requests.clear();
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record AttemptWindow(int requests, Instant windowStart) {
    }
}
