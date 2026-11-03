package com.teamformation.backend.dto;

import java.util.List;

/**
 * Result of a successful roster add. SRNs already present are ignored and
 * reported rather than failing the request.
 */
public record RosterAddResponse(
        List<RosterEntryResponse> added,
        List<String> alreadyOnRoster
) {
}
