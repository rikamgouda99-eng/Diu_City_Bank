package com.dcb.repository;

import com.dcb.entity.BillPaymentEntity;
import com.dcb.enums.BillType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillPaymentRepo extends JpaRepository<BillPaymentEntity, Long> {
    Page<BillPaymentEntity> findByBankIdOrderByPaymentDateDesc(String bankId, Pageable pageable);

    Optional<BillPaymentEntity> findByReference(String reference);

    @Query("select b from BillPaymentEntity b where (:billType is null or b.billType = :billType) " +
            "and (:bankId is null or b.bankId = :bankId) order by b.paymentDate desc")
    Page<BillPaymentEntity> search(@Param("billType") BillType billType, @Param("bankId") String bankId, Pageable pageable);
}
