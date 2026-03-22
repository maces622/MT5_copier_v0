package com.zyc.copier_v0.modules.account.config.repository;

import com.zyc.copier_v0.modules.account.config.domain.CopyRelationStatus;
import com.zyc.copier_v0.modules.account.config.entity.CopyRelationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyRelationRepository extends JpaRepository<CopyRelationEntity, Long> {

    Optional<CopyRelationEntity> findByMasterAccount_IdAndFollowerAccount_Id(Long masterAccountId, Long followerAccountId);

    List<CopyRelationEntity> findByMasterAccount_IdOrderByPriorityAscIdAsc(Long masterAccountId);

    List<CopyRelationEntity> findByMasterAccount_IdAndStatusOrderByPriorityAscIdAsc(Long masterAccountId, CopyRelationStatus status);

    List<CopyRelationEntity> findByFollowerAccount_IdOrderByPriorityAscIdAsc(Long followerAccountId);

    List<CopyRelationEntity> findByFollowerAccount_IdAndStatusIn(Long followerAccountId, Collection<CopyRelationStatus> statuses);

    List<CopyRelationEntity> findAllByStatusIn(Collection<CopyRelationStatus> statuses);

    List<CopyRelationEntity> findAllByOrderByUpdatedAtDescIdDesc();

    List<CopyRelationEntity> findByMasterAccount_IdInOrFollowerAccount_IdInOrderByUpdatedAtDescIdDesc(
            Collection<Long> masterAccountIds,
            Collection<Long> followerAccountIds
    );
}
