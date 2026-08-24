package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Report;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateReportRequest;
import com.datn.foodshare.domain.request.UpdateReportStatusRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.ReportRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.ReportStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        User currentUser = getAuthenticatedUser();

        Report report = Report.builder()
                .reporter(currentUser)
                .title(request.getTitle())
                .content(request.getContent())
                .reportType(request.getReportType())
                .evidenceUrl(trimToNull(request.getEvidenceUrl()))
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .reportStatus(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);

        log.info("User {} đã tạo report mới: {}", currentUser.getId(), savedReport.getId());

        return ReportResponse.from(savedReport);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(Pageable pageable) {
        User currentUser = getAuthenticatedUser();
        return reportRepository.findByReporterId(currentUser.getId(), pageable)
                .map(ReportResponse::from);
    }

    @Transactional(readOnly = true)
    public ReportResponse getMyReportDetail(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Report không tồn tại: " + id));

        if (!report.getReporter().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền xem report này");
        }

        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> adminGetAllReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByReportStatus(status, pageable)
                    .map(ReportResponse::from);
        }
        return reportRepository.findAllWithReporter(pageable)
                .map(ReportResponse::from);
    }

    @Transactional
    public ReportResponse adminGetReportDetail(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Report không tồn tại: " + id));

        if (report.getReportStatus() == ReportStatus.PENDING) {
            report.setReportStatus(ReportStatus.REVIEWING);
            report = reportRepository.save(report);
            log.info("Admin đã xem report {}, chuyển trạng thái sang REVIEWING", report.getId());
        }

        return ReportResponse.from(report);
    }

    @Transactional
    public ReportResponse adminUpdateReportStatus(Long id, UpdateReportStatusRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Report không tồn tại: " + id));

        report.setReportStatus(request.getReportStatus());
        report.setResponse(trimToNull(request.getResponse()));

        if (request.getReportStatus() == ReportStatus.RESOLVED || request.getReportStatus() == ReportStatus.REJECTED) {
            if (report.getResolvedAt() == null) {
                report.setResolvedAt(Instant.now());
            }
        } else {
            report.setResolvedAt(null);
        }

        Report savedReport = reportRepository.save(report);
        
        log.info("Admin đã cập nhật trạng thái report {} thành {}", savedReport.getId(), savedReport.getReportStatus());

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedReport.getReporter())
                .title("Cập nhật khiếu nại / báo cáo")
                .content("Báo cáo của bạn (Mã: " + savedReport.getId() + ") đã được chuyển sang trạng thái: " + savedReport.getReportStatus())
                .type(NotificationType.SYSTEM)
                .referenceType(NotificationReferenceType.REPORT)
                .referenceId(savedReport.getId())
                .build());

        return ReportResponse.from(savedReport);
    }

    private User getAuthenticatedUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    private String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
