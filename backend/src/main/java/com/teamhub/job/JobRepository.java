package com.teamhub.job;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link Job} (PRD §9, TASK-042). */
public interface JobRepository extends JpaRepository<Job, UUID> {}
