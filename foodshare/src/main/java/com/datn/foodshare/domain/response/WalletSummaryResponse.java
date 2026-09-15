package com.datn.foodshare.domain.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
@Data
@Builder
public class WalletSummaryResponse {
    private BigDecimal totalEarned;
    private BigDecimal earnedBalance;
    private BigDecimal totalPending;
    private BigDecimal totalCompleted;
    private BigDecimal rawAvailableBalance;
    private BigDecimal availableBalance;
    private BigDecimal minPayoutAmount;
    private BigDecimal maxPayoutAmount;
    private Integer pendingCount;
    private BigDecimal platformFeePercentage;
    private org.springframework.data.domain.Page<PayoutResponse> transactions;
}
