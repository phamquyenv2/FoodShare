package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface StatisticRepository extends Repository<User, Long> {

    @Query("SELECT COUNT(u) FROM User u")
    long countTotalUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt BETWEEN :fromTs AND :toTs")
    long countNewUsers(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("SELECT COUNT(u) FROM User u WHERE u.active = true")
    long countActiveUsers();

    @Query("SELECT COUNT(fp) FROM FoodPost fp")
    long countTotalFoodPosts();

    @Query("SELECT COUNT(fp) FROM FoodPost fp WHERE fp.postStatus = :status")
    long countFoodPostsByStatus(@Param("status") PostStatus status);

    @Query("SELECT COUNT(o) FROM Order o")
    long countTotalOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status")
    long countOrdersByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(p.platformFee), 0) FROM Payout p WHERE p.payoutStatus = :status AND p.createdAt BETWEEN :fromTs AND :toTs")
    BigDecimal sumRevenue(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs, @Param("status") TransactionStatus status);

    @Query("SELECT COALESCE(SUM(p.netAmount), 0) FROM Payout p WHERE p.payoutStatus = :status AND p.createdAt BETWEEN :fromTs AND :toTs")
    BigDecimal sumPayout(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs, @Param("status") TransactionStatus status);

    @Query("SELECT CAST(FUNCTION('DATE', u.createdAt) AS string) as dateStr, COUNT(u.id) as val FROM User u WHERE u.createdAt BETWEEN :fromTs AND :toTs GROUP BY CAST(FUNCTION('DATE', u.createdAt) AS string) ORDER BY dateStr")
    List<DailyMetricProjection> getUserRegistrationsChart(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("SELECT CAST(FUNCTION('DATE', o.createdAt) AS string) as dateStr, COUNT(o.id) as val FROM Order o WHERE o.createdAt BETWEEN :fromTs AND :toTs GROUP BY CAST(FUNCTION('DATE', o.createdAt) AS string) ORDER BY dateStr")
    List<DailyMetricProjection> getOrderCountsChart(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

    @Query("SELECT CAST(FUNCTION('DATE', p.createdAt) AS string) as dateStr, SUM(p.platformFee) as val FROM Payout p WHERE p.payoutStatus = :status AND p.createdAt BETWEEN :fromTs AND :toTs GROUP BY CAST(FUNCTION('DATE', p.createdAt) AS string) ORDER BY dateStr")
    List<DailyMetricProjection> getDailyRevenueChart(@Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs, @Param("status") TransactionStatus status);
}
