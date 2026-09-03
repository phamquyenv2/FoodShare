package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.service.OrderService;
import com.datn.foodshare.util.annotation.ApiMessage;
import com.datn.foodshare.util.error.PermissionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Tạo đơn tiếp nhận thành công")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @PostMapping("/batch")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Tạo đơn tiếp nhận hàng loạt thành công")
    public ResponseEntity<java.util.List<OrderResponse>> batchCreateOrders(@Valid @RequestBody com.datn.foodshare.domain.request.BatchCreateOrderRequest request) throws PermissionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.batchCreateOrders(request));
    }

    @GetMapping("/my")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Lấy danh sách đơn tiếp nhận thành công")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws PermissionException {
        return ResponseEntity.ok(orderService.getMyOrders(pageable));
    }

    @GetMapping("/{id}")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Lấy chi tiết đơn tiếp nhận thành công")
    public ResponseEntity<OrderResponse> getOrderDetail(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.getOrderDetail(id));
    }

    @PatchMapping("/{id}/cancel")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Hủy đơn tiếp nhận thành công")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @GetMapping("/supplier")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Lấy danh sách đơn tiếp nhận của nhà cung cấp thành công")
    public ResponseEntity<Page<OrderResponse>> getSupplierOrders(
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) com.datn.foodshare.util.constant.OrderStatus status,
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws PermissionException {
        return ResponseEntity.ok(orderService.getSupplierOrders(status, keyword, pageable));
    }

    @PatchMapping("/{id}/accept")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Chấp nhận đơn tiếp nhận thành công")
    public ResponseEntity<OrderResponse> acceptOrder(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.acceptOrder(id));
    }

    @PatchMapping("/{id}/reject")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Từ chối đơn tiếp nhận thành công")
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable("id") Long id,
            @Valid @RequestBody com.datn.foodshare.domain.request.RejectOrderRequest request) throws PermissionException {
        return ResponseEntity.ok(orderService.rejectOrder(id, request));
    }

    @PatchMapping("/{id}/ready")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Xác nhận chuẩn bị món xong thành công")
    public ResponseEntity<OrderResponse> readyForPickupOrder(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.readyForPickupOrder(id));
    }

    @PatchMapping("/{id}/deliver")
    @Secured("ROLE_SUPPLIER")
    @ApiMessage("Xác nhận đã giao món thành công")
    public ResponseEntity<OrderResponse> deliverOrder(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.deliverOrder(id));
    }

    @PatchMapping("/{id}/complete")
    @Secured({"ROLE_RECIPIENT", "ROLE_ORGANIZATION"})
    @ApiMessage("Xác nhận đã nhận món thành công")
    public ResponseEntity<OrderResponse> completeOrder(@PathVariable("id") Long id) throws PermissionException {
        return ResponseEntity.ok(orderService.completeOrder(id));
    }
}
