package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {
    List<PayoutAccount> findByBusinessProfileIdAndIsActiveTrue(Long businessProfileId);
    Optional<PayoutAccount> findByIdAndBusinessProfileId(Long id, Long businessProfileId);
    Optional<PayoutAccount> findByBusinessProfileIdAndIsDefaultTrue(Long businessProfileId);
}
