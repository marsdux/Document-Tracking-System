package doctrack.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic brute-force mitigation: after MAX_ATTEMPTS failed logins for a
 * username, that username is locked out for LOCKOUT_MILLIS. In-memory
 * and per-process (resets on restart) -- appropriate for a single-user
 * desktop tool, not a substitute for server-side rate limiting on a
 * networked deployment.
 */
public final class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MILLIS = 5 * 60 * 1000L; // 5 minutes

    private static final class State {
        int failedAttempts = 0;
        long lockedUntil = 0L;
    }

    private static final ConcurrentHashMap<String, State> ATTEMPTS = new ConcurrentHashMap<>();

    private LoginAttemptTracker() { }

    /** Returns remaining lockout seconds if locked, or 0 if the user may attempt login. */
    public static long secondsUntilUnlocked(String username) {
        State s = ATTEMPTS.get(key(username));
        if (s == null) return 0;
        long remaining = s.lockedUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    public static void recordFailure(String username) {
        State s = ATTEMPTS.computeIfAbsent(key(username), k -> new State());
        synchronized (s) {
            s.failedAttempts++;
            if (s.failedAttempts >= MAX_ATTEMPTS) {
                s.lockedUntil = System.currentTimeMillis() + LOCKOUT_MILLIS;
            }
        }
    }

    public static void recordSuccess(String username) {
        ATTEMPTS.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase();
    }
}
