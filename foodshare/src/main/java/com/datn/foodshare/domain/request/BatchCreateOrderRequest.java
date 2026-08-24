package com.datn.foodshare.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateOrderRequest {

    @NotEmpty(message = "Danh sách đơn hàng không được để trống")
    @Valid
    private List<CreateOrderRequest> orders;
}
