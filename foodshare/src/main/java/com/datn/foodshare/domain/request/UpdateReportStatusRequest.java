package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateReportStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ReportStatus reportStatus;

    private String response;
}
