package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.util.constant.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private OrderStatus orderStatus;
    private BigDecimal totalAmount;
    private String receiverNote;
    private Instant readyAt;
    private Instant pickupDeadline;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Instant rejectedAt;
    private List<OrderDetailInfo> orderDetails;
    private ReceiverInfo receiver;
    private SupplierInfo supplier;
    private Instant createdAt;
    private Instant updatedAt;

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
    }

    @Getter
    @Builder
    public static class ReceiverInfo {
        private Long id;
        private String fullName;
    }

    @Getter
    @Builder
    public static class SupplierInfo {
        private Long businessProfileId;
        private String name;
    }

    public static OrderResponse from(Order order) {
        List<OrderDetailInfo> details = order.getOrderDetails().stream()
                .map(OrderResponse::mapDetail)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .orderStatus(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .receiverNote(order.getReceiverNote())
                .readyAt(order.getReadyAt())
                .pickupDeadline(order.getPickupDeadline())
                .deliveredAt(order.getDeliveredAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .rejectedAt(order.getRejectedAt())
                .orderDetails(details)
                .receiver(ReceiverInfo.builder()
                        .id(order.getReceiver().getId())
                        .fullName(order.getReceiver().getFullName())
                        .build())
                .supplier(SupplierInfo.builder()
                        .businessProfileId(order.getBusinessProfile().getId())
                        .name(order.getBusinessProfile().getName())
                        .build())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private static OrderDetailInfo mapDetail(OrderDetail detail) {
        return OrderDetailInfo.builder()
                .id(detail.getId())
                .foodPost(FoodPostInfo.builder()
                        .id(detail.getFoodPost().getId())
                        .name(detail.getFoodPost().getName())
                        .pickupAddress(detail.getFoodPost().getPickupAddress())
                        .build())
                .unitPrice(detail.getUnitPrice())
                .quantity(detail.getQuantity())
                .subtotal(detail.getSubtotal())
                .build();
    }
}
