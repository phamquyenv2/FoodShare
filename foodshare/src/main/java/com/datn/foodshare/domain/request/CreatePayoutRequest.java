package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePayoutRequest {
    @NotNull(message = "ID tài khoản nhận tiền không được để trống")
    private Long payoutAccountId;

    @DecimalMin(value = "0.01", message = "Số tiền rút phải lớn hơn 0")
    private java.math.BigDecimal amount;

    public CreatePayoutRequest(Long payoutAccountId) {
        this.payoutAccountId = payoutAccountId;
    }

    public CreatePayoutRequest(Long payoutAccountId, java.math.BigDecimal amount) {
        this.payoutAccountId = payoutAccountId;
        this.amount = amount;
    }
}
