package com.dcb.repository;

import com.dcb.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepo extends JpaRepository<IdempotencyRecordEntity, String> {
}
