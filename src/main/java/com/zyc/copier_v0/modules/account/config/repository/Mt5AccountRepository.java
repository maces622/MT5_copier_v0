package com.zyc.copier_v0.modules.account.config.repository;

import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Mt5AccountRepository extends JpaRepository<Mt5AccountEntity, Long> {

    Optional<Mt5AccountEntity> findByServerNameAndMt5Login(String serverName, Long mt5Login);

    List<Mt5AccountEntity> findByUserIdOrderByIdAsc(Long userId);

    List<Mt5AccountEntity> findAllByOrderByIdAsc();
}
