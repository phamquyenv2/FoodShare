package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Payout;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PayoutResponse {
    private Long id;
    private Long orderId;
    private Long payoutAccountId;
    private String payoutCode;
    private BigDecimal grossAmount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private TransactionStatus status;
    private String externalTransactionId;
    private Instant completedAt;
    private Instant failedAt;
    private String failureReason;

    public static PayoutResponse from(Payout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .orderId(payout.getOrder().getId())
                .payoutAccountId(payout.getPayoutAccount().getId())
                .payoutCode(payout.getPayoutCode())
                .grossAmount(payout.getGrossAmount())
                .platformFee(payout.getPlatformFee())
                .netAmount(payout.getNetAmount())
                .status(payout.getPayoutStatus())
                .externalTransactionId(payout.getExternalTransactionId())
                .completedAt(payout.getCompletedAt())
                .failedAt(payout.getFailedAt())
                .failureReason(payout.getFailureReason())
                .build();
    }
}
