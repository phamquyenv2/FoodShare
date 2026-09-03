package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.response.AdminDashboardResponse;
import com.datn.foodshare.service.StatisticService;
import com.datn.foodshare.util.annotation.ApiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;

@RestController
@RequestMapping("/api/admin/statistics")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class StatisticController {

    private final StatisticService statisticService;

    @GetMapping("/dashboard")
    @ApiMessage("Lấy dữ liệu thống kê dashboard thành công")
    public ResponseEntity<AdminDashboardResponse> getDashboardStatistics(
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date toDate) {
        
        Instant from = fromDate != null ? fromDate.toInstant() : null;
        Instant to = toDate != null ? toDate.toInstant() : null;
        
        return ResponseEntity.ok(statisticService.getDashboardStatistics(from, to));
    }
}
