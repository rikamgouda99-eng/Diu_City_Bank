package com.dcb.repository;

import com.dcb.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferRepo extends JpaRepository<TransferEntity, Long> {
    Optional<TransferEntity> findByReference(String reference);
}
