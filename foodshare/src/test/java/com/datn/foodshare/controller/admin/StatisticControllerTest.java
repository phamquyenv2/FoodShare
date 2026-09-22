package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.response.AdminDashboardResponse;
import com.datn.foodshare.service.StatisticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticControllerTest {

    @Mock
    private StatisticService statisticService;

    private StatisticController controller;

    @BeforeEach
    void setUp() {
        controller = new StatisticController(statisticService);
    }

    @Test
    void getDashboardStatistics_withDates_returns200() {
        Date from = new Date();
        Date to = new Date();
        AdminDashboardResponse response = AdminDashboardResponse.builder().build();
        when(statisticService.getDashboardStatistics(any(), any())).thenReturn(response);

        ResponseEntity<AdminDashboardResponse> result = controller.getDashboardStatistics(from, to);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getDashboardStatistics_nullDates_returns200() {
        AdminDashboardResponse response = AdminDashboardResponse.builder().build();
        when(statisticService.getDashboardStatistics(null, null)).thenReturn(response);

        ResponseEntity<AdminDashboardResponse> result = controller.getDashboardStatistics(null, null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
