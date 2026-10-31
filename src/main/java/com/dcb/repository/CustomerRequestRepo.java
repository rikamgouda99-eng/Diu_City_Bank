package com.dcb.repository;

import com.dcb.entity.CustomerRequestEntity;
import com.dcb.enums.RequestStatus;
import com.dcb.enums.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRequestRepo extends JpaRepository<CustomerRequestEntity, Long> {
    List<CustomerRequestEntity> findByBankIdOrderByCreatedAtDesc(String bankId);

    boolean existsByBankIdAndRequestTypeAndStatus(String bankId, RequestType type, RequestStatus status);

    @Query("select r from CustomerRequestEntity r where (:status is null or r.status = :status) order by r.createdAt desc")
    Page<CustomerRequestEntity> search(@Param("status") RequestStatus status, Pageable pageable);

    long countByStatus(RequestStatus status);
}
