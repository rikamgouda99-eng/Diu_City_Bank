package com.dcb.repository;

import com.dcb.entity.EmiScheduleEntity;
import com.dcb.enums.EmiStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmiScheduleRepo extends JpaRepository<EmiScheduleEntity, Long> {
    List<EmiScheduleEntity> findByLoanIdOrderByEmiNumberAsc(String loanId);

    List<EmiScheduleEntity> findByLoanIdAndStatusOrderByEmiNumberAsc(String loanId, EmiStatus status);

    Optional<EmiScheduleEntity> findFirstByLoanIdAndStatusOrderByEmiNumberAsc(String loanId, EmiStatus status);

    Page<EmiScheduleEntity> findByStatusOrderByPaymentDateDesc(EmiStatus status, Pageable pageable);
}
