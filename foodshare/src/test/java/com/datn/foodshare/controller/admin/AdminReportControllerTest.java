package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.UpdateReportStatusRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.service.ReportService;
import com.datn.foodshare.util.constant.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    @Mock
    private ReportService reportService;

    private AdminReportController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReportController(reportService);
    }

    @Test
    void getAllReports_returns200() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ReportResponse> page = new PageImpl<>(List.of(ReportResponse.builder().id(1L).build()));
        when(reportService.adminGetAllReports(ReportStatus.PENDING, pageable)).thenReturn(page);

        ResponseEntity<Page<ReportResponse>> result = controller.getAllReports(ReportStatus.PENDING, pageable);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void getReportDetail_returns200() {
        ReportResponse response = ReportResponse.builder().id(10L).build();
        when(reportService.adminGetReportDetail(10L)).thenReturn(response);

        ResponseEntity<ReportResponse> result = controller.getReportDetail(10L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void updateReportStatus_returns200() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest();
        ReportResponse response = ReportResponse.builder().id(10L).build();
        when(reportService.adminUpdateReportStatus(10L, request)).thenReturn(response);

        ResponseEntity<ReportResponse> result = controller.updateReportStatus(10L, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
