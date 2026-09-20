package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<Review> findByOrderId(Long orderId);

    @Query(value = "SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order WHERE r.businessProfile.id = :businessProfileId",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.businessProfile.id = :businessProfileId")
    Page<Review> findByBusinessProfileId(@Param("businessProfileId") Long businessProfileId, Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order WHERE r.reviewer.id = :reviewerId")
    Page<Review> findByReviewerId(@Param("reviewerId") Long reviewerId, Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.reviewer JOIN FETCH r.order")
    Page<Review> findAllWithDetails(Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.businessProfile.id = :businessProfileId")
    Double getAverageRatingByBusinessProfileId(@Param("businessProfileId") Long businessProfileId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.businessProfile.id = :businessProfileId")
    long countByBusinessProfileId(@Param("businessProfileId") Long businessProfileId);

    @Query("SELECT r.businessProfile.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.businessProfile.id IN :businessProfileIds GROUP BY r.businessProfile.id")
    List<Object[]> getReviewSummariesByBusinessProfileIds(@Param("businessProfileIds") Collection<Long> businessProfileIds);
}
