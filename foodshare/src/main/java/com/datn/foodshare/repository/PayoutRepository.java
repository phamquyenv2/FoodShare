package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
    Optional<Payout> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
    
    // For calculating total summary
    List<Payout> findByOrderBusinessProfileId(Long businessProfileId);
    
    // For transaction history with pagination
    org.springframework.data.domain.Page<Payout> findByOrderBusinessProfileIdOrderByCreatedAtDesc(Long businessProfileId, org.springframework.data.domain.Pageable pageable);
}
