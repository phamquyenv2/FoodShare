package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {

    Optional<BusinessProfile> findByUserId(Long userId);

    Optional<BusinessProfile> findByTaxCode(String taxCode);
}
