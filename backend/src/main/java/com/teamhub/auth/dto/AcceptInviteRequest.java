package com.teamhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for accepting an invite and setting a password (PRD §6.5, TASK-054). */
public record AcceptInviteRequest(
    @NotBlank String token, @Size(min = 8) @NotBlank String password) {}
