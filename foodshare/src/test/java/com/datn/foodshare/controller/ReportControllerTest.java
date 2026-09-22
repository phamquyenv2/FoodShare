package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateReportRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.service.ReportService;
import com.datn.foodshare.util.error.PermissionException;
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
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(reportService);
    }

    @Test
    void createReport_returns201() {
        CreateReportRequest request = new CreateReportRequest();
        ReportResponse response = ReportResponse.builder().id(1L).build();
        when(reportService.createReport(request)).thenReturn(response);

        ResponseEntity<ReportResponse> entity = controller.createReport(request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void getMyReports_returns200() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReportResponse> page = new PageImpl<>(List.of(ReportResponse.builder().id(1L).build()));
        when(reportService.getMyReports(pageable)).thenReturn(page);

        ResponseEntity<Page<ReportResponse>> entity = controller.getMyReports(pageable);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(page, entity.getBody());
    }

    @Test
    void getMyReportDetail_returns200() throws PermissionException {
        ReportResponse response = ReportResponse.builder().id(10L).build();
        when(reportService.getMyReportDetail(10L)).thenReturn(response);

        ResponseEntity<ReportResponse> entity = controller.getMyReportDetail(10L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }
}
