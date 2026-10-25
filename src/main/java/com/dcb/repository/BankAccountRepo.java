package com.dcb.repository;

import com.dcb.entity.BankAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankAccountRepo extends JpaRepository<BankAccountEntity, String> {
    Optional<BankAccountEntity> findByCustomerId(String customerId);
}
