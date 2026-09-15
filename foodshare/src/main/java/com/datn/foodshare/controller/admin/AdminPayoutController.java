package com.datn.foodshare.controller.admin;

import com.datn.foodshare.domain.request.RejectPayoutRequest;
import com.datn.foodshare.domain.response.PayoutResponse;
import com.datn.foodshare.service.PayoutService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.constant.PayoutStatus;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payouts")
@Secured("ROLE_ADMIN")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;

    @GetMapping
    @ApiMessage("Lấy danh sách yêu cầu rút tiền thành công")
    public ResponseEntity<Page<PayoutResponse>> getPayouts(
            @RequestParam(name = "status", required = false) PayoutStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) throws PermissionException {
        return ResponseEntity.ok(payoutService.getPayoutRequestsForAdmin(status, pageable));
    }

    @PatchMapping("/{id}/approve")
    @ApiMessage("Duyệt yêu cầu rút tiền thành công")
    public ResponseEntity<PayoutResponse> approve(
            @PathVariable(name = "id") Long id) throws PermissionException {
        return ResponseEntity.ok(payoutService.approvePayout(id));
    }

    @PatchMapping("/{id}/reject")
    @ApiMessage("Từ chối yêu cầu rút tiền thành công")
    public ResponseEntity<PayoutResponse> reject(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody RejectPayoutRequest request) throws PermissionException {
        return ResponseEntity.ok(payoutService.rejectPayout(id, request.getReason()));
    }
}
