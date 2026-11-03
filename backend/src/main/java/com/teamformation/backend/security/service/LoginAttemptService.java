package com.teamformation.backend.security.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory failed-login throttle: five failures for one email within fifteen
 * minutes block further attempts until the window expires.
 *
 * <p>Limitation: state is per application instance and is lost on restart.
 * A multi-instance deployment needs a shared store such as Redis.</p>
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        String key = key(email);
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (Instant.now().isAfter(window.windowStart().plus(WINDOW))) {
            attempts.remove(key, window);
            return false;
        }
        return window.failures() >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailure(String email) {
        String key = key(email);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || now.isAfter(current.windowStart().plus(WINDOW))) {
                return new AttemptWindow(1, now);
            }
            return new AttemptWindow(current.failures() + 1, current.windowStart());
        });
    }

    public void reset(String email) {
        attempts.remove(key(email));
    }

    public void clearAll() {
        attempts.clear();
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record AttemptWindow(int failures, Instant windowStart) {
    }
}
