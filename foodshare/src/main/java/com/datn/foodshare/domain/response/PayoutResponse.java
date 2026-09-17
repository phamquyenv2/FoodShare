package com.datn.foodshare.domain.response;

import com.datn.foodshare.domain.entity.Payout;
import com.datn.foodshare.util.constant.PayoutStatus;
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
    private Long businessProfileId;
    private String supplierName;
    private String payoutCode;
    private BigDecimal grossAmount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private BigDecimal requestedAmount;
    private PayoutStatus status;
    private String bankCode;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private String externalTransactionId;
    private Instant completedAt;
    private Instant failedAt;
    private String failureReason;
    private String rejectionReason;
    private Instant reviewedAt;
    private Instant createdAt;

    public static PayoutResponse from(Payout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .orderId(payout.getOrder() == null ? null : payout.getOrder().getId())
                .payoutAccountId(payout.getPayoutAccount().getId())
                .businessProfileId(payout.getBusinessProfile().getId())
                .supplierName(payout.getBusinessProfile().getName())
                .payoutCode(payout.getPayoutCode())
                .grossAmount(payout.getGrossAmount())
                .platformFee(payout.getPlatformFee())
                .netAmount(payout.getNetAmount())
                .requestedAmount(payout.getRequestedAmount())
                .status(payout.getPayoutStatus())
                .bankCode(payout.getBankCode())
                .bankName(payout.getBankName())
                .accountNumber(payout.getAccountNumber())
                .accountHolderName(payout.getAccountHolderName())
                .externalTransactionId(payout.getExternalTransactionId())
                .completedAt(payout.getCompletedAt())
                .failedAt(payout.getFailedAt())
                .failureReason(payout.getFailureReason())
                .rejectionReason(payout.getRejectionReason())
                .reviewedAt(payout.getReviewedAt())
                .createdAt(payout.getCreatedAt())
                .build();
    }
}
