package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Payout;
import com.datn.foodshare.util.constant.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
    Optional<Payout> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"businessProfile", "payoutAccount"})
    Page<Payout> findByBusinessProfileIdOrderByCreatedAtDesc(
            Long businessProfileId,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(p.requestedAmount), 0)
            FROM Payout p
            WHERE p.businessProfile.id = :businessProfileId
              AND p.payoutStatus IN :statuses
            """)
    BigDecimal sumRequestedAmount(
            @Param("businessProfileId") Long businessProfileId,
            @Param("statuses") Collection<PayoutStatus> statuses);

    long countByBusinessProfileIdAndPayoutStatus(Long businessProfileId, PayoutStatus payoutStatus);

    @EntityGraph(attributePaths = {"businessProfile", "payoutAccount"})
    Page<Payout> findByPayoutStatusOrderByCreatedAtDesc(PayoutStatus payoutStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"businessProfile", "payoutAccount"})
    @Query("SELECT p FROM Payout p")
    Page<Payout> findAllWithDetails(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"businessProfile", "businessProfile.user", "payoutAccount"})
    @Query("SELECT p FROM Payout p WHERE p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") Long id);
}
