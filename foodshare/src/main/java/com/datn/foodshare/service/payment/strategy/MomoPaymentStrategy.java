package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.HashMap;
import com.datn.foodshare.util.error.BusinessException;

@Component
public class MomoPaymentStrategy implements PaymentStrategy {
    private static final Logger log = LoggerFactory.getLogger(MomoPaymentStrategy.class);

    private final RestClient client;
    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}") private String endpoint;
    @Value("${momo.refund-endpoint:https://test-payment.momo.vn/v2/gateway/api/refund}") private String refundEndpoint;
    @Value("${momo.partner-code:}") private String partnerCode;
    @Value("${momo.access-key:}") private String accessKey;
    @Value("${momo.secret-key:}") private String secretKey;
    @Value("${momo.redirect-url:http://localhost:5173/recipient/orders}") private String redirectUrl;
    @Value("${momo.ipn-url:http://localhost:8080/api/payments/momo/callback}") private String ipnUrl;

    public MomoPaymentStrategy() {
        this.client = RestClient.create();
    }

    @Override public Payment createPayment(Order order, Payment payment) {
        if (partnerCode.isBlank() || accessKey.isBlank() || secretKey.isBlank())
            throw new IllegalStateException("Thiếu cấu hình MOMO_PARTNER_CODE/MOMO_ACCESS_KEY/MOMO_SECRET_KEY");
        String orderId = "FS-" + order.getId() + "-" + System.currentTimeMillis();
        String safeRedirectUrl = normalizeUrl(redirectUrl);
        String safeIpnUrl = normalizeUrl(ipnUrl);
        String requestId = UUID.randomUUID().toString();
        String info = "Thanh toan don " + order.getOrderCode();
        String raw = "accessKey=" + accessKey + "&amount=" + payment.getAmount().longValue()
                + "&extraData=&ipnUrl=" + safeIpnUrl + "&orderId=" + orderId + "&orderInfo=" + info
                + "&partnerCode=" + partnerCode + "&redirectUrl=" + safeRedirectUrl
                + "&requestId=" + requestId + "&requestType=captureWallet";
        Map<String,Object> body = new HashMap<>();
        body.put("partnerCode", partnerCode); body.put("partnerName", "FoodShare"); body.put("storeId", "FoodShareStore"); body.put("requestId", requestId);
        body.put("amount", payment.getAmount().longValue()); body.put("orderId", orderId); body.put("orderInfo", info);
        body.put("redirectUrl", safeRedirectUrl); body.put("ipnUrl", safeIpnUrl); body.put("lang", "vi");
        body.put("autoCapture", true); body.put("requestType", "captureWallet"); body.put("extraData", "");
        body.put("signature", GatewaySigning.hmacSha256(secretKey, raw));
        @SuppressWarnings("unchecked") Map<String,Object> response = client.post().uri(endpoint).body(body).retrieve().body(Map.class);
        if (response == null || !Integer.valueOf(0).equals(response.get("resultCode")))
            throw new BusinessException("MoMo không tạo được giao dịch: " + response);
        payment.setPaymentStatus(TransactionStatus.PROCESSING);
        payment.setProvider("MOMO"); payment.setExternalTransactionId(orderId);
        payment.setPaymentUrl(String.valueOf(response.get("payUrl")));
        return payment;
    }

    private String normalizeUrl(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.startsWith("[") && result.contains("](")) {
            int start = result.indexOf("](") + 2;
            int end = result.lastIndexOf(')');
            if (end > start) result = result.substring(start, end);
        }
        return result;
    }
    @Override
    public Payment processPayment(Order order, Payment payment) {
        payment.setPaymentStatus(TransactionStatus.PROCESSING);
        payment.setProvider("MOMO");
        payment.setExternalTransactionId("MOMO-" + UUID.randomUUID());
        return payment;
    }

    @Override public Payment processRefund(Payment payment) {
        String refundOrderId = "FS-REF-" + payment.getId() + "-" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        if (partnerCode != null && !partnerCode.isBlank() && accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            try {
                long amount = payment.getAmount().longValue();
                String description = "Hoan tien don hang " + (payment.getOrder() != null ? payment.getOrder().getOrderCode() : payment.getId());
                long transId = 0L;

                String raw = "accessKey=" + accessKey + "&amount=" + amount
                        + "&description=" + description + "&orderId=" + refundOrderId
                        + "&partnerCode=" + partnerCode + "&requestId=" + requestId
                        + "&transId=" + transId;

                Map<String, Object> body = new HashMap<>();
                body.put("partnerCode", partnerCode);
                body.put("orderId", refundOrderId);
                body.put("requestId", requestId);
                body.put("amount", amount);
                body.put("transId", transId);
                body.put("lang", "vi");
                body.put("description", description);
                body.put("signature", GatewaySigning.hmacSha256(secretKey, raw));

                @SuppressWarnings("unchecked")
                Map<String, Object> response = client.post().uri(refundEndpoint).body(body).retrieve().body(Map.class);
                if (response != null) {
                    log.info("MoMo refund response for payment {}: {}", payment.getId(), response);
                    if (response.get("transId") != null) {
                        refundOrderId = String.valueOf(response.get("transId"));
                    }
                }
            } catch (Exception ex) {
                log.warn("Lỗi khi gọi API MoMo refund cho payment {}: {}", payment.getId(), ex.getMessage());
            }
        }

        payment.setPaymentStatus(TransactionStatus.REFUNDED);
        payment.setRefundTransactionId(refundOrderId);
        payment.setRefundedAt(Instant.now());
        return payment;
    }
}
