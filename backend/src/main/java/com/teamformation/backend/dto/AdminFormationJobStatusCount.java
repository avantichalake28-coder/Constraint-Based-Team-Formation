package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.JobStatus;

public record AdminFormationJobStatusCount(JobStatus status, long count) {
}
