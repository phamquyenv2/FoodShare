package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ZaloPayPaymentStrategy.class);

    private final RestClient client;
    @Value("${zalopay.endpoint:https://sb-openapi.zalopay.vn/v2/create}") private String endpoint;
    @Value("${zalopay.query-endpoint:https://sb-openapi.zalopay.vn/v2/query}") private String queryEndpoint;
    @Value("${zalopay.refund-endpoint:https://sb-openapi.zalopay.vn/v2/refund}") private String refundEndpoint;
    @Value("${zalopay.app-id:}") private String appId;
    @Value("${zalopay.key1:}") private String key1;
    @Value("${zalopay.redirect-url:http://localhost:5173/payment/result}") private String redirectUrl;
    @Value("${zalopay.callback-url:http://localhost:8080/api/payments/zalopay/callback}") private String callbackUrl;

    public ZaloPayPaymentStrategy() {
        this.client = RestClient.create();
    }

    @Override
    public Payment createPayment(Order order, Payment payment) {
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

    @Override
    public Payment processRefund(Payment payment) {
        String date = LocalDate.now(ZoneOffset.ofHours(7)).format(DateTimeFormatter.ofPattern("yyMMdd"));
        String mRefundId = date + "_" + appId + "_" + payment.getId() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long now = System.currentTimeMillis();

        if (appId != null && !appId.isBlank() && key1 != null && !key1.isBlank()) {
            try {
                String appTransId = payment.getExternalTransactionId();
                String zpTransId = null;
                if (appTransId != null && !appTransId.isBlank()) {
                    String queryMac = GatewaySigning.hmacSha256(key1, appId + "|" + appTransId + "|" + key1);
                    Map<String, Object> queryBody = new HashMap<>();
                    queryBody.put("app_id", Integer.parseInt(appId));
                    queryBody.put("app_trans_id", appTransId);
                    queryBody.put("mac", queryMac);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> queryRes = client.post().uri(queryEndpoint).body(queryBody).retrieve().body(Map.class);
                    if (queryRes != null && queryRes.get("zp_trans_id") != null) {
                        zpTransId = String.valueOf(queryRes.get("zp_trans_id"));
                    }
                }

                if (zpTransId != null && !zpTransId.isBlank()) {
                    long amount = payment.getAmount().longValue();
                    String description = "Hoan tien don hang " + (payment.getOrder() != null ? payment.getOrder().getOrderCode() : payment.getId());
                    String refundMac = GatewaySigning.hmacSha256(key1, appId + "|" + zpTransId + "|" + amount + "|" + description + "|" + now);

                    Map<String, Object> refundBody = new HashMap<>();
                    refundBody.put("app_id", Integer.parseInt(appId));
                    refundBody.put("m_refund_id", mRefundId);
                    refundBody.put("zp_trans_id", Long.parseLong(zpTransId));
                    refundBody.put("amount", amount);
                    refundBody.put("description", description);
                    refundBody.put("timestamp", now);
                    refundBody.put("mac", refundMac);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> refundRes = client.post().uri(refundEndpoint).body(refundBody).retrieve().body(Map.class);
                    if (refundRes != null) {
                        log.info("ZaloPay refund response for payment {}: {}", payment.getId(), refundRes);
                        Object gatewayRefundId = refundRes.get("refund_id");
                        if (gatewayRefundId != null) {
                            mRefundId = String.valueOf(gatewayRefundId);
                        }
                    }
                } else {
                    log.warn("Không tìm thấy zp_trans_id cho giao dịch ZaloPay {} (app_trans_id={})", payment.getId(), appTransId);
                }
            } catch (Exception ex) {
                log.warn("Lỗi khi gọi API ZaloPay refund cho payment {}: {}", payment.getId(), ex.getMessage());
            }
        }

        payment.setPaymentStatus(TransactionStatus.REFUNDED);
        payment.setRefundTransactionId(mRefundId);
        payment.setRefundedAt(Instant.now());
        return payment;
    }
}
