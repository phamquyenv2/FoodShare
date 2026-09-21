package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.domain.entity.Payment;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private OrderStatus orderStatus;
    private TransactionStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private String receiverNote;
    private Instant readyAt;
    private Instant pickupDeadline;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Instant rejectedAt;
    private String rejectionReason;
    private List<OrderDetailInfo> orderDetails;
    private ReceiverInfo receiver;
    private SupplierInfo supplier;
    private Boolean hasRefundRequest;
    private com.datn.foodshare.util.constant.ReportStatus refundStatus;
    private Boolean hasActiveReport;
    private Boolean hasReviewed;
    private ReviewInfo review;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Builder
    public static class ReviewInfo {
        private Long id;
        private Integer rating;
        private String comment;
        private Instant createdAt;
    }

    @Getter
    @Builder
    public static class OrderDetailInfo {
        private Long id;
        private FoodPostInfo foodPost;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal subtotal;
    }

    @Getter
    @Builder
    public static class FoodPostInfo {
        private Long id;
        private String name;
        private String pickupAddress;
        private String imageUrl;
    }

    @Getter
    @Builder
    public static class ReceiverInfo {
        private Long id;
        private String fullName;
        private String phone;
    }

    @Getter
    @Builder
    public static class SupplierInfo {
        private Long businessProfileId;
        private String name;
        private String avatarUrl;
        private String phone;
    }

    public static OrderResponse from(Order order) {
        return from(order, null, null, false);
    }

    public static OrderResponse from(Order order, com.datn.foodshare.domain.entity.Report refundReport) {
        return from(order, refundReport, null, false);
    }

    public static OrderResponse from(Order order, com.datn.foodshare.domain.entity.Report refundReport, com.datn.foodshare.domain.entity.Review review) {
        return from(order, refundReport, review, false);
    }

    public static OrderResponse from(Order order, com.datn.foodshare.domain.entity.Report refundReport, com.datn.foodshare.domain.entity.Review review, boolean hasActiveReport) {
        List<OrderDetailInfo> details = order.getOrderDetails() != null
                ? order.getOrderDetails().stream().map(OrderResponse::mapDetail).toList()
                : List.of();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(mapPaymentStatus(order))
                .paymentMethod(mapPaymentMethod(order))
                .totalAmount(order.getTotalAmount())
                .receiverNote(order.getReceiverNote())
                .readyAt(order.getReadyAt())
                .pickupDeadline(order.getPickupDeadline())
                .deliveredAt(order.getDeliveredAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .rejectedAt(order.getRejectedAt())
                .rejectionReason(order.getRejectionReason())
                .orderDetails(details)
                .receiver(order.getReceiver() != null ? ReceiverInfo.builder()
                        .id(order.getReceiver().getId())
                        .fullName(order.getReceiver().getFullName())
                        .phone(order.getReceiver().getPhone())
                        .build() : null)
                .supplier(order.getBusinessProfile() != null ? SupplierInfo.builder()
                        .businessProfileId(order.getBusinessProfile().getId())
                        .name(order.getBusinessProfile().getName())
                        .avatarUrl(order.getBusinessProfile().getUser() != null ? order.getBusinessProfile().getUser().getAvatarUrl() : null)
                        .phone(order.getBusinessProfile().getUser() != null ? order.getBusinessProfile().getUser().getPhone() : null)
                        .build() : null)
                .hasRefundRequest(refundReport != null)
                .refundStatus(refundReport != null ? refundReport.getReportStatus() : null)
                .hasActiveReport(hasActiveReport)
                .hasReviewed(review != null)
                .review(review != null ? ReviewInfo.builder()
                        .id(review.getId())
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .createdAt(review.getCreatedAt())
                        .build() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private static TransactionStatus mapPaymentStatus(Order order) {
        if (order.getPayments() == null) return null;
        return order.getPayments().stream()
                .max(Comparator.comparing((Payment payment) ->
                                payment.getPaymentStatus() == TransactionStatus.SUCCESS)
                        .thenComparing(Payment::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Payment::getPaymentStatus)
                .orElse(null);
    }

    private static PaymentMethod mapPaymentMethod(Order order) {
        if (order.getPayments() == null) return null;
        return order.getPayments().stream()
                .max(Comparator.comparing((Payment payment) ->
                                payment.getPaymentStatus() == TransactionStatus.SUCCESS)
                        .thenComparing(Payment::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Payment::getMethod)
                .orElse(null);
    }

    private static OrderDetailInfo mapDetail(OrderDetail detail) {
        return OrderDetailInfo.builder()
                .id(detail.getId())
                .foodPost(FoodPostInfo.builder()
                        .id(detail.getFoodPost().getId())
                        .name(detail.getFoodPost().getName())
                        .pickupAddress(detail.getFoodPost().getPickupAddress())
                        .imageUrl(detail.getFoodPost().getImages() != null && !detail.getFoodPost().getImages().isEmpty() 
                                ? detail.getFoodPost().getImages().get(0).getImageUrl() : null)
                        .build())
                .unitPrice(detail.getUnitPrice())
                .quantity(detail.getQuantity())
                .subtotal(detail.getSubtotal())
                .build();
    }
}
