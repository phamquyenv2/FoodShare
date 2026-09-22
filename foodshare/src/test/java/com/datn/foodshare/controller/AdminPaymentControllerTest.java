package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.service.AdminPaymentService;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentControllerTest {

    @Mock
    private AdminPaymentService adminPaymentService;

    private AdminPaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminPaymentController(adminPaymentService);
    }

    @Test
    void list_withValidStatus_callsSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PaymentResponse> page = new PageImpl<>(List.of(PaymentResponse.builder().id(1L).build()));
        when(adminPaymentService.search("key", TransactionStatus.SUCCESS, pageable)).thenReturn(page);

        Page<PaymentResponse> result = controller.list("key", "success", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(adminPaymentService).search("key", TransactionStatus.SUCCESS, pageable);
    }

    @Test
    void list_withAllOrInvalidStatus_callsSearchWithNullStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PaymentResponse> page = new PageImpl<>(List.of());
        when(adminPaymentService.search(null, null, pageable)).thenReturn(page);

        Page<PaymentResponse> r1 = controller.list(null, "all", pageable);
        assertNotNull(r1);

        Page<PaymentResponse> r2 = controller.list(null, "invalid_status", pageable);
        assertNotNull(r2);
    }

    @Test
    void detail_callsService() {
        PaymentResponse response = PaymentResponse.builder().id(5L).build();
        when(adminPaymentService.detail(5L)).thenReturn(response);

        PaymentResponse result = controller.detail(5L);

        assertEquals(5L, result.getId());
        verify(adminPaymentService).detail(5L);
    }

    @Test
    void refund_callsService() {
        PaymentResponse response = PaymentResponse.builder().id(5L).build();
        when(adminPaymentService.refund(5L)).thenReturn(response);

        PaymentResponse result = controller.refund(5L);

        assertEquals(5L, result.getId());
        verify(adminPaymentService).refund(5L);
    }
}
