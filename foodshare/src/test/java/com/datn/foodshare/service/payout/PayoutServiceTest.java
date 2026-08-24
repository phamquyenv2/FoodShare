package com.datn.foodshare.service.payout;

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
import com.datn.foodshare.service.PayoutService;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private PayoutAccountRepository payoutAccountRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private com.datn.foodshare.repository.UserRepository userRepository;

    private PayoutService payoutService;

    private static final Long SUPPLIER_ID = 10L;
    private static final Long BP_ID = 5L;
    private static final Long ORDER_ID = 100L;
    private static final Long ACCOUNT_ID = 50L;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutService(
                payoutRepository,
                payoutAccountRepository,
                orderRepository,
                userRepository
        );
    }

    private User mockSupplier() {
        User user = new User();
        user.setId(SUPPLIER_ID);
        user.setRole(Role.SUPPLIER);
        BusinessProfile bp = new BusinessProfile();
        bp.setId(BP_ID);
        user.setBusinessProfile(bp);
        return user;
    }

    @Test
    void createPayoutAccount_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_ID));
            when(userRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(mockSupplier()));

            when(payoutAccountRepository.save(any(PayoutAccount.class))).thenAnswer(inv -> {
                PayoutAccount acc = inv.getArgument(0);
                acc.setId(ACCOUNT_ID);
                return acc;
            });

            CreatePayoutAccountRequest req = new CreatePayoutAccountRequest(
                    "VCB", "Vietcombank", "123456789", "Nguyen Van A", true
            );
            
            when(payoutAccountRepository.findByBusinessProfileIdAndIsDefaultTrue(BP_ID))
                    .thenReturn(Optional.empty());

            PayoutAccountResponse res = payoutService.createPayoutAccount(req);

            assertNotNull(res);
            assertEquals("VCB", res.getBankCode());
            assertTrue(res.isDefault());
            verify(payoutAccountRepository).save(any(PayoutAccount.class));
        }
    }

    @Test
    void createPayout_success_calculatesCorrectAmounts() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_ID));
            when(userRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(mockSupplier()));

            Order order = new Order();
            order.setId(ORDER_ID);
            BusinessProfile bp = new BusinessProfile();
            bp.setId(BP_ID);
            order.setBusinessProfile(bp);
            order.setOrderStatus(OrderStatus.COMPLETED);
            order.setTotalAmount(new BigDecimal("100000.00")); // 100k

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(payoutRepository.existsByOrderId(ORDER_ID)).thenReturn(false);

            PayoutAccount acc = new PayoutAccount();
            acc.setId(ACCOUNT_ID);
            acc.setBankCode("MOMO");
            acc.setActive(true);
            when(payoutAccountRepository.findByIdAndBusinessProfileId(ACCOUNT_ID, BP_ID))
                    .thenReturn(Optional.of(acc));

            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> {
                Payout p = inv.getArgument(0);
                p.setId(999L);
                return p;
            });

            CreatePayoutRequest req = new CreatePayoutRequest(ACCOUNT_ID);
            PayoutResponse res = payoutService.createPayout(ORDER_ID, req);

            assertNotNull(res);
            assertEquals(new BigDecimal("100000.00"), res.getGrossAmount());
            // 5% of 100k = 5000
            assertEquals(new BigDecimal("5000.00"), res.getPlatformFee());
            // 100k - 5k = 95000
            assertEquals(new BigDecimal("95000.00"), res.getNetAmount());
            assertEquals(TransactionStatus.PROCESSING, res.getStatus());
            assertTrue(res.getExternalTransactionId().startsWith("EWALLET-"));
        }
    }

    @Test
    void createPayout_rejectsDuplicate() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_ID));
            when(userRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(mockSupplier()));

            Order order = new Order();
            order.setId(ORDER_ID);
            BusinessProfile bp = new BusinessProfile();
            bp.setId(BP_ID);
            order.setBusinessProfile(bp);
            order.setOrderStatus(OrderStatus.COMPLETED);

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            // Simulate duplicate
            when(payoutRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            CreatePayoutRequest req = new CreatePayoutRequest(ACCOUNT_ID);

            BusinessException ex = assertThrows(BusinessException.class, () -> payoutService.createPayout(ORDER_ID, req));
            assertTrue(ex.getMessage().contains("đã được tạo yêu cầu rút tiền"));
            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void createPayout_rejectsNonCompletedOrder() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_ID));
            when(userRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(mockSupplier()));

            Order order = new Order();
            order.setId(ORDER_ID);
            BusinessProfile bp = new BusinessProfile();
            bp.setId(BP_ID);
            order.setBusinessProfile(bp);
            order.setOrderStatus(OrderStatus.DELIVERED); // Not yet COMPLETED

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            CreatePayoutRequest req = new CreatePayoutRequest(ACCOUNT_ID);

            BusinessException ex = assertThrows(BusinessException.class, () -> payoutService.createPayout(ORDER_ID, req));
            assertTrue(ex.getMessage().contains("chưa hoàn thành"));
        }
    }
}
