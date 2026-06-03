package com.teamhub.billing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link TenantQuotaState} (TASK-065). */
public interface TenantQuotaStateRepository extends JpaRepository<TenantQuotaState, UUID> {}
