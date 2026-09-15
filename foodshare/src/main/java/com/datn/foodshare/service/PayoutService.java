package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payout;
import com.datn.foodshare.domain.entity.PayoutAccount;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreatePayoutAccountRequest;
import com.datn.foodshare.domain.request.CreatePayoutRequest;
import com.datn.foodshare.domain.response.PayoutAccountResponse;
import com.datn.foodshare.domain.response.PayoutResponse;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.PayoutAccountRepository;
import com.datn.foodshare.repository.PayoutRepository;
import com.datn.foodshare.repository.SupplierEarningRepository;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.PayoutStatus;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationChannel;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final OrderRepository orderRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final SupplierEarningRepository supplierEarningRepository;
    private final SupplierEarningService supplierEarningService;
    private final UserRepository userRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ApplicationEventPublisher eventPublisher;

    private BigDecimal getPlatformFeePercentage() {
        return systemConfigRepository.findByConfigKey("PLATFORM_FEE_PERCENTAGE")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(new BigDecimal("0.05"));
    }

    private BigDecimal getMinPayoutAmount() {
        return systemConfigRepository.findByConfigKey("MIN_PAYOUT_AMOUNT")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(new BigDecimal("50000"));
    }

    private BigDecimal getMaxPayoutAmount() {
        return systemConfigRepository.findByConfigKey("MAX_PAYOUT_AMOUNT")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(new BigDecimal("20000000"));
    }

    private User getAuthenticatedUser() throws PermissionException {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new PermissionException("Chưa đăng nhập"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Tài khoản không tồn tại"));
    }

    @Transactional
    public PayoutAccountResponse createPayoutAccount(CreatePayoutAccountRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ Supplier mới được quản lý tài khoản nhận tiền");
        }
        BusinessProfile bp = currentUser.getBusinessProfile();
        if (bp == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp");
        }

        if (request.isDefault()) {
            payoutAccountRepository.findByBusinessProfileIdAndIsDefaultTrue(bp.getId())
                    .ifPresent(existing -> {
                        existing.setDefault(false);
                        payoutAccountRepository.save(existing);
                    });
        }

        PayoutAccount account = PayoutAccount.builder()
                .businessProfile(bp)
                .bankCode(request.getBankCode())
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .isDefault(request.isDefault())
                .isActive(true)
                .build();

        return PayoutAccountResponse.from(payoutAccountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<PayoutAccountResponse> getMyPayoutAccounts() throws PermissionException {
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ Supplier mới được xem tài khoản nhận tiền");
        }
        BusinessProfile bp = currentUser.getBusinessProfile();
        if (bp == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp");
        }

        return payoutAccountRepository.findByBusinessProfileIdAndIsActiveTrue(bp.getId())
                .stream()
                .map(PayoutAccountResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public PayoutResponse createPayout(Long orderId, CreatePayoutRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        BusinessProfile bp = requireSupplierProfile(currentUser);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        if (!order.getBusinessProfile().getId().equals(bp.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác trên đơn hàng này");
        }

        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("Đơn hàng chưa hoàn thành, không đủ điều kiện payout");
        }

        boolean hasSuccessfulPayment = order.getPayments().stream()
                .anyMatch(payment -> payment.getPaymentStatus() == TransactionStatus.SUCCESS
                        && payment.getMethod() != PaymentMethod.CASH);

        if (!hasSuccessfulPayment) {
            throw new BusinessException("Đơn hàng chưa thanh toán online thành công, không đủ điều kiện payout");
        }

        businessProfileRepository.findByIdForUpdate(bp.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ doanh nghiệp"));
        if (payoutRepository.existsByOrderId(orderId)) {
            throw new BusinessException("Đơn hàng này đã được tạo yêu cầu rút tiền (payout)");
        }
        BigDecimal amount = supplierEarningService.recordForCompletedOrder(order)
                .orElseThrow(() -> new BusinessException("Không tìm thấy doanh thu online hợp lệ cho đơn hàng"))
                .getNetAmount();
        return createRequestLocked(bp, request.getPayoutAccountId(), amount, order);
    }

    @Transactional
    public PayoutResponse createPayoutRequest(CreatePayoutRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        BusinessProfile profile = requireSupplierProfile(currentUser);
        if (request.getAmount() == null) {
            throw new BusinessException("Số tiền rút không được để trống");
        }
        businessProfileRepository.findByIdForUpdate(profile.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ doanh nghiệp"));
        return createRequestLocked(profile, request.getPayoutAccountId(), request.getAmount(), null);
    }

    @Transactional(readOnly = true)
    public com.datn.foodshare.domain.response.WalletSummaryResponse getWalletSummary(org.springframework.data.domain.Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        BusinessProfile bp = requireSupplierProfile(currentUser);
        BigDecimal earnedBalance = zeroIfNull(supplierEarningRepository.sumActiveNetAmount(bp.getId()));
        BigDecimal totalPending = zeroIfNull(payoutRepository.sumRequestedAmount(
                bp.getId(), List.of(PayoutStatus.PENDING)));
        BigDecimal totalCompleted = zeroIfNull(payoutRepository.sumRequestedAmount(
                bp.getId(), List.of(PayoutStatus.SUCCESS)));
        BigDecimal rawAvailable = earnedBalance.subtract(totalPending).subtract(totalCompleted);
        BigDecimal available = rawAvailable.max(BigDecimal.ZERO);
        BigDecimal currentFee = getPlatformFeePercentage();
        Page<Payout> pagedPayouts = payoutRepository
                .findByBusinessProfileIdOrderByCreatedAtDesc(bp.getId(), pageable);
        Page<PayoutResponse> transactions = pagedPayouts.map(PayoutResponse::from);

        return com.datn.foodshare.domain.response.WalletSummaryResponse.builder()
                .totalEarned(available)
                .earnedBalance(earnedBalance)
                .totalPending(totalPending)
                .totalCompleted(totalCompleted)
                .rawAvailableBalance(rawAvailable)
                .availableBalance(available)
                .pendingCount(Math.toIntExact(payoutRepository
                        .countByBusinessProfileIdAndPayoutStatus(bp.getId(), PayoutStatus.PENDING)))
                .platformFeePercentage(currentFee.multiply(new BigDecimal("100")))
                .minPayoutAmount(getMinPayoutAmount())
                .maxPayoutAmount(getMaxPayoutAmount())
                .transactions(transactions)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> getMyPayoutRequests(org.springframework.data.domain.Pageable pageable)
            throws PermissionException {
        BusinessProfile profile = requireSupplierProfile(getAuthenticatedUser());
        return payoutRepository.findByBusinessProfileIdOrderByCreatedAtDesc(profile.getId(), pageable)
                .map(PayoutResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPayoutRequestsForAdmin(
            PayoutStatus status,
            org.springframework.data.domain.Pageable pageable) throws PermissionException {
        requireAdmin(getAuthenticatedUser());
        Page<Payout> payouts = status == null
                ? payoutRepository.findAllWithDetails(pageable)
                : payoutRepository.findByPayoutStatusOrderByCreatedAtDesc(status, pageable);
        return payouts.map(PayoutResponse::from);
    }

    @Transactional
    public PayoutResponse approvePayout(Long payoutId) throws PermissionException {
        User admin = getAuthenticatedUser();
        requireAdmin(admin);
        Payout payout = findPayoutForUpdate(payoutId);
        if (payout.getPayoutStatus() == PayoutStatus.SUCCESS) {
            return PayoutResponse.from(payout);
        }
        requirePendingPayout(payout, PayoutStatus.SUCCESS);

        Instant now = Instant.now();
        payout.setPayoutStatus(PayoutStatus.SUCCESS);
        payout.setReviewedBy(admin);
        payout.setReviewedAt(now);
        payout.setCompletedAt(now);
        payout.setExternalTransactionId("PAYOUT-" + UUID.randomUUID());
        Payout saved = payoutRepository.save(payout);
        publishPayoutDecision(saved, "Yêu cầu rút tiền đã được duyệt",
                "Yêu cầu " + saved.getPayoutCode() + " đã được duyệt thành công.");
        return PayoutResponse.from(saved);
    }

    @Transactional
    public PayoutResponse rejectPayout(Long payoutId, String reason) throws PermissionException {
        User admin = getAuthenticatedUser();
        requireAdmin(admin);
        Payout payout = findPayoutForUpdate(payoutId);
        if (payout.getPayoutStatus() == PayoutStatus.FAILED) {
            return PayoutResponse.from(payout);
        }
        requirePendingPayout(payout, PayoutStatus.FAILED);

        String rejectionReason = reason == null ? null : reason.trim();
        if (rejectionReason == null || rejectionReason.isEmpty()) {
            throw new BusinessException("Lý do từ chối không được để trống");
        }
        Instant now = Instant.now();
        payout.setPayoutStatus(PayoutStatus.FAILED);
        payout.setReviewedBy(admin);
        payout.setReviewedAt(now);
        payout.setFailedAt(now);
        payout.setFailureReason(rejectionReason);
        payout.setRejectionReason(rejectionReason);
        Payout saved = payoutRepository.save(payout);
        publishPayoutDecision(saved, "Yêu cầu rút tiền bị từ chối",
                "Yêu cầu " + saved.getPayoutCode() + " bị từ chối: " + rejectionReason);
        return PayoutResponse.from(saved);
    }

    private PayoutResponse createRequestLocked(
            BusinessProfile profile,
            Long payoutAccountId,
            BigDecimal requestedAmount,
            Order legacyOrder) {
        BigDecimal amount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        validateRequestedAmount(amount);

        PayoutAccount account = payoutAccountRepository
                .findByIdAndBusinessProfileId(payoutAccountId, profile.getId())
                .orElseThrow(() -> new BusinessException(
                        "Tài khoản nhận tiền không tồn tại hoặc không thuộc quyền sở hữu"));
        if (!account.isActive()) {
            throw new BusinessException("Tài khoản nhận tiền đã bị vô hiệu hóa");
        }

        BigDecimal earned = zeroIfNull(supplierEarningRepository.sumActiveNetAmount(profile.getId()));
        BigDecimal reservedOrPaid = zeroIfNull(payoutRepository.sumRequestedAmount(
                profile.getId(), List.of(PayoutStatus.PENDING, PayoutStatus.SUCCESS)));
        BigDecimal available = earned.subtract(reservedOrPaid);
        if (amount.compareTo(available) > 0) {
            throw new BusinessException("Số dư khả dụng không đủ để thực hiện yêu cầu rút tiền");
        }

        Payout payout = Payout.builder()
                .order(legacyOrder)
                .businessProfile(profile)
                .payoutAccount(account)
                .payoutCode("PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .grossAmount(amount)
                .platformFee(BigDecimal.ZERO.setScale(2))
                .netAmount(amount)
                .requestedAmount(amount)
                .payoutStatus(PayoutStatus.PENDING)
                .bankCode(account.getBankCode())
                .bankName(account.getBankName())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .build();
        Payout saved = payoutRepository.save(payout);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(profile.getUser())
                .title("Yêu cầu rút tiền đang chờ duyệt")
                .content("Yêu cầu rút " + amount.setScale(0, RoundingMode.HALF_UP)
                        + "đ đã được ghi nhận.")
                .type(NotificationType.PAYMENT)
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(saved.getId())
                .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL))
                .build());
        return PayoutResponse.from(saved);
    }

    private void validateRequestedAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tiền rút phải lớn hơn 0");
        }
        BigDecimal minimum = getMinPayoutAmount();
        BigDecimal maximum = getMaxPayoutAmount();
        if (amount.compareTo(minimum) < 0) {
            throw new BusinessException("Số tiền rút nhỏ hơn mức tối thiểu cho phép");
        }
        if (amount.compareTo(maximum) > 0) {
            throw new BusinessException("Số tiền rút vượt quá hạn mức tối đa cho phép");
        }
    }

    private BusinessProfile requireSupplierProfile(User user) throws PermissionException {
        if (user.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ Supplier mới được thao tác với ví");
        }
        if (user.getBusinessProfile() == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp");
        }
        return user.getBusinessProfile();
    }

    private void requireAdmin(User user) throws PermissionException {
        if (user.getRole() != Role.ADMIN) {
            throw new PermissionException("Chỉ Admin mới được duyệt yêu cầu rút tiền");
        }
    }

    private Payout findPayoutForUpdate(Long payoutId) {
        return payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new BusinessException("Yêu cầu rút tiền không tồn tại: " + payoutId));
    }

    private void requirePendingPayout(Payout payout, PayoutStatus targetStatus) {
        if (payout.getPayoutStatus() != PayoutStatus.PENDING) {
            throw new BusinessException("Không thể chuyển payout từ " + payout.getPayoutStatus()
                    + " sang " + targetStatus);
        }
    }

    private void publishPayoutDecision(Payout payout, String title, String content) {
        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(payout.getBusinessProfile().getUser())
                .title(title)
                .content(content)
                .type(NotificationType.PAYMENT)
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(payout.getId())
                .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL))
                .build());
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
