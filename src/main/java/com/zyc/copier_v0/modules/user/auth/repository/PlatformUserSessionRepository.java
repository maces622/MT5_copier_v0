package com.zyc.copier_v0.modules.user.auth.repository;

import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserSessionEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserSessionRepository extends JpaRepository<PlatformUserSessionEntity, Long> {

    Optional<PlatformUserSessionEntity> findBySessionTokenHashAndExpiresAtAfter(String sessionTokenHash, Instant now);
}
