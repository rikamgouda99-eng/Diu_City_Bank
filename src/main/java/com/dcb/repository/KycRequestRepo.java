package com.dcb.repository;

import com.dcb.entity.KycRequestEntity;
import com.dcb.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycRequestRepo extends JpaRepository<KycRequestEntity, Long> {
    List<KycRequestEntity> findByBankIdOrderBySubmittedAtDesc(String bankId);

    Optional<KycRequestEntity> findFirstByBankIdOrderBySubmittedAtDesc(String bankId);

    Optional<KycRequestEntity> findByIdAndBankId(Long id, String bankId);

    @Query("select k from KycRequestEntity k where (:status is null or k.status = :status) order by k.submittedAt desc")
    Page<KycRequestEntity> search(@Param("status") KycStatus status, Pageable pageable);

    long countByStatus(KycStatus status);
}
