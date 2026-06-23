package com.platform.auth.repository;

import com.platform.auth.entity.User;
import com.platform.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMsisdn(String msisdn);

    Optional<User> findByEmail(String email);

    boolean existsByMsisdn(String msisdn);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
           "u.msisdn IS NOT NULL AND u.msisdn != '' AND " +
           "(:msisdn IS NULL OR :msisdn = '' OR u.msisdn LIKE %:msisdn%) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:startTime IS NULL OR u.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR u.createdAt <= :endTime)")
    Page<User> searchUsers(
            @Param("msisdn") String msisdn,
            @Param("status") UserStatus status,
            @Param("startTime") java.time.Instant startTime,
            @Param("endTime") java.time.Instant endTime,
            Pageable pageable);

    @Query("SELECT u.id FROM User u WHERE " +
           "u.msisdn IS NOT NULL AND u.msisdn != '' AND " +
           "(:msisdn IS NULL OR :msisdn = '' OR u.msisdn LIKE %:msisdn%) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:startTime IS NULL OR u.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR u.createdAt <= :endTime)")
    List<Long> searchUserIds(
            @Param("msisdn") String msisdn,
            @Param("status") UserStatus status,
            @Param("startTime") java.time.Instant startTime,
            @Param("endTime") java.time.Instant endTime);
}

