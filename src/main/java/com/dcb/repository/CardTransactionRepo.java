package com.dcb.repository;

import com.dcb.entity.CardTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CardTransactionRepo extends JpaRepository<CardTransactionEntity, Long> {
    Page<CardTransactionEntity> findByCardIdOrderByCreatedAtDesc(Long cardId, Pageable pageable);

    Optional<CardTransactionEntity> findByReference(String reference);

    /** Successful spends (not repayments) on a card since the given time. */
    @Query("select coalesce(sum(t.amount), 0) from CardTransactionEntity t where t.cardId = :cardId " +
            "and t.status = com.dcb.enums.TransactionStatus.SUCCESS " +
            "and t.type <> com.dcb.enums.CardTransactionType.REPAYMENT and t.createdAt >= :since")
    BigDecimal sumSpendSince(@Param("cardId") Long cardId, @Param("since") LocalDateTime since);
}
