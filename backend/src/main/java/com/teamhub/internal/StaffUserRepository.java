package com.teamhub.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link StaffUser} entities. */
public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

  Optional<StaffUser> findByEmail(String email);
}
