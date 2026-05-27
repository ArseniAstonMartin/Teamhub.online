package com.teamhub.tenancy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Tenant} entities. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {}
