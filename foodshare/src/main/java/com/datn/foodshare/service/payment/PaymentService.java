package com.datn.foodshare.service.payment;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.domain.response.UserPaymentSummaryResponse;
import com.datn.foodshare.util.constant.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.service.SupplierEarningService;
import com.datn.foodshare.service.payment.strategy.PaymentStrategy;
import com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {


    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final SupplierEarningService supplierEarningService;
    private final ApplicationEventPublisher eventPublisher;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUserId)) {
            throw new PermissionException("Bạn không có quyền thanh toán đơn hàng này");
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED
                || order.getOrderStatus() == OrderStatus.REJECTED) {
            throw new BusinessException("Không thể tạo thanh toán cho đơn hàng đã hủy hoặc bị từ chối");
        }

        if (orderRepository.hasActiveReport(
                orderId,
                com.datn.foodshare.util.constant.ReportReferenceType.ORDER,
                List.of(com.datn.foodshare.util.constant.ReportStatus.PENDING, com.datn.foodshare.util.constant.ReportStatus.REVIEWING))) {
            throw new BusinessException("Đơn hàng đang có khiếu nại cần đối soát, không thể thực hiện thanh toán");
        }

        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Đơn hàng miễn phí không yêu cầu thanh toán");
        }

        List<Payment> existingPayments = paymentRepository.findByOrderId(orderId);
        for (Payment existingPayment : existingPayments) {
            if (existingPayment.getPaymentStatus() == TransactionStatus.SUCCESS) {
                throw new BusinessException("Đơn hàng đã được thanh toán");
            }
            if (existingPayment.getPaymentStatus() == TransactionStatus.PENDING || 
                existingPayment.getPaymentStatus() == TransactionStatus.PROCESSING) {
                existingPayment.setPaymentStatus(TransactionStatus.CANCELLED);
                paymentRepository.save(existingPayment);
            }
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .method(request.getMethod())
                .paymentStatus(TransactionStatus.PENDING)
                .build();

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(request.getMethod());
        payment = strategy.createPayment(order, payment);

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse handlePaymentSuccess(Long paymentId) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Payment payment = findPaymentForUpdate(paymentId);

        if (!payment.getOrder().getReceiver().getId().equals(currentUserId)) {
            throw new PermissionException("Bạn không có quyền thao tác trên giao dịch này");
        }

        if (payment.getPaymentStatus() == TransactionStatus.SUCCESS) {
            return PaymentResponse.from(payment);
        }

        if (payment.getOrder().getOrderStatus() == OrderStatus.CANCELLED
                || payment.getOrder().getOrderStatus() == OrderStatus.REJECTED) {
            throw new BusinessException("Không thể xác nhận thanh toán cho đơn hàng đã kết thúc");
        }

        if (orderRepository.hasActiveReport(
                payment.getOrder().getId(),
                com.datn.foodshare.util.constant.ReportReferenceType.ORDER,
                List.of(com.datn.foodshare.util.constant.ReportStatus.PENDING, com.datn.foodshare.util.constant.ReportStatus.REVIEWING))) {
            throw new BusinessException("Đơn hàng đang có khiếu nại cần đối soát, không thể hoàn tất thanh toán");
        }

        requireTransitionFromActive(payment, TransactionStatus.SUCCESS);

        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);
        publishPaymentNotification(
                savedPayment,
                "Thanh toán thành công",
                "Thanh toán cho đơn " + savedPayment.getOrder().getOrderCode() + " đã thành công.",
                true);
        return PaymentResponse.from(savedPayment);
    }

    @Transactional
    public PaymentResponse handlePaymentFailure(Long paymentId) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Payment payment = findPaymentForUpdate(paymentId);

        if (!payment.getOrder().getReceiver().getId().equals(currentUserId)) {
            throw new PermissionException("Bạn không có quyền thao tác trên giao dịch này");
        }

        if (payment.getPaymentStatus() == TransactionStatus.FAILED) {
            return PaymentResponse.from(payment);
        }

        requireTransitionFromActive(payment, TransactionStatus.FAILED);

        payment.setPaymentStatus(TransactionStatus.FAILED);
        Payment savedPayment = paymentRepository.save(payment);
        publishPaymentNotification(
                savedPayment,
                "Thanh toán thất bại",
                "Thanh toán cho đơn " + savedPayment.getOrder().getOrderCode() + " không thành công. Vui lòng thử lại.",
                false);
        return PaymentResponse.from(savedPayment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId) throws PermissionException {
        if (!SecurityUtil.hasRole("ADMIN")) {
            throw new PermissionException("Chỉ Admin mới có quyền hoàn tiền");
        }
        Payment payment = findPaymentForUpdate(paymentId);

        if (payment.getPaymentStatus() == TransactionStatus.REFUNDED) {
            return PaymentResponse.from(payment);
        }

        if (payment.getPaymentStatus() != TransactionStatus.SUCCESS) {
            throw new BusinessException("Chỉ có thể hoàn tiền các giao dịch đã thành công");
        }

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
        payment = strategy.processRefund(payment);
        Payment refundedPayment = paymentRepository.save(payment);
        supplierEarningService.reverseForRefundedPayment(refundedPayment);
        publishPaymentNotification(
                refundedPayment,
                "Thanh toán đã được hoàn lại",
                "Khoản thanh toán cho đơn " + refundedPayment.getOrder().getOrderCode() + " đã được hoàn lại.",
                true);
        return PaymentResponse.from(refundedPayment);
    }

    @Transactional
    public void processMomoCallback(Map<String, Object> payload) {
        if (payload == null
                || !"0".equals(String.valueOf(payload.get("resultCode")))) {
            return;
        }

        String orderId = String.valueOf(payload.get("orderId"));

        Payment payment = paymentRepository
                .findByExternalTransactionId(orderId)
                .orElse(null);

        if (payment == null
                || payment.getPaymentStatus() == TransactionStatus.SUCCESS) {
            return;
        }

        long callbackAmount = Long.parseLong(
                String.valueOf(payload.get("amount"))
        );

        if (payment.getAmount().longValue() != callbackAmount) {
            return;
        }

        markSuccessful(payment);
    }

    @Transactional
    public PaymentResponse processMomoRedirect(
            String orderId,
            String resultCode,
            Long amount
    ) throws PermissionException {
        if (!"0".equals(resultCode)) {
            throw new BusinessException("MoMo thanh toán chưa thành công");
        }

        Long userId = SecurityUtil
                .getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Payment payment = paymentRepository
                .findByExternalTransactionId(orderId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch MoMo"));

        if (!payment.getOrder().getReceiver().getId().equals(userId)) {
            throw new PermissionException("Không có quyền");
        }

        if (amount == null
                || payment.getAmount().longValue() != amount) {
            throw new BusinessException("Số tiền không khớp");
        }

        if (payment.getPaymentStatus() != TransactionStatus.SUCCESS) {
            markSuccessful(payment);
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse processZaloPayRedirect(
            String appTransId,
            String status,
            Long amount
    ) throws PermissionException {
        if (!"1".equals(status)) {
            throw new BusinessException("ZaloPay thanh toán chưa thành công");
        }

        Long userId = SecurityUtil
                .getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));

        Payment payment = paymentRepository
                .findByExternalTransactionId(appTransId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch ZaloPay"));

        if (!payment.getOrder().getReceiver().getId().equals(userId)) {
            throw new PermissionException("Không có quyền");
        }

        if (amount == null
                || payment.getAmount().longValue() != amount) {
            throw new BusinessException("Số tiền không khớp");
        }

        if (payment.getPaymentStatus() != TransactionStatus.SUCCESS) {
            markSuccessful(payment);
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void processZaloPayCallback(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }

        try {
            String resultCode = payload.get("resultCode") == null
                    ? null
                    : String.valueOf(payload.get("resultCode"));

            if (resultCode == null) {
                Object returnCode = payload.get("returncode");

                if (returnCode == null) {
                    returnCode = payload.get("return_code");
                }

                resultCode = returnCode == null
                        ? null
                        : String.valueOf(returnCode);
            }

            String type = payload.get("type") == null
                    ? null
                    : String.valueOf(payload.get("type"));

            Map<String, Object> data = payload;

            if (payload.get("data") instanceof String rawData) {
                data = objectMapper.readValue(rawData, Map.class);
            }

            Object transactionId = data.get("app_trans_id");

            if (transactionId == null) {
                transactionId = data.get("apptransid");
            }

            if (transactionId == null) {
                transactionId = data.get("zptranstoken");
            }

            if (transactionId == null) {
                return;
            }

            String appTransId = String.valueOf(transactionId);
            String status = resultCode != null ? resultCode : type;

            if (!"1".equals(status)) {
                return;
            }

            Long amount = data.get("amount") == null
                    ? null
                    : Long.valueOf(String.valueOf(data.get("amount")));

            Payment payment = paymentRepository
                    .findByExternalTransactionId(appTransId)
                    .orElse(null);

            if (payment == null
                    || payment.getPaymentStatus() == TransactionStatus.SUCCESS
                    || (amount != null
                    && payment.getAmount().longValue() != amount)) {
                return;
            }

            if (amount == null
                    && payload.get("zptranstoken") != null) {
                return;
            }

            markSuccessful(payment);
        } catch (Exception exception) {
            log.warn(
                    "ZaloPay callback không hợp lệ: {}",
                    exception.getMessage()
            );
        }
    }

    private void markSuccessful(Payment payment) {
        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        Payment saved = paymentRepository.save(payment);
        publishPaymentNotification(saved, "Thanh toán thành công", "Thanh toán cho đơn " + saved.getOrder().getOrderCode() + " đã thành công.", true);
    }

    private Payment findPaymentForUpdate(Long paymentId) {
        Long orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại"));
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại: " + orderId));
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại"));
    }

    private void requireTransitionFromActive(Payment payment, TransactionStatus targetStatus) {
        TransactionStatus currentStatus = payment.getPaymentStatus();
        if (currentStatus != TransactionStatus.PENDING && currentStatus != TransactionStatus.PROCESSING) {
            throw new BusinessException(
                    "Không thể chuyển giao dịch từ " + currentStatus + " sang " + targetStatus);
        }
    }

    private void publishPaymentNotification(Payment payment, String title, String content, boolean email) {
        Set<NotificationChannel> channels = email
                ? Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL)
                : Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);
        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(payment.getOrder().getReceiver())
                .title(title)
                .content(content)
                .type(NotificationType.PAYMENT)
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .channels(channels)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMyPaymentHistory(String keyword, TransactionStatus status, PaymentMethod method, Pageable pageable) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return paymentRepository.findByReceiverId(currentUserId, normalizedKeyword, status, method, pageable)
                .map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public UserPaymentSummaryResponse getMyPaymentSummary() throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));
        return UserPaymentSummaryResponse.builder()
                .totalSpent(paymentRepository.sumSpentByReceiverId(currentUserId))
                .totalRefunded(paymentRepository.sumRefundedByReceiverId(currentUserId))
                .totalTransactions(paymentRepository.countByReceiverId(currentUserId))
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getMyPaymentDetail(Long paymentId) throws PermissionException {
        Long currentUserId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Giao dịch không tồn tại: " + paymentId));
        if (payment.getOrder() == null || payment.getOrder().getReceiver() == null
                || !payment.getOrder().getReceiver().getId().equals(currentUserId)) {
            throw new PermissionException("Bạn không có quyền xem giao dịch này");
        }
        return PaymentResponse.from(payment);
    }
}
