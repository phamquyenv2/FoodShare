package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreatePayoutAccountRequest;
import com.datn.foodshare.domain.request.CreatePayoutRequest;
import com.datn.foodshare.domain.response.PayoutAccountResponse;
import com.datn.foodshare.domain.response.PayoutResponse;
import com.datn.foodshare.service.PayoutService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @PostMapping("/accounts")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Thêm tài khoản nhận tiền thành công")
    public ResponseEntity<PayoutAccountResponse> createPayoutAccount(@Valid @RequestBody CreatePayoutAccountRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(payoutService.createPayoutAccount(request));
    }

    @GetMapping("/accounts")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy danh sách tài khoản nhận tiền thành công")
    public ResponseEntity<List<PayoutAccountResponse>> getMyPayoutAccounts() throws PermissionException {
        return ResponseEntity.ok(payoutService.getMyPayoutAccounts());
    }

    @PostMapping("/order/{orderId}")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Tạo yêu cầu rút tiền thành công")
    public ResponseEntity<PayoutResponse> createPayout(
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody CreatePayoutRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(payoutService.createPayout(orderId, request));
    }

    @GetMapping("/my/wallet")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy thông vị ví và lịch sử giao dịch thành công")
    public ResponseEntity<com.datn.foodshare.domain.response.WalletSummaryResponse> getMyWallet(
            @org.springframework.data.web.PageableDefault(sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable) throws PermissionException {
        return ResponseEntity.ok(payoutService.getWalletSummary(pageable));
    }
}
