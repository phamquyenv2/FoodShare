package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.BusinessProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {

    Optional<BusinessProfile> findByUserId(Long userId);

    Optional<BusinessProfile> findByTaxCode(String taxCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BusinessProfile bp WHERE bp.id = :id")
    Optional<BusinessProfile> findByIdForUpdate(@Param("id") Long id);
}
