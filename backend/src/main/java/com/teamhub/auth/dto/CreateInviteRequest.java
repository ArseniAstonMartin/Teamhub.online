package com.teamhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for creating a new invite (PRD §6.5, TASK-054). */
public record CreateInviteRequest(@Email @NotBlank String email, @NotBlank String role) {}
