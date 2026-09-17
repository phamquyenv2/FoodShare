package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseTest {
    @Test
    void noPaymentReturnsNull() {
        assertThat(response(List.of()).getPaymentStatus()).isNull();
    }

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    void mapsEveryExistingPaymentStatus(TransactionStatus status) {
        assertThat(response(List.of(payment(1L, status))).getPaymentStatus()).isEqualTo(status);
    }

    @Test
    void latestAttemptWinsRegardlessOfCollectionOrder() {
        assertThat(response(List.of(payment(3L, TransactionStatus.PROCESSING),
                payment(1L, TransactionStatus.FAILED), payment(2L, TransactionStatus.CANCELLED)))
                .getPaymentStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    void successfulPaymentIsNotHiddenByAnotherFailedAttempt() {
        assertThat(response(List.of(payment(1L, TransactionStatus.SUCCESS),
                payment(2L, TransactionStatus.FAILED))).getPaymentStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void refundedPaymentIsNotShownAsSuccessful() {
        assertThat(response(List.of(payment(1L, TransactionStatus.FAILED),
                payment(2L, TransactionStatus.REFUNDED))).getPaymentStatus()).isEqualTo(TransactionStatus.REFUNDED);
    }

    private OrderResponse response(List<Payment> payments) {
        return OrderResponse.from(Order.builder().receiver(User.builder().id(1L).build())
                .businessProfile(BusinessProfile.builder().id(2L).build()).payments(payments).build());
    }

    private Payment payment(Long id, TransactionStatus status) {
        return Payment.builder().id(id).paymentStatus(status).build();
    }
}
