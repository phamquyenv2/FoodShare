package com.datn.foodshare.domain.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class UserPaymentSummaryResponse {
    private BigDecimal totalSpent;
    private BigDecimal totalRefunded;
    private Long totalTransactions;
}
