package com.teamformation.backend.service;

import com.teamformation.backend.config.FormationJobProperties;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.repository.FormationJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled recovery for abandoned formation jobs.
 *
 * <p>A synchronous formation request always commits a READY job before it runs
 * and moves it to RUNNING inside T2. If the process dies, or a deployment
 * interrupts a run, that row can remain committed in READY or RUNNING forever
 * and no future request can start because the active-job rule would reject it.
 * This reaper fails such rows after a configurable timeout.</p>
 *
 * <p>Safety properties:</p>
 * <ul>
 *   <li>Discovery is a read-only candidate query. It never performs a bulk
 *       update.</li>
 *   <li>Each candidate is re-read with {@code SELECT ... FOR UPDATE} in its own
 *       transaction and its status and age are rechecked before any write. If a
 *       concurrent T2 completed the job first, the reaper does nothing.</li>
 *   <li>The row lock makes the sweep safe when several application instances
 *       run the schedule at the same time.</li>
 *   <li>The job is failed with the stable {@code STALE_JOB} / {@code RECOVERY}
 *       pair and a safe message. There is no automatic retry: a new run must be
 *       requested explicitly.</li>
 * </ul>
 */
@Component
public class FormationJobReaper {

    private static final Logger log = LoggerFactory.getLogger(FormationJobReaper.class);

    private static final List<JobStatus> RECOVERABLE_STATUSES =
            List.of(JobStatus.READY, JobStatus.RUNNING);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final FormationJobRepository formationJobRepository;
    private final FormationJobProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Counter reapedCounter;

    public FormationJobReaper(
            FormationJobRepository formationJobRepository,
            FormationJobProperties properties,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.formationJobRepository = formationJobRepository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.reapedCounter = Counter.builder("formation.jobs.reaped")
                .description("Formation jobs failed by the stale-job reaper")
                .register(meterRegistry);
    }

    /**
     * Scheduled entry point. A failure in discovery or in one candidate is
     * logged and never rethrown, so a transient database problem cannot stop
     * future scheduled sweeps.
     */
    @Scheduled(
            fixedDelayString = "${formation.jobs.reaper-interval:PT1M}",
            initialDelayString = "${formation.jobs.reaper-initial-delay:PT1M}")
    public void scheduledReap() {
        try {
            reapStaleJobs();
        } catch (RuntimeException failure) {
            log.error("The stale formation job sweep failed", failure);
        }
    }

    /**
     * Visible for tests and operations. Reaps every job older than the
     * configured stale timeout relative to now.
     *
     * @return the number of jobs moved to FAILED
     */
    public int reapStaleJobs() {
        return reapStaleJobs(Instant.now());
    }

    /**
     * Visible for tests. The supplied instant is the sweep's clock; the stale
     * timeout is still applied exactly as in production.
     *
     * @return the number of jobs moved to FAILED
     */
    public int reapStaleJobs(Instant now) {
        Duration timeout = properties.staleTimeout();
        Instant cutoff = now.minus(timeout);
        List<Long> candidates = formationJobRepository.findStaleJobIds(
                RECOVERABLE_STATUSES, cutoff);

        int reaped = 0;
        for (Long jobId : candidates) {
            try {
                Boolean changed = transactionTemplate.execute(
                        status -> reapOne(jobId, now, cutoff, timeout));
                if (Boolean.TRUE.equals(changed)) {
                    reaped++;
                    reapedCounter.increment();
                }
            } catch (RuntimeException failure) {
                log.error("Could not reap stale formation job {}", jobId, failure);
            }
        }
        if (reaped > 0) {
            log.warn("Recovered {} stale formation job(s) older than {}",
                    reaped, timeout);
        }
        return reaped;
    }

    private boolean reapOne(Long jobId, Instant now, Instant cutoff, Duration timeout) {
        FormationJob job = formationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) {
            return false;
        }
        if (job.getJobStatus() != JobStatus.READY
                && job.getJobStatus() != JobStatus.RUNNING) {
            // A concurrent transaction already finished or failed the job.
            return false;
        }

        Instant anchor = job.getStartedAt() != null
                ? job.getStartedAt()
                : job.getCreatedAt();
        if (anchor != null && !anchor.isBefore(cutoff)) {
            // The candidate query and this recheck use the same cutoff, but a
            // concurrent commit could have refreshed the anchor between them.
            return false;
        }

        String reason = "The formation job was still " + job.getJobStatus()
                + " after the " + timeout + " stale timeout and was recovered as FAILED";
        job.markFailed(FailureCode.STALE_JOB, FailurePhase.RECOVERY,
                truncate(reason), now);
        formationJobRepository.saveAndFlush(job);
        log.warn("Formation job {} in project {} was reaped as STALE_JOB",
                jobId, job.getProject().getId());
        return true;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}
