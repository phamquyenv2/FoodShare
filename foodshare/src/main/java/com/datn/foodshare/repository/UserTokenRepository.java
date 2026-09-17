package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserToken> findByRefreshTokenAndRevokedFalse(String refreshToken);
}
