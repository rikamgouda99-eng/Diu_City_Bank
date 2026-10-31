package com.dcb.repository;

import com.dcb.entity.CardEntity;
import com.dcb.enums.CardStatus;
import com.dcb.enums.CardType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepo extends JpaRepository<CardEntity, Long> {
    List<CardEntity> findByBankIdOrderByAppliedAtDesc(String bankId);

    Optional<CardEntity> findByCardIdAndBankId(Long cardId, String bankId);

    boolean existsByBankIdAndCardTypeAndStatusIn(String bankId, CardType cardType, Collection<CardStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardEntity c where c.cardId = :cardId")
    Optional<CardEntity> lockById(@Param("cardId") Long cardId);

    @Query("select c from CardEntity c where (:status is null or c.status = :status) " +
            "and (:cardType is null or c.cardType = :cardType) order by c.appliedAt desc")
    Page<CardEntity> search(@Param("status") CardStatus status, @Param("cardType") CardType cardType, Pageable pageable);

    long countByStatus(CardStatus status);
}
