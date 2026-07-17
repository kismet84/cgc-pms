package com.cgcpms.org.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrgPositionUserAssignmentRequest(@NotNull List<@NotNull Long> userIds) {
}
