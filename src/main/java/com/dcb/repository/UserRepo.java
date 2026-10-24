package com.dcb.repository;

import com.dcb.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByGmailId(String gmailId);
    boolean existsByGmailId(String gmailId);
    boolean existsByBankId(String bankId);
}
