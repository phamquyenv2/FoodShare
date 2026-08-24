package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Bài đăng không được để trống")
    private Long foodPostId;

    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private int quantity;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String receiverNote;
}
