package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreatePaymentRequest;
import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.service.payment.PaymentService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order/{orderId}")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Khởi tạo thanh toán thành công")
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody CreatePaymentRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(orderId, request));
    }

    @PatchMapping("/{paymentId}/success")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Mô phỏng thanh toán thành công")
    public ResponseEntity<PaymentResponse> handlePaymentSuccess(@PathVariable Long paymentId) throws PermissionException {
        return ResponseEntity.ok(paymentService.handlePaymentSuccess(paymentId));
    }

    @PatchMapping("/{paymentId}/failure")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Mô phỏng thanh toán thất bại")
    public ResponseEntity<PaymentResponse> handlePaymentFailure(@PathVariable Long paymentId) throws PermissionException {
        return ResponseEntity.ok(paymentService.handlePaymentFailure(paymentId));
    }

    @PatchMapping("/{paymentId}/refund")
    @Secured("ROLE_ADMIN")
    @ApiMessage("Hoàn tiền thành công")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId));
    }
}
