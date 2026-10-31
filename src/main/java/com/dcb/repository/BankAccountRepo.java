package com.dcb.repository;

import com.dcb.entity.BankAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface BankAccountRepo extends JpaRepository<BankAccountEntity, String> {
    Optional<BankAccountEntity> findByCustomerId(String customerId);

    // SELECT ... FOR UPDATE: every balance change goes through one of these two
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccountEntity a where a.accountNo = :accountNo")
    Optional<BankAccountEntity> lockByAccountNo(@Param("accountNo") String accountNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccountEntity a where a.customerId = :customerId")
    Optional<BankAccountEntity> lockByCustomerId(@Param("customerId") String customerId);

    @Query("select a from BankAccountEntity a where (:search is null or a.accountNo like concat('%', :search, '%') or a.customerId like concat('%', :search, '%'))")
    Page<BankAccountEntity> search(@Param("search") String search, Pageable pageable);

    @Query("select coalesce(sum(a.accountBalance), 0) from BankAccountEntity a")
    BigDecimal totalBalance();
}
