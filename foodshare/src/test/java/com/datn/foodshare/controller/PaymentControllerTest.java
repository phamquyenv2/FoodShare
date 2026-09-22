package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.domain.response.UserPaymentSummaryResponse;
import com.datn.foodshare.service.payment.PaymentService;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
    }

    private PaymentResponse mockPaymentResponse(Long id) {
        return PaymentResponse.builder()
                .id(id)
                .amount(BigDecimal.valueOf(100000))
                .method(PaymentMethod.MOMO)
                .status(TransactionStatus.SUCCESS)
                .build();
    }

    @Test
    void createPayment_returns201() throws PermissionException {
        CreatePaymentRequest request = new CreatePaymentRequest(PaymentMethod.MOMO);
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.createPayment(10L, request)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.createPayment(10L, request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void handlePaymentSuccess_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.handlePaymentSuccess(1L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.handlePaymentSuccess(1L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void handlePaymentFailure_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.handlePaymentFailure(1L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.handlePaymentFailure(1L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void refundPayment_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.refundPayment(1L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.refundPayment(1L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void momoCallback_callsServiceAndReturns204() {
        Map<String, Object> payload = Map.of("orderId", "123", "resultCode", 0);

        ResponseEntity<Void> entity = controller.momoCallback(payload);

        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
        verify(paymentService).processMomoCallback(payload);
    }

    @Test
    void momoRedirectResult_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.processMomoRedirect("ORD-1", "0", 50000L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.momoRedirectResult("ORD-1", "0", 50000L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void zaloPayRedirectResult_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(1L);
        when(paymentService.processZaloPayRedirect("TRANS-1", "1", 50000L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.zaloPayRedirectResult("TRANS-1", "1", 50000L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void zaloPayCallback_callsServiceAndReturns204() {
        Map<String, Object> payload = Map.of("data", "sample");

        ResponseEntity<Void> entity = controller.zaloPayCallback(payload);

        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
        verify(paymentService).processZaloPayCallback(payload);
    }

    @Test
    void zaloPayCallbackRedirect_callsServiceWithPayloadAndReturns204() {
        ResponseEntity<Void> entity = controller.zaloPayCallbackRedirect("token123", "1", "Success");

        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
        verify(paymentService).processZaloPayCallback(Map.of(
                "zptranstoken", "token123",
                "returncode", "1",
                "returnmessage", "Success"
        ));
    }

    @Test
    void getMyPaymentHistory_withVariousParams() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentResponse> page = new PageImpl<>(List.of(mockPaymentResponse(1L)));

        when(paymentService.getMyPaymentHistory(eq("keyword"), eq(TransactionStatus.SUCCESS), eq(PaymentMethod.MOMO), eq(pageable)))
                .thenReturn(page);

        ResponseEntity<Page<PaymentResponse>> entity = controller.getMyPaymentHistory(
                "keyword", "success", "momo", pageable
        );

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(page, entity.getBody());

        // Test with invalid enum and 'all'
        when(paymentService.getMyPaymentHistory(eq(null), eq(null), eq(null), eq(pageable)))
                .thenReturn(page);

        ResponseEntity<Page<PaymentResponse>> entity2 = controller.getMyPaymentHistory(
                null, "all", "invalid_method", pageable
        );
        assertEquals(HttpStatus.OK, entity2.getStatusCode());
    }

    @Test
    void getMyPaymentSummary_returns200() throws PermissionException {
        UserPaymentSummaryResponse summary = UserPaymentSummaryResponse.builder()
                .totalTransactions(5L)
                .totalSpent(BigDecimal.valueOf(250000))
                .totalRefunded(BigDecimal.ZERO)
                .build();
        when(paymentService.getMyPaymentSummary()).thenReturn(summary);

        ResponseEntity<UserPaymentSummaryResponse> entity = controller.getMyPaymentSummary();

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(summary, entity.getBody());
    }

    @Test
    void getMyPaymentDetail_returns200() throws PermissionException {
        PaymentResponse response = mockPaymentResponse(99L);
        when(paymentService.getMyPaymentDetail(99L)).thenReturn(response);

        ResponseEntity<PaymentResponse> entity = controller.getMyPaymentDetail(99L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }
}
