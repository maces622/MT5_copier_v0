package com.zyc.copier_v0.modules.user.auth.repository;

import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, Long> {

    Optional<PlatformUserEntity> findByPlatformId(String platformId);

    Optional<PlatformUserEntity> findByShareId(String shareId);

    Optional<PlatformUserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByPlatformId(String platformId);

    boolean existsByShareId(String shareId);

    boolean existsByUsernameIgnoreCase(String username);
}
