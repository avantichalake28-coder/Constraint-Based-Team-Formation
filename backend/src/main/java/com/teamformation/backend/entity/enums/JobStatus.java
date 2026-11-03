package com.teamformation.backend.entity.enums;

public enum JobStatus {
    READY,
    RUNNING,
    COMPLETED,
    FAILED;

    /**
     * A formation run is active while it is queued (READY) or executing
     * (RUNNING). COMPLETED and FAILED runs are no longer active.
     */
    public boolean isActive() {
        return this == READY || this == RUNNING;
    }
}

