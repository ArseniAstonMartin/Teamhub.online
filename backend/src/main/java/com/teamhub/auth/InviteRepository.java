package com.teamhub.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Invite} entities (TASK-054). */
public interface InviteRepository extends JpaRepository<Invite, UUID> {

  Optional<Invite> findByTokenHash(String tokenHash);

  List<Invite> findByTenantIdAndEmailAndAcceptedAtIsNull(UUID tenantId, String email);

  Optional<Invite> findByIdAndTenantId(UUID id, UUID tenantId);
}
