package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.AdminDashboardResponse;
import com.datn.foodshare.repository.StatisticRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticService {

    private final StatisticRepository statisticRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStatistics(Instant fromDate, Instant toDate) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        if (toDate == null) {
            toDate = now.toInstant();
        }
        if (fromDate == null) {
            fromDate = now.minusDays(30).toInstant();
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc");
        }

        AdminDashboardResponse.Overview overview = getOverviewStats(fromDate, toDate);
        AdminDashboardResponse.ChartData chartData = getChartData(fromDate, toDate);

        return AdminDashboardResponse.builder()
                .overview(overview)
                .chartData(chartData)
                .build();
    }

    private AdminDashboardResponse.Overview getOverviewStats(Instant fromDate, Instant toDate) {
        long totalUsers = statisticRepository.countTotalUsers();
        long newUsers = statisticRepository.countNewUsers(fromDate, toDate);
        long activeUsers = statisticRepository.countActiveUsers();

        long totalFoodPosts = statisticRepository.countTotalFoodPosts();
        long availableFoodPosts = statisticRepository.countFoodPostsByStatus(PostStatus.AVAILABLE);

        long totalOrders = statisticRepository.countTotalOrders();
        long completedOrders = statisticRepository.countOrdersByStatus(OrderStatus.COMPLETED);
        long cancelledOrders = statisticRepository.countOrdersByStatus(OrderStatus.CANCELLED);

        BigDecimal totalRevenue = statisticRepository.sumRevenue(fromDate, toDate, TransactionStatus.SUCCESS);
        BigDecimal totalPayout = statisticRepository.sumPayout(fromDate, toDate, TransactionStatus.SUCCESS);

        return AdminDashboardResponse.Overview.builder()
                .totalUsers(totalUsers)
                .newUsers(newUsers)
                .activeUsers(activeUsers)
                .totalFoodPosts(totalFoodPosts)
                .availableFoodPosts(availableFoodPosts)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .totalPayout(totalPayout)
                .build();
    }

    private AdminDashboardResponse.ChartData getChartData(Instant fromDate, Instant toDate) {
        List<AdminDashboardResponse.DailyMetric> userRegs = statisticRepository.getUserRegistrationsChart(fromDate, toDate).stream()
                .map(p -> AdminDashboardResponse.DailyMetric.builder().date(p.getDateStr()).value(p.getVal()).build())
                .toList();
        
        List<AdminDashboardResponse.DailyMetric> orderCounts = statisticRepository.getOrderCountsChart(fromDate, toDate).stream()
                .map(p -> AdminDashboardResponse.DailyMetric.builder().date(p.getDateStr()).value(p.getVal()).build())
                .toList();
        
        List<AdminDashboardResponse.DailyMetric> dailyRevenue = statisticRepository.getDailyRevenueChart(fromDate, toDate, TransactionStatus.SUCCESS).stream()
                .map(p -> AdminDashboardResponse.DailyMetric.builder().date(p.getDateStr()).value(p.getVal()).build())
                .toList();

        return AdminDashboardResponse.ChartData.builder()
                .userRegistrations(userRegs)
                .orderCounts(orderCounts)
                .dailyRevenue(dailyRevenue)
                .build();
    }
}
