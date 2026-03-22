package com.zyc.copier_v0.modules.account.config.repository;

import com.zyc.copier_v0.modules.account.config.entity.MasterShareConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterShareConfigRepository extends JpaRepository<MasterShareConfigEntity, Long> {

    Optional<MasterShareConfigEntity> findByMasterAccount_Id(Long masterAccountId);

    List<MasterShareConfigEntity> findByMasterAccount_UserIdAndShareEnabledTrueOrderByMasterAccount_IdAsc(Long userId);

    List<MasterShareConfigEntity> findByMasterAccount_UserIdAndShareCodeFingerprintAndShareEnabledTrue(
            Long userId,
            String shareCodeFingerprint
    );
}
