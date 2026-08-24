package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateReportRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.service.ReportService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ApiMessage("Gửi báo cáo thành công")
    public ResponseEntity<ReportResponse> createReport(@Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(request));
    }

    @GetMapping
    @ApiMessage("Lấy danh sách báo cáo của tôi thành công")
    public ResponseEntity<Page<ReportResponse>> getMyReports(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportService.getMyReports(pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Lấy chi tiết báo cáo thành công")
    public ResponseEntity<ReportResponse> getMyReportDetail(@PathVariable Long id) throws PermissionException {
        return ResponseEntity.ok(reportService.getMyReportDetail(id));
    }
}
