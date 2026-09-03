package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order WHERE r.businessProfile.id = :businessProfileId")
    Page<Review> findByBusinessProfileId(@Param("businessProfileId") Long businessProfileId, Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order WHERE r.reviewer.id = :reviewerId")
    Page<Review> findByReviewerId(@Param("reviewerId") Long reviewerId, Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order")
    Page<Review> findAllWithDetails(Pageable pageable);
}
