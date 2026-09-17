package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Report;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateReportRequest;
import com.datn.foodshare.domain.request.UpdateReportStatusRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.ReportRepository;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.ReportStatus;
import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.OrderStatus;
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
import java.util.Set;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final Duration INSPECTION_WINDOW = Duration.ofHours(24);

    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.datn.foodshare.repository.PaymentRepository paymentRepository;
    private final com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    private final SupplierEarningService supplierEarningService;

    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        User currentUser = getAuthenticatedUser();
        if (request.getReferenceType() == ReportReferenceType.ORDER) {
            validateOrderDispute(request.getReferenceId(), currentUser, Instant.now());
        }

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
        
        // Notify all Admins
        java.util.List<User> admins = userRepository.findByRole(com.datn.foodshare.util.constant.Role.ADMIN);
        for (User admin : admins) {
            eventPublisher.publishEvent(NotificationEvent.builder()
                    .source(this)
                    .user(admin)
                    .title("Báo cáo mới")
                    .content("Người dùng " + currentUser.getFullName() + " vừa gửi một báo cáo/khiếu nại. Vui lòng kiểm tra.")
                    .type(NotificationType.REPORT)
                    .referenceType(NotificationReferenceType.REPORT)
                    .referenceId(savedReport.getId())
                    .build());
        }

        return ReportResponse.from(savedReport);
    }

    private void validateOrderDispute(Long orderId, User reporter, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));
        if (!order.getReceiver().getId().equals(reporter.getId())) {
            throw new BusinessException("Chỉ người nhận của đơn hàng mới có thể gửi khiếu nại");
        }
        if (order.getOrderStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw new BusinessException("Chỉ có thể khiếu nại đơn đang ở trạng thái đã giao (DELIVERED)");
        }
        if (now.isAfter(order.getDeliveredAt().plus(INSPECTION_WINDOW))) {
            throw new BusinessException("Cửa sổ khiếu nại 24 giờ của đơn hàng đã kết thúc");
        }
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

        boolean refunded = false;
        if (request.getReportStatus() == ReportStatus.RESOLVED && report.getReferenceType() == ReportReferenceType.ORDER) {
            java.util.List<com.datn.foodshare.domain.entity.Payment> payments = paymentRepository.findByOrderId(report.getReferenceId());
            for (com.datn.foodshare.domain.entity.Payment payment : payments) {
                if (payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.SUCCESS) {
                    try {
                        com.datn.foodshare.service.payment.strategy.PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
                        com.datn.foodshare.domain.entity.Payment refundedPayment = strategy.processRefund(payment);
                        paymentRepository.save(refundedPayment);
                        supplierEarningService.reverseForRefundedPayment(refundedPayment);
                        refunded = true;
                    } catch (Exception e) {
                        log.error("Failed to refund payment ID {} on report resolution: {}", payment.getId(), e.getMessage(), e);
                    }
                }
            }
        }
        
        log.info("Admin đã cập nhật trạng thái report {} thành {}", savedReport.getId(), savedReport.getReportStatus());

        String notificationContent = refunded
                ? "Báo cáo của bạn (Mã: " + savedReport.getId() + ") đã được giải quyết thành công. Khoản thanh toán cho đơn hàng đã được hoàn lại."
                : "Báo cáo của bạn (Mã: " + savedReport.getId() + ") đã được chuyển sang trạng thái: " + savedReport.getReportStatus();

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedReport.getReporter())
                .title("Cập nhật khiếu nại / báo cáo")
                .content(notificationContent)
                .type(NotificationType.SYSTEM)
                .referenceType(NotificationReferenceType.REPORT)
                .referenceId(savedReport.getId())
                .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH))
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
