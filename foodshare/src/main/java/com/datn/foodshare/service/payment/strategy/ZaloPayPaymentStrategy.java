package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.HashMap;
import com.datn.foodshare.util.error.BusinessException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

@Component

public class ZaloPayPaymentStrategy implements PaymentStrategy {
    private final RestClient client;
    @Value("${zalopay.endpoint:https://sb-openapi.zalopay.vn/v2/create}") private String endpoint;
    @Value("${zalopay.app-id:}") private String appId;
    @Value("${zalopay.key1:}") private String key1;
    @Value("${zalopay.redirect-url:http://localhost:5173/recipient/orders}") private String redirectUrl;
    @Value("${zalopay.callback-url:http://localhost:8080/api/payments/zalopay/callback}") private String callbackUrl;

    public ZaloPayPaymentStrategy() {
        this.client = RestClient.create();
    }

    @Override public Payment createPayment(Order order, Payment payment) {
        if (appId.isBlank() || key1.isBlank()) throw new IllegalStateException("Thiếu ZALOPAY_APP_ID/ZALOPAY_KEY1");
        long now = System.currentTimeMillis();
        String appTransId = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMdd")) + "_" + order.getId() + "_" + now;
        String redirect = redirectUrl.trim();
        if (redirect.startsWith("[") && redirect.contains("](")) {
            int start = redirect.indexOf("](") + 2;
            int end = redirect.lastIndexOf(')');
            if (end > start) redirect = redirect.substring(start, end);
        }
        String embed = "{\"redirecturl\":\"" + redirect.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        String item = "[]";
        String mac = GatewaySigning.hmacSha256(key1, appId + "|" + appTransId + "|foodshare" + order.getReceiver().getId() + "|" + payment.getAmount().longValue() + "|" + now + "|" + embed + "|" + item);
        Map<String,Object> body = new HashMap<>();
        body.put("app_id", Integer.parseInt(appId)); body.put("app_user", "foodshare" + order.getReceiver().getId()); body.put("app_time", now);
        body.put("amount", payment.getAmount().longValue()); body.put("app_trans_id", appTransId); body.put("embed_data", embed); body.put("item", item);
        body.put("description", "Thanh toan don " + order.getOrderCode()); body.put("bank_code", ""); body.put("callback_url", callbackUrl); body.put("mac", mac);
        body.put("redirect_url", redirect);
        @SuppressWarnings("unchecked") Map<String,Object> response = client.post().uri(endpoint).body(body).retrieve().body(Map.class);
        if (response == null || !Integer.valueOf(1).equals(response.get("return_code"))) throw new BusinessException("ZaloPay không tạo được giao dịch: " + response);
        payment.setPaymentStatus(TransactionStatus.PROCESSING); payment.setProvider("ZALOPAY"); payment.setExternalTransactionId(appTransId);
        payment.setPaymentUrl(String.valueOf(response.get("order_url")));
        return payment;
    }
    @Override
    public Payment processPayment(Order order, Payment payment) {
        payment.setPaymentStatus(TransactionStatus.PROCESSING);
        payment.setProvider("ZALOPAY");
        payment.setExternalTransactionId("ZALOPAY-" + UUID.randomUUID());
        return payment;
    }

    @Override public Payment processRefund(Payment payment) {
        payment.setPaymentStatus(TransactionStatus.REFUNDED);
        payment.setRefundTransactionId("ZALOPAY-REF-" + UUID.randomUUID());
        payment.setRefundedAt(Instant.now());
        return payment;
    }
}
