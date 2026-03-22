package com.zyc.copier_v0.modules.copy.engine.repository;

import com.zyc.copier_v0.modules.copy.engine.entity.ExecutionCommandEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionCommandRepository extends JpaRepository<ExecutionCommandEntity, Long> {

    boolean existsByMasterEventIdAndFollowerAccountId(String masterEventId, Long followerAccountId);

    List<ExecutionCommandEntity> findByMasterEventIdOrderByIdAsc(String masterEventId);

    List<ExecutionCommandEntity> findByFollowerAccountIdOrderByIdDesc(Long followerAccountId);

    List<ExecutionCommandEntity> findByMasterAccountIdOrderByIdDesc(Long masterAccountId);

    List<ExecutionCommandEntity> findByMasterAccountIdAndMasterOrderIdOrderByIdAsc(Long masterAccountId, Long masterOrderId);

    List<ExecutionCommandEntity> findByMasterAccountIdAndMasterPositionIdOrderByIdAsc(Long masterAccountId, Long masterPositionId);

    Optional<ExecutionCommandEntity> findFirstByMasterEventIdAndFollowerAccountId(String masterEventId, Long followerAccountId);
}
