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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order/{orderId}")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Khởi tạo thanh toán thành công")
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody CreatePaymentRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(orderId, request));
    }

    @PatchMapping("/{paymentId}/success")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Mô phỏng thanh toán thành công")
    public ResponseEntity<PaymentResponse> handlePaymentSuccess(@PathVariable("paymentId") Long paymentId) throws PermissionException {
        return ResponseEntity.ok(paymentService.handlePaymentSuccess(paymentId));
    }

    @PostMapping("/momo/callback")
    @ApiMessage("Đã nhận callback MoMo")
    public ResponseEntity<Void> momoCallback(@RequestBody Map<String, Object> payload) {
        paymentService.processMomoCallback(payload);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/momo/result")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Đã xử lý kết quả MoMo")
    public ResponseEntity<PaymentResponse> momoRedirectResult(
            @RequestParam("orderId") String orderId,
            @RequestParam("resultCode") String resultCode,
            @RequestParam(value = "amount", required = false) Long amount) throws PermissionException {
        return ResponseEntity.ok(paymentService.processMomoRedirect(orderId, resultCode, amount));
    }

    @PostMapping("/zalopay/result")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Đã xử lý kết quả ZaloPay")
    public ResponseEntity<PaymentResponse> zaloPayRedirectResult(
            @RequestParam("apptransid") String appTransId,
            @RequestParam("status") String status,
            @RequestParam(value = "amount", required = false) Long amount) throws PermissionException {
        return ResponseEntity.ok(paymentService.processZaloPayRedirect(appTransId, status, amount));
    }

    @PostMapping("/zalopay/callback")
    @ApiMessage("Đã nhận callback ZaloPay")
    public ResponseEntity<Void> zaloPayCallback(@RequestBody Map<String, Object> payload) {
        paymentService.processZaloPayCallback(payload);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zalopay/callback")
    @ApiMessage("Đã nhận kết quả callback ZaloPay")
    public ResponseEntity<Void> zaloPayCallbackRedirect(
            @RequestParam(value = "zptranstoken", required = false) String transactionToken,
            @RequestParam(value = "returncode", required = false) String returnCode,
            @RequestParam(value = "returnmessage", required = false) String returnMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("zptranstoken", transactionToken);
        payload.put("returncode", returnCode);
        payload.put("returnmessage", returnMessage);
        paymentService.processZaloPayCallback(payload);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{paymentId}/failure")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Mô phỏng thanh toán thất bại")
    public ResponseEntity<PaymentResponse> handlePaymentFailure(@PathVariable("paymentId") Long paymentId) throws PermissionException {
        return ResponseEntity.ok(paymentService.handlePaymentFailure(paymentId));
    }

    @PatchMapping("/{paymentId}/refund")
    @Secured("ROLE_ADMIN")
    @ApiMessage("Hoàn tiền thành công")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable("paymentId") Long paymentId) throws PermissionException {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId));
    }
}
