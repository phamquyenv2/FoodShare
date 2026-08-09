package com.datn.foodshare.domain.entity;

import com.datn.foodshare.util.constant.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends BaseModel {

    @Column(nullable = false, unique = true, length = 30)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_profile_id", nullable = false)
    private BusinessProfile businessProfile;

    @Column(length = 1000)
    private String receiverNote;

    @Column
    private Instant readyAt;

    @Column
    private Instant pickupDeadline;

    @Column
    private Instant deliveredAt;

    @Column
    private Instant completedAt;

    @Column
    private Instant cancelledAt;

    @Column(length = 1000)
    private String cancellationReason;

    @Column
    private Instant rejectedAt;

    @Column(length = 1000)
    private String rejectionReason;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<Payment> payments = new ArrayList<>();

    @JsonIgnore
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Review review;

    @JsonIgnore
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Payout payout;
}
