package com.datn.foodshare.domain.request;

import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectOrderRequest {

    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
    @JsonAlias("reason")
    private String rejectionReason;
}
