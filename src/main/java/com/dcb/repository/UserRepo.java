package com.dcb.repository;

import com.dcb.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface UserRepo extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByMailId(String mailId);
    boolean existsByMailId(String mailId);
    boolean existsByBankId(String bankId);
}
