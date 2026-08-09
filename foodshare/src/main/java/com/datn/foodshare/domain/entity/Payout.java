package com.datn.foodshare.domain.entity;

import com.datn.foodshare.util.constant.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Payout extends BaseModel {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_account_id", nullable = false)
    private PayoutAccount payoutAccount;

    @Column(nullable = false, unique = true, length = 30)
    private String payoutCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus payoutStatus = TransactionStatus.PENDING;

    @Column(unique = true, length = 255)
    private String externalTransactionId;

    @Column
    private Instant completedAt;

    @Column
    private Instant failedAt;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 1000)
    private String note;
}
