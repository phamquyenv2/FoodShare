package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {
    @NotNull(message = "ID tài khoản nhận tiền không được để trống")
    private Long payoutAccountId;
}
