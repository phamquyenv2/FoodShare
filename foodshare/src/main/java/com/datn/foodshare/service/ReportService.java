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
import com.datn.foodshare.util.constant.ReportType;
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
import java.util.List;
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
            validateOrderDispute(request.getReferenceId(), currentUser, request.getReportType(), Instant.now());
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
                    .title(request.getReportType() == ReportType.REFUND ? "Yêu cầu hoàn tiền mới" : "Báo cáo mới")
                    .content("Người dùng " + currentUser.getFullName() + (request.getReportType() == ReportType.REFUND ? " vừa gửi yêu cầu hoàn tiền đơn hàng #" + request.getReferenceId() : " vừa gửi một báo cáo/khiếu nại. Vui lòng kiểm tra."))
                    .type(NotificationType.REPORT)
                    .referenceType(NotificationReferenceType.REPORT)
                    .referenceId(savedReport.getId())
                    .build());
        }

        return ReportResponse.from(savedReport);
    }

    private void validateOrderDispute(Long orderId, User reporter, ReportType reportType, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));
        if (!order.getReceiver().getId().equals(reporter.getId())) {
            throw new BusinessException("Chỉ người nhận của đơn hàng mới có thể gửi khiếu nại");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REJECTED) {
            throw new BusinessException("Không thể gửi khiếu nại/hoàn tiền cho đơn đã hủy hoặc bị từ chối");
        }
        if (reportType == ReportType.REFUND) {
            boolean isAlreadyRefunded = order.getPayments() != null && order.getPayments().stream()
                    .anyMatch(p -> p.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.REFUNDED);
            if (isAlreadyRefunded) {
                throw new BusinessException("Đơn hàng này đã được hoàn tiền thành công");
            }

            boolean hasActiveRefundReport = reportRepository.existsActiveRefundReport(
                    ReportReferenceType.ORDER,
                    orderId,
                    List.of(ReportStatus.PENDING, ReportStatus.REVIEWING, ReportStatus.RESOLVED)
            );
            if (hasActiveRefundReport) {
                throw new BusinessException("Đơn hàng này đã có yêu cầu hoàn tiền đang được xử lý hoặc đã hoàn tất");
            }

            if (order.getOrderStatus() == OrderStatus.DELIVERED) {
                if (order.getDeliveredAt() != null && now.isAfter(order.getDeliveredAt().plus(INSPECTION_WINDOW))) {
                    throw new BusinessException("Cửa sổ khiếu nại 24 giờ của đơn hàng đã kết thúc");
                }
            } else if (order.getOrderStatus() == OrderStatus.COMPLETED) {
                Instant refTime = order.getCompletedAt() != null ? order.getCompletedAt() : order.getDeliveredAt();
                if (refTime != null && now.isAfter(refTime.plus(INSPECTION_WINDOW))) {
                    throw new BusinessException("Cửa sổ khiếu nại 24 giờ của đơn hàng đã kết thúc");
                }
            } else if (order.getOrderStatus() != OrderStatus.ACCEPTED && order.getOrderStatus() != OrderStatus.READY_FOR_PICKUP) {
                throw new BusinessException("Chỉ có thể yêu cầu hoàn tiền cho đơn hàng đang thực hiện hoặc đã hoàn tất");
            }
        } else {
            if (order.getOrderStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
                throw new BusinessException("Chỉ có thể khiếu nại đơn đang ở trạng thái đã giao (DELIVERED)");
            }
            if (now.isAfter(order.getDeliveredAt().plus(INSPECTION_WINDOW))) {
                throw new BusinessException("Cửa sổ khiếu nại 24 giờ của đơn hàng đã kết thúc");
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(Pageable pageable) {
        User currentUser = getAuthenticatedUser();
        return reportRepository.findByReporterId(currentUser.getId(), pageable)
                .map(this::mapReportResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse getMyReportDetail(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Report không tồn tại: " + id));

        if (!report.getReporter().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền xem report này");
        }

        return mapReportResponse(report);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> adminGetAllReports(ReportStatus status, Pageable pageable) {
        if (status != null) {
            return reportRepository.findByReportStatus(status, pageable)
                    .map(this::mapReportResponse);
        }
        return reportRepository.findAllWithReporter(pageable)
                .map(this::mapReportResponse);
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

        return mapReportResponse(report);
    }

    private ReportResponse mapReportResponse(Report report) {
        Long targetBusinessProfileId = null;
        String targetBusinessName = null;
        String targetName = null;
        String orderCode = null;
        java.math.BigDecimal amount = null;
        String paymentMethod = null;

        if (report.getReferenceType() == ReportReferenceType.ORDER && report.getReferenceId() != null) {
            Order order = orderRepository.findById(report.getReferenceId()).orElse(null);
            if (order != null) {
                orderCode = order.getOrderCode();
                amount = order.getTotalAmount();
                targetName = "Đơn hàng #" + order.getId() + (order.getOrderCode() != null ? " (" + order.getOrderCode() + ")" : "");
                if (order.getBusinessProfile() != null) {
                    targetBusinessProfileId = order.getBusinessProfile().getId();
                    targetBusinessName = order.getBusinessProfile().getName();
                }
                java.util.List<com.datn.foodshare.domain.entity.Payment> payments = paymentRepository.findByOrderId(order.getId());
                com.datn.foodshare.domain.entity.Payment successPayment = payments.stream()
                        .filter(p -> p.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.SUCCESS)
                        .findFirst()
                        .orElse(null);
                if (successPayment != null) {
                    paymentMethod = successPayment.getMethod() != null ? successPayment.getMethod().name() : null;
                    if (successPayment.getAmount() != null) {
                        amount = successPayment.getAmount();
                    }
                }
            }
        }

        return ReportResponse.from(report, targetBusinessProfileId, targetBusinessName, targetName, orderCode, amount, paymentMethod);
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

        return mapReportResponse(savedReport);
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
