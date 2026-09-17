package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.PaymentResponse;
import com.datn.foodshare.service.AdminPaymentService;
import com.datn.foodshare.util.constant.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN")
public class AdminPaymentController {
    private final AdminPaymentService service;

    @GetMapping
    public Page<PaymentResponse> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status,
                                      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TransactionStatus parsed = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try { parsed = TransactionStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        return service.search(keyword, parsed, pageable);
    }

    @GetMapping("/{id}")
    public PaymentResponse detail(@PathVariable Long id) { return service.detail(id); }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id) {
        return service.refund(id);
    }
}
