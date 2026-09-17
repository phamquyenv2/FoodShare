package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.service.AdminOrderService;
import com.datn.foodshare.util.constant.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN")
public class AdminOrderController {
    private final AdminOrderService service;

    @GetMapping
    public Page<OrderResponse> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OrderStatus parsed = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try { parsed = OrderStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException ignored) { }
        }
        return service.search(keyword, parsed, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id) { return service.detail(id); }

    @PostMapping("/{id}/refund")
    public OrderResponse refund(@PathVariable Long id) {
        return service.refundOrder(id);
    }
}
