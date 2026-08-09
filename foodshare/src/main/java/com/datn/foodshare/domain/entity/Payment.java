package com.datn.foodshare.domain.entity;

import com.datn.foodshare.util.constant.PaymentMethod;
import com.datn.foodshare.util.constant.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Payment extends BaseModel {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus paymentStatus = TransactionStatus.PENDING;

    @Column(unique = true, length = 255)
    private String externalTransactionId;

    @Column
    private Instant paidAt;

    @Column(length = 50)
    private String provider;

    @Column(length = 255)
    private String transferContent;

    @Column
    private Instant expiresAt;

    @Column(unique = true, length = 255)
    private String refundTransactionId;

    @Column
    private Instant refundedAt;
}
