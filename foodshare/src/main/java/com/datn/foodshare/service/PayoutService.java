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
import com.datn.foodshare.repository.PayoutAccountRepository;
import com.datn.foodshare.repository.PayoutRepository;
import com.datn.foodshare.repository.SystemConfigRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final OrderRepository orderRepository;
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
        if (currentUser.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ Supplier mới được yêu cầu rút tiền");
        }
        BusinessProfile bp = currentUser.getBusinessProfile();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        if (!order.getBusinessProfile().getId().equals(bp.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác trên đơn hàng này");
        }

        boolean hasSuccessfulPayment = order.getPayments().stream()
                .anyMatch(p -> p.getPaymentStatus() == TransactionStatus.SUCCESS);

        if (!hasSuccessfulPayment) {
            throw new BusinessException("Đơn hàng chưa thanh toán thành công, không đủ điều kiện payout");
        }

        if (payoutRepository.existsByOrderId(orderId)) {
            throw new BusinessException("Đơn hàng này đã được tạo yêu cầu rút tiền (payout)");
        }

        PayoutAccount account = payoutAccountRepository.findByIdAndBusinessProfileId(request.getPayoutAccountId(), bp.getId())
                .orElseThrow(() -> new BusinessException("Tài khoản nhận tiền không tồn tại hoặc không thuộc quyền sở hữu"));

        if (!account.isActive()) {
            throw new BusinessException("Tài khoản nhận tiền đã bị vô hiệu hóa");
        }

        BigDecimal grossAmount = order.getTotalAmount();
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Đơn hàng không có doanh thu để rút");
        }

        BigDecimal netAmountRaw = grossAmount.subtract(grossAmount.multiply(getPlatformFeePercentage()));
        if (netAmountRaw.compareTo(getMinPayoutAmount()) < 0) {
            throw new BusinessException("Số tiền nhận được (" + netAmountRaw.setScale(0, RoundingMode.HALF_UP) + "đ) nhỏ hơn mức tối thiểu cho phép rút (" + getMinPayoutAmount().setScale(0, RoundingMode.HALF_UP) + "đ)");
        }
        if (netAmountRaw.compareTo(getMaxPayoutAmount()) > 0) {
            throw new BusinessException("Số tiền nhận được (" + netAmountRaw.setScale(0, RoundingMode.HALF_UP) + "đ) vượt quá hạn mức tối đa cho phép rút trong 1 lần (" + getMaxPayoutAmount().setScale(0, RoundingMode.HALF_UP) + "đ)");
        }

        BigDecimal platformFee = grossAmount.multiply(getPlatformFeePercentage()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(platformFee);

        Payout payout = Payout.builder()
                .order(order)
                .payoutAccount(account)
                .payoutCode("PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .grossAmount(grossAmount)
                .platformFee(platformFee)
                .netAmount(netAmount)
                .payoutStatus(TransactionStatus.PROCESSING)
                .externalTransactionId((account.getBankCode().equalsIgnoreCase("MOMO") || account.getBankCode().equalsIgnoreCase("ZALOPAY") ? "EWALLET-" : "BANK-") + UUID.randomUUID().toString())
                .build();

        Payout savedPayout = payoutRepository.save(payout);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(bp.getUser())
                .title("Yêu cầu rút tiền đang được xử lý")
                .content("Yêu cầu rút " + netAmountRaw.setScale(0, RoundingMode.HALF_UP) + "đ từ đơn hàng " + order.getOrderCode() + " đã được ghi nhận.")
                .type(NotificationType.PAYMENT)
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(savedPayout.getId())
                .build());

        return PayoutResponse.from(savedPayout);
    }

    @Transactional(readOnly = true)
    public com.datn.foodshare.domain.response.WalletSummaryResponse getWalletSummary(org.springframework.data.domain.Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ Supplier mới có thể truy cập ví");
        }
        BusinessProfile bp = currentUser.getBusinessProfile();
        if (bp == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp");
        }

        List<Payout> allPayouts = payoutRepository.findByOrderBusinessProfileId(bp.getId());

        BigDecimal totalPending = BigDecimal.ZERO;
        BigDecimal totalCompleted = BigDecimal.ZERO;
        int pendingCount = 0;

        for (Payout p : allPayouts) {
            if (p.getPayoutStatus() == TransactionStatus.PENDING || p.getPayoutStatus() == TransactionStatus.PROCESSING) {
                totalPending = totalPending.add(p.getNetAmount());
                pendingCount++;
            } else if (p.getPayoutStatus() == TransactionStatus.SUCCESS) {
                totalCompleted = totalCompleted.add(p.getNetAmount());
            }
        }

        // Calculate total earnings from orders with SUCCESS payment status
        BigDecimal totalOrderAmount = orderRepository.sumTotalAmountByBusinessProfileIdAndPaymentStatus(bp.getId(), TransactionStatus.SUCCESS);
        if (totalOrderAmount == null) {
            totalOrderAmount = BigDecimal.ZERO;
        }
        
        BigDecimal currentFee = getPlatformFeePercentage();
        BigDecimal platformFee = totalOrderAmount.multiply(currentFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRevenue = totalOrderAmount.subtract(platformFee);
        
        // Available Balance = Total Revenue - (Pending + Completed Withdrawals)
        BigDecimal totalEarned = totalRevenue.subtract(totalPending).subtract(totalCompleted);
        if (totalEarned.compareTo(BigDecimal.ZERO) < 0) {
            totalEarned = BigDecimal.ZERO;
        }

        Page<Payout> pagedPayouts = payoutRepository.findByOrderBusinessProfileIdOrderByCreatedAtDesc(bp.getId(), pageable);
        Page<PayoutResponse> transactions = pagedPayouts.map(PayoutResponse::from);

        return com.datn.foodshare.domain.response.WalletSummaryResponse.builder()
                .totalEarned(totalEarned)
                .totalPending(totalPending)
                .totalCompleted(totalCompleted)
                .pendingCount(pendingCount)
                .platformFeePercentage(currentFee.multiply(new BigDecimal("100"))) // e.g. 0.05 * 100 = 5
                .transactions(transactions)
                .build();
    }
}
