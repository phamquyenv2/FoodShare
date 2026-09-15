package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payout;
import com.datn.foodshare.domain.entity.PayoutAccount;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.SystemConfig;
import com.datn.foodshare.domain.entity.SupplierEarning;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.event.NotificationEvent;
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
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.constant.PayoutStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
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
    private BusinessProfileRepository businessProfileRepository;
    @Mock
    private SupplierEarningRepository supplierEarningRepository;
    @Mock
    private SupplierEarningService supplierEarningService;
    @Mock
    private com.datn.foodshare.repository.UserRepository userRepository;
    @Mock
    private SystemConfigRepository systemConfigRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                businessProfileRepository,
                supplierEarningRepository,
                supplierEarningService,
                userRepository,
                systemConfigRepository,
                eventPublisher
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
            order.getPayments().add(successfulPayment(order));

            PayoutAccount acc = payoutAccount(ACCOUNT_ID, "MOMO", false, true);
            stubPayoutPrerequisites(order, acc);

            when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> {
                Payout p = inv.getArgument(0);
                p.setId(999L);
                return p;
            });

            CreatePayoutRequest req = new CreatePayoutRequest(ACCOUNT_ID);
            PayoutResponse res = payoutService.createPayout(ORDER_ID, req);

            assertNotNull(res);
            assertEquals(new BigDecimal("95000.00"), res.getRequestedAmount());
            assertEquals(new BigDecimal("0.00"), res.getPlatformFee());
            assertEquals(new BigDecimal("95000.00"), res.getNetAmount());
            assertEquals(PayoutStatus.PENDING, res.getStatus());
            assertNull(res.getExternalTransactionId());
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
            order.getPayments().add(successfulPayment(order));

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(businessProfileRepository.findByIdForUpdate(BP_ID)).thenReturn(Optional.of(bp));
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

    @Test
    void createPayoutAccount_whenReplacingDefault_deactivatesOldDefault() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            PayoutAccount oldDefault = payoutAccount(1L, "VCB", true, true);
            when(payoutAccountRepository.findByBusinessProfileIdAndIsDefaultTrue(BP_ID))
                    .thenReturn(Optional.of(oldDefault));
            when(payoutAccountRepository.save(any(PayoutAccount.class))).thenAnswer(invocation -> {
                PayoutAccount account = invocation.getArgument(0);
                if (account.getId() == null) {
                    account.setId(ACCOUNT_ID);
                }
                return account;
            });

            PayoutAccountResponse result = payoutService.createPayoutAccount(
                    new CreatePayoutAccountRequest("MOMO", "MoMo", "0900000000", "Supplier", true)
            );

            assertFalse(oldDefault.isDefault());
            assertTrue(result.isDefault());
            verify(payoutAccountRepository).save(oldDefault);
            verify(payoutAccountRepository, times(2)).save(any(PayoutAccount.class));
        }
    }

    @Test
    void createPayoutAccount_whenUserIsNotSupplier_rejectsWithoutSaving() {
        User recipient = mockSupplier();
        recipient.setRole(Role.RECIPIENT);
        try (MockedStatic<SecurityUtil> su = authenticatedAs(recipient)) {
            CreatePayoutAccountRequest request = new CreatePayoutAccountRequest(
                    "VCB", "Vietcombank", "123", "Recipient", false);

            assertThrows(PermissionException.class, () -> payoutService.createPayoutAccount(request));

            verify(payoutAccountRepository, never()).save(any());
        }
    }

    @Test
    void getMyPayoutAccounts_returnsOnlyActiveAccountsFromRepository() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            PayoutAccount account = payoutAccount(ACCOUNT_ID, "VCB", true, true);
            when(payoutAccountRepository.findByBusinessProfileIdAndIsActiveTrue(BP_ID))
                    .thenReturn(List.of(account));

            List<PayoutAccountResponse> result = payoutService.getMyPayoutAccounts();

            assertEquals(1, result.size());
            assertEquals(ACCOUNT_ID, result.get(0).getId());
            assertEquals("VCB", result.get(0).getBankCode());
        }
    }

    @Test
    void createPayout_whenOrderBelongsToAnotherSupplier_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(new BigDecimal("100000"));
            order.getBusinessProfile().setId(999L);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(PermissionException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            verifyNoInteractions(payoutRepository);
        }
    }

    @Test
    void createPayout_whenOrderHasNoSuccessfulPayment_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(new BigDecimal("100000"));
            order.getPayments().clear();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            assertTrue(exception.getMessage().contains("chưa thanh toán online thành công"));
            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void createPayout_whenAccountIsInactive_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(new BigDecimal("100000"));
            PayoutAccount account = payoutAccount(ACCOUNT_ID, "VCB", false, false);
            stubPayoutPrerequisites(order, account);

            assertThrows(BusinessException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void createPayout_whenGrossAmountIsZero_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(BigDecimal.ZERO);
            stubPayoutPrerequisites(order, payoutAccount(ACCOUNT_ID, "VCB", false, true));

            assertThrows(BusinessException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            verifyNoInteractions(systemConfigRepository);
        }
    }

    @Test
    void createPayout_whenNetAmountIsBelowMinimum_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(new BigDecimal("50000"));
            stubPayoutPrerequisites(order, payoutAccount(ACCOUNT_ID, "VCB", false, true));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            assertTrue(exception.getMessage().contains("nhỏ hơn mức tối thiểu"));
            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void createPayout_whenNetAmountExceedsMaximum_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Order order = eligibleOrder(new BigDecimal("30000000"));
            stubPayoutPrerequisites(order, payoutAccount(ACCOUNT_ID, "VCB", false, true));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> payoutService.createPayout(ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID)));

            assertTrue(exception.getMessage().contains("vượt quá hạn mức tối đa"));
            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void createPayout_usesConfiguredFeeAndPublishesNotification() throws PermissionException {
        User supplier = mockSupplier();
        supplier.getBusinessProfile().setUser(supplier);
        try (MockedStatic<SecurityUtil> su = authenticatedAs(supplier)) {
            Order order = eligibleOrder(new BigDecimal("100000"));
            order.setOrderCode("ORD-100");
            PayoutAccount account = payoutAccount(ACCOUNT_ID, "VCB", false, true);
            stubPayoutPrerequisites(order, account);
            when(supplierEarningService.recordForCompletedOrder(order))
                    .thenReturn(Optional.of(earning(order, "90000.00")));
            when(supplierEarningRepository.sumActiveNetAmount(BP_ID))
                    .thenReturn(new BigDecimal("90000.00"));
            when(systemConfigRepository.findByConfigKey("MIN_PAYOUT_AMOUNT"))
                    .thenReturn(Optional.of(config("1000")));
            when(systemConfigRepository.findByConfigKey("MAX_PAYOUT_AMOUNT"))
                    .thenReturn(Optional.of(config("1000000")));
            when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> {
                Payout payout = invocation.getArgument(0);
                payout.setId(999L);
                return payout;
            });

            PayoutResponse result = payoutService.createPayout(
                    ORDER_ID, new CreatePayoutRequest(ACCOUNT_ID));

            assertEquals(new BigDecimal("0.00"), result.getPlatformFee());
            assertEquals(new BigDecimal("90000.00"), result.getNetAmount());
            assertNull(result.getExternalTransactionId());
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(999L, eventCaptor.getValue().getReferenceId());
        }
    }

    @Test
    void getWalletSummary_aggregatesStatusesAndAppliesConfiguredFee() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Payout pending = payout(1L, PayoutStatus.PENDING, "100");
            Payout processing = payout(2L, PayoutStatus.PENDING, "200");
            Payout completed = payout(3L, PayoutStatus.SUCCESS, "300");
            Payout failed = payout(4L, PayoutStatus.FAILED, "400");
            List<Payout> payouts = List.of(pending, processing, completed, failed);
            PageRequest pageable = PageRequest.of(0, 10);
            when(payoutRepository.sumRequestedAmount(BP_ID, List.of(PayoutStatus.PENDING)))
                    .thenReturn(new BigDecimal("300"));
            when(payoutRepository.sumRequestedAmount(BP_ID, List.of(PayoutStatus.SUCCESS)))
                    .thenReturn(new BigDecimal("300"));
            when(supplierEarningRepository.sumActiveNetAmount(BP_ID)).thenReturn(new BigDecimal("900000"));
            when(systemConfigRepository.findByConfigKey("PLATFORM_FEE_PERCENTAGE"))
                    .thenReturn(Optional.of(config("0.10")));
            when(payoutRepository.findByBusinessProfileIdOrderByCreatedAtDesc(BP_ID, pageable))
                    .thenReturn(new PageImpl<>(payouts, pageable, payouts.size()));
            when(payoutRepository.countByBusinessProfileIdAndPayoutStatus(BP_ID, PayoutStatus.PENDING))
                    .thenReturn(2L);

            var result = payoutService.getWalletSummary(pageable);

            assertEquals(new BigDecimal("300"), result.getTotalPending());
            assertEquals(new BigDecimal("300"), result.getTotalCompleted());
            assertEquals(2, result.getPendingCount());
            assertEquals(0, new BigDecimal("899400.00").compareTo(result.getTotalEarned()));
            assertEquals(new BigDecimal("10.00"), result.getPlatformFeePercentage());
            assertEquals(4, result.getTransactions().getTotalElements());
        }
    }

    @Test
    void getWalletSummary_whenRevenueIsNullAndWithdrawalsExist_clampsAvailableBalanceToZero()
            throws PermissionException {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            Payout pending = payout(1L, PayoutStatus.PENDING, "100");
            PageRequest pageable = PageRequest.of(0, 10);
            when(payoutRepository.sumRequestedAmount(BP_ID, List.of(PayoutStatus.PENDING)))
                    .thenReturn(new BigDecimal("100"));
            when(payoutRepository.sumRequestedAmount(BP_ID, List.of(PayoutStatus.SUCCESS)))
                    .thenReturn(BigDecimal.ZERO);
            when(supplierEarningRepository.sumActiveNetAmount(BP_ID)).thenReturn(BigDecimal.ZERO);
            when(payoutRepository.findByBusinessProfileIdOrderByCreatedAtDesc(BP_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));
            when(payoutRepository.countByBusinessProfileIdAndPayoutStatus(BP_ID, PayoutStatus.PENDING))
                    .thenReturn(1L);

            var result = payoutService.getWalletSummary(pageable);

            assertEquals(BigDecimal.ZERO, result.getTotalEarned());
            assertEquals(BigDecimal.ZERO, result.getTotalCompleted());
            assertEquals(1, result.getPendingCount());
        }
    }

    @Test
    void createPayoutRequest_reservesAggregateBalanceAndSnapshotsDestination() throws PermissionException {
        User supplier = mockSupplier();
        supplier.getBusinessProfile().setUser(supplier);
        try (MockedStatic<SecurityUtil> su = authenticatedAs(supplier)) {
            PayoutAccount account = payoutAccount(ACCOUNT_ID, "VCB", true, true);
            when(businessProfileRepository.findByIdForUpdate(BP_ID))
                    .thenReturn(Optional.of(supplier.getBusinessProfile()));
            when(payoutAccountRepository.findByIdAndBusinessProfileId(ACCOUNT_ID, BP_ID))
                    .thenReturn(Optional.of(account));
            when(supplierEarningRepository.sumActiveNetAmount(BP_ID))
                    .thenReturn(new BigDecimal("200000.00"));
            when(payoutRepository.sumRequestedAmount(
                    BP_ID, List.of(PayoutStatus.PENDING, PayoutStatus.SUCCESS)))
                    .thenReturn(new BigDecimal("50000.00"));
            when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> {
                Payout payout = invocation.getArgument(0);
                payout.setId(900L);
                return payout;
            });

            PayoutResponse result = payoutService.createPayoutRequest(
                    new CreatePayoutRequest(ACCOUNT_ID, new BigDecimal("100000")));

            assertEquals(PayoutStatus.PENDING, result.getStatus());
            assertEquals(new BigDecimal("100000.00"), result.getRequestedAmount());
            assertEquals("VCB", result.getBankCode());
            assertEquals("123456789", result.getAccountNumber());
            verify(businessProfileRepository).findByIdForUpdate(BP_ID);
        }
    }

    @Test
    void createPayoutRequest_whenAmountExceedsAvailableBalance_rejects() {
        try (MockedStatic<SecurityUtil> su = authenticatedAs(mockSupplier())) {
            when(businessProfileRepository.findByIdForUpdate(BP_ID))
                    .thenReturn(Optional.of(mockSupplier().getBusinessProfile()));
            when(payoutAccountRepository.findByIdAndBusinessProfileId(ACCOUNT_ID, BP_ID))
                    .thenReturn(Optional.of(payoutAccount(ACCOUNT_ID, "VCB", true, true)));
            when(supplierEarningRepository.sumActiveNetAmount(BP_ID))
                    .thenReturn(new BigDecimal("100000"));
            when(payoutRepository.sumRequestedAmount(
                    BP_ID, List.of(PayoutStatus.PENDING, PayoutStatus.SUCCESS)))
                    .thenReturn(new BigDecimal("50000"));

            assertThrows(BusinessException.class, () -> payoutService.createPayoutRequest(
                    new CreatePayoutRequest(ACCOUNT_ID, new BigDecimal("60000"))));

            verify(payoutRepository, never()).save(any());
        }
    }

    @Test
    void approvePayout_fromPending_marksSuccessAndPublishesOnce() throws PermissionException {
        User admin = mockSupplier();
        admin.setRole(Role.ADMIN);
        Payout payout = payout(90L, PayoutStatus.PENDING, "100000");
        payout.getBusinessProfile().setUser(mockSupplier());
        when(payoutRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(payout));
        when(payoutRepository.save(payout)).thenReturn(payout);

        try (MockedStatic<SecurityUtil> su = authenticatedAs(admin)) {
            PayoutResponse result = payoutService.approvePayout(90L);

            assertEquals(PayoutStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getCompletedAt());
            assertNotNull(result.getReviewedAt());
            assertTrue(result.getExternalTransactionId().startsWith("PAYOUT-"));
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }
    }

    @Test
    void approvePayout_whenAlreadySuccess_isIdempotent() throws PermissionException {
        User admin = mockSupplier();
        admin.setRole(Role.ADMIN);
        Payout payout = payout(91L, PayoutStatus.SUCCESS, "100000");
        when(payoutRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(payout));

        try (MockedStatic<SecurityUtil> su = authenticatedAs(admin)) {
            assertEquals(PayoutStatus.SUCCESS, payoutService.approvePayout(91L).getStatus());
            verify(payoutRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Test
    void rejectPayout_fromPending_recordsReasonAndReleasesReservation() throws PermissionException {
        User admin = mockSupplier();
        admin.setRole(Role.ADMIN);
        Payout payout = payout(92L, PayoutStatus.PENDING, "100000");
        payout.getBusinessProfile().setUser(mockSupplier());
        when(payoutRepository.findByIdForUpdate(92L)).thenReturn(Optional.of(payout));
        when(payoutRepository.save(payout)).thenReturn(payout);

        try (MockedStatic<SecurityUtil> su = authenticatedAs(admin)) {
            PayoutResponse result = payoutService.rejectPayout(92L, "  Sai thông tin tài khoản  ");

            assertEquals(PayoutStatus.FAILED, result.getStatus());
            assertEquals("Sai thông tin tài khoản", result.getRejectionReason());
            assertNotNull(result.getFailedAt());
        }
    }

    @Test
    void rejectPayout_afterSuccess_rejectsConflictingTransition() {
        User admin = mockSupplier();
        admin.setRole(Role.ADMIN);
        Payout payout = payout(93L, PayoutStatus.SUCCESS, "100000");
        when(payoutRepository.findByIdForUpdate(93L)).thenReturn(Optional.of(payout));

        try (MockedStatic<SecurityUtil> su = authenticatedAs(admin)) {
            assertThrows(BusinessException.class,
                    () -> payoutService.rejectPayout(93L, "Không hợp lệ"));
            verify(payoutRepository, never()).save(any());
        }
    }

    private MockedStatic<SecurityUtil> authenticatedAs(User user) {
        MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class);
        security.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(user.getId()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return security;
    }

    private Order eligibleOrder(BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(ORDER_ID);
        BusinessProfile profile = new BusinessProfile();
        profile.setId(BP_ID);
        order.setBusinessProfile(profile);
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setTotalAmount(totalAmount);
        order.getPayments().add(successfulPayment(order));
        return order;
    }

    private void stubPayoutPrerequisites(Order order, PayoutAccount account) {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        lenient().when(payoutRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
        lenient().when(businessProfileRepository.findByIdForUpdate(BP_ID))
                .thenReturn(Optional.of(order.getBusinessProfile()));
        BigDecimal netAmount = order.getTotalAmount() == null
                ? BigDecimal.ZERO
                : order.getTotalAmount().multiply(new BigDecimal("0.95")).setScale(2);
        lenient().when(supplierEarningService.recordForCompletedOrder(order))
                .thenReturn(Optional.of(earning(order, netAmount.toPlainString())));
        lenient().when(supplierEarningRepository.sumActiveNetAmount(BP_ID)).thenReturn(netAmount);
        lenient().when(payoutRepository.sumRequestedAmount(
                BP_ID, List.of(PayoutStatus.PENDING, PayoutStatus.SUCCESS))).thenReturn(BigDecimal.ZERO);
        lenient().when(payoutAccountRepository.findByIdAndBusinessProfileId(ACCOUNT_ID, BP_ID))
                .thenReturn(Optional.of(account));
    }

    private SupplierEarning earning(Order order, String netAmount) {
        SupplierEarning earning = new SupplierEarning();
        earning.setOrder(order);
        earning.setBusinessProfile(order.getBusinessProfile());
        earning.setNetAmount(new BigDecimal(netAmount));
        return earning;
    }

    private PayoutAccount payoutAccount(Long id, String bankCode, boolean isDefault, boolean active) {
        PayoutAccount account = new PayoutAccount();
        account.setId(id);
        account.setBusinessProfile(mockSupplier().getBusinessProfile());
        account.setBankCode(bankCode);
        account.setBankName(bankCode);
        account.setAccountNumber("123456789");
        account.setAccountHolderName("Supplier");
        account.setDefault(isDefault);
        account.setActive(active);
        return account;
    }

    private Payout payout(Long id, PayoutStatus status, String netAmount) {
        Payout payout = new Payout();
        payout.setId(id);
        payout.setOrder(eligibleOrder(new BigDecimal("1000")));
        payout.setBusinessProfile(mockSupplier().getBusinessProfile());
        payout.setPayoutAccount(payoutAccount(ACCOUNT_ID, "VCB", false, true));
        payout.setPayoutCode("PO-" + id);
        payout.setGrossAmount(new BigDecimal(netAmount));
        payout.setPlatformFee(BigDecimal.ZERO);
        payout.setNetAmount(new BigDecimal(netAmount));
        payout.setRequestedAmount(new BigDecimal(netAmount));
        payout.setBankCode("VCB");
        payout.setBankName("Vietcombank");
        payout.setAccountNumber("123456789");
        payout.setAccountHolderName("Supplier");
        payout.setPayoutStatus(status);
        return payout;
    }

    private SystemConfig config(String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigValue(value);
        return config;
    }

    private Payment successfulPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setId(77L);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(com.datn.foodshare.util.constant.PaymentMethod.MOMO);
        payment.setPaymentStatus(TransactionStatus.SUCCESS);
        return payment;
    }
}
