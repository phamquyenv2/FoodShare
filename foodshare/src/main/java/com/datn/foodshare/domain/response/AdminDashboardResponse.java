package com.datn.foodshare.domain.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AdminDashboardResponse {

    private Overview overview;
    private ChartData chartData;

    @Data
    @Builder
    public static class Overview {
        private long totalUsers;
        private long newUsers;
        private long activeUsers;

        private long totalFoodPosts;
        private long availableFoodPosts;

        private long totalOrders;
        private long completedOrders;
        private long cancelledOrders;

        private BigDecimal totalRevenue;
        private BigDecimal totalPayout;
    }

    @Data
    @Builder
    public static class ChartData {
        private List<DailyMetric> userRegistrations;
        private List<DailyMetric> orderCounts;
        private List<DailyMetric> dailyRevenue;
    }

    @Data
    @Builder
    public static class DailyMetric {
        private String date; // Format: YYYY-MM-DD
        private Number value;
    }
}
