package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByRefreshTokenAndRevokedFalse(String refreshToken);
}
