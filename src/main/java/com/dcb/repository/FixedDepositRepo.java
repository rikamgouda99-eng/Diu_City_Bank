package com.dcb.repository;

import com.dcb.entity.FixedDepositEntity;
import com.dcb.enums.FdStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixedDepositRepo extends JpaRepository<FixedDepositEntity, String> {
    List<FixedDepositEntity> findByBankIdOrderByCreatedAtDesc(String bankId);

    Optional<FixedDepositEntity> findByFdIdAndBankId(String fdId, String bankId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FixedDepositEntity f where f.fdId = :fdId")
    Optional<FixedDepositEntity> lockById(@Param("fdId") String fdId);

    @Query("select f.fdId from FixedDepositEntity f where f.status = com.dcb.enums.FdStatus.ACTIVE and f.maturityDate <= :today")
    List<String> findMaturedIds(@Param("today") LocalDate today);

    @Query("select f from FixedDepositEntity f where (:status is null or f.status = :status) order by f.createdAt desc")
    Page<FixedDepositEntity> search(@Param("status") FdStatus status, Pageable pageable);

    long countByStatus(FdStatus status);
}
