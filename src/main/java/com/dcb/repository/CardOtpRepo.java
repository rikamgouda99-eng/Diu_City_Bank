package com.dcb.repository;

import com.dcb.entity.CardOtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardOtpRepo extends JpaRepository<CardOtpEntity, Long> {
}
