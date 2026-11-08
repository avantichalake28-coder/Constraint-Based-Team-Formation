package com.teamformation.backend.service;

import com.teamformation.backend.config.FormationJobProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Bounded in-process admission control for synchronous formation runs.
 *
 * <p>A permit is taken for the whole request (pre-check, T1, T2), not only for
 * the algorithm. A request that cannot obtain both a global and a per-mentor
 * permit is rejected immediately with a controlled 429 response; it never
 * queues. Permits are released in a {@code finally} block on success, failure and
 * exception.</p>
 *
 * <p>This is deliberately a single-instance guard. It is not a durable queue or
 * a distributed lease; the database project lock remains the cross-instance
 * correctness mechanism.</p>
 */
@Component
public class FormationExecutionThrottle {

    private final Semaphore globalPermits;
    private final int perMentorLimit;
    private final ConcurrentHashMap<Long, Semaphore> perMentorPermits =
            new ConcurrentHashMap<>();

    public FormationExecutionThrottle(FormationJobProperties properties) {
        this.globalPermits = new Semaphore(properties.globalConcurrency(), true);
        this.perMentorLimit = properties.perMentorConcurrency();
    }

    /**
     * Attempts to acquire the permits without blocking.
     *
     * @return a lease that must be closed, or {@link Lease#NONE} when the
     *         request must be rejected with 429
     */
    public Lease tryAcquire(long mentorId) {
        if (!globalPermits.tryAcquire()) {
            return Lease.NONE;
        }
        Semaphore mentorPermits = perMentorPermits.computeIfAbsent(
                mentorId, ignored -> new Semaphore(perMentorLimit, true));
        if (!mentorPermits.tryAcquire()) {
            globalPermits.release();
            return Lease.NONE;
        }
        return new Lease(globalPermits, mentorPermits);
    }

    /**
     * Visible for testing: the number of free global permits.
     */
    public int availableGlobalPermits() {
        return globalPermits.availablePermits();
    }

    public static final class Lease implements AutoCloseable {

        public static final Lease NONE = new Lease(null, null);

        private final Semaphore global;
        private final Semaphore perMentor;
        private boolean closed;

        private Lease(Semaphore global, Semaphore perMentor) {
            this.global = global;
            this.perMentor = perMentor;
        }

        public boolean acquired() {
            return global != null;
        }

        @Override
        public synchronized void close() {
            if (closed || global == null) {
                closed = true;
                return;
            }
            closed = true;
            perMentor.release();
            global.release();
        }
    }
}
