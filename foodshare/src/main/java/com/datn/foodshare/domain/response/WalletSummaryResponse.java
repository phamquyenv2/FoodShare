package com.datn.foodshare.domain.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WalletSummaryResponse {
    private BigDecimal totalEarned;
    private BigDecimal totalPending;
    private BigDecimal totalCompleted;
    private Integer pendingCount;
    private BigDecimal platformFeePercentage;
    private org.springframework.data.domain.Page<PayoutResponse> transactions;
}
