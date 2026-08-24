package com.datn.foodshare.service;

import com.datn.foodshare.domain.response.AdminDashboardResponse;
import com.datn.foodshare.repository.DailyMetricProjection;
import com.datn.foodshare.repository.StatisticRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticServiceTest {

    @Mock
    private StatisticRepository statisticRepository;

    private StatisticService statisticService;

    @BeforeEach
    void setUp() {
        statisticService = new StatisticService(statisticRepository);
    }

    @Test
    void getDashboardStatistics_success() {
        Instant toDate = Instant.now();
        Instant fromDate = toDate.minus(30, ChronoUnit.DAYS);

        when(statisticRepository.countTotalUsers()).thenReturn(100L);
        when(statisticRepository.countNewUsers(any(Instant.class), any(Instant.class))).thenReturn(20L);
        when(statisticRepository.countActiveUsers()).thenReturn(90L);
        when(statisticRepository.countTotalFoodPosts()).thenReturn(50L);
        when(statisticRepository.countFoodPostsByStatus(com.datn.foodshare.util.constant.PostStatus.AVAILABLE)).thenReturn(30L);
        when(statisticRepository.countTotalOrders()).thenReturn(200L);
        when(statisticRepository.countOrdersByStatus(OrderStatus.COMPLETED)).thenReturn(150L);
        when(statisticRepository.countOrdersByStatus(OrderStatus.CANCELLED)).thenReturn(10L);
        when(statisticRepository.sumRevenue(any(Instant.class), any(Instant.class), eq(TransactionStatus.SUCCESS))).thenReturn(new BigDecimal("1000000"));
        when(statisticRepository.sumPayout(any(Instant.class), any(Instant.class), eq(TransactionStatus.SUCCESS))).thenReturn(new BigDecimal("800000"));

        DailyMetricProjection mockProjection = new DailyMetricProjection() {
            @Override
            public String getDateStr() {
                return "2023-10-01";
            }
            @Override
            public Number getVal() {
                return 5;
            }
        };

        when(statisticRepository.getUserRegistrationsChart(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(mockProjection));
        when(statisticRepository.getOrderCountsChart(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(mockProjection));
        when(statisticRepository.getDailyRevenueChart(any(Instant.class), any(Instant.class), eq(TransactionStatus.SUCCESS)))
                .thenReturn(List.of(mockProjection));

        AdminDashboardResponse response = statisticService.getDashboardStatistics(fromDate, toDate);

        assertNotNull(response);
        assertNotNull(response.getOverview());
        assertEquals(100L, response.getOverview().getTotalUsers());
        assertEquals(20L, response.getOverview().getNewUsers());
        assertEquals(90L, response.getOverview().getActiveUsers());
        assertEquals(50L, response.getOverview().getTotalFoodPosts());
        assertEquals(30L, response.getOverview().getAvailableFoodPosts());
        assertEquals(200L, response.getOverview().getTotalOrders());
        assertEquals(150L, response.getOverview().getCompletedOrders());
        assertEquals(10L, response.getOverview().getCancelledOrders());
        assertEquals(new BigDecimal("1000000"), response.getOverview().getTotalRevenue());
        assertEquals(new BigDecimal("800000"), response.getOverview().getTotalPayout());

        assertNotNull(response.getChartData());
        assertEquals(1, response.getChartData().getUserRegistrations().size());
        assertEquals("2023-10-01", response.getChartData().getUserRegistrations().get(0).getDate());
        assertEquals(5, response.getChartData().getUserRegistrations().get(0).getValue());
    }

    @Test
    void getDashboardStatistics_invalidDateRange() {
        Instant fromDate = Instant.now();
        Instant toDate = fromDate.minus(1, ChronoUnit.DAYS);

        assertThrows(IllegalArgumentException.class, () -> {
            statisticService.getDashboardStatistics(fromDate, toDate);
        });
    }

    @Test
    void getDashboardStatistics_nullDates() {
        when(statisticRepository.countTotalUsers()).thenReturn(10L);
        when(statisticRepository.countNewUsers(any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(statisticRepository.countActiveUsers()).thenReturn(10L);
        when(statisticRepository.countTotalFoodPosts()).thenReturn(0L);
        when(statisticRepository.countFoodPostsByStatus(com.datn.foodshare.util.constant.PostStatus.AVAILABLE)).thenReturn(0L);
        when(statisticRepository.countTotalOrders()).thenReturn(0L);
        when(statisticRepository.countOrdersByStatus(any())).thenReturn(0L);
        when(statisticRepository.sumRevenue(any(Instant.class), any(Instant.class), eq(TransactionStatus.SUCCESS))).thenReturn(BigDecimal.ZERO);
        when(statisticRepository.sumPayout(any(Instant.class), any(Instant.class), eq(TransactionStatus.SUCCESS))).thenReturn(BigDecimal.ZERO);

        AdminDashboardResponse response = statisticService.getDashboardStatistics(null, null);

        assertNotNull(response);
        assertNotNull(response.getOverview());
        assertEquals(10L, response.getOverview().getTotalUsers());
    }
}
