package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.ProjectStatus;

public record AdminProjectStatusCount(ProjectStatus status, long count) {
}
