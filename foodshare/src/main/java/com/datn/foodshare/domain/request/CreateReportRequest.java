package com.datn.foodshare.domain.request;

import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 200, message = "Tiêu đề không vượt quá 200 ký tự")
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @NotNull(message = "Loại báo cáo không được để trống")
    private ReportType reportType;

    @Size(max = 1000, message = "URL bằng chứng không vượt quá 1000 ký tự")
    private String evidenceUrl;

    @NotNull(message = "Loại tham chiếu không được để trống")
    private ReportReferenceType referenceType;

    @NotNull(message = "ID tham chiếu không được để trống")
    private Long referenceId;
}
