package com.teamhub.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link RefreshToken} entities. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByHash(String hash);

  /**
   * Looks up a refresh token by hash and acquires a {@code SELECT ... FOR UPDATE} row lock.
   *
   * <p>Used by the rotation flow to serialise concurrent refreshes of the same token: the second
   * transaction blocks until the first commits, then re-reads the now-revoked row and is rejected,
   * preserving the single-use guarantee under PostgreSQL's default READ COMMITTED isolation.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RefreshToken r where r.hash = :hash")
  Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);
}
