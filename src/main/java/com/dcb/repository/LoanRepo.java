package com.dcb.repository;

import com.dcb.entity.LoanEntity;
import com.dcb.enums.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepo extends JpaRepository<LoanEntity, String> {
    List<LoanEntity> findByBankIdOrderByAppliedAtDesc(String bankId);

    Optional<LoanEntity> findByLoanIdAndBankId(String loanId, String bankId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanEntity l where l.loanId = :loanId")
    Optional<LoanEntity> lockById(@Param("loanId") String loanId);

    @Query("select l from LoanEntity l where (:status is null or l.status = :status) order by l.appliedAt desc")
    Page<LoanEntity> search(@Param("status") LoanStatus status, Pageable pageable);

    long countByStatus(LoanStatus status);

    long countByBankIdAndStatus(String bankId, LoanStatus status);

    @Query("select coalesce(sum(l.outstandingPrincipal), 0) from LoanEntity l where l.status = com.dcb.enums.LoanStatus.ACTIVE")
    BigDecimal totalOutstanding();
}
