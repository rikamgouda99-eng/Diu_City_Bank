package com.dcb.repository;

import com.dcb.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepo extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByMailId(String mailId);
    boolean existsByMailId(String mailId);
    boolean existsByBankId(String bankId);
    @Query(value = "SELECT * FROM user_login_table WHERE is_active = true AND is_approved = false;",nativeQuery = true)
    List<UserEntity> getPendingUsers();

    @Query("select u from UserEntity u where u.role = 'CUSTOMER' and (:search is null " +
            "or lower(u.bankId) like lower(concat('%', :search, '%')) " +
            "or lower(u.mailId) like lower(concat('%', :search, '%')) " +
            "or lower(u.firstName) like lower(concat('%', :search, '%')) " +
            "or lower(u.lastName) like lower(concat('%', :search, '%')))")
    Page<UserEntity> searchCustomers(@Param("search") String search, Pageable pageable);

    long countByRole(String role);
}
