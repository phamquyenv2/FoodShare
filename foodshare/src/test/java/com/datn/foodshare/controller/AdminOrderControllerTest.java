package com.datn.foodshare.controller;

import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.service.AdminOrderService;
import com.datn.foodshare.util.constant.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock
    private AdminOrderService adminOrderService;

    private AdminOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminOrderController(adminOrderService);
    }

    @Test
    void list_withValidStatus_callsSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrderResponse> page = new PageImpl<>(List.of(OrderResponse.builder().id(1L).build()));
        when(adminOrderService.search("key", OrderStatus.COMPLETED, pageable)).thenReturn(page);

        Page<OrderResponse> result = controller.list("key", "completed", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(adminOrderService).search("key", OrderStatus.COMPLETED, pageable);
    }

    @Test
    void list_withAllOrInvalidStatus_callsSearchWithNullStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrderResponse> page = new PageImpl<>(List.of());
        when(adminOrderService.search(null, null, pageable)).thenReturn(page);

        Page<OrderResponse> r1 = controller.list(null, "all", pageable);
        assertNotNull(r1);

        Page<OrderResponse> r2 = controller.list(null, "invalid_status", pageable);
        assertNotNull(r2);
    }

    @Test
    void detail_callsService() {
        OrderResponse response = OrderResponse.builder().id(5L).build();
        when(adminOrderService.detail(5L)).thenReturn(response);

        OrderResponse result = controller.detail(5L);

        assertEquals(5L, result.getId());
        verify(adminOrderService).detail(5L);
    }

    @Test
    void refund_callsService() {
        OrderResponse response = OrderResponse.builder().id(5L).build();
        when(adminOrderService.refundOrder(5L)).thenReturn(response);

        OrderResponse result = controller.refund(5L);

        assertEquals(5L, result.getId());
        verify(adminOrderService).refundOrder(5L);
    }
}
