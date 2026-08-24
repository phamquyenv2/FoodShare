package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> findByUserIdAndIsActiveTrue(Long userId);
    Optional<UserDevice> findByFcmToken(String fcmToken);
}
