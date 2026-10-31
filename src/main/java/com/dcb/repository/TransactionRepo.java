package com.dcb.repository;

import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepo extends JpaRepository<TransactionStatusEntity, String>,
        JpaSpecificationExecutor<TransactionStatusEntity> {

    Page<TransactionStatusEntity> findByAccountNoOrderByCreatedAtDesc(String accountNo, Pageable pageable);

    List<TransactionStatusEntity> findByAccountNoAndCreatedAtBetweenOrderByCreatedAtAsc(
            String accountNo, LocalDateTime from, LocalDateTime to);

    Optional<TransactionStatusEntity> findByTransactionIdAndAccountNo(String transactionId, String accountNo);

    Optional<TransactionStatusEntity> findFirstByAccountNoOrderByCreatedAtDesc(String accountNo);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByStatus(TransactionStatus status);
}
