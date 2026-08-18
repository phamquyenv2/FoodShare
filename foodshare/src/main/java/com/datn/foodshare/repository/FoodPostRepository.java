package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.util.constant.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodPostRepository extends JpaRepository<FoodPost, Long>, JpaSpecificationExecutor<FoodPost> {

    @Query("""
            SELECT DISTINCT fp FROM FoodPost fp
            LEFT JOIN FETCH fp.images
            WHERE fp.id IN :ids
            """)
    List<FoodPost> findAllWithImagesByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT fp FROM FoodPost fp
            JOIN FETCH fp.category c
            WHERE fp.businessProfile.id = :businessProfileId
            """)
    Page<FoodPost> findByBusinessProfileId(
            @Param("businessProfileId") Long businessProfileId,
            Pageable pageable);

    @Query("""
            SELECT fp FROM FoodPost fp
            JOIN FETCH fp.businessProfile bp
            JOIN FETCH fp.category c
            WHERE (:status IS NULL OR fp.postStatus = :status)
            """)
    Page<FoodPost> findAllForAdmin(
            @Param("status") PostStatus status,
            Pageable pageable);

    @Query("""
            SELECT fp FROM FoodPost fp
            LEFT JOIN FETCH fp.images
            JOIN FETCH fp.businessProfile
            JOIN FETCH fp.category
            WHERE fp.id = :id
            """)
    Optional<FoodPost> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE FoodPost fp
            SET fp.postStatus = 'EXPIRED'
            WHERE fp.postStatus IN ('AVAILABLE', 'OUT_OF_STOCK')
              AND fp.expiresAt <= :now
            """)
    int markExpired(@Param("now") Instant now);

    List<FoodPost> findAllByPostStatusAndExpiresAtAfterAndAvailableQuantityGreaterThan(
            PostStatus postStatus, Instant now, int minQuantity);
}

