package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.SupplierEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface SupplierEarningRepository extends JpaRepository<SupplierEarning, Long> {

    Optional<SupplierEarning> findByOrderId(Long orderId);

    Optional<SupplierEarning> findByPaymentId(Long paymentId);

    @Query("""
            SELECT COALESCE(SUM(e.netAmount), 0)
            FROM SupplierEarning e
            WHERE e.businessProfile.id = :businessProfileId
              AND e.reversedAt IS NULL
            """)
    BigDecimal sumActiveNetAmount(@Param("businessProfileId") Long businessProfileId);
}
