package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.UpdateReportStatusRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.service.ReportService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.constant.ReportStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    @ApiMessage("Lấy danh sách báo cáo (Admin) thành công")
    public ResponseEntity<Page<ReportResponse>> getAllReports(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportService.adminGetAllReports(status, pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Lấy chi tiết báo cáo (Admin) thành công")
    public ResponseEntity<ReportResponse> getReportDetail(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.adminGetReportDetail(id));
    }

    @PatchMapping("/{id}/status")
    @ApiMessage("Cập nhật trạng thái báo cáo thành công")
    public ResponseEntity<ReportResponse> updateReportStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest request) {
        return ResponseEntity.ok(reportService.adminUpdateReportStatus(id, request));
    }
}
