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
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.05"); // 5%

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

        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("Đơn hàng chưa hoàn thành, không đủ điều kiện payout");
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

        BigDecimal platformFee = grossAmount.multiply(PLATFORM_FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
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

        return PayoutResponse.from(payoutRepository.save(payout));
    }
}
